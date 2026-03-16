package com.swipeify

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.swipeify.ui.theme.SwipeifyTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SwipeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("swipeify_prefs", MODE_PRIVATE)
        val token = prefs.getString("auth_token", "") ?: ""
        val genres = prefs.getStringSet("selected_genres", setOf())?.toList() ?: listOf()
        val artists = prefs.getStringSet("selected_artists", setOf())?.toList() ?: listOf()
        val moods = prefs.getStringSet("selected_moods", setOf())?.toList() ?: listOf()
        val energy = prefs.getFloat("energy_level", 0.5f)

        setContent {
            SwipeifyTheme {
                MainScreen(
                    authToken = token,
                    selectedGenres = genres,
                    selectedArtists = artists,
                    selectedMoods = moods,
                    energyLevel = energy
                )
            }
        }
    }
}

// ─── Build smart search query from onboarding answers ────────────────────────

fun buildSearchQuery(
    genres: List<String>,
    artists: List<String>,
    moods: List<String>,
    energy: Float
): String {
    val parts = mutableListOf<String>()
    artists.take(2).forEach { parts.add(it) }
    genres.take(2).forEach { parts.add(it) }
    moods.take(2).forEach { mood ->
        parts.add(when (mood.lowercase()) {
            "happy" -> "happy upbeat"
            "sad" -> "sad emotional"
            "relaxed" -> "chill ambient"
            "party" -> "party dance"
            "workout" -> "workout energy"
            "meditative" -> "meditation peaceful"
            "heartbreak" -> "heartbreak emotional"
            "late night" -> "late night vibes"
            "morning" -> "morning fresh"
            "hype" -> "hype trap"
            "sleep" -> "sleep calm"
            "angry" -> "aggressive intense"
            else -> mood
        })
    }
    parts.add(when {
        energy < 0.3f -> "chill slow"
        energy < 0.6f -> "mid tempo"
        else -> "energetic fast"
    })
    return parts.joinToString(" ").ifEmpty { "top hits" }
}

// helper extension
private fun MutableList<String>.add(artist: String) = add(artist)

// main screen

@Composable
fun MainScreen(
    authToken: String,
    selectedGenres: List<String>,
    selectedArtists: List<String>,
    selectedMoods: List<String>,
    energyLevel: Float
) {
    var currentTab by remember { mutableStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF111111),
                tonalElevation = 0.dp
            ) {
                listOf(
                    Pair("Discover", "♪"),
                    Pair("Liked", "♥"),
                    Pair("Playlists", "☰"),
                    Pair("Profile", "◉")
                ).forEachIndexed { index, (label, icon) ->
                    NavigationBarItem(
                        selected = currentTab == index,
                        onClick = { currentTab = index },
                        icon = {
                            Text(
                                icon,
                                fontSize = 20.sp,
                                color = if (currentTab == index) Color(0xFF1DB954) else Color(0xFF666666)
                            )
                        },
                        label = {
                            Text(
                                label,
                                fontSize = 11.sp,
                                color = if (currentTab == index) Color(0xFF1DB954) else Color(0xFF666666)
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent)
                    )
                }
            }
        },
        containerColor = Color(0xFF0D0D0D)
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (currentTab) {
                0 -> DiscoverScreen(
                    authToken, selectedGenres, selectedArtists, selectedMoods, energyLevel,
                    onNavigateToPlaylists = { currentTab = 2 }
                )
                1 -> LikedSongsScreen(authToken)
                2 -> PlaylistsScreen(authToken)
                3 -> ProfileScreen(authToken)
            }
        }
    }
}

// ─── Discover Screen ──────────────────────────────────────────────────────────

