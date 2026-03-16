package com.swipeify

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swipeify.ui.theme.SwipeifyTheme

class OnboardingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SwipeifyTheme {
                OnboardingFlow(
                    onFinish = { genres, artists, languages, moods, energy ->
                        val prefs = getSharedPreferences("swipeify_prefs", MODE_PRIVATE)
                        prefs.edit()
                            .putStringSet("selected_genres", genres.toSet())
                            .putStringSet("selected_artists", artists.toSet())
                            .putStringSet("selected_languages", languages.toSet())
                            .putStringSet("selected_moods", moods.toSet())
                            .putFloat("energy_level", energy)
                            .apply()
                        startActivity(Intent(this, SwipeActivity::class.java))
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun OnboardingFlow(
    onFinish: (List<String>, List<String>, List<String>, List<String>, Float) -> Unit
) {
    var currentStep by remember { mutableIntStateOf(0) }
    val selectedGenres = remember { mutableStateListOf<String>() }
    val selectedArtists = remember { mutableStateListOf<String>() }
    val selectedLanguages = remember { mutableStateListOf<String>() }
    val selectedMoods = remember { mutableStateListOf<String>() }
    var energyLevel by remember { mutableFloatStateOf(0.5f) }

    AnimatedContent(
        targetState = currentStep,
        transitionSpec = {
            slideInHorizontally { it } + fadeIn() togetherWith
                    slideOutHorizontally { -it } + fadeOut()
        },
        label = "onboarding_step"
    ) { step ->
        when (step) {
            0 -> GenreStep(
                selected = selectedGenres,
                onNext = { currentStep++ }
            )
            1 -> ArtistStep(
                selected = selectedArtists,
                onNext = { currentStep++ },
                onBack = { currentStep-- }
            )
            2 -> LanguageStep(
                selected = selectedLanguages,
                onNext = { currentStep++ },
                onBack = { currentStep-- }
            )
            3 -> MoodStep(
                selected = selectedMoods,
                onNext = { currentStep++ },
                onBack = { currentStep-- }
            )
            4 -> EnergyStep(
                energy = energyLevel,
                onEnergyChange = { energyLevel = it },
                onFinish = {
                    onFinish(
                        selectedGenres,
                        selectedArtists,
                        selectedLanguages,
                        selectedMoods,
                        energyLevel
                    )
                },
                onBack = { currentStep-- }
            )
        }
    }
}

// ─── Reusable scaffold ────────────────────────────────────────────────────────

@Composable
fun OnboardingScaffold(
    title: String,
    subtitle: String,
    step: Int,
    totalSteps: Int,
    onBack: (() -> Unit)? = null,
    nextLabel: String = "Continue →",
    nextEnabled: Boolean = true,
    onNext: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0D0D0D), Color(0xFF1A1A2E), Color(0xFF0D0D0D))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            // Progress dots
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(totalSteps) { i ->
                    Box(
                        modifier = Modifier
                            .height(4.dp)
                            .width(if (i == step) 24.dp else 12.dp)
                            .background(
                                if (i <= step) Color(0xFF1DB954) else Color(0xFF333333),
                                RoundedCornerShape(2.dp)
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(title, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(8.dp))
            Text(subtitle, fontSize = 14.sp, color = Color(0xFFAAAAAA), textAlign = TextAlign.Center)

            Spacer(modifier = Modifier.height(28.dp))

            content()

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onNext,
                enabled = nextEnabled,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (nextEnabled) Color(0xFF1DB954) else Color(0xFF333333),
                    disabledContainerColor = Color(0xFF333333)
                )
            ) {
                Text(
                    nextLabel,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (nextEnabled) Color.Black else Color(0xFF666666)
                )
            }

            if (onBack != null) {
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = onBack) {
                    Text("← Back", color = Color(0xFF666666), fontSize = 14.sp)
                }
            }
        }
    }
}

// ─── Reusable chip ────────────────────────────────────────────────────────────

@Composable
fun SelectionChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(
                if (isSelected) Color(0xFF1DB954).copy(alpha = 0.15f) else Color(0xFF1A1A1A),
                RoundedCornerShape(24.dp)
            )
            .border(
                1.5.dp,
                if (isSelected) Color(0xFF1DB954) else Color(0xFF333333),
                RoundedCornerShape(24.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) Color(0xFF1DB954) else Color(0xFFCCCCCC)
        )
    }
}

// ─── Step 1: Genres ───────────────────────────────────────────────────────────

@Composable
fun GenreStep(selected: MutableList<String>, onNext: () -> Unit) {
    val genres = listOf(
        "Pop", "Hip-Hop", "Rock", "Electronic", "R&B",
        "Jazz", "Classical", "Metal", "Indie", "Latin",
        "Country", "Reggae", "Blues", "Soul", "Funk",
        "Punk", "Alternative", "K-Pop", "Afrobeats", "Lo-Fi"
    )
    OnboardingScaffold(
        title = "What's your vibe?",
        subtitle = "Pick genres you love",
        step = 0,
        totalSteps = 5,
        nextEnabled = selected.isNotEmpty(),
        nextLabel = if (selected.isEmpty()) "Select at least one" else "Continue →",
        onNext = onNext
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(genres.size) {
                val genre = genres[it]
                SelectionChip(genre, selected.contains(genre)) {
                    if (selected.contains(genre)) selected.remove(genre) else selected.add(genre)
                }
            }
        }
    }
}

// ─── Step 2: Artists ──────────────────────────────────────────────────────────

