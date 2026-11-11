package com.ironlab.pro

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject

/** SAF-based catalog. No READ_MEDIA_AUDIO permission and no private audio copies. */
data class LocalSong(
    val uri: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
)

data class LocalPlaylist(
    val id: String,
    var name: String,
    val entries: MutableList<String>,
)

class MusicLibrary(private val context: Context) {
    private val prefs = context.getSharedPreferences("ironlab_native_library_v1", Context.MODE_PRIVATE)
    val songs = mutableListOf<LocalSong>()
    val favorites = mutableSetOf<String>()
    val playlists = mutableListOf<LocalPlaylist>()
    var selectedPlaylistId = "treino"
        private set

    init { load() }

    private fun load() {
        try {
            val data = JSONObject(prefs.getString("catalog", "{}") ?: "{}")
            val songList = data.optJSONArray("songs") ?: JSONArray()
            for (i in 0 until songList.length()) {
                val row = songList.optJSONObject(i) ?: continue
                val uri = row.optString("uri")
                if (!uri.startsWith("content://")) continue
                songs.add(LocalSong(
                    uri = uri,
                    title = row.optString("title", "Faixa sem título"),
                    artist = row.optString("artist", "Artista desconhecido"),
                    durationMs = row.optLong("durationMs", 0L),
                ))
            }
            val favs = data.optJSONArray("favorites") ?: JSONArray()
            for (i in 0 until favs.length()) {
                val uri = favs.optString(i)
                if (songs.any { it.uri == uri }) favorites.add(uri)
            }
            val list = data.optJSONArray("playlists") ?: JSONArray()
            for (i in 0 until list.length()) {
                val row = list.optJSONObject(i) ?: continue
                val id = row.optString("id")
                val name = row.optString("name")
                if (id.isEmpty() || name.isEmpty()) continue
                val entries = mutableListOf<String>()
                val values = row.optJSONArray("entries") ?: JSONArray()
                for (j in 0 until values.length()) {
                    val uri = values.optString(j)
                    if (songs.any { it.uri == uri } && !entries.contains(uri)) entries.add(uri)
                }
                playlists.add(LocalPlaylist(id, name, entries))
            }
            selectedPlaylistId = data.optString("selectedPlaylist", "treino")
        } catch (_: Exception) {
            // Corrupt preferences must not prevent the music player from opening.
        }
        if (playlists.isEmpty()) playlists.add(LocalPlaylist("treino", "Treino principal", mutableListOf()))
        if (playlists.none { it.id == selectedPlaylistId }) selectedPlaylistId = playlists.first().id
    }

    @Synchronized
    fun save() {
        val payload = JSONObject()
        val rows = JSONArray()
        for (s in songs) {
            rows.put(JSONObject()
                .put("uri", s.uri).put("title", s.title)
                .put("artist", s.artist).put("durationMs", s.durationMs))
        }
        val favs = JSONArray()
        favorites.forEach { favs.put(it) }
        val playlistRows = JSONArray()
        for (p in playlists) {
            val entries = JSONArray()
            p.entries.forEach { entries.put(it) }
            playlistRows.put(JSONObject().put("id", p.id).put("name", p.name).put("entries", entries))
        }
        payload.put("songs", rows)
            .put("favorites", favs)
            .put("playlists", playlistRows)
            .put("selectedPlaylist", selectedPlaylistId)
        prefs.edit().putString("catalog", payload.toString()).apply()
    }

    fun selectPlaylist(id: String) {
        if (playlists.any { it.id == id }) { selectedPlaylistId = id; save() }
    }

    fun currentPlaylist(): LocalPlaylist =
        playlists.find { it.id == selectedPlaylistId } ?: playlists.first()

    fun createPlaylist(name: String): Boolean {
        val clean = name.trim().take(38)
        if (clean.length < 2 || playlists.size >= 20) return false
        val id = "playlist-${System.currentTimeMillis()}"
        playlists.add(LocalPlaylist(id, clean, mutableListOf()))
        selectedPlaylistId = id
        save()
        return true
    }

    fun deleteSelectedPlaylist(): Boolean {
        if (playlists.size <= 1) return false
        playlists.removeAll { it.id == selectedPlaylistId }
        selectedPlaylistId = playlists.first().id
        save()
        return true
    }

    fun toggleFavorite(uri: String): Boolean {
        if (!songs.any { it.uri == uri }) return false
        val result = if (favorites.contains(uri)) { favorites.remove(uri); false }
        else { favorites.add(uri); true }
        save()
        return result
    }

    fun toggleInPlaylist(uri: String): Boolean {
        if (!songs.any { it.uri == uri }) return false
        val p = currentPlaylist()
        val added = if (p.entries.contains(uri)) { p.entries.remove(uri); false }
        else { p.entries.add(uri); true }
        save()
        return added
    }

    fun addToPlaylist(uri: String, id: String): Boolean {
        val p = playlists.find { it.id == id } ?: return false
        if (!songs.any { it.uri == uri } || p.entries.contains(uri)) return false
        p.entries.add(uri)
        save()
        return true
    }

    fun remove(uri: String) {
        songs.removeAll { it.uri == uri }
        favorites.remove(uri)
        playlists.forEach { it.entries.remove(uri) }
        save()
    }

    /** Read song metadata only after the person grants URI access through the Android picker. */
    fun importGrantedUris(uris: List<Uri>): Pair<Int, Int> {
        var added = 0
        var ignored = 0
        for (uri in uris.take(500)) {
            if (uri.scheme != ContentResolver.SCHEME_CONTENT || songs.any { it.uri == uri.toString() }) {
                ignored++
                continue
            }
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                var name = "Música importada"
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) name = cursor.getString(0) ?: name
                }
                name = name.substringBeforeLast('.').take(150).ifBlank { "Música importada" }
                var title = name
                var artist = "Artista desconhecido"
                var duration = 0L
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, uri)
                    title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                        ?.trim()?.take(150)?.ifBlank { name } ?: name
                    artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                        ?.trim()?.take(120)?.ifBlank { "Artista desconhecido" } ?: "Artista desconhecido"
                    duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull() ?: 0L
                } catch (_: Exception) {
                    // Unsupported embedded metadata must not prevent importing valid audio files.
                } finally {
                    retriever.release()
                }
                songs.add(LocalSong(uri.toString(), title, artist, duration))
                added++
            } catch (_: SecurityException) {
                ignored++
            } catch (_: Exception) {
                ignored++
            }
        }
        save()
        return Pair(added, ignored)
    }
}