@Composable
fun DiscoverScreen(
    authToken: String,
    selectedGenres: List<String>,
    selectedArtists: List<String>,
    selectedMoods: List<String>,
    energyLevel: Float,
    onNavigateToPlaylists: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var allTracks by remember { mutableStateOf<List<SpotifyTrack>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var currentIndex by remember { mutableStateOf(0) }
    var likeMessage by remember { mutableStateOf("") }
    var showEndDialog by remember { mutableStateOf(false) }
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var searchOffset by remember { mutableStateOf(0) }

    // Collect liked tracks during session
    val sessionLikedTracks = remember { mutableStateListOf<SpotifyTrack>() }

    // Preload playlists in background
    var cachedPlaylists by remember { mutableStateOf<List<PlaylistData>>(emptyList()) }
    LaunchedEffect(Unit) {
        try {
            val response = withContext(Dispatchers.IO) {
                ApiClient.service.getPlaylists("Bearer $authToken")
            }
            cachedPlaylists = response.playlists
        } catch (e: Exception) {
            android.util.Log.e("SWIPEIFY", "Preload playlists failed: ${e.message}")
        }
    }

    suspend fun loadTracks(offset: Int, likedTracks: List<SpotifyTrack>) {
        isLoading = true
        try {
            val spotifyToken = SpotifyTokenManager.getToken()
            val query = if (likedTracks.isNotEmpty()) {
                val likedArtists = likedTracks.map { it.artists.firstOrNull()?.name ?: "" }.distinct().take(2)
                buildSearchQuery(selectedGenres, likedArtists + selectedArtists, selectedMoods, energyLevel)
            } else {
                buildSearchQuery(selectedGenres, selectedArtists, selectedMoods, energyLevel)
            }
            val response = withContext(Dispatchers.IO) {
                SpotifyClient.service.searchTracks(
                    token = "Bearer $spotifyToken",
                    query = query,
                    limit = 20,
                    offset = offset
                )
            }
            allTracks = response.tracks.items.filter { it.album.images.isNotEmpty() }
            currentIndex = 0
        } catch (e: Exception) {
            android.util.Log.e("SWIPEIFY", "Failed to load tracks: ${e.message}")
        }
        isLoading = false
    }

    LaunchedEffect(Unit) {
        loadTracks(0, emptyList())
    }

    // Trigger end dialog when all cards swiped
    LaunchedEffect(currentIndex, allTracks.size) {
        if (allTracks.isNotEmpty() && currentIndex >= allTracks.size && !showEndDialog) {
            showEndDialog = true
        }
    }

    // End of batch dialog
    if (showEndDialog) {
        Dialog(onDismissRequest = {}) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1A1A1A), RoundedCornerShape(24.dp))
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Nice session!", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "You liked ${sessionLikedTracks.size} songs.\nWhat do you want to do?",
                    color = Color(0xFFAAAAAA),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))

                // Add liked songs to playlist
                if (sessionLikedTracks.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .background(Color(0xFF1DB954), RoundedCornerShape(25.dp))
                            .clickable {
                                showEndDialog = false
                                showPlaylistDialog = true
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Add to Playlist", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // More songs
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .background(Color(0xFF2A2A2A), RoundedCornerShape(25.dp))
                        .clickable {
                            showEndDialog = false
                            searchOffset += 20
                            coroutineScope.launch {
                                loadTracks(searchOffset, sessionLikedTracks)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("More Songs", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                }

                Spacer(modifier = Modifier.height(12.dp))

                // View playlists
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .background(Color(0xFF333333), RoundedCornerShape(25.dp))
                        .clickable {
                            showEndDialog = false
                            onNavigateToPlaylists()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("View My Playlists", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFAAAAAA))
                }
            }
        }
    }

    // Playlist dialog - shows after session ends, for ALL liked songs at once
    if (showPlaylistDialog && sessionLikedTracks.isNotEmpty()) {
        SessionPlaylistDialog(
            authToken = authToken,
            tracks = sessionLikedTracks,
            preloadedPlaylists = cachedPlaylists,
            onDismiss = {
                showPlaylistDialog = false
                onNavigateToPlaylists()
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Discover", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(
                    if (allTracks.isNotEmpty()) "${minOf(currentIndex + 1, allTracks.size)}/${allTracks.size}" else "",
                    fontSize = 14.sp,
                    color = Color(0xFF888888)
                )
            }

            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color(0xFF1DB954))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Finding music for you...", color = Color(0xFFAAAAAA), fontSize = 14.sp)
                        }
                    }
                }
                allTracks.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No songs found. Try again!", color = Color(0xFFAAAAAA), fontSize = 16.sp)
                    }
                }
                currentIndex < allTracks.size -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        if (currentIndex + 1 < allTracks.size) {
                            SongCard(
                                track = allTracks[currentIndex + 1],
                                modifier = Modifier.fillMaxWidth(0.88f).fillMaxHeight(0.78f).offset(y = 16.dp)
                            )
                        }
                        SwipeableCard(
                            track = allTracks[currentIndex],
                            onSwipeRight = {
                                val track = allTracks[currentIndex]
                                sessionLikedTracks.add(track)
                                likeMessage = "Liked!"
                                coroutineScope.launch {
                                    try {
                                        withContext(Dispatchers.IO) {
                                            ApiClient.service.likeSong(
                                                token = "Bearer $authToken",
                                                request = LikeSongRequest(
                                                    spotify_track_id = track.id,
                                                    track_name = track.name,
                                                    artist_name = track.artists.firstOrNull()?.name ?: "",
                                                    album_name = track.album.name,
                                                    album_art_url = track.album.images.firstOrNull()?.url,
                                                    preview_url = track.preview_url
                                                )
                                            )
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.e("SWIPEIFY", "Like failed: ${e.message}")
                                    }
                                }
                                currentIndex++
                            },
                            onSwipeLeft = {
                                likeMessage = "Skipped"
                                currentIndex++
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ActionButton(label = "✕", color = Color(0xFFFF4444)) {
                            likeMessage = "Skipped"
                            currentIndex++
                        }
                        if (likeMessage.isNotEmpty()) {
                            Text(
                                likeMessage,
                                color = if (likeMessage == "Liked!") Color(0xFF1DB954) else Color(0xFFAAAAAA),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        ActionButton(label = "♥", color = Color(0xFF1DB954)) {
                            val track = allTracks[currentIndex]
                            sessionLikedTracks.add(track)
                            likeMessage = "Liked!"
                            coroutineScope.launch {
                                try {
                                    withContext(Dispatchers.IO) {
                                        ApiClient.service.likeSong(
                                            token = "Bearer $authToken",
                                            request = LikeSongRequest(
                                                spotify_track_id = track.id,
                                                track_name = track.name,
                                                artist_name = track.artists.firstOrNull()?.name ?: "",
                                                album_name = track.album.name,
                                                album_art_url = track.album.images.firstOrNull()?.url,
                                                preview_url = track.preview_url
                                            )
                                        )
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("SWIPEIFY", "Like failed: ${e.message}")
                                }
                            }
                            currentIndex++
                        }
                    }
                }
            }
        }
    }
}