@Composable
fun ArtistStep(selected: MutableList<String>, onNext: () -> Unit, onBack: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<SpotifyArtistItem>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(query) {
        if (query.length < 2) {
            searchResults = emptyList()
            return@LaunchedEffect
        }
        isSearching = true
        kotlinx.coroutines.delay(400)
        try {
            val token = withContext(Dispatchers.IO) { SpotifyTokenManager.getToken() }
            val response = withContext(Dispatchers.IO) {
                SpotifyClient.service.searchArtists(
                    token = "Bearer $token",
                    query = query
                )
            }
            searchResults = response.artists.items
        } catch (e: Exception) {
            android.util.Log.e("SWIPEIFY", "Artist search failed: ${e.message}")
        }
        isSearching = false
    }

    OnboardingScaffold(
        title = "Favorite artists?",
        subtitle = "Search any artist on Spotify",
        step = 1,
        totalSteps = 5,
        onBack = onBack,
        nextEnabled = selected.isNotEmpty(),
        nextLabel = if (selected.isEmpty()) "Select at least one" else "Continue →",
        onNext = onNext
    ) {
        // Search bar
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search artists...", color = Color(0xFF888888)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF1DB954),
                unfocusedBorderColor = Color(0xFF333333),
                cursorColor = Color(0xFF1DB954),
                focusedContainerColor = Color(0xFF1A1A1A),
                unfocusedContainerColor = Color(0xFF1A1A1A)
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Selected artists chips
        if (selected.isNotEmpty()) {
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(selected.size) { i ->
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF1DB954).copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                            .border(1.dp, Color(0xFF1DB954), RoundedCornerShape(20.dp))
                            .clickable { selected.removeAt(i) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("${selected[i]} ✕", color = Color(0xFF1DB954), fontSize = 12.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Search results or loading
        Box(modifier = Modifier.weight(1f)) {
            when {
                isSearching -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFF1DB954), modifier = Modifier.size(32.dp))
                    }
                }
                searchResults.isNotEmpty() -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(searchResults.size) { i ->
                            val artist = searchResults[i]
                            val isSelected = selected.contains(artist.name)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .background(
                                        if (isSelected) Color(0xFF1DB954).copy(alpha = 0.15f) else Color(0xFF1A1A1A),
                                        RoundedCornerShape(24.dp)
                                    )
                                    .border(
                                        1.5.dp,
                                        if (isSelected) Color(0xFF1DB954) else Color(0xFF333333),
                                        RoundedCornerShape(24.dp)
                                    )
                                    .clickable {
                                        if (isSelected) selected.remove(artist.name)
                                        else selected.add(artist.name)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    artist.name,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSelected) Color(0xFF1DB954) else Color(0xFFCCCCCC),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                            }
                        }
                    }
                }
                query.length < 2 -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Type to search any artist", color = Color(0xFF666666), fontSize = 14.sp)
                    }
                }
                else -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No artists found", color = Color(0xFF666666), fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

// ─── Step 3: Languages ────────────────────────────────────────────────────────

@Composable
fun LanguageStep(selected: MutableList<String>, onNext: () -> Unit, onBack: () -> Unit) {
    val languages = listOf(
        "English", "Spanish", "Hindi", "Korean", "French",
        "Portuguese", "Arabic", "Japanese", "German", "Italian",
        "Punjabi", "Turkish", "Swahili", "Dutch", "Russian"
    )
    OnboardingScaffold(
        title = "Language preference?",
        subtitle = "Optional — pick any you enjoy",
        step = 2,
        totalSteps = 5,
        onBack = onBack,
        nextLabel = "Continue →",
        nextEnabled = true, // optional step
        onNext = onNext
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(languages.size) {
                val lang = languages[it]
                SelectionChip(lang, selected.contains(lang)) {
                    if (selected.contains(lang)) selected.remove(lang) else selected.add(lang)
                }
            }
        }
    }
}

// ─── Step 4: Moods ────────────────────────────────────────────────────────────

@Composable
fun MoodStep(selected: MutableList<String>, onNext: () -> Unit, onBack: () -> Unit) {
    val moods = listOf(
        "Happy", "Sad", "Angry", "Relaxed",
        "Party", "Workout", "Meditative", "Heartbreak",
        "Late Night", "Morning", "Hype", "Sleep"
    )
    OnboardingScaffold(
        title = "What's your mood?",
        subtitle = "Optional — we'll match your energy",
        step = 3,
        totalSteps = 5,
        onBack = onBack,
        nextLabel = "Continue →",
        nextEnabled = true, // optional step
        onNext = onNext
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(moods.size) {
                val mood = moods[it]
                SelectionChip(mood, selected.contains(mood)) {
                    if (selected.contains(mood)) selected.remove(mood) else selected.add(mood)
                }
            }
        }
    }
}

// ─── Step 5: Energy Level ─────────────────────────────────────────────────────

@Composable
fun EnergyStep(
    energy: Float,
    onEnergyChange: (Float) -> Unit,
    onFinish: () -> Unit,
    onBack: () -> Unit
) {
    val label = when {
        energy < 0.2f -> "Super Chill"
        energy < 0.4f -> "Relaxed"
        energy < 0.6f -> "Balanced"
        energy < 0.8f -> "Energetic"
        else -> "Full Hype"
    }

    OnboardingScaffold(
        title = "Energy level?",
        subtitle = "Drag the slider to set your vibe",
        step = 4,
        totalSteps = 5,
        onBack = onBack,
        nextLabel = "Let's Go 🎵",
        nextEnabled = true,
        onNext = onFinish
    ) {
        Spacer(modifier = Modifier.height(40.dp))

        Text(
            text = label,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(40.dp))

        Slider(
            value = energy,
            onValueChange = onEnergyChange,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF1DB954),
                activeTrackColor = Color(0xFF1DB954),
                inactiveTrackColor = Color(0xFF333333)
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Chill", color = Color(0xFF666666), fontSize = 12.sp)
            Text("Hype", color = Color(0xFF666666), fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}