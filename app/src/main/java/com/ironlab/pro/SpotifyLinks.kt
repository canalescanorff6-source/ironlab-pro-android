package com.ironlab.pro

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import org.json.JSONArray
import org.json.JSONObject

/**
 * Atalhos para playlists e músicas do Spotify.
 * Não usa senha, API privada, download de áudio ou ferramentas para bloquear anúncios.
 * Autenticação e reprodução ocorrem no aplicativo oficial Spotify.
 */
class SpotifyLinks(private val activity: ComponentActivity) {
    private val prefs = activity.getSharedPreferences("ironlab_spotify_links_v1", Context.MODE_PRIVATE)
    private val kinds = setOf("playlist", "track", "album", "artist", "show", "episode")
    private data class Entry(val title: String, val url: String)

    private fun canonical(text: String): String? {
        val raw = text.trim().substringBefore(' ').substringBefore('\n')
        val uri = try { Uri.parse(raw) } catch (_: Exception) { return null }
        if (uri.scheme != "https" || uri.host?.lowercase() != "open.spotify.com") return null
        val parts = uri.pathSegments.filterNot { it.startsWith("intl-") }
        if (parts.size < 2 || parts[0] !in kinds ||
            !parts[1].matches(Regex("^[A-Za-z0-9]{10,35}$"))) return null
        return "https://open.spotify.com/" + parts[0] + "/" + parts[1]
    }
    private fun load(): MutableList<Entry> {
        val list = mutableListOf<Entry>()
        try {
            val a = JSONArray(prefs.getString("items", "[]"))
            for (i in 0 until a.length()) {
                val obj = a.optJSONObject(i) ?: continue
                val url = canonical(obj.optString("url")) ?: continue
                list.add(Entry(obj.optString("title").take(60).ifBlank { "Spotify" }, url))
            }
        } catch (_: Exception) {}
        return list
    }
    private fun save(list: List<Entry>) {
        val a = JSONArray()
        list.take(80).forEach { a.put(JSONObject().put("title", it.title).put("url", it.url)) }
        prefs.edit().putString("items", a.toString()).apply()
    }
    fun openLiked() = open("https://open.spotify.com/collection/tracks")

    private fun open(url: String) {
        val parts = Uri.parse(url).pathSegments
        val appUri = if (url.endsWith("/collection/tracks")) "spotify:collection:tracks"
        else "spotify:" + parts[0] + ":" + parts[1]
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(appUri)).setPackage("com.spotify.music"))
        } catch (_: Exception) {
            try { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            catch (_: Exception) {
                Toast.makeText(activity,"Instale o Spotify ou um navegador.",Toast.LENGTH_LONG).show()
            }
        }
    }

    fun addDialog(onSaved: () -> Unit, providedLink: String = "") {
        val fields = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 12, 40, 12)
        }
        val title = EditText(activity).apply {
            hint = "Nome: Playlist de treino"
            isSingleLine = true
        }
        val link = EditText(activity).apply {
            hint = "https://open.spotify.com/playlist/..."
            setText(providedLink)
            isSingleLine = false
            maxLines = 3
        }
        fields.addView(title)
        fields.addView(link)
        val dialog = AlertDialog.Builder(activity)
            .setTitle("Salvar link do Spotify")
            .setMessage("Cole o link compartilhado pelo Spotify. Apenas o atalho será salvo; nenhuma música será baixada.")
            .setView(fields)
            .setPositiveButton("SALVAR", null)
            .setNegativeButton("Cancelar", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val url = canonical(link.text.toString())
                if (url == null) {
                    link.error = "Use um link de música ou playlist do Spotify."
                    return@setOnClickListener
                }
                val list = load()
                if (list.any { it.url == url } || list.size >= 80) {
                    link.error = "Link já salvo ou limite de 80 atalhos."
                    return@setOnClickListener
                }
                val name = title.text.toString().trim().take(60).ifBlank { "Minha música Spotify" }
                list.add(0, Entry(name, url))
                save(list)
                dialog.dismiss()
                onSaved()
            }
        }
        dialog.show()
    }

    fun render(container: LinearLayout, onRefresh: () -> Unit) {
        container.removeAllViews()
        val items = load()
        if (items.isEmpty()) {
            container.addView(TextView(activity).apply {
                text = "No Spotify, toque em Compartilhar → Copiar link para adicionar playlists e músicas aqui."
                textSize = 11f
                setTextColor(Color.rgb(163,190,199))
                setPadding(8,8,8,8)
            })
        }
        items.forEach { item ->
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(5,7,5,7)
            }
            val label = TextView(activity).apply {
                text = "♫  " + item.title
                textSize = 12f
                maxLines = 2
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f)
            }
            val go = TextView(activity).apply {
                text = "ABRIR ↗"
                textSize = 10f
                setTextColor(Color.rgb(130,225,208))
                setPadding(10,12,10,12)
                setOnClickListener { open(item.url) }
            }
            val more = TextView(activity).apply {
                text = "⋮"
                textSize = 23f
                setTextColor(Color.WHITE)
                setPadding(9,6,9,6)
                setOnClickListener {
                    AlertDialog.Builder(activity).setTitle(item.title)
                        .setItems(arrayOf("Abrir no Spotify", "Copiar link", "Excluir atalho")) { _, which ->
                            when (which) {
                                0 -> open(item.url)
                                1 -> {
                                    val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Spotify",item.url))
                                    Toast.makeText(activity,"Link copiado.",Toast.LENGTH_SHORT).show()
                                }
                                2 -> { save(load().filterNot { it.url == item.url }); onRefresh() }
                            }
                        }.show()
                }
            }
            row.addView(label)
            row.addView(go)
            row.addView(more)
            container.addView(row)
        }
    }

    fun receiveShared(intent: Intent?, onSaved: () -> Unit) {
        if (intent?.action != Intent.ACTION_SEND || intent.type != "text/plain") return
        val shared = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        val regex = Regex("https://open\\.spotify\\.com/(?:intl-[A-Za-z-]+/)?(?:playlist|track|album|artist|show|episode)/[A-Za-z0-9]{10,35}")
        val found = regex.find(shared) ?: return
        addDialog(onSaved,found.value)
    }
}