// ─── Playlist Picker Dialog ───────────────────────────────────────────────────

@Composable
fun SessionPlaylistDialog(
    authToken: String,
    tracks: List<SpotifyTrack>,
    preloadedPlaylists: List<PlaylistData>,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var playlists by remember { mutableStateOf(preloadedPlaylists) }
    var showCreateNew by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A1A1A), RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            Text("Save to Playlist", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "${tracks.size} liked songs will be added",
                fontSize = 13.sp,
                color = Color(0xFF888888)
            )

            Spacer(modifier = Modifier.height(20.dp))

            if (isSaving) {
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color(0xFF1DB954))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Saving songs...", color = Color(0xFFAAAAAA), fontSize = 13.sp)
                    }
                }
            } else {
                // Existing playlists list
                if (playlists.isNotEmpty()) {
                    playlists.forEach { playlist ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    isSaving = true
                                    coroutineScope.launch {
                                        tracks.forEach { track ->
                                            try {
                                                withContext(Dispatchers.IO) {
                                                    ApiClient.service.addSongToPlaylist(
                                                        token = "Bearer $authToken",
                                                        playlistId = playlist.id,
                                                        request = AddSongToPlaylistRequest(
                                                            spotify_track_id = track.id,
                                                            track_name = track.name,
                                                            artist_name = track.artists.firstOrNull()?.name ?: "",
                                                            album_name = track.album.name,
                                                            album_art_url = track.album.images.firstOrNull()?.url,
                                                            preview_url = track.preview_url
                                                        )
                                                    )
                                                }
                                            } catch (e: Exception) {
                                                android.util.Log.e("SWIPEIFY", "Failed adding to playlist: ${e.message}")
                                            }
                                        }
                                        onDismiss()
                                    }
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(Color(0xFF333333), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (playlist.cover_art != null) {
                                    AsyncImage(
                                        model = playlist.cover_art,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Text("☰", fontSize = 18.sp, color = Color(0xFF666666))
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(playlist.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Text("${playlist.song_count} songs", color = Color(0xFF888888), fontSize = 12.sp)
                            }
                        }
                        HorizontalDivider(color = Color(0xFF2A2A2A))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Create new playlist section
                if (showCreateNew) {
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        label = { Text("Playlist name", color = Color(0xFF888888)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF1DB954),
                            unfocusedBorderColor = Color(0xFF444444),
                            cursorColor = Color(0xFF1DB954),
                            focusedContainerColor = Color(0xFF222222),
                            unfocusedContainerColor = Color(0xFF222222)
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier.weight(1f).height(44.dp).background(Color(0xFF333333), RoundedCornerShape(22.dp)).clickable { showCreateNew = false },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Cancel", color = Color(0xFFAAAAAA), fontSize = 14.sp)
                        }
                        Box(
                            modifier = Modifier.weight(1f).height(44.dp).background(Color(0xFF1DB954), RoundedCornerShape(22.dp)).clickable {
                                if (newPlaylistName.isNotBlank()) {
                                    isSaving = true
                                    coroutineScope.launch {
                                        try {
                                            val created = withContext(Dispatchers.IO) {
                                                ApiClient.service.createPlaylist(
                                                    "Bearer $authToken",
                                                    CreatePlaylistRequest(newPlaylistName)
                                                )
                                            }
                                            tracks.forEach { track ->
                                                try {
                                                    withContext(Dispatchers.IO) {
                                                        ApiClient.service.addSongToPlaylist(
                                                            token = "Bearer $authToken",
                                                            playlistId = created.playlist.id,
                                                            request = AddSongToPlaylistRequest(
                                                                spotify_track_id = track.id,
                                                                track_name = track.name,
                                                                artist_name = track.artists.firstOrNull()?.name ?: "",
                                                                album_name = track.album.name,
                                                                album_art_url = track.album.images.firstOrNull()?.url,
                                                                preview_url = track.preview_url
                                                            )
                                                        )
                                                    }
                                                } catch (e: Exception) {
                                                    android.util.Log.e("SWIPEIFY", "Add song failed: ${e.message}")
                                                }
                                            }
                                        } catch (e: Exception) {
                                            android.util.Log.e("SWIPEIFY", "Create playlist failed: ${e.message}")
                                        }
                                        onDismiss()
                                    }
                                }
                            },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Create & Save", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { showCreateNew = true }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(44.dp).background(Color(0xFF1DB954).copy(alpha = 0.15f), RoundedCornerShape(10.dp)).border(1.dp, Color(0xFF1DB954), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("+", fontSize = 22.sp, color = Color(0xFF1DB954))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Create new playlist", color = Color(0xFF1DB954), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Skip for now", color = Color(0xFF666666), fontSize = 14.sp)
                }
            }
        }
    }
}

// ─── Playlists Screen ─────────────────────────────────────────────────────────

@Composable
fun PlaylistsScreen(authToken: String) {
    var playlists by remember { mutableStateOf<List<PlaylistData>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedPlaylist by remember { mutableStateOf<PlaylistData?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun loadPlaylists() {
        coroutineScope.launch {
            isLoading = true
            try {
                val response = withContext(Dispatchers.IO) {
                    ApiClient.service.getPlaylists("Bearer $authToken")
                }
                playlists = response.playlists
            } catch (e: Exception) {
                android.util.Log.e("SWIPEIFY", "Failed to load playlists: ${e.message}")
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { loadPlaylists() }

    if (selectedPlaylist != null) {
        PlaylistDetailScreen(
            authToken = authToken,
            playlist = selectedPlaylist!!,
            onBack = { selectedPlaylist = null }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D))
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Text("Playlists", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(4.dp))
        Text("${playlists.size} playlists", fontSize = 14.sp, color = Color(0xFF888888))
        Spacer(modifier = Modifier.height(20.dp))

        when {
            isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF1DB954))
                }
            }
            playlists.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("☰", fontSize = 48.sp, color = Color(0xFF333333))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No playlists yet", color = Color(0xFFAAAAAA), fontSize = 16.sp)
                        Text("Like songs to create playlists!", color = Color(0xFF666666), fontSize = 13.sp)
                    }
                }
            }
            else -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(playlists) { playlist ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF1A1A1A), RoundedCornerShape(16.dp))
                                .clickable { selectedPlaylist = playlist }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .background(Color(0xFF333333), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (playlist.cover_art != null) {
                                    AsyncImage(model = playlist.cover_art, contentDescription = null, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                                } else {
                                    Text("☰", fontSize = 24.sp, color = Color(0xFF666666))
                                }
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(playlist.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("${playlist.song_count} songs", color = Color(0xFF888888), fontSize = 13.sp)
                            }
                            Text("›", fontSize = 22.sp, color = Color(0xFF555555))
                        }
                    }
                }
            }
        }
    }
}

