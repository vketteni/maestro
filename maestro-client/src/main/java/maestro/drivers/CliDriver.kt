package maestro.drivers

import maestro.DeviceInfo
import maestro.Driver
import maestro.KeyCode
import maestro.Platform
import maestro.Point
import maestro.ScreenRecording
import maestro.SwipeDirection
import maestro.TreeNode
import okio.Sink
import java.io.File
import kotlin.concurrent.thread

class CliDriver : Driver {

    private var open = false

    private var currentProcess: Process? = null
    private val buffer = mutableListOf<Byte>()

    override fun name(): String {
        return "CLI"
    }

    override fun open() {
        open = true
    }

    override fun close() {
        open = false
    }

    override fun deviceInfo(): DeviceInfo {
        return DeviceInfo(
            platform = Platform.CLI,
            widthPixels = 100,
            heightPixels = 100,
            widthGrid = 100,
            heightGrid = 100,
        )
    }

    override fun launchApp(appId: String) {
        if (currentProcess != null) {
            currentProcess?.destroy()
            currentProcess?.waitFor()
        }

        val parts = appId.split("\\s".toRegex())
            .map { it.trim() }

        currentProcess = ProcessBuilder(*parts.toTypedArray())
            .start()

        thread(isDaemon = true) {
            currentProcess?.inputStream?.apply {
                while (open) {
                    val b = read()

                    if (b < 0) {
                        return@apply
                    } else {
                        synchronized(buffer) {
                            buffer.add(b.toByte())
                        }
                    }
                }
            }
        }
    }

    override fun stopApp(appId: String) {
        currentProcess?.destroy()
    }

    override fun clearAppState(appId: String) {
        // No-op
    }

    override fun clearKeychain() {
        // No-op
    }

    override fun pullAppState(appId: String, outFile: File) {
        // No-op
    }

    override fun pushAppState(appId: String, stateFile: File) {
        // No-op
    }

    override fun tap(point: Point) {
        // No-op
    }

    override fun longPress(point: Point) {
        // No-op
    }

    override fun pressKey(code: KeyCode) {
        when (code) {
            KeyCode.ENTER -> {
                currentProcess
                    ?.outputStream
                    ?.bufferedWriter(charset = Charsets.US_ASCII)
                    ?.apply {
                        newLine()
                        flush()
                    }
            }
            KeyCode.BACKSPACE -> {
                currentProcess
                    ?.outputStream
                    ?.bufferedWriter(charset = Charsets.US_ASCII)
                    ?.apply {
                        write("\b")
                        flush()
                    }
            }
            else -> {
                // No op
            }
        }
    }

    override fun contentDescriptor(): TreeNode {
        return synchronized(buffer) {
            val bytes = buffer.toByteArray()
            TreeNode(
                attributes = mapOf(
                    "text" to String(bytes),
                    "bounds" to "0,0,100,100",
                ),
            )
        }
    }

    override fun scrollVertical() {
        // No-op
    }

    override fun swipe(start: Point, end: Point, durationMs: Long) {
        // No-op
    }

    override fun swipe(swipeDirection: SwipeDirection, durationMs: Long) {
        // No-op
    }

    override fun swipe(elementPoint: Point, direction: SwipeDirection, durationMs: Long) {
        // No-op
    }

    override fun backPress() {
        // No-op
    }

    override fun inputText(text: String) {
        currentProcess
            ?.outputStream
            ?.bufferedWriter(charset = Charsets.US_ASCII)
            ?.apply {
                write(text)
                flush()
            }
    }

    override fun openLink(link: String) {
        // No-op
    }

    override fun hideKeyboard() {
        // No-op
    }

    override fun takeScreenshot(out: Sink) {
        error("Not supported")
    }

    override fun startScreenRecording(out: Sink): ScreenRecording {
        error("Not supported")
    }

    override fun setLocation(latitude: Double, longitude: Double) {
        // No-op
    }

    override fun eraseText(charactersToErase: Int) {
        currentProcess
            ?.outputStream
            ?.bufferedWriter(charset = Charsets.US_ASCII)
            ?.apply {
                repeat(charactersToErase) {
                    write("\b")
                }
                flush()
            }
    }

    override fun setProxy(host: String, port: Int) {
        error("Not supported")
    }

    override fun resetProxy() {
        error("Not supported")
    }

    override fun isShutdown(): Boolean {
        return !open
    }

    override fun isUnicodeInputSupported(): Boolean {
        return true
    }
}
