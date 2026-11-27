package com.feerlessleadr.downbeat.ma

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Artwork addresses. The server writes its own internal address into image URLs, so the host is always replaced by
 * the address the user typed. Verified form: <server>/imageproxy/<proxy_id>?size=512&fmt=jpg
 */
object ImageUrls {
    /** Builds an artwork URL from an image reference, or null when the image has no proxy id. */
    fun forImage(serverUrl: String, image: MediaItemImage?, size: Int = 512): String? {
        image ?: return null
        val base = serverUrl.trim().toHttpUrlOrNull() ?: return null
        val proxyId = image.proxyId
        if (proxyId.isNullOrBlank()) return null
        return base.newBuilder()
            .encodedPath("/imageproxy/$proxyId")
            .query(null)
            .addQueryParameter("size", size.toString())
            .addQueryParameter("fmt", "jpg")
            .build()
            .toString()
    }

    /** Takes a URL the server produced (such as PlayerMedia.imageUrl) and points it at the address the user typed. */
    fun rewriteHost(serverUrl: String, serverProvidedUrl: String?): String? {
        serverProvidedUrl ?: return null
        val base = serverUrl.trim().toHttpUrlOrNull() ?: return null
        val given = serverProvidedUrl.toHttpUrlOrNull() ?: return null
        return given.newBuilder()
            .scheme(base.scheme)
            .host(base.host)
            .port(base.port)
            .build()
            .toString()
    }
}
