package uk.co.aaronburt.rtsp

import android.app.Application
import uk.co.aaronburt.rtsp.data.AppContainer

class RtspApplication : Application() {
    val container = AppContainer()
}
