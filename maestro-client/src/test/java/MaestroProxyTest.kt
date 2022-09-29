import dadb.Dadb
import maestro.Maestro
import maestro.mock.Proxy
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import java.io.File

@Disabled("Local testing only")
internal class MaestroProxyTest {

    private lateinit var maestro: Maestro
    private lateinit var proxy: Proxy

    private val appId = "com.reddit.frontpage.alpha"
//    private val appId = "com.etsy.android"

    @Before
    fun setUp() {
        val dadb = Dadb.create("localhost", 5555)
        maestro = Maestro.android(dadb)

        proxy = Proxy()
    }

    @Test
    fun `start proxy record`() {
        val path = File("app.replay").toPath()
        proxy.startRecord(path)
        Thread.sleep(5000)
    }

    @Test
    fun `start proxy replay`() {
        val file = File("app.replay")
        proxy.startReplay(replayFilePath = file.toPath())
        Thread.sleep(10000)
    }

    @Test
    fun `start proxy from maestro (record)`() {
        maestro.clearAppState(appId)
        maestro.clearKeychain()
        val path = File("app.replay").toPath()
        maestro.mockStartRecord(path)
        Thread.sleep(50 * 1000)
        maestro.mockStop()
    }

    @Test
    fun `start proxy from maestro (replay)`() {
        maestro.clearAppState(appId)
        val path = File("app.replay").toPath()
        maestro.mockStartReplay(path)
        Thread.sleep(50 * 1000)
        maestro.mockStop()
    }
}
