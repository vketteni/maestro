package maestro.utils

import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.time.LocalDateTime
import java.util.concurrent.TimeoutException

internal object NetUtil {

    private val logger = LoggerFactory.getLogger(NetUtil::class.java)

    /**
     * @return host ip address
     */
    fun getHostAddress(): String = InetAddress.getLocalHost().hostAddress

    /**
     * Checks if port is (already) in use by opening a socket connection on it.
     *
     * @return true, if port on host is open
     * Warning: This operation is slow and expensive
     */
    @Suppress("EmptyCatchBlock", "SwallowedException")
    fun isPortAvailable(host: String, port: Int): Boolean {
        try {
            ServerSocket().use { socket ->
                socket.reuseAddress = false
                socket.localPort
                socket.bind(InetSocketAddress(InetAddress.getByName(host), port), 1)
                return true
            }
        } catch (ex: IOException) {
            return false
        }
    }

    /**
     * @param startFrom Initial port to start from
     * @param exclude ports to exclude
     * @return A free port on host to use
     */
    fun getAvailablePort(host: String, startFrom: Int = 8000, exclude: List<Int> = emptyList()): Int {
        var port = startFrom
        var tries = 0
        while (port in exclude || !isPortAvailable(host = host, port = port)) {
            port += if (tries < 3) 1 else 5 // ports in this range are occupied, increase range
            tries += 1

            logger.debug("[port search] $host:$port (unavailable)")

            if (tries > 50) throw TimeoutException("Unable to find an available port")
        }

        logger.debug("[port search] $host:$port (available)")

        return port
    }

    @Suppress("EmptyCatchBlock", "SwallowedException")
    fun waitForPortToBeFree(host: String, port: Int, timeoutInSecs: Long = 60) {
        val timeoutTime: LocalDateTime = LocalDateTime.now().plusSeconds(timeoutInSecs)
        while (true) {
            if (timeoutTime.isBefore(LocalDateTime.now())) {
                throw TimeoutException("Timed out waiting for port to be released")
            }
            try {
                Socket(host, port).use { s -> }
            } catch (ioe: IOException) {
                return
            }
            try {
                Thread.sleep(100)
            } catch (e: InterruptedException) {
            }
        }
    }

    @Suppress("EmptyCatchBlock", "SwallowedException")
    fun waitForPortToBeInUse(host: String, port: Int, timeoutInSecs: Long = 60) {
        val timeoutTime = LocalDateTime.now().plusSeconds(timeoutInSecs)
        while (true) {
            if (timeoutTime.isBefore(LocalDateTime.now())) {
                throw TimeoutException("Timed out waiting for port to be accessible")
            }
            try {
                Socket(host, port).use { _ -> return }
            } catch (ioe: IOException) {
                try {
                    Thread.sleep(100)
                } catch (e: InterruptedException) {
                }
            }
        }
    }
}
