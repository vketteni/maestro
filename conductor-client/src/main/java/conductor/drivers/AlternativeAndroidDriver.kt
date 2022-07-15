/*
 *
 *  Copyright (c) 2022 mobile.dev inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 */

package conductor.drivers

import conductor.Conductor
import conductor.DeviceInfo
import conductor.Driver
import conductor.Point
import conductor.TreeNode
import conductor.android.models.DeviceInfoResponse
import conductor.android.models.TapRequest
import conductor.android.models.ViewHierarchyResponse
import dadb.AdbShellResponse
import dadb.AdbShellStream
import dadb.Dadb
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.json.serializer.KotlinxSerializer
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import okio.buffer
import okio.sink
import okio.source
import org.slf4j.LoggerFactory
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeoutException
import javax.xml.parsers.DocumentBuilderFactory

class AlternativeAndroidDriver(
    private val dadb: Dadb,
    private val hostPort: Int,
) : Driver {

    private val baseUrl = "http://localhost:$hostPort"
    private val client by lazy {
        HttpClient(CIO) {
            install(JsonFeature) {
                serializer = KotlinxSerializer()
            }
        }
    }
    private val documentBuilderFactory = DocumentBuilderFactory.newInstance()

    private var instrumentationSession: AdbShellStream? = null

    override fun name(): String {
        return "Android Device ($dadb)"
    }

    override fun open() {
        installConductorApks()

        instrumentationSession = dadb.openShell()
        instrumentationSession?.write(
            "am instrument -w -m -e debug false " +
                "-e class 'dev.mobile.conductor.ConductorDriverService#grpcServer' " +
                "dev.mobile.conductor.test/androidx.test.runner.AndroidJUnitRunner &\n"
        )

        try {
            awaitLaunch()
        } catch (ignored: InterruptedException) {
            instrumentationSession?.close()
            return
        }
    }

    private fun awaitLaunch() {
        val startTime = System.currentTimeMillis()

        while (System.currentTimeMillis() - startTime < SERVER_LAUNCH_TIMEOUT_MS) {
            try {
                dadb.open("tcp:7001").close()
                return
            } catch (ignored: Exception) {
                // Continue
            }

            Thread.sleep(100)
        }

        throw TimeoutException("Conductor Android driver did not start up in time")
    }

    override fun close() {
        uninstallConductorApks()
        instrumentationSession?.close()
        instrumentationSession = null
        client.close()
    }

    override fun deviceInfo(): DeviceInfo {
        val response = runBlocking {
            dadb.tcpForward(
                hostPort,
                7001
            ).use {
                client.get<DeviceInfoResponse>("$baseUrl/device/info")
            }
        }

        return DeviceInfo(
            widthPixels = response.widthPixels,
            heightPixels = response.heightPixels
        )
    }

    override fun launchApp(appId: String) {
        if (!isPackageInstalled(appId)) {
            throw IllegalArgumentException("Package $appId is not installed")
        }

        shell("am force-stop $appId")
        shell("monkey --pct-syskeys 0 -p $appId 1")
    }

    override fun tap(point: Point) {
        runBlocking {
            dadb.tcpForward(
                hostPort,
                7001
            ).use {
                client.post<Unit>(
                    "$baseUrl/device/tap"
                ) {
                    contentType(ContentType.Application.Json)
                    body = TapRequest(
                        x = point.x,
                        y = point.y
                    )
                }
            }
        }
    }

    override fun contentDescriptor(): TreeNode {
        LOGGER.info("Get content descriptor")

        val response = runBlocking {
            dadb.tcpForward(
                hostPort,
                7001
            ).use {
                client.get<ViewHierarchyResponse>("$baseUrl/device/hierarchy")
            }
        }

        LOGGER.info("Parsing content descriptor")

        val document = documentBuilderFactory
            .newDocumentBuilder()
            .parse(response.hierarchy.byteInputStream())


        LOGGER.info("Mapping content descriptor")

        return mapHierarchy(document)
    }

    override fun scrollVertical() {
        dadb.shell("input swipe 500 1000 700 -900 2000")
    }

    override fun backPress() {
        dadb.shell("input keyevent 4")
    }

    override fun inputText(text: String) {
        dadb.shell("input text $text")
    }

    private fun mapHierarchy(node: Node): TreeNode {
        val attributes = if (node is Element) {
            val attributesBuilder = mutableMapOf<String, String>()

            if (node.hasAttribute("text")) {
                val text = node.getAttribute("text")

                if (text.isNotBlank()) {
                    attributesBuilder["text"] = text
                } else if (node.hasAttribute("content-desc")) {
                    // Using content-desc as fallback for text
                    attributesBuilder["text"] = node.getAttribute("content-desc")
                } else {
                    attributesBuilder["text"] = text
                }
            }

            if (node.hasAttribute("resource-id")) {
                attributesBuilder["resource-id"] = node.getAttribute("resource-id")
            }

            if (node.hasAttribute("clickable")) {
                attributesBuilder["clickable"] = node.getAttribute("clickable")
            }

            if (node.hasAttribute("bounds")) {
                attributesBuilder["bounds"] = node.getAttribute("bounds")
            }

            attributesBuilder
        } else {
            emptyMap()
        }

        val children = mutableListOf<TreeNode>()
        val childNodes = node.childNodes
        (0 until childNodes.length).forEach { i ->
            children += mapHierarchy(childNodes.item(i))
        }

        return TreeNode(
            attributes = attributes,
            children = children,
            clickable = (node as? Element)
                ?.getAttribute("clickable")
                ?.let { it == "true" }
                ?: false
        )
    }

    private fun installConductorApks() {
        val conductorAppApk = File.createTempFile("conductor-app", ".apk")
        val conductorServerApk = File.createTempFile("conductor-server", ".apk")
        Conductor::class.java.getResourceAsStream("/conductor-app.apk")?.let {
            val bufferedSink = conductorAppApk.sink().buffer()
            bufferedSink.writeAll(it.source())
            bufferedSink.flush()
        }
        Conductor::class.java.getResourceAsStream("/conductor-server.apk")?.let {
            val bufferedSink = conductorServerApk.sink().buffer()
            bufferedSink.writeAll(it.source())
            bufferedSink.flush()
        }
        install(conductorAppApk)
        if (!isPackageInstalled("dev.mobile.conductor")) {
            throw IllegalStateException("dev.mobile.conductor was not installed")
        }
        install(conductorServerApk)
    }

    private fun uninstallConductorApks() {
        if (isPackageInstalled("dev.mobile.conductor.test")) {
            uninstall("dev.mobile.conductor.test")
        }
        if (isPackageInstalled("dev.mobile.conductor")) {
            uninstall("dev.mobile.conductor")
        }
    }

    private fun install(apkFile: File) {
        try {
            dadb.install(apkFile)
        } catch (installError: IOException) {
            throw IOException("Failed to install apk " + apkFile + ": " + installError.message, installError)
        }
    }

    private fun uninstall(packageName: String) {
        try {
            dadb.uninstall(packageName)
        } catch (error: IOException) {
            throw IOException("Failed to uninstall package " + packageName + ": " + error.message, error)
        }
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        val output: String = shell("pm list packages $packageName")
        return output.split("\n".toRegex())
            .map { line -> line.split(":".toRegex()) }
            .filter { parts -> parts.size == 2 }
            .map { parts -> parts[1] }
            .any { linePackageName -> linePackageName == packageName }
    }

    private fun shell(command: String): String {
        val response: AdbShellResponse = try {
            dadb.shell(command)
        } catch (e: IOException) {
            throw IOException(command, e)
        }

        if (response.exitCode != 0) {
            throw IOException("$command: ${response.allOutput}")
        }
        return response.output
    }

    companion object {

        private const val SERVER_LAUNCH_TIMEOUT_MS = 5000
        private val LOGGER = LoggerFactory.getLogger(AlternativeAndroidDriver::class.java)

    }
}
