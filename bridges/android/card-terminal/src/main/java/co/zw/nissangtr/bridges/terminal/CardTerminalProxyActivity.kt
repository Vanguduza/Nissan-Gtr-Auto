package co.zw.nissangtr.bridges.terminal

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle

/**
 * Invisible hop that starts the acquirer's terminal app for a result and hands the answer back to
 * [IntentCardTerminalBridge]. Keeps activity-result plumbing out of the host app.
 */
class CardTerminalProxyActivity : Activity() {
    private var token: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        token = intent.getLongExtra(EXTRA_TOKEN, 0)
        if (savedInstanceState != null) return // re-created while the terminal app is open: keep waiting
        @Suppress("DEPRECATION")
        val target = intent.getParcelableExtra<Intent>(EXTRA_TARGET)
        if (target == null) {
            TerminalResultRelay.deliver(token, launched = false, resultOk = false, extras = emptyMap())
            finish()
            return
        }
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(target, REQUEST)
        } catch (e: ActivityNotFoundException) {
            TerminalResultRelay.deliver(token, launched = false, resultOk = false, extras = emptyMap())
            finish()
        } catch (e: SecurityException) {
            TerminalResultRelay.deliver(token, launched = false, resultOk = false, extras = emptyMap())
            finish()
        }
    }

    @Deprecated("Activity result API of the platform Activity")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST) return
        val extras = buildMap {
            data?.extras?.let { b -> b.keySet().forEach { k -> put(k, b.get(k)?.toString()) } }
        }
        TerminalResultRelay.deliver(token, launched = true, resultOk = resultCode == RESULT_OK, extras = extras)
        finish()
    }

    companion object {
        const val EXTRA_TOKEN = "co.zw.nissangtr.terminal.TOKEN"
        const val EXTRA_TARGET = "co.zw.nissangtr.terminal.TARGET"
        private const val REQUEST = 0x4354 // "CT"
    }
}
