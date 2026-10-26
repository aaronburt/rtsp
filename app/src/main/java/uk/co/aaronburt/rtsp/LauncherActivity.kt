package uk.co.aaronburt.rtsp

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/** Keep the app's task independent of whichever launcher alias is currently enabled. */
class LauncherActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        })
        finish()
    }
}
