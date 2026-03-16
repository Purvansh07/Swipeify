package com.swipeify

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

private const val BASE_URL = "http://10.0.2.2:8000/"

// ─── Request Models ───────────────────────────────────────────────────────────

data class RegisterRequest(val full_name: String, val username: String, val email: String, val password: String, val phone: String = "")
data class LoginRequest(val email: String, val password: String)
data class LikeSongRequest(val spotify_track_id: String, val track_name: String, val artist_name: String, val album_name: String, val album_art_url: String?, val preview_url: String?)
data class CreatePlaylistRequest(val name: String)
data class AddSongToPlaylistRequest(val spotify_track_id: String, val track_name: String, val artist_name: String, val album_name: String, val album_art_url: String?, val preview_url: String?)

// ─── Response Models ──────────────────────────────────────────────────────────

data class UserData(val id: Int, val full_name: String, val username: String, val email: String)
data class AuthResponse(val message: String, val token: String, val user: UserData)
data class MessageResponse(val message: String)

data class LikedSong(val id: Int, val spotify_track_id: String, val track_name: String, val artist_name: String, val album_name: String, val album_art_url: String?, val preview_url: String?, val liked_at: String)
data class LikedSongsResponse(val songs: List<LikedSong>)

data class PlaylistData(val id: Int, val name: String, val song_count: Int, val created_at: String, val cover_art: String?)
data class PlaylistResponse(val message: String, val playlist: PlaylistData)
data class PlaylistsResponse(val playlists: List<PlaylistData>)
data class PlaylistSongData(val id: Int, val spotify_track_id: String, val track_name: String, val artist_name: String, val album_name: String, val album_art_url: String?, val preview_url: String?)
data class PlaylistBasic(val id: Int, val name: String)
data class PlaylistDetailResponse(val playlist: PlaylistBasic, val songs: List<PlaylistSongData>)

// ─── Spotify Models ───────────────────────────────────────────────────────────

data class SpotifyTrack(val id: String, val name: String, val artists: List<SpotifyArtist>, val album: SpotifyAlbum, val preview_url: String?)
data class SpotifyArtist(val id: String = "", val name: String)
data class SpotifyAlbum(val name: String, val images: List<SpotifyImage>)
data class SpotifyImage(val url: String)
data class SpotifySearchResponse(val tracks: SpotifyTracks)
data class SpotifyTracks(val items: List<SpotifyTrack>)
data class SpotifyArtistSearchResponse(val artists: SpotifyArtistResults)
data class SpotifyArtistResults(val items: List<SpotifyArtistItem>)
data class SpotifyArtistItem(val id: String, val name: String, val images: List<SpotifyImage>)

// ─── API Interface ────────────────────────────────────────────────────────────

interface ApiService {
    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): AuthResponse

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): AuthResponse

    @POST("api/auth/songs/like")
    suspend fun likeSong(@Header("Authorization") token: String, @Body request: LikeSongRequest): MessageResponse

    @GET("api/auth/songs/liked")
    suspend fun getLikedSongs(@Header("Authorization") token: String): LikedSongsResponse

    @DELETE("api/auth/songs/unlike/{track_id}")
    suspend fun unlikeSong(@Header("Authorization") token: String, @Path("track_id") trackId: String): MessageResponse

    @POST("api/auth/playlists/create")
    suspend fun createPlaylist(@Header("Authorization") token: String, @Body request: CreatePlaylistRequest): PlaylistResponse

    @GET("api/auth/playlists")
    suspend fun getPlaylists(@Header("Authorization") token: String): PlaylistsResponse

    @GET("api/auth/playlists/{playlist_id}/songs")
    suspend fun getPlaylistSongs(@Header("Authorization") token: String, @Path("playlist_id") playlistId: Int): PlaylistDetailResponse

    @POST("api/auth/playlists/{playlist_id}/songs")
    suspend fun addSongToPlaylist(@Header("Authorization") token: String, @Path("playlist_id") playlistId: Int, @Body request: AddSongToPlaylistRequest): MessageResponse

    @DELETE("api/auth/playlists/{playlist_id}")
    suspend fun deletePlaylist(@Header("Authorization") token: String, @Path("playlist_id") playlistId: Int): MessageResponse
}

interface SpotifyApiService {
    @GET("v1/search")
    suspend fun searchTracks(@Header("Authorization") token: String, @Query("q") query: String, @Query("type") type: String = "track", @Query("limit") limit: Int = 20, @Query("offset") offset: Int = 0): SpotifySearchResponse

    @GET("v1/search")
    suspend fun searchArtists(@Header("Authorization") token: String, @Query("q") query: String, @Query("type") type: String = "artist", @Query("limit") limit: Int = 15): SpotifyArtistSearchResponse
}

object ApiClient {
    val service: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}

object SpotifyClient {
    val service: SpotifyApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.spotify.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SpotifyApiService::class.java)
    }
}