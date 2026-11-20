package com.lumatv.player

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    companion object {
        const val DEFAULT_PLAYLIST_URL = "http://cdntv.online/high/c6t3h5p9vr/playlist.m3u8"
    }

    private enum class Screen {
        CATEGORIES,
        CATEGORY_CHANNELS,
        PLAYER,
        SETTINGS
    }

    private var currentScreen = Screen.CATEGORIES

    private lateinit var screenCategories: LinearLayout
    private lateinit var screenCategoryChannels: LinearLayout
    private lateinit var screenPlayer: FrameLayout
    private lateinit var screenSettings: LinearLayout

    private lateinit var btnSearchMain: Button
    private lateinit var btnRefreshMain: Button
    private lateinit var btnSettingsMain: Button
    private lateinit var rvCategories: RecyclerView

    private lateinit var btnBackFromCategory: Button
    private lateinit var tvCategoryHeaderTitle: TextView
    private lateinit var btnSearchCategory: Button
    private lateinit var rvCategoryChannels: RecyclerView

    private lateinit var playerView: PlayerView
    private lateinit var tvClockOverlay: TextView
    private lateinit var playerRightPanel: LinearLayout
    private lateinit var tabLiveProgram: Button
    private lateinit var tabArchiveProgram: Button
    private lateinit var rvPlayerEpg: RecyclerView

    private lateinit var osdBottomBar: LinearLayout
    private lateinit var tvOsdBadge: TextView
    private lateinit var tvOsdChannelName: TextView
    private lateinit var tvOsdCurrentShow: TextView
    private lateinit var tvOsdNextShow: TextView
    private lateinit var tvOsdTimeRight: TextView
    private lateinit var osdProgressBar: ProgressBar
    private lateinit var btnOsdPlayPause: Button
    private lateinit var btnOsdTogglePanel: Button
    private lateinit var btnOsdFavorite: Button
    private lateinit var btnOsdAspect: Button
    private lateinit var btnOsdBackToList: Button

    private lateinit var btnCloseSettings: Button
    private lateinit var btnSetPlaylists: Button
    private lateinit var btnSetEpg: Button
    private lateinit var btnSetRefreshPlaylist: Button
    private lateinit var btnSetRefreshEpg: Button

    private lateinit var player: ExoPlayer

    private var allChannels: List<Channel> = emptyList()
    private var categories: List<Pair<String, List<Channel>>> = emptyList()
    private var epgMap: Map<String, List<EpgProgram>> = emptyMap()

    private var currentCategoryName: String = ""
    private var currentCategoryChannels: List<Channel> = emptyList()
    private var activeChannel: Channel? = null
    private var isArchiveTab = false
    private var resizeModeIndex = 0

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateTimeFormat = SimpleDateFormat("dd MMM / HH:mm", Locale.getDefault())
    private val handler = Handler(Looper.getMainLooper())

    private val clockRunnable = object : Runnable {
        override fun run() {
            tvClockOverlay.text = timeFormat.format(Date())
            handler.postDelayed(this, 10000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        setupListeners()

        player = ExoPlayer.Builder(this).build()
        playerView.player = player

        handler.post(clockRunnable)

        val prefs = getSharedPreferences("luma_televizo_prefs", Context.MODE_PRIVATE)
        if (!prefs.contains("m3u_url")) {
            prefs.edit().putString("m3u_url", DEFAULT_PLAYLIST_URL).apply()
        }

        val m3uUrl = prefs.getString("m3u_url", DEFAULT_PLAYLIST_URL) ?: DEFAULT_PLAYLIST_URL
        val epgUrl = prefs.getString("epg_url", "") ?: ""

        showCategoriesScreen()
        loadPlaylistAndEpg(m3uUrl, epgUrl)
    }

    private fun bindViews() {
        screenCategories = findViewById(R.id.screenCategories)
        screenCategoryChannels = findViewById(R.id.screenCategoryChannels)
        screenPlayer = findViewById(R.id.screenPlayer)
        screenSettings = findViewById(R.id.screenSettings)

        btnSearchMain = findViewById(R.id.btnSearchMain)
        btnRefreshMain = findViewById(R.id.btnRefreshMain)
        btnSettingsMain = findViewById(R.id.btnSettingsMain)
        rvCategories = findViewById(R.id.rvCategories)
        rvCategories.layoutManager = GridLayoutManager(this, 2)

        btnBackFromCategory = findViewById(R.id.btnBackFromCategory)
        tvCategoryHeaderTitle = findViewById(R.id.tvCategoryHeaderTitle)
        btnSearchCategory = findViewById(R.id.btnSearchCategory)
        rvCategoryChannels = findViewById(R.id.rvCategoryChannels)
        rvCategoryChannels.layoutManager = GridLayoutManager(this, 2)

        playerView = findViewById(R.id.playerView)
        tvClockOverlay = findViewById(R.id.tvClockOverlay)
        playerRightPanel = findViewById(R.id.playerRightPanel)
        tabLiveProgram = findViewById(R.id.tabLiveProgram)
        tabArchiveProgram = findViewById(R.id.tabArchiveProgram)
        rvPlayerEpg = findViewById(R.id.rvPlayerEpg)
        rvPlayerEpg.layoutManager = LinearLayoutManager(this)

        osdBottomBar = findViewById(R.id.osdBottomBar)
        tvOsdBadge = findViewById(R.id.tvOsdBadge)
        tvOsdChannelName = findViewById(R.id.tvOsdChannelName)
        tvOsdCurrentShow = findViewById(R.id.tvOsdCurrentShow)
        tvOsdNextShow = findViewById(R.id.tvOsdNextShow)
        tvOsdTimeRight = findViewById(R.id.tvOsdTimeRight)
        osdProgressBar = findViewById(R.id.osdProgressBar)
        btnOsdPlayPause = findViewById(R.id.btnOsdPlayPause)
        btnOsdTogglePanel = findViewById(R.id.btnOsdTogglePanel)
        btnOsdFavorite = findViewById(R.id.btnOsdFavorite)
        btnOsdAspect = findViewById(R.id.btnOsdAspect)
        btnOsdBackToList = findViewById(R.id.btnOsdBackToList)

        btnCloseSettings = findViewById(R.id.btnCloseSettings)
        btnSetPlaylists = findViewById(R.id.btnSetPlaylists)
        btnSetEpg = findViewById(R.id.btnSetEpg)
        btnSetRefreshPlaylist = findViewById(R.id.btnSetRefreshPlaylist)
        btnSetRefreshEpg = findViewById(R.id.btnSetRefreshEpg)
    }

    private fun setupListeners() {
        btnSettingsMain.setOnClickListener { switchScreen(Screen.SETTINGS); btnSetPlaylists.requestFocus() }
        btnCloseSettings.setOnClickListener { showCategoriesScreen() }

        btnRefreshMain.setOnClickListener { reloadCurrentPlaylist() }
        btnSetRefreshPlaylist.setOnClickListener { reloadCurrentPlaylist(); showCategoriesScreen() }
        btnSetRefreshEpg.setOnClickListener { reloadCurrentPlaylist(); showCategoriesScreen() }

        btnSearchMain.setOnClickListener { showSearchDialog() }
        btnSearchCategory.setOnClickListener { showSearchDialog() }

        btnSetPlaylists.setOnClickListener { showEditUrlDialog(isEpg = false) }
        btnSetEpg.setOnClickListener { showEditUrlDialog(isEpg = true) }

        btnBackFromCategory.setOnClickListener { showCategoriesScreen() }

        tabLiveProgram.setOnClickListener { selectPlayerTab(archive = false) }
        tabArchiveProgram.setOnClickListener { selectPlayerTab(archive = true) }

        btnOsdPlayPause.setOnClickListener {
            if (player.isPlaying) {
                player.pause()
                btnOsdPlayPause.text = "▶"
            } else {
                player.play()
                btnOsdPlayPause.text = "❚❚"
            }
        }

        btnOsdTogglePanel.setOnClickListener {
            if (playerRightPanel.visibility == View.VISIBLE) {
                playerRightPanel.visibility = View.GONE
            } else {
                playerRightPanel.visibility = View.VISIBLE
                tabLiveProgram.requestFocus()
            }
        }

        btnOsdFavorite.setOnClickListener {
            activeChannel?.let { ch ->
                ch.isFavorite = !ch.isFavorite
                saveFavorites()
                rebuildCategories()
                btnOsdFavorite.setTextColor(if (ch.isFavorite) Color.parseColor("#FFD166") else Color.WHITE)
                Toast.makeText(this, if (ch.isFavorite) "Добавлено в Избранное" else "Удалено из Избранного", Toast.LENGTH_SHORT).show()
            }
        }

        btnOsdAspect.setOnClickListener {
            val modes = listOf(
                AspectRatioFrameLayout.RESIZE_MODE_FIT,
                AspectRatioFrameLayout.RESIZE_MODE_FILL,
                AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            )
            resizeModeIndex = (resizeModeIndex + 1) % modes.size
            playerView.resizeMode = modes[resizeModeIndex]
        }

        btnOsdBackToList.setOnClickListener {
            player.stop()
            openCategory(currentCategoryName, currentCategoryChannels)
        }

        playerView.setOnClickListener {
            if (osdBottomBar.visibility == View.VISIBLE) {
                osdBottomBar.visibility = View.GONE
                playerRightPanel.visibility = View.GONE
            } else {
                osdBottomBar.visibility = View.VISIBLE
                playerRightPanel.visibility = View.VISIBLE
                tabLiveProgram.requestFocus()
            }
        }
    }

    private fun switchScreen(s: Screen) {
        currentScreen = s
        screenCategories.visibility = if (s == Screen.CATEGORIES) View.VISIBLE else View.GONE
        screenCategoryChannels.visibility = if (s == Screen.CATEGORY_CHANNELS) View.VISIBLE else View.GONE
        screenPlayer.visibility = if (s == Screen.PLAYER) View.VISIBLE else View.GONE
        screenSettings.visibility = if (s == Screen.SETTINGS) View.VISIBLE else View.GONE
    }

    private fun showCategoriesScreen() {
        switchScreen(Screen.CATEGORIES)
        rvCategories.adapter = CategoryAdapter(categories) { name, list ->
            openCategory(name, list)
        }
        rvCategories.post {
            if (categories.isNotEmpty()) rvCategories.requestFocus() else btnSettingsMain.requestFocus()
        }
    }

    private fun openCategory(name: String, list: List<Channel>) {
        currentCategoryName = name
        currentCategoryChannels = list
        tvCategoryHeaderTitle.text = name
        switchScreen(Screen.CATEGORY_CHANNELS)

        rvCategoryChannels.adapter = ChannelAdapter(list, { getLiveProgram(it) }) { selectedChannel ->
            openPlayerWithSplitEpg(selectedChannel)
        }
        rvCategoryChannels.post { rvCategoryChannels.requestFocus() }
    }

    private fun openPlayerWithSplitEpg(channel: Channel) {
        activeChannel = channel
        switchScreen(Screen.PLAYER)
        playerRightPanel.visibility = View.VISIBLE
        osdBottomBar.visibility = View.VISIBLE

        updateOsdInfo(channel, null)
        playStream(channel.url)
        selectPlayerTab(archive = false)
        tabLiveProgram.requestFocus()
    }

    private fun selectPlayerTab(archive: Boolean) {
        isArchiveTab = archive
        tabLiveProgram.isSelected = !archive
        tabArchiveProgram.isSelected = archive

        val channel = activeChannel ?: return
        val now = System.currentTimeMillis()
        val allProgs = getProgramsForChannel(channel)

        if (!archive) {
            val upcoming = allProgs.filter { it.endMs > now }.take(40)
            rvPlayerEpg.adapter = EpgAdapter(upcoming, isArchive = false) {
                playStream(channel.url)
                updateOsdInfo(channel, null)
            }
        } else {
            val maxAgeMs = (channel.catchupDays.takeIf { it > 0 } ?: 5) * 24L * 3600L * 1000L
            val past = allProgs.filter { it.isPast(now) && it.startMs >= now - maxAgeMs }
                .sortedByDescending { it.startMs }
                .take(60)
            rvPlayerEpg.adapter = EpgAdapter(past, isArchive = true) { archiveProg ->
                val url = Parsers.buildArchiveUrl(channel, archiveProg)
                playStream(url)
                updateOsdInfo(channel, archiveProg)
            }
        }
    }

    private fun updateOsdInfo(channel: Channel, archiveProg: EpgProgram?) {
        tvOsdBadge.text = channel.name.take(3).uppercase(Locale.getDefault())
        tvOsdChannelName.text = channel.name
        btnOsdFavorite.setTextColor(if (channel.isFavorite) Color.parseColor("#FFD166") else Color.WHITE)

        if (archiveProg != null) {
            tvOsdCurrentShow.text = archiveProg.title
            tvOsdNextShow.text = "АРХИВ • ${dateTimeFormat.format(Date(archiveProg.startMs))}"
            tvOsdTimeRight.text = "${timeFormat.format(Date(archiveProg.startMs))}
${archiveProg.durationMinutes()} мин."
            osdProgressBar.progress = 50
            return
        }

        val now = System.currentTimeMillis()
        val progs = getProgramsForChannel(channel)
        val liveIdx = progs.indexOfFirst { it.isLive(now) }

        if (liveIdx >= 0) {
            val cur = progs[liveIdx]
            val next = progs.getOrNull(liveIdx + 1)
            tvOsdCurrentShow.text = cur.title
            tvOsdNextShow.text = if (next != null) "Далее: ${next.title}" else "Прямой эфир"
            tvOsdTimeRight.text = "${timeFormat.format(Date(cur.startMs))}
Далее ${timeFormat.format(Date(cur.endMs))}"
            val total = (cur.endMs - cur.startMs).coerceAtLeast(1L)
            val passed = (now - cur.startMs).coerceIn(0L, total)
            osdProgressBar.progress = ((passed * 100L) / total).toInt()
        } else {
            tvOsdCurrentShow.text = channel.name
            tvOsdNextShow.text = "Прямой эфир"
            tvOsdTimeRight.text = timeFormat.format(Date())
            osdProgressBar.progress = 100
        }
    }

    private fun playStream(url: String) {
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.play()
        btnOsdPlayPause.text = "❚❚"
    }

    private fun reloadCurrentPlaylist() {
        val prefs = getSharedPreferences("luma_televizo_prefs", Context.MODE_PRIVATE)
        val m3uUrl = prefs.getString("m3u_url", DEFAULT_PLAYLIST_URL) ?: DEFAULT_PLAYLIST_URL
        val epgUrl = prefs.getString("epg_url", "") ?: ""
        loadPlaylistAndEpg(m3uUrl, epgUrl)
    }

    private fun loadPlaylistAndEpg(m3uUrl: String, manualEpgUrl: String) {
        Toast.makeText(this, "Загрузка плейлиста...", Toast.LENGTH_SHORT).show()
        thread {
            try {
                val parsed = Parsers.parseM3u(m3uUrl)
                runOnUiThread {
                    if (parsed.channels.isNotEmpty()) {
                        allChannels = parsed.channels
                        restoreFavorites()
                        rebuildCategories()
                        showCategoriesScreen()
                        Toast.makeText(this, "Загружено каналов: ${allChannels.size}", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Плейлист пуст или недоступен", Toast.LENGTH_LONG).show()
                    }
                }

                val effectiveEpg = manualEpgUrl.ifBlank { parsed.detectedEpgUrl ?: "" }
                if (effectiveEpg.isNotBlank()) {
                    val loadedEpg = Parsers.parseXmltv(effectiveEpg)
                    runOnUiThread {
                        epgMap = loadedEpg
                        if (currentScreen == Screen.CATEGORY_CHANNELS) {
                            rvCategoryChannels.adapter?.notifyDataSetChanged()
                        }
                        Toast.makeText(this, "Программа передач обновлена", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Ошибка загрузки: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun rebuildCategories() {
        val result = mutableListOf<Pair<String, List<Channel>>>()
        val favs = allChannels.filter { it.isFavorite }
        if (favs.isNotEmpty()) {
            result.add("Избранное" to favs)
        }
        val grouped = allChannels.groupBy { it.category }
        for ((cat, list) in grouped) {
            result.add(cat to list)
        }
        categories = result
    }

    private fun getProgramsForChannel(channel: Channel): List<EpgProgram> {
        if (channel.tvgId.isNotEmpty()) {
            epgMap[channel.tvgId]?.let { return it }
        }
        return epgMap[channel.name.lowercase(Locale.getDefault())] ?: emptyList()
    }

    private fun getLiveProgram(channel: Channel): EpgProgram? {
        val now = System.currentTimeMillis()
        return getProgramsForChannel(channel).firstOrNull { it.isLive(now) }
    }

    private fun showEditUrlDialog(isEpg: Boolean) {
        val prefs = getSharedPreferences("luma_televizo_prefs", Context.MODE_PRIVATE)
        val key = if (isEpg) "epg_url" else "m3u_url"
        val currentVal = prefs.getString(key, if (isEpg) "" else DEFAULT_PLAYLIST_URL) ?: ""

        val input = EditText(this).apply {
            setText(currentVal)
            hint = if (isEpg) "https://.../epg.xml.gz" else DEFAULT_PLAYLIST_URL
            isFocusable = true
        }

        AlertDialog.Builder(this)
            .setTitle(if (isEpg) "Ссылка на программу передач (XMLTV)" else "Ссылка на плейлист M3U")
            .setView(input)
            .setPositiveButton("Сохранить и обновить") { _, _ ->
                val newUrl = input.text.toString().trim()
                prefs.edit().putString(key, newUrl).apply()
                reloadCurrentPlaylist()
                showCategoriesScreen()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showSearchDialog() {
        val input = EditText(this).apply {
            hint = "Введите название канала..."
            isFocusable = true
        }
        AlertDialog.Builder(this)
            .setTitle("Поиск канала")
            .setView(input)
            .setPositiveButton("Найти") { _, _ ->
                val q = input.text.toString().trim().lowercase(Locale.getDefault())
                if (q.isNotEmpty()) {
                    val found = allChannels.filter { it.name.lowercase(Locale.getDefault()).contains(q) }
                    openCategory("Поиск: $q", found)
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun saveFavorites() {
        val prefs = getSharedPreferences("luma_televizo_prefs", Context.MODE_PRIVATE)
        val favs = allChannels.filter { it.isFavorite }.map { it.url }.toSet()
        prefs.edit().putStringSet("favorites", favs).apply()
    }

    private fun restoreFavorites() {
        val prefs = getSharedPreferences("luma_televizo_prefs", Context.MODE_PRIVATE)
        val favs = prefs.getStringSet("favorites", emptySet()) ?: emptySet()
        allChannels.forEach { it.isFavorite = favs.contains(it.url) }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            when (currentScreen) {
                Screen.PLAYER -> {
                    if (playerRightPanel.visibility == View.VISIBLE || osdBottomBar.visibility == View.VISIBLE) {
                        playerRightPanel.visibility = View.GONE
                        osdBottomBar.visibility = View.GONE
                        playerView.requestFocus()
                        return true
                    }
                    player.stop()
                    openCategory(currentCategoryName, currentCategoryChannels)
                    return true
                }
                Screen.CATEGORY_CHANNELS -> {
                    showCategoriesScreen()
                    return true
                }
                Screen.SETTINGS -> {
                    showCategoriesScreen()
                    return true
                }
                Screen.CATEGORIES -> { }
            }
        } else if (currentScreen == Screen.PLAYER && playerRightPanel.visibility != View.VISIBLE) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_CHANNEL_UP) {
                switchChannelByOffset(-1)
                return true
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN) {
                switchChannelByOffset(1)
                return true
            } else if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                playerRightPanel.visibility = View.VISIBLE
                osdBottomBar.visibility = View.VISIBLE
                tabLiveProgram.requestFocus()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun switchChannelByOffset(offset: Int) {
        val cur = activeChannel ?: return
        if (currentCategoryChannels.isEmpty()) return
        val idx = currentCategoryChannels.indexOfFirst { it.url == cur.url }
        if (idx >= 0) {
            val nextIdx = (idx + offset + currentCategoryChannels.size) % currentCategoryChannels.size
            val nextCh = currentCategoryChannels[nextIdx]
            activeChannel = nextCh
            playStream(nextCh.url)
            updateOsdInfo(nextCh, null)
            osdBottomBar.visibility = View.VISIBLE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(clockRunnable)
        player.release()
    }

    class CategoryAdapter(
        private val items: List<Pair<String, List<Channel>>>,
        private val onClick: (String, List<Channel>) -> Unit
    ) : RecyclerView.Adapter<CategoryAdapter.VH>() {

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvName: TextView = v.findViewById(R.id.tvCategoryName)
            val tvCount: TextView = v.findViewById(R.id.tvCategoryCount)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_category, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val (name, list) = items[position]
            holder.tvName.text = name
            holder.tvCount.text = list.size.toString()

            holder.itemView.setOnFocusChangeListener { _, hasFocus ->
                val textColor = if (hasFocus) Color.parseColor("#182B5C") else Color.WHITE
                val countColor = if (hasFocus) Color.parseColor("#182B5C") else Color.parseColor("#D6E2FF")
                holder.tvName.setTextColor(textColor)
                holder.tvCount.setTextColor(countColor)
            }
            holder.itemView.setOnClickListener { onClick(name, list) }
        }

        override fun getItemCount(): Int = items.size
    }

    inner class ChannelAdapter(
        private val channels: List<Channel>,
        private val liveLookup: (Channel) -> EpgProgram?,
        private val onClick: (Channel) -> Unit
    ) : RecyclerView.Adapter<ChannelAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvBadge: TextView = v.findViewById(R.id.tvChannelBadge)
            val tvName: TextView = v.findViewById(R.id.tvChannelName)
            val tvProg: TextView = v.findViewById(R.id.tvChannelCurrentProgram)
            val tvFav: TextView = v.findViewById(R.id.tvFavStar)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_channel, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val ch = channels[position]
            val live = liveLookup(ch)

            holder.tvBadge.text = ch.name.take(3).uppercase(Locale.getDefault())
            holder.tvName.text = ch.name
            holder.tvProg.text = live?.title ?: "Прямой эфир"
            holder.tvFav.visibility = if (ch.isFavorite) View.VISIBLE else View.GONE

            holder.itemView.setOnFocusChangeListener { _, hasFocus ->
                val titleColor = if (hasFocus) Color.parseColor("#182B5C") else Color.WHITE
                val subColor = if (hasFocus) Color.parseColor("#28438A") else Color.parseColor("#CBD8F8")
                holder.tvName.setTextColor(titleColor)
                holder.tvProg.setTextColor(subColor)
            }
            holder.itemView.setOnClickListener { onClick(ch) }
        }

        override fun getItemCount(): Int = channels.size
    }

    inner class EpgAdapter(
        private val programs: List<EpgProgram>,
        private val isArchive: Boolean,
        private val onClick: (EpgProgram) -> Unit
    ) : RecyclerView.Adapter<EpgAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvTitle: TextView = v.findViewById(R.id.tvEpgTitle)
            val tvTime: TextView = v.findViewById(R.id.tvEpgTime)
            val tvDur: TextView = v.findViewById(R.id.tvEpgDuration)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_epg, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val p = programs[position]
            val now = System.currentTimeMillis()
            val startStr = if (isArchive) dateTimeFormat.format(Date(p.startMs)) else timeFormat.format(Date(p.startMs))
            val endStr = timeFormat.format(Date(p.endMs))

            holder.tvTitle.text = p.title
            holder.tvTime.text = if (!isArchive && p.isLive(now)) {
                "Сейчас / $startStr — $endStr"
            } else {
                "$startStr — $endStr"
            }
            holder.tvDur.text = "${p.durationMinutes()} минут"

            holder.itemView.setOnFocusChangeListener { _, hasFocus ->
                val titleColor = if (hasFocus) Color.parseColor("#182B5C") else Color.WHITE
                val subColor = if (hasFocus) Color.parseColor("#28438A") else Color.parseColor("#CBD8F8")
                holder.tvTitle.setTextColor(titleColor)
                holder.tvTime.setTextColor(subColor)
                holder.tvDur.setTextColor(subColor)
            }
            holder.itemView.setOnClickListener { onClick(p) }
        }

        override fun getItemCount(): Int = programs.size
    }
}