// ─── Playlist Detail Screen ───────────────────────────────────────────────────

@Composable
fun PlaylistDetailScreen(
    authToken: String,
    playlist: PlaylistData,
    onBack: () -> Unit
) {
    var songs by remember { mutableStateOf<List<PlaylistSongData>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try {
            val response = withContext(Dispatchers.IO) {
                ApiClient.service.getPlaylistSongs("Bearer $authToken", playlist.id)
            }
            songs = response.songs
        } catch (e: Exception) {
            android.util.Log.e("SWIPEIFY", "Failed to load playlist songs: ${e.message}")
        }
        isLoading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D))
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("← ", color = Color(0xFF1DB954), fontSize = 20.sp, modifier = Modifier.clickable { onBack() })
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(playlist.name, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("${playlist.song_count} songs", fontSize = 13.sp, color = Color(0xFF888888))
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        when {
            isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF1DB954))
            }
            songs.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No songs in this playlist yet", color = Color(0xFFAAAAAA), fontSize = 15.sp)
            }
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(songs) { song ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1A1A1A), RoundedCornerShape(14.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(model = song.album_art_url, contentDescription = null, modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(song.track_name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(song.artist_name, color = Color(0xFFAAAAAA), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

// ─── Swipeable Card ───────────────────────────────────────────────────────────

@Composable
fun SwipeableCard(track: SpotifyTrack, onSwipeRight: () -> Unit, onSwipeLeft: () -> Unit) {
    var offsetX by remember { mutableStateOf(0f) }
    val rotation = (offsetX / 30f).coerceIn(-15f, 15f)
    val animatedOffsetX by animateFloatAsState(targetValue = offsetX, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "offsetX")
    val likeAlpha = (offsetX / 300f).coerceIn(0f, 1f)
    val skipAlpha = (-offsetX / 300f).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .fillMaxHeight(0.78f)
            .offset(x = animatedOffsetX.dp)
            .rotate(rotation)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        when {
                            offsetX > 120f -> onSwipeRight()
                            offsetX < -120f -> onSwipeLeft()
                        }
                        offsetX = 0f
                    },
                    onDragCancel = { offsetX = 0f },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x * 0.4f
                    }
                )
            }
    ) {
        SongCard(track = track, modifier = Modifier.fillMaxSize())
        if (likeAlpha > 0.05f) {
            Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).background(Color(0xFF1DB954).copy(alpha = likeAlpha * 0.4f)), contentAlignment = Alignment.Center) {
                Text("LIKE", fontSize = 40.sp, fontWeight = FontWeight.Black, color = Color(0xFF1DB954).copy(alpha = likeAlpha))
            }
        }
        if (skipAlpha > 0.05f) {
            Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).background(Color(0xFFFF4444).copy(alpha = skipAlpha * 0.4f)), contentAlignment = Alignment.Center) {
                Text("SKIP", fontSize = 40.sp, fontWeight = FontWeight.Black, color = Color(0xFFFF4444).copy(alpha = skipAlpha))
            }
        }
    }
}

