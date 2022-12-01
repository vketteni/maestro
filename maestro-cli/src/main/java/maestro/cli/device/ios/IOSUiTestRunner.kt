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

    fun ensureOpen(device: Device.Connected) {
        Simctl.ensureLaunchApp(device.instanceId, UI_TEST_RUNNER_APP_BUNDLE_ID)
    }
}