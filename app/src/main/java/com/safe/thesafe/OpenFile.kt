package com.safe.thesafe

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView

class OpenFile  (private val activity: ActivityChoser) : WebChromeClient() {
    override fun onShowFileChooser(webView: WebView?, filePathCallback: ValueCallback<Array<Uri>>?, fileChooserParams: FileChooserParams): Boolean {
        if (activity.messageUpload != null) {
            activity.messageUpload?.onReceiveValue(null)
            activity.messageUpload = null
        }
        activity.messageUpload = filePathCallback
        val intent: Intent = fileChooserParams.createIntent()
        try {
            activity.startActivityForResult(intent, ActivityChoser.REQUEST_SELECT_FILE)
            (activity as Activity).overridePendingTransition(
                android.R.anim.fade_in,
                android.R.anim.fade_out
            )
        } catch (e: ActivityNotFoundException) {
            activity.messageUpload = null
            return false
        }
        return true
    }
    interface ActivityChoser {
        companion object {
            const val REQUEST_SELECT_FILE = 100;
        }
        var messageUpload: ValueCallback<Array<Uri>>?
        fun startActivityForResult(intent: Intent, req: Int)
    }
}