from xmlrpc.client import Boolean
from json import load as json_load, dump as json_dump, JSONDecodeError

from mitmproxy import io, http, ctx
from mitmproxy.exceptions import FlowReadException
import time
from difflib import Match, SequenceMatcher as SM
import math
import atexit

"""
# Record
mitmdump -w app.replay

# Replay from file
mitmdump -s script.py --set replay=app.replay

# Replay from file + save replay session into a new file
mitmdump -s script.py --set replay=app.replay -w session.replay

# Replay from file but use a flow file as http source
mitmdump -s script.py -r session.replay --set replay=app.replay

# View/Edit replay file in Web UI
mitmweb -n -r file.replay
"""

VERSION = "2.0"

print(
"""
-------------------------
# Network Replay Plugin
# Version: {}
-------------------------

[START] {}
""".format(
    VERSION,
    time.strftime("%Y-%m-%d %H:%M"))
)

# Configs
NAME = "N/A"

MAX_BYTE_SIZE_FUZZY = 100000/2 * 1 # Max content type size (bytes) for doing fuzzy matching
CONTENT_TYPE_FUZZY = ["text", "xml", "html", "json"]
THRESHOLD_MATCH_URL = 0.6
THRESHOLD_MATCH_CONTENT = 0.0
THRESHOLD_PATH_COMPONENTS = 0.9

STATS_OUTPUT_FILE = None
REPORTER = None

@atexit.register
def on_exit():
    REPORTER.pp_stats()
    REPORTER.generate_report()
    print("\n[END]: {}".format(time.strftime("%Y-%m-%d %H:%M")))

# Helpers

class FlowMatcher:
    def __init__(self, flows) -> None:
        self.flows = flows or []

    def load(self, flows):
        self.flows = flows
    
    def get_text_similarity_score(self, text1, text2):
        return SM(None, text1, text2).quick_ratio()

    def get_content_type(self, headers) -> Boolean:
        for key in headers:
            if key.lower() == "content-type":
                return headers[key]
        return None
    
    def is_same_request(self, flow1, flow2):
        m1 = flow1.request.method
        m2 = flow2.request.method
        h1 = flow1.request.pretty_host
        h2 = flow2.request.pretty_host
        c1 = self.get_content_type(flow1.request.headers)
        c2 = self.get_content_type(flow2.request.headers)
        p1 = '/'.join(flow1.request.path_components)
        p2 = '/'.join(flow2.request.path_components)

        is_same_request = m1 == m2 and h1 == h2 and c1 == c2

        path_components_score = 0
        if is_same_request:
            path_components_score = self.get_text_similarity_score(p1, p2)
        
        return is_same_request and path_components_score >= THRESHOLD_PATH_COMPONENTS

    def match_score(self, flow1, flow2):
        result = {}
        similarity_score_url = 0
        similarity_score_content = 0

        # URL
        url1 = flow1.request.pretty_url
        url2 = flow2.request.pretty_url
        similarity_score_url = self.get_text_similarity_score(url1, url2)

        # Content
        h1 = self.get_content_type(flow1.request.headers) or ""
        h2 = self.get_content_type(flow2.request.headers) or ""

        c1 = flow1.request.content or ""
        c2 = flow2.request.content or ""
        s1 = len(c1)
        s2 = len(c2)

        is_same_content_type = h1 == h2
        is_same_content_size = c1 == c2
        is_content_text = any(h.lower() in h1.lower() for h in CONTENT_TYPE_FUZZY)
        is_content_large = s1 > MAX_BYTE_SIZE_FUZZY or s2 > MAX_BYTE_SIZE_FUZZY
        perform_fuzzy_matching = is_content_text and not is_content_large
        
        if not is_same_content_type:
            similarity_score_content = 0
        elif is_same_content_size:
            similarity_score_content = 1.0
        elif perform_fuzzy_matching:
            similarity_score_content = self.get_text_similarity_score(c1, c2)

        result['score_url'] = similarity_score_url
        result['score_content'] = similarity_score_content
        
        return result

    def calculate_top_match(self, matches):
        result = {}
        matches = sorted(matches, key = lambda x: (x["score_url"], x["score_content"]), reverse=True)
        match = None

        top_match = next(iter(matches), None)
        if top_match:
            su = top_match["score_url"]
            sc = top_match["score_content"]
            is_score_below_threshold = su < THRESHOLD_MATCH_URL or sc < THRESHOLD_MATCH_CONTENT
            if not is_score_below_threshold:
                match = top_match

        result["matches"] = matches
        result["match"] = match

        return result
        

    def find_match(self, requestFlow) -> http.HTTPFlow:
        matches = []
        for flow in self.flows:
            if self.is_same_request(flow, requestFlow):
                result = self.match_score(flow, requestFlow)
                result["flow"] = flow
                matches.append(result)
        return self.calculate_top_match(matches)


