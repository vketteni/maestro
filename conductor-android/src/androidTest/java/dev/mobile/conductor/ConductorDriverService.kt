package dev.mobile.conductor

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ConductorDriverService {

    @Test
    fun grpcServer() {
        val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        ConductorDriverServer(uiDevice)
            .start()
    }

}
