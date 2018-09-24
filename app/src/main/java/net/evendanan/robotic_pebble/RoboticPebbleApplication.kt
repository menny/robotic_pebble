package net.evendanan.robotic_pebble

import android.app.Application
import android.os.StrictMode

class RoboticPebbleApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            //this is required since Android VM will crash the app if we use file:// URI.
            //But, since the Pebble app was built with support for file:// URIs....
            //the other way to do it would be to use FileProvider, but the Pebble app is only
            //allowing content:// from well-known providers (gmail, Inbox, downloads and a few others)
            StrictMode.VmPolicy.Builder().apply {
                detectAll()
                StrictMode.setVmPolicy(build())
            }
        }
    }
}
