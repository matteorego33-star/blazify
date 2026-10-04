/**
 * Blazify Project (C) 2026
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See NOTICE for contributors
 */

package com.blazify.music.listentogether

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ListenTogetherServer(
    val name: String,
    val url: String,
    val location: String,
    val operator: String
)

// Blazify's own server comes first and is the default. The community server
// it borrowed until now allows only clients on its list to host rooms, and
// since September Blazify was not on it: every "Create room" was refused.
// The Meowery stays as a choice — joining its rooms still works from here.
object ListenTogetherServers {
    private const val ServersJson = """
        [
          {
            "name": "Blazify",
            "url": "wss://blazify-together.onrender.com/ws",
            "location": "Singapore",
            "operator": "Rajendra Pandey"
          },
          {
            "name": "The Meowery",
            "url": "wss://metroserverx.meowery.eu/ws",
            "location": "Poland",
            "operator": "Nyx"
          }
        ]
    """

    private val json = Json { ignoreUnknownKeys = true }

    val servers: List<ListenTogetherServer> by lazy {
        json.decodeFromString(ServersJson)
    }

    val defaultServerUrl: String
        get() = servers.first().url

    fun findByUrl(url: String): ListenTogetherServer? = servers.firstOrNull { it.url == url }
}
