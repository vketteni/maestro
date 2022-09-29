package maestro.mock

import maestro.utils.NetUtil
import maestro.utils.NetUtil.waitForPortToBeFree
import maestro.utils.NetUtil.waitForPortToBeInUse
import org.slf4j.LoggerFactory
import org.zeroturnaround.exec.ProcessExecutor
import org.zeroturnaround.exec.ProcessResult
import org.zeroturnaround.exec.stream.slf4j.Slf4jStream
import java.io.File
import java.nio.file.Path
import java.util.concurrent.Future
import kotlin.io.path.absolutePathString

class Proxy(
    val port: Int = 5050,
    val host: String = NetUtil.getHostAddress()
) {
    private var process: Future<ProcessResult>? = null

    private val defaultScriptFilepath: Path = File("replay-script.py").toPath()

    fun startRecord(filepath: Path) {
        val params = listOf(
            Pair("-p", "$port"),
            Pair("-w", filepath.absolutePathString()), // file output
        )

        startMitmproxy(params)

        logger.info("Proxy started (Record)")
    }

    fun startReplay(scriptFilePath: Path? = null, replayFilePath: Path) {
        val paramReplayScript = scriptFilePath ?: defaultScriptFilepath
        val params = listOf(
            Pair("-p", "$port"),
            Pair("-s", paramReplayScript.absolutePathString()),
            Pair("--set", "replay=${replayFilePath.absolutePathString()}"),
            Pair("--set", "stats=replay_stats.log"),
            Pair("-w", "playback.replay"),
        )

        startMitmproxy(params)

        logger.info("Proxy started (Replay)")
    }

    private fun startMitmproxy(args: List<Pair<String, String>>) {
        val execute = mutableListOf<String>()
        execute.add("mitmdump")
        for (arg in args) {
            execute.add(arg.first)
            execute.add(arg.second)
        }
        val output = File("mitm_record.log").outputStream()
        logger.info("Running cmd: $execute")
        process = ProcessExecutor()
            .command(execute)
            .redirectOutput(Slf4jStream.ofCaller().asInfo())
            .redirectOutputAlsoTo(output)
            .redirectError(Slf4jStream.ofCaller().asError())
            .destroyOnExit()
            .start().future

        waitForPortToBeInUse(host, port)
    }

    fun stop() {
        process?.cancel(true)
        process = null
        waitForPortToBeFree(host, port)
        logger.info("Proxy stopped")
    }

    companion object {
        private val logger = LoggerFactory.getLogger(Proxy::class.java)
    }
}