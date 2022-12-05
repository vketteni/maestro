package maestro.cli.device.ios

import maestro.Maestro
import maestro.cli.device.Device
import okio.buffer
import okio.sink
import okio.source
import org.rauschig.jarchivelib.ArchiverFactory
import java.io.File
import java.lang.RuntimeException
import java.nio.file.Files

object IOSUiTestRunner {

    private const val UI_TEST_RUNNER_PATH = "/maestro-driver-iosUITests-Runner.zip"
    private const val XCTEST_RUN_PATH = "/maestro-driver-ios-config.xctestrun"
    private const val UI_TEST_HOST_PATH = "/maestro-driver-ios.zip"
    const val UI_TEST_RUNNER_APP_BUNDLE_ID = "dev.mobile.maestro-driver-iosUITests.xctrunner"

    fun install(device: Device.Connected) {
        val iOSDriverRunnerApp = File.createTempFile("maestro-driver-iosUITests-Runner", ".zip")
        val uncompressedFile = Files.createTempDirectory("ui_test_runner_uncompressed").toFile()
        Maestro::class.java.getResourceAsStream(UI_TEST_RUNNER_PATH)?.let {
            val bufferedSink = iOSDriverRunnerApp.sink().buffer()
            bufferedSink.writeAll(it.source())
            bufferedSink.flush()
        }

        try {
            val archiver = ArchiverFactory.createArchiver(iOSDriverRunnerApp)
            archiver.extract(iOSDriverRunnerApp, uncompressedFile)
            val appFile = uncompressedFile
                .walkTopDown()
                .first {
                    it.isDirectory &&
                        it.name.endsWith(".app") &&
                        !it.path.contains("__MACOSX")
                }

            Simctl.installApp(device.instanceId, appFile.absolutePath)
        } catch (exception: Exception) {
            throw RuntimeException("Failed to install ui test runner for iOS ${exception.message}", exception)
        }
    }

    fun ensureOpen() {
        Simctl.ensureAppAlive(UI_TEST_RUNNER_APP_BUNDLE_ID)
    }

    fun runXCTest(device: Device.Connected) {
        val processOutput = ProcessBuilder(
            "bash",
            "-c",
            "xcrun simctl spawn booted launchctl print system | grep $UI_TEST_RUNNER_APP_BUNDLE_ID | awk '/$UI_TEST_RUNNER_APP_BUNDLE_ID/ {print \$3}'"
        ).start().inputStream.source().buffer().readUtf8().trim()

        if (!processOutput.contains(UI_TEST_RUNNER_APP_BUNDLE_ID)) {
            val tempDir = System.getenv("TMPDIR")
            val xctestRunFile = File("$tempDir/maestro-driver-ios-config.xctestrun")

            writeFileToDestination(XCTEST_RUN_PATH, xctestRunFile)

            extractZipToApp("maestro-driver-iosUITests-Runner", UI_TEST_RUNNER_PATH)
            extractZipToApp("maestro-driver-ios", UI_TEST_HOST_PATH)

            Simctl.runXcTestWithoutBuild(
                device.instanceId,
                xctestRunFile.absolutePath
            )
        }
    }

    private fun extractZipToApp(appFileName: String, srcAppPath: String) {
        val appFile = File("${System.getenv("TMPDIR")}/Debug-iphonesimulator").apply { mkdir() }
        val appZip = File("${System.getenv("TMPDIR")}/$appFileName.zip")

        writeFileToDestination(srcAppPath, appZip)
        ArchiverFactory.createArchiver(appZip).apply {
            extract(appZip, appFile)
        }
    }

    private fun writeFileToDestination(srcPath: String, destFile: File) {
        Maestro::class.java.getResourceAsStream(srcPath)?.let {
            val bufferedSink = destFile.sink().buffer()
            bufferedSink.writeAll(it.source())
            bufferedSink.flush()
        }
    }
}