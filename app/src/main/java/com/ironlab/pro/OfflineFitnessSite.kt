package com.ironlab.pro

import android.content.Context
import android.webkit.WebResourceResponse
import android.webkit.WebView
import org.json.JSONObject

/**
 * Serves only the exact published resources included by the build.
 * The official HTTPS base URL preserves the existing WebView storage origin.
 */
class OfflineFitnessSite(context: Context) {
    private val assets = context.applicationContext.assets
    private val resources: Map<String, String> by lazy {
        val manifest = assets.open("offline/manifest.json").bufferedReader().use {
            JSONObject(it.readText()).getJSONObject("resources")
        }
        manifest.keys().asSequence().associateWith { manifest.getString(it) }
    }

    fun resource(url: String): WebResourceResponse? {
        val name = resources[url] ?: return null
        val mime = when {
            name.endsWith(".js") -> "application/javascript"
            name.endsWith(".css") -> "text/css"
            else -> "application/octet-stream"
        }
        return WebResourceResponse(mime, "UTF-8", assets.open("offline/$name")).apply {
            responseHeaders = mapOf("Access-Control-Allow-Origin" to "*")
        }
    }

    fun show(view: WebView, url: String) {
        val html = assets.open("offline/index.html").bufferedReader().use { it.readText() }
        view.loadDataWithBaseURL(url, html, "text/html", "UTF-8", url)
    }
}

