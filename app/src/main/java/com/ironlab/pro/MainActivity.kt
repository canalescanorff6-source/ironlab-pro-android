package com.ironlab.pro

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.ironlab.pro.databinding.ActivityMainBinding
import java.util.concurrent.Executors
import kotlin.math.max

/**
 * Native Android player + current IRONLAB fitness site.
 * Audio lives in PlaybackService, not in this activity or a WebView.
 * Search covers files imported from the device; we do not scrape paid music services.
 */
class MainActivity : ComponentActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var library: MusicLibrary
    private val spotifyLinks by lazy { SpotifyLinks(this) }
    private val worker = Executors.newSingleThreadExecutor()
    private val uiHandler = Handler(Looper.getMainLooper())
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var webView: WebView? = null
    private var webUploadCallback: android.webkit.ValueCallback<Array<Uri>>? = null
    private var currentScreen = "music"
    private var viewMode = "all"
    private var search = ""
    private val site = "https://ironlab-pro-3r9500.v2.appdeploy.ai/"
    private val ink = Color.rgb(10, 20, 27)
    private val mint = Color.rgb(130, 225, 208)
    private val muted = Color.rgb(163, 190, 199)
    private val white = Color.rgb(237, 246, 247)

    private val pickSongs = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { urls ->
        if (urls.isEmpty()) return@registerForActivityResult
        binding.importBtn.isEnabled = false
        toast("Importando ${urls.size} arquivo(s)…")
        worker.execute {
            val result = library.importGrantedUris(urls)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                binding.importBtn.isEnabled = true
                refreshList()
                toast("${result.first} músicas importadas; ${result.second} ignoradas.")
            }
        }
    }

    private val webPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        webUploadCallback?.onReceiveValue(
            WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        )
        webUploadCallback = null
    }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            renderPlayback()
            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) refreshList()
        }
    }
    private val positionTick = object : Runnable {
        override fun run() {
            updateTime()
            uiHandler.postDelayed(this, 800)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = ink
        window.navigationBarColor = ink
        window.decorView.systemUiVisibility = 0
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        library = MusicLibrary(this)

        binding.importBtn.setOnClickListener { pickSongs.launch(arrayOf("audio/*")) }
        binding.catalogBtn.setOnClickListener { openOfficialCatalogSearch() }
        binding.spotifyLikedBtn.setOnClickListener { spotifyLinks.openLiked() }
        binding.spotifySaveLinkBtn.setOnClickListener { spotifyLinks.addDialog(::refreshSpotifyLinks) }
        refreshSpotifyLinks()
        spotifyLinks.receiveShared(intent, ::refreshSpotifyLinks)
        binding.musicTab.setOnClickListener { switchScreen("music") }
        binding.trainingTab.setOnClickListener { switchScreen("training") }
        binding.nutritionTab.setOnClickListener { switchScreen("nutrition") }
        binding.headerBadge.setOnClickListener {
            if (currentScreen != "music") openInBrowser() else toast("Player offline • sem anúncios do IRONLAB")
        }
        binding.allSongsBtn.setOnClickListener { viewMode = "all"; refreshList() }
        binding.favoritesBtn.setOnClickListener { viewMode = "favorites"; refreshList() }
        binding.playlistBtn.setOnClickListener { playlistChooser() }
        binding.searchInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                search = s?.toString()?.trim() ?: ""
                refreshList()
            }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })
        binding.playBtn.setOnClickListener { playPause() }
        binding.previousBtn.setOnClickListener { controller?.seekToPreviousMediaItem() }
        binding.nextBtn.setOnClickListener { controller?.seekToNextMediaItem() }
        binding.shuffleBtn.setOnClickListener {
            controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
            renderPlayback()
        }
        binding.repeatBtn.setOnClickListener {
            controller?.let {
                it.repeatMode = when (it.repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }
            }
            renderPlayback()
        }
        binding.seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = Unit
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val c = controller ?: return
                if (c.duration > 0) c.seekTo(binding.seek.progress.toLong())
            }
        })
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (currentScreen == "training") {
                    val browser = webView
                    if (browser?.canGoBack() == true) browser.goBack()
                    else switchScreen("music")
                } else {
                    // Collapse to the launcher instead of stopping music.
                    moveTaskToBack(true)
                }
            }
        })
        refreshList()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (::binding.isInitialized) spotifyLinks.receiveShared(intent, ::refreshSpotifyLinks)
    }

    private fun refreshSpotifyLinks() {
        spotifyLinks.render(binding.spotifySavedLinks, ::refreshSpotifyLinks)
    }

    override fun onStart() {
        super.onStart()
        uiHandler.removeCallbacks(positionTick)
        uiHandler.post(positionTick)
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener({
            try {
                if (controllerFuture !== future) return@addListener
                val c = future.get()
                controller = c
                c.addListener(listener)
                renderPlayback()
            } catch (_: Exception) {
                toast("Não foi possível conectar ao serviço de música.")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onStop() {
        uiHandler.removeCallbacks(positionTick)
        controller?.removeListener(listener)
        controller = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onStop()
    }

    override fun onDestroy() {
        uiHandler.removeCallbacks(positionTick)
        webUploadCallback?.onReceiveValue(null)
        webUploadCallback = null
        webView?.destroy()
        worker.shutdown()
        super.onDestroy()
    }

    private fun switchScreen(screen: String) {
        val previous = currentScreen
        currentScreen = screen
        val music = screen == "music"
        binding.musicPage.visibility = if (music) View.VISIBLE else View.GONE
        binding.trainingPage.visibility = if (music) View.GONE else View.VISIBLE
        val tabs = listOf(
            binding.musicTab to "music",
            binding.trainingTab to "training",
            binding.nutritionTab to "nutrition"
        )
        for ((button, name) in tabs) {
            val selected = screen == name
            button.backgroundTintList = ColorStateList.valueOf(if (selected) mint else Color.rgb(28, 52, 63))
            button.setTextColor(if (selected) ink else white)
        }
        binding.headerBadge.text = if (music) "OFFLINE AUDIO" else "ABRIR CHROME ↗"
        if (!music) {
            ensureWebView()
            if (previous != screen) {
                webView?.loadUrl(site + if (screen == "nutrition") "#nutricao" else "#treinos")
            }
        }
    }

    /** External discovery only. Playback rights and ads remain with each official provider. */
    private fun openOfficialCatalogSearch() {
        val input = EditText(this).apply {
            hint = "Nome da música ou artista"
            setSingleLine(true)
            setPadding(dp(18), dp(12), dp(18), dp(12))
        }
        AlertDialog.Builder(this)
            .setTitle("Buscar músicas comerciais")
            .setMessage("Escolha um serviço oficial. O IRONLAB não libera músicas pagas nem remove anúncios de terceiros.")
            .setView(input)
            .setPositiveButton("CONTINUAR") { _, _ ->
                val q = input.text.toString().trim().take(120)
                if (q.length < 2) {
                    toast("Informe pelo menos dois caracteres.")
                } else {
                    val names = arrayOf("YouTube Music", "Spotify", "Deezer", "Apple Music")
                    val encoded = Uri.encode(q)
                    val destinations = arrayOf(
                        "https://music.youtube.com/search?q=$encoded",
                        "https://open.spotify.com/search/$encoded",
                        "https://www.deezer.com/search/$encoded",
                        "https://music.apple.com/br/search?term=$encoded"
                    )
                    AlertDialog.Builder(this)
                        .setTitle("Onde pesquisar '$q'?")
                        .setItems(names) { _, index ->
                            try {
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(destinations[index])))
                            } catch (_: Exception) {
                                toast("Nenhum navegador disponível para abrir o serviço.")
                            }
                        }
                        .setNegativeButton("Cancelar", null)
                        .show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun openInBrowser() {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(site + if (currentScreen == "nutrition") "#nutricao" else "#treinos")))
    }

    private fun ensureWebView() {
        if (webView != null) return
        val browser = WebView(this)
        webView = browser
        browser.setBackgroundColor(ink)
        browser.settings.javaScriptEnabled = true
        browser.settings.domStorageEnabled = true
        browser.settings.allowFileAccess = false
        browser.settings.allowContentAccess = false
        browser.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        browser.settings.setSupportMultipleWindows(false)
        browser.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url ?: return true
                if (url.scheme == "https" && url.host == "ironlab-pro-3r9500.v2.appdeploy.ai") return false
                if (url.scheme == "https" || url.scheme == "http") {
                    try { startActivity(Intent(Intent.ACTION_VIEW, url)) } catch (_: Exception) {}
                }
                return true
            }
        }
        browser.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: android.webkit.ValueCallback<Array<Uri>>?,
                fileChooserParams: WebChromeClient.FileChooserParams?
            ): Boolean {
                webUploadCallback?.onReceiveValue(null)
                webUploadCallback = filePathCallback
                return try {
                    webPicker.launch(fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        type = "*/*"
                        addCategory(Intent.CATEGORY_OPENABLE)
                    })
                    true
                } catch (_: Exception) {
                    webUploadCallback?.onReceiveValue(null)
                    webUploadCallback = null
                    false
                }
            }
        }
        binding.trainingPage.addView(browser, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))
        browser.loadUrl(site + "#treinos")
        toast("Seus dados salvos no Chrome podem não aparecer no WebView. Use ABRIR CHROME para a sessão original.")
    }

    private fun toast(text: String) { Toast.makeText(this, text, Toast.LENGTH_LONG).show() }

    private fun visibleSongs(): List<LocalSong> {
        var list = when (viewMode) {
            "favorites" -> library.songs.filter { library.favorites.contains(it.uri) }
            "playlist" -> {
                val ids = library.currentPlaylist().entries
                ids.mapNotNull { id -> library.songs.find { it.uri == id } }
            }
            else -> library.songs.toList()
        }
        if (search.isNotBlank()) list = list.filter {
            it.title.contains(search, ignoreCase = true) ||
                it.artist.contains(search, ignoreCase = true)
        }
        return list
    }

    private fun refreshList() {
        if (!::binding.isInitialized) return
        val songs = visibleSongs()
        val parent = binding.songList
        parent.removeAllViews()
        binding.songCount.text = "${library.songs.size} MÚSICAS"
        binding.playlistCount.text = "${library.playlists.size} PLAYLISTS"
        for (button in listOf(binding.allSongsBtn,binding.favoritesBtn,binding.playlistBtn)) {
            button.backgroundTintList = ColorStateList.valueOf(Color.rgb(28,52,63))
            button.setTextColor(white)
        }
        val selected = when (viewMode) {
            "favorites" -> binding.favoritesBtn
            "playlist" -> binding.playlistBtn
            else -> binding.allSongsBtn
        }
        selected.backgroundTintList = ColorStateList.valueOf(mint)
        selected.setTextColor(ink)
        if (songs.isEmpty()) {
            val message = when (viewMode) {
                "playlist" -> "Sua playlist está vazia. Importe músicas e adicione pelo menu ⋮."
                "favorites" -> "Nenhuma favorita ainda. Use o menu ⋮ de uma música."
                else -> "Nenhuma música encontrada. Toque em IMPORTAR MÚSICAS para selecionar arquivos do celular."
            }
            parent.addView(text(message,12,muted,false).apply {
                background = ContextCompat.getDrawable(this@MainActivity,R.drawable.bg_card)
                setPadding(dp(20),dp(24),dp(20),dp(24))
            })
            return
        }
        for ((index,song) in songs.withIndex()) parent.addView(songRow(song,index))
    }

    private fun songRow(song: LocalSong,index: Int): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = ContextCompat.getDrawable(this@MainActivity,R.drawable.bg_card)
            setPadding(dp(10),dp(9),dp(8),dp(9))
            val lp=LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = dp(6)
            layoutParams=lp
        }
        row.addView(text("♫",25,mint,true).apply {
            gravity=Gravity.CENTER
            layoutParams=LinearLayout.LayoutParams(dp(43),dp(43))
        })
        val titleBox = LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(6),0,dp(2),0)
            layoutParams=LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f)
        }
        titleBox.addView(text(song.title,12,white,true).apply { maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END })
        titleBox.addView(text("${song.artist} · ${formatTime(song.durationMs)}",9,muted,false).apply {
            maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END
        })
        row.addView(titleBox)
        row.addView(button("▶",true).apply {
            contentDescription="Reproduzir ${song.title}"
            setOnClickListener { playSong(song,visibleSongs()) }
        })
        row.addView(button("⋮",false).apply {
            contentDescription="Opções para ${song.title}"
            setOnClickListener { songOptions(song) }
        })
        return row
    }

    private fun songOptions(song: LocalSong) {
        val favorite=library.favorites.contains(song.uri)
        val options=arrayOf(
            if (favorite) "♡ Remover das favoritas" else "♥ Adicionar às favoritas",
            "＋ Adicionar à playlist",
            "− Tirar da biblioteca (não exclui o arquivo)"
        )
        AlertDialog.Builder(this).setTitle(song.title).setItems(options){_,which ->
            when(which){
                0 -> {library.toggleFavorite(song.uri);refreshList()}
                1 -> {
                    val lists=library.playlists.map { it.name }.toTypedArray()
                    AlertDialog.Builder(this).setTitle("Adicionar à playlist")
                        .setItems(lists){_,idx ->
                            val added=library.addToPlaylist(song.uri,library.playlists[idx].id)
                            toast(if (added) "Música adicionada." else "Ela já está nesta playlist.")
                        }.show()
                }
                2 -> AlertDialog.Builder(this).setTitle("Remover da biblioteca?")
                    .setMessage("O arquivo original permanecerá no celular.")
                    .setPositiveButton("Remover"){_,_ -> library.remove(song.uri);refreshList()}
                    .setNegativeButton("Cancelar",null).show()
            }
        }.show()
    }

    private fun playlistChooser() {
        val names=library.playlists.map { "${it.name} (${it.entries.size})" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Minhas playlists")
            .setSingleChoiceItems(names, library.playlists.indexOfFirst { it.id==library.currentPlaylist().id }) {dialog,which ->
                library.selectPlaylist(library.playlists[which].id)
                viewMode="playlist"
                refreshList()
                dialog.dismiss()
            }
            .setPositiveButton("CRIAR"){_,_ ->
                val input=EditText(this)
                input.hint="Ex.: Treino intenso"
                AlertDialog.Builder(this).setTitle("Criar playlist")
                    .setView(input)
                    .setPositiveButton("SALVAR"){_,_ ->
                        if (library.createPlaylist(input.text.toString())) {
                            viewMode="playlist";refreshList()
                        } else toast("Nome inválido ou limite de 20 listas.")
                    }
                    .setNegativeButton("Cancelar",null).show()
            }
            .setNeutralButton("EXCLUIR ATUAL"){_,_ ->
                AlertDialog.Builder(this).setTitle("Excluir playlist selecionada?")
                    .setMessage("As músicas continuam na biblioteca.")
                    .setPositiveButton("EXCLUIR"){_,_ ->
                        if (!library.deleteSelectedPlaylist()) toast("É preciso manter uma playlist.")
                        refreshList()
                    }.setNegativeButton("Cancelar",null).show()
            }.setNegativeButton("Cancelar",null).show()
    }

    private fun playSong(song: LocalSong, visible: List<LocalSong>) {
        val c=controller ?: run { toast("Player conectando; tente novamente."); return }
        val list=visible.ifEmpty { listOf(song) }
        val i=list.indexOfFirst {it.uri==song.uri}.coerceAtLeast(0)
        val mediaItems=list.map { s ->
            MediaItem.Builder()
                .setMediaId(s.uri)
                .setUri(Uri.parse(s.uri))
                .setMediaMetadata(MediaMetadata.Builder().setTitle(s.title).setArtist(s.artist).build())
                .build()
        }
        c.setMediaItems(mediaItems,i,0L)
        c.prepare()
        c.play()
        renderPlayback()
    }

    private fun playPause() {
        val c=controller ?: run { toast("Player conectando; tente novamente.");return }
        if(c.mediaItemCount==0){
            val first=visibleSongs().firstOrNull() ?: library.songs.firstOrNull()
            if(first!=null)playSong(first,visibleSongs()) else toast("Importe suas músicas primeiro.")
        } else if(c.isPlaying)c.pause() else c.play()
        renderPlayback()
    }

    private fun renderPlayback() {
        if (!::binding.isInitialized) return
        val c=controller
        val item=c?.currentMediaItem
        binding.nowTitle.text=item?.mediaMetadata?.title?.toString() ?: "NENHUMA MÚSICA SELECIONADA"
        binding.nowArtist.text=item?.mediaMetadata?.artist?.toString() ?: "Importe arquivos para começar."
        binding.playBtn.text=if(c?.isPlaying==true) "Ⅱ" else "▶"
        val shuffle=c?.shuffleModeEnabled==true
        binding.shuffleBtn.setTextColor(if(shuffle)mint else white)
        val repeat=c?.repeatMode ?: Player.REPEAT_MODE_ALL
        binding.repeatBtn.text=if(repeat==Player.REPEAT_MODE_ONE)"↻¹" else "↻"
        binding.repeatBtn.setTextColor(if(repeat==Player.REPEAT_MODE_OFF)muted else mint)
        updateTime()
    }

    private fun updateTime() {
        if (!::binding.isInitialized) return
        val c=controller
        val duration=if(c!=null&&c.duration>0) c.duration else 0L
        val position=if(c!=null&&c.currentPosition>0) c.currentPosition else 0L
        binding.seek.max=duration.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        binding.seek.progress=position.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        binding.currentTime.text=formatTime(position)
        binding.totalTime.text=formatTime(duration)
    }

    private fun formatTime(milliseconds: Long): String {
        val seconds=max(0L,milliseconds/1000L)
        val minutes=seconds/60L
        return "%02d:%02d".format(minutes,seconds%60L)
    }

    private fun button(label:String,primary:Boolean): Button=Button(this).apply {
        text=label
        textSize=14f
        minHeight=0
        minimumHeight=0
        minWidth=0
        minimumWidth=0
        setPadding(dp(5),0,dp(5),0)
        setTextColor(if(primary)ink else white)
        backgroundTintList=ColorStateList.valueOf(if(primary)mint else Color.rgb(28,52,63))
        layoutParams=LinearLayout.LayoutParams(dp(40),dp(42))
    }
    private fun text(value:String,size:Int,color:Int,bold:Boolean)=TextView(this).apply {
        text=value
        textSize=size.toFloat()
        setTextColor(color)
        if(bold)typeface=Typeface.create("sans-serif",Typeface.BOLD)
    }
    private fun dp(value:Int):Int=(value*resources.displayMetrics.density+0.5f).toInt()
}