class Replay:
    def __init__(self):
        self.stats = {}
        global REPORTER
        REPORTER = Reporter()

    def load(self, loader):
        loader.add_option(
            "replay", str, "", "Replay file that contains flows"
        )
        loader.add_option(
            "config", str, "", "Config file for replay"
        )
        loader.add_option(
            "stats", str, "", "Stats output file"
        )

        # helps with gRPC
        loader.add_option(
            name="stream_large_bodies",
            typespec=int,
            default=0,
            help="Stream large bodies",
        )
    
    def set_configs(self, data):
        global NAME
        global THRESHOLD_MATCH_URL
        global THRESHOLD_MATCH_CONTENT
        global THRESHOLD_PATH_COMPONENTS
        global MAX_BYTE_SIZE_FUZZY
        global CONTENT_TYPE_FUZZY

        NAME = data.get("name") or NAME
        settings = data.get("fuzzy_matching")
        if settings:
            THRESHOLD_MATCH_URL = settings.get("threshold_url") or THRESHOLD_MATCH_URL
            THRESHOLD_MATCH_CONTENT = settings.get("threshold_content") or THRESHOLD_MATCH_CONTENT
            THRESHOLD_PATH_COMPONENTS = settings.get("threshold_path_components") or THRESHOLD_PATH_COMPONENTS
            MAX_BYTE_SIZE_FUZZY = settings.get("max_content_size") or MAX_BYTE_SIZE_FUZZY
            CONTENT_TYPE_FUZZY = settings.get("content_type") or CONTENT_TYPE_FUZZY

    def parse_flows_file(self, filepath):
        print("-Parsing file: {}".format(filepath))
        flows = []
        with open(filepath, "rb") as logfile:
            freader = io.FlowReader(logfile)
            try:
                for f in freader.stream():
                    if isinstance(f, http.HTTPFlow):
                        flows.append(f)
            except FlowReadException as e:
                raise Exception(f"Flow file corrupt: {e}")
        return flows

    def parse_config_file(self, filepath):
        print("-Parsing file: {}".format(filepath))
        with open(filepath, "rb") as cf:
            try:
                return json_load(cf)
            except JSONDecodeError as e:
                raise Exception("Unable to read config file: {}".format(filepath))       
        
    def running(self):

        # flows
        optionReplayFile = ctx.options.replay
        if not optionReplayFile:
            raise Exception("--set replay=[file] missing")
        flows = self.parse_flows_file(optionReplayFile)
        self.matcher = FlowMatcher(flows)

        # config
        optionConfigFile = ctx.options.config
        if optionConfigFile:
            data = self.parse_config_file(optionConfigFile)
            self.set_configs(data)

        # report
        optionStatsFile = ctx.options.stats
        if optionStatsFile:
            global STATS_OUTPUT_FILE
            STATS_OUTPUT_FILE = optionStatsFile

        print("Session name: {}".format(NAME))

    def done(self):
        print("Exiting Replay Plugin")

    async def request(self, flow):
        result = self.matcher.find_match(flow)
        result['request'] = flow

        REPORTER.add_match_result(result)
        REPORTER.pp_match_result(result)

        match = result["match"]
        if match:
            response = match["flow"].response
            flow.response = response
        else:
            flow.response = http.Response.make(
            400,  # (optional) status code
            b"No replay response found",  # (optional) content
            {"Content-Type": "text/html"}  # (optional) headers
        )

class Reporter:

    def __init__(self):
        self.log = []

    def add_match_result(self, result):
        self.log.append(result)

    def generate_report(self):
        filename = STATS_OUTPUT_FILE

        if not filename:
            print("Skipped generating report. Output filepath not defined.")
            return

        stats = self.calculate_stats()
        with open(filename, "w+") as f:
            json_dump(stats, f, ensure_ascii=True, indent=4, sort_keys=True)
        
        print("\nReport generated: {}".format(filename))

    def pp_match_result(self, result):
        request = result['request']
        matches = result["matches"]
        match = result["match"]

        u1 = request.request.pretty_url[:100]

        log = "---\n"
        log += "Request: {}\n".format(u1)
        log += "Possible matches: {}\n".format(len(matches))
        if match:
            u2 = match["flow"].request.pretty_url[:100]
            su = match["score_url"]
            sc = match["score_content"]
            log += "Match:\n"
            log += " url: {}\n".format(u2)
            log += " score_url: {}\n".format(su)
            log += " score_content: {}\n".format(sc)
        log += "Result: {}\n".format("[MATCH]" if match else "[NO MATCH]")
        log += "---\n"

        ctx.log.info(log)

    def calculate_stats(self):
        total_requests = len(self.log)
        total_matches = 0
        no_match_urls = []

        for result in self.log:
            if result['match']:
                total_matches += 1
            else:
                url = result['request'].request.pretty_url
                url = (url[:100] + ' [...]') if len(url) > 100 else url
                no_match_urls.append(url)
        
        success_rate = ((total_matches/total_requests) * 100) if total_requests > 0 else 0

        return {
            "total_requests": total_requests,
            "total_matches": total_matches,
            "urls_no_match": no_match_urls,
            "success_rate": success_rate
        }

    def pp_stats(self):
        stats = self.calculate_stats()
        urls = stats["urls_no_match"]

        log = "\n-*-*- Stats -*-*-\n"
        log += "Matches: {}/{}\n".format(stats["total_matches"], stats["total_requests"])
        log += "Success rate: {}%\n".format(int(stats["success_rate"]))
        if len(urls) > 0:
            u = '\n'.join(urls)
            log += "\nNo match urls:\n{}".format(u)

        print(log)






addons = [
    Replay()
]