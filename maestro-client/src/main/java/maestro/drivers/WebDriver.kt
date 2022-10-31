package maestro.drivers

import io.github.bonigarcia.wdm.WebDriverManager
import maestro.DeviceInfo
import maestro.Driver
import maestro.KeyCode
import maestro.Point
import maestro.TreeNode
import okio.Sink
import org.jsoup.nodes.Node
import org.openqa.selenium.By
import org.openqa.selenium.WebElement
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.interactions.Actions
import java.io.File

class WebDriver : Driver {

    private var seleniumDriver: org.openqa.selenium.WebDriver? = null

    override fun name(): String {
        TODO("Not yet implemented")
    }

    override fun open() {
        WebDriverManager.chromedriver().setup()

        seleniumDriver = ChromeDriver.builder()
            .build()
    }

    override fun close() {
        seleniumDriver?.quit()
        seleniumDriver = null
    }

    override fun deviceInfo(): DeviceInfo {
        val driver = ensureOpen()

        val windowSize = driver.manage().window().size

        return DeviceInfo(
            widthPixels = windowSize.width,
            heightPixels = windowSize.height,
        )
    }

    override fun launchApp(appId: String) {
        val driver = ensureOpen()

        driver.get(appId)
    }

    override fun stopApp(appId: String) {
        val driver = ensureOpen()

        driver.close()  // TODO close the actual window
    }

    override fun contentDescriptor(): TreeNode {
        val driver = ensureOpen()

        val node = driver.findElement(By.tagName("body"))

        return mapNode(node)
//        val html = driver.pageSource
//
//        val body = Jsoup.parse(html)
//            .body()
//
//        return mapNode(body)
    }

    private fun mapNode(node: WebElement): TreeNode {
        val rect = node.rect
        return TreeNode(
            attributes = mapOf(
                "text" to node.getAttribute("text"),
                "bounds" to "${rect.x},${rect.y},${rect.x + rect.width},${rect.y + rect.height}",
            ),
            children = node.findElements(By.xpath("./*"))
                .map { mapNode(it) }
        )
    }

    private fun mapNode(node: Node): TreeNode {
        val driver = ensureOpen()


        return TreeNode(
            attributes = mapOf(
                "text" to node.attr("#text"),

                ),
            children = node.childNodes().map { mapNode(it) },
        )
    }

    override fun clearAppState(appId: String) {
        // TODO
    }

    override fun clearKeychain() {
        // Do nothing
    }

    override fun pullAppState(appId: String, outFile: File) {
        TODO("Not yet implemented")
    }

    override fun pushAppState(appId: String, stateFile: File) {
        TODO("Not yet implemented")
    }

    override fun tap(point: Point) {
        val driver = ensureOpen()

        Actions(driver)
            .moveByOffset(0, 0)
            .moveByOffset(point.x, point.y)
            .click()
            .build()
            .perform()
    }

    override fun longPress(point: Point) {
        TODO("Not yet implemented")
    }

    override fun pressKey(code: KeyCode) {
        TODO("Not yet implemented")
    }

    override fun scrollVertical() {
        TODO("Not yet implemented")
    }

    override fun swipe(start: Point, end: Point) {
        TODO("Not yet implemented")
    }

    override fun backPress() {
        TODO("Not yet implemented")
    }

    override fun inputText(text: String) {
        TODO("Not yet implemented")
    }

    override fun openLink(link: String) {
        TODO("Not yet implemented")
    }

    override fun hideKeyboard() {
        TODO("Not yet implemented")
    }

    override fun clipboardPaste() {
        TODO("Not yet implemented")
    }

    override fun takeScreenshot(out: Sink) {
        // TODO
    }

    override fun setLocation(latitude: Double, longitude: Double) {
        TODO("Not yet implemented")
    }

    private fun ensureOpen(): org.openqa.selenium.WebDriver {
        return seleniumDriver ?: error("Driver is not open")
    }
}

fun main() {
    WebDriver().apply {
        try {
            open()

            launchApp("https://google.com")

            println(contentDescriptor())
        } finally {
            close()
        }
    }
}
