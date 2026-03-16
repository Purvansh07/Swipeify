package com.swipeify

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

object SpotifyTokenManager {
    // Your Spotify app credentials - client credentials flow (no user login needed)
    private const val CLIENT_ID = "3a46720cc7de44259ab3b01ae51e2ebf"
    private const val CLIENT_SECRET = "c361761e7ddb4cb4b7a0215168246ad4"

    private var cachedToken: String? = null
    private var tokenExpiry: Long = 0

    suspend fun getToken(): String {
        // Return cached token if still valid
        if (cachedToken != null && System.currentTimeMillis() < tokenExpiry) {
            return cachedToken!!
        }

        return withContext(Dispatchers.IO) {
            val credentials = Base64.encodeToString(
                "$CLIENT_ID:$CLIENT_SECRET".toByteArray(),
                Base64.NO_WRAP
            )

            val client = OkHttpClient()
            val body = FormBody.Builder()
                .add("grant_type", "client_credentials")
                .build()

            val request = Request.Builder()
                .url("https://accounts.spotify.com/api/token")
                .post(body)
                .header("Authorization", "Basic $credentials")
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.peekBody(Long.MAX_VALUE).string()
            val json = JSONObject(responseBody)

            cachedToken = json.getString("access_token")
            tokenExpiry = System.currentTimeMillis() + (3540 * 1000)
            cachedToken!!
            // Token expires in 3600 seconds, refresh 60s early
            tokenExpiry = System.currentTimeMillis() + (3540 * 1000)
            cachedToken!!
        }
    }
}