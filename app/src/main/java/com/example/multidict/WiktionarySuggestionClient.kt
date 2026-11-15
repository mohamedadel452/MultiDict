package com.example.multidict

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Fetches German word autocomplete suggestions from the Wiktionary OpenSearch API
 * using [HttpURLConnection] on [Dispatchers.IO].
 */
object WiktionarySuggestionClient {

    suspend fun fetchSuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return@withContext emptyList()

        var connection: HttpURLConnection? = null
        try {
            val encodedQuery = URLEncoder.encode(trimmed, Charsets.UTF_8.name())
            val urlString =
                "https://de.wiktionary.org/w/api.php?action=opensearch&search=$encodedQuery&limit=10&namespace=0&format=json"
            val url = URL(urlString)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("User-Agent", "MultiDict/1.0 (Android; German Learner App)")
                setRequestProperty("Accept", "application/json")
            }

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext emptyList()
            }

            val responseBody = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val rootArray = JSONArray(responseBody)
            val suggestionsArray = rootArray.optJSONArray(1) ?: return@withContext emptyList()

            val results = ArrayList<String>(suggestionsArray.length())
            for (i in 0 until suggestionsArray.length()) {
                val word = suggestionsArray.optString(i).trim()
                if (word.isNotEmpty()) {
                    results.add(word)
                }
            }
            results
        } catch (_: Exception) {
            // Ignore network and parsing errors silently per specification
            emptyList()
        } finally {
            connection?.disconnect()
        }
    }
}