// ─── Song Card ────────────────────────────────────────────────────────────────

@Composable
fun SongCard(track: SpotifyTrack, modifier: Modifier = Modifier) {
    Box(modifier = modifier.shadow(16.dp, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp)).background(Color(0xFF1A1A1A))) {
        AsyncImage(model = track.album.images.firstOrNull()?.url, contentDescription = "Album art", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Transparent, Color(0xFF000000).copy(alpha = 0.7f), Color(0xFF000000).copy(alpha = 0.95f)))))
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(24.dp)) {
            Text(track.name, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(4.dp))
            Text(track.artists.joinToString(", ") { it.name }, fontSize = 16.sp, color = Color(0xFFCCCCCC))
            Spacer(modifier = Modifier.height(4.dp))
            Text(track.album.name, fontSize = 13.sp, color = Color(0xFF888888), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ─── Action Button ────────────────────────────────────────────────────────────

@Composable
fun ActionButton(label: String, color: Color, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.size(64.dp), shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A1A)), contentPadding = PaddingValues(0.dp), elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)) {
        Text(label, fontSize = 24.sp, color = color)
    }
}

// ─── Liked Songs Screen ───────────────────────────────────────────────────────

@Composable
fun LikedSongsScreen(authToken: String) {
    var songs by remember { mutableStateOf<List<LikedSong>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            val response = withContext(Dispatchers.IO) { ApiClient.service.getLikedSongs("Bearer $authToken") }
            songs = response.songs
        } catch (e: Exception) {
            android.util.Log.e("SWIPEIFY", "Failed to load liked songs: ${e.message}")
        }
        isLoading = false
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0D0D0D)).padding(horizontal = 20.dp)) {
        Spacer(modifier = Modifier.height(24.dp))
        Text("Liked Songs", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(4.dp))
        Text("${songs.size} songs", fontSize = 14.sp, color = Color(0xFF888888))
        Spacer(modifier = Modifier.height(20.dp))

        when {
            isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color(0xFF1DB954)) }
            songs.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("♥", fontSize = 48.sp, color = Color(0xFF333333))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No liked songs yet", color = Color(0xFFAAAAAA), fontSize = 16.sp)
                    Text("Swipe right to save songs!", color = Color(0xFF666666), fontSize = 13.sp)
                }
            }
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(songs) { song ->
                    Row(
                        modifier = Modifier.fillMaxWidth().background(Color(0xFF1A1A1A), RoundedCornerShape(16.dp)).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(model = song.album_art_url, contentDescription = null, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(song.track_name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(song.artist_name, color = Color(0xFFAAAAAA), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text("♥", fontSize = 16.sp, color = Color(0xFF1DB954))
                    }
                }
            }
        }
    }
}

// ─── Profile Screen ───────────────────────────────────────────────────────────

@Composable
fun ProfileScreen(authToken: String) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("swipeify_prefs", Context.MODE_PRIVATE)
    val fullName = prefs.getString("full_name", "User") ?: "User"
    val username = prefs.getString("username", "") ?: ""

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0D0D0D)).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        Box(modifier = Modifier.size(100.dp).background(Color(0xFF1DB954), CircleShape), contentAlignment = Alignment.Center) {
            Text(fullName.firstOrNull()?.uppercaseChar()?.toString() ?: "U", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(fullName, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(4.dp))
        Text("@$username", fontSize = 14.sp, color = Color(0xFF888888))
        Spacer(modifier = Modifier.height(40.dp))
        Box(
            modifier = Modifier.fillMaxWidth().height(54.dp).background(Color(0xFF1A1A1A), RoundedCornerShape(28.dp)),
            contentAlignment = Alignment.Center
        ) {
            TextButton(
                onClick = {
                    prefs.edit().clear().commit()
                    val intent = Intent(context, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxSize()
            ) {
                Text("Log Out", color = Color(0xFFFF4444), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}