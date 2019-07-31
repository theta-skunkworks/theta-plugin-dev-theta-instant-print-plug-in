package skunkworks.instantprint

import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.KeyEvent
import com.theta360.pluginlibrary.activity.PluginActivity
import com.theta360.pluginlibrary.callback.KeyCallback
import com.theta360.pluginlibrary.receiver.KeyReceiver
import org.theta4j.osc.CommandResponse
import org.theta4j.osc.CommandState
import org.theta4j.osc.OptionSet
import org.theta4j.webapi.CaptureMode
import org.theta4j.webapi.ExposureCompensation
import org.theta4j.webapi.Options
import org.theta4j.webapi.Theta
import java.io.File
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference


class MainActivity : PluginActivity() {
    companion object {
        private val TAG = MainActivity::class.java.simpleName

        private val PIXELS_TO_FORWARD = 255
    }

    private val executor = Executors.newSingleThreadExecutor()

    private val isProceeding = AtomicReference(false)

    private val theta = Theta.createForPlugin()

    private var mPrinter: Printer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    override fun onResume() {
        super.onResume()

        Thread.setDefaultUncaughtExceptionHandler { _, e -> Log.e(TAG, "Uncaught Exception", e) }

        mPrinter = Printer(applicationContext)

        setKeyCallback(object : KeyCallback {
            override fun onKeyDown(code: Int, event: KeyEvent) {
                if (code == KeyReceiver.KEYCODE_CAMERA) {
                    takePictureAndPrint()
                } else if (code == KeyReceiver.KEYCODE_WLAN_ON_OFF) {
                    testPrint()
                }
            }

            override fun onKeyUp(code: Int, event: KeyEvent) {
            }

            override fun onKeyLongPress(code: Int, event: KeyEvent) {
            }
        })
    }

    override fun onPause() {
        super.onPause()
        setKeyCallback(null)

        Thread.setDefaultUncaughtExceptionHandler(null)
    }

    private fun takePictureAndPrint() {
        if (isProceeding.get()) {
            Log.d(TAG, "Skip")
            return
        }

        executor.submit {
            isProceeding.set(true)

            try {
                theta.setOption(Options.CAPTURE_MODE, CaptureMode.IMAGE)
                val opts = OptionSet.Builder()
                    .put(Options.SHUTTER_VOLUME, 100)
                    .put(Options.EXPOSURE_DELAY, 5)
                    .put(Options.EXPOSURE_COMPENSATION, ExposureCompensation.PLUS_1_0)
                    .build()
                theta.setOptions(opts)

                val res = waitForDone(theta.takePicture())
                val file = resolveFile(res.result!!.fileUrl)
                Log.d(TAG, "file: $file")

                val src = BitmapFactory.decodeFile(file.absolutePath)
                val data = ImgConv.convert(ImgConv.shiftCenter(src))
                src.recycle()

                mPrinter!!.print(data)
                mPrinter!!.forward(PIXELS_TO_FORWARD)
            } finally {
                isProceeding.set(false)
            }
        }
    }

    private fun testPrint() {
        if (isProceeding.get()) {
            Log.d(TAG, "Skip")
            return
        }

        executor.submit {
            isProceeding.set(true)

            try {
                val src = assets.open("lena.jpg").use(BitmapFactory::decodeStream)
                val data = ImgConv.convert(src)
                src.recycle()
                mPrinter!!.print(data)
                mPrinter!!.forward(PIXELS_TO_FORWARD)
            } finally {
                isProceeding.set(false)
            }
        }
    }

    /**
     * example of url : "http://192.168.1.1/files/abcde/100RICOH/R0011607.JPG"
     */
    private fun resolveFile(url: URL): File {
        val paths = url.path.split("/") // example: ["", "files", "abcde", "100RICOH", "R0011607.JPG"]
        val dcfDirName = paths[3]
        val fileName = paths[4]
        val dcimDir = File(Environment.getExternalStorageDirectory(), Environment.DIRECTORY_DCIM)
        val dcfDir = File(dcimDir, dcfDirName)
        return File(dcfDir, fileName)
    }

    private fun <R> waitForDone(response: CommandResponse<R>): CommandResponse<R> {
        var res = response
        while (res.state != CommandState.DONE) {
            res = theta.commandStatus(res)
            Thread.sleep(100)
        }
        return res
    }
}
