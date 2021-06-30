package com.safe.thesafe

import android.app.Application
import android.content.Intent
import com.appsflyer.AppsFlyerConversionListener
import com.appsflyer.AppsFlyerLib
import com.onesignal.OneSignal
import org.json.JSONException

class AppClass : Application() {
    override fun onCreate() {
        super.onCreate()
        val onesig = getString(R.string.onesignal)
        val APSFLYER_KEY = getString(R.string.appsfl)
        OneSignal.setLogLevel(OneSignal.LOG_LEVEL.VERBOSE, OneSignal.LOG_LEVEL.NONE);
        OneSignal.initWithContext(this)
        OneSignal.setAppId(onesig)

        OneSignal.setNotificationOpenedHandler { result ->
            result.notification.additionalData?.let { additionalData ->
                val addData = result.notification.additionalData
                val keys: Iterator<String> = addData.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (additionalData.has("url")) {
                        val url = additionalData.getString("url")
                        Intent(this, MainActivity::class.java).also {
                            it.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            it.putExtra("notification_url", url.toString())
                            try {
                                OneSignal.sendTag(key, addData.get(key).toString())
                            } catch (e: JSONException) {
                                e.printStackTrace()
                            }
                            startActivity(it)
                        }
                    }
                    else {
                        try {
                            OneSignal.sendTag(key, addData.get(key).toString())
                        } catch (e: JSONException) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }

        val osConversionErrorListener = object : AppsFlyerConversionListener {
            override fun onConversionDataSuccess(p0: MutableMap<String, Any>?) {}
            override fun onConversionDataFail(error: String?) {
                OneSignal.sendTag("AppsFlyer conversion error", error)
            }
            override fun onAppOpenAttribution(p0: MutableMap<String, String>?) {}
            override fun onAttributionFailure(error: String?) {
                OneSignal.sendTag("AppsFlyer attribution error", error)
            }
        }
        AppsFlyerLib.getInstance().init(APSFLYER_KEY, osConversionErrorListener, this)
        AppsFlyerLib.getInstance().startTracking(this)

    }
}