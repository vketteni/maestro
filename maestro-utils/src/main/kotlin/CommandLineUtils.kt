package maestro.utils

import okio.buffer
import okio.source
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

object CommandLineUtils {

    fun runCommand(command: String, waitForCompletion: Boolean = true, outputFile: File? = null): Process {
        val parts = command.split("\\s".toRegex())
            .map { it.trim() }

        return runCommand(parts, waitForCompletion, outputFile)
    }

    @Suppress("SpreadOperator")
    fun runCommand(parts: List<String>, waitForCompletion: Boolean = true, outputFile: File? = null): Process {
        val process = if (outputFile != null) {
            ProcessBuilder(*parts.toTypedArray())
                .redirectOutput(outputFile)
                .redirectError(ProcessBuilder.Redirect.PIPE)
                .start()
        } else {
            ProcessBuilder(*parts.toTypedArray())
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectError(ProcessBuilder.Redirect.PIPE)
                .start()
        }

        if (waitForCompletion) {
            if (!process.waitFor(5, TimeUnit.MINUTES)) {
                throw TimeoutException()
            }

            if (process.exitValue() != 0) {
                val processOutput = process.errorStream
                    .source()
                    .buffer()
                    .readUtf8()


                throw IllegalStateException(processOutput)
            }
        }

        return process
    }
}
