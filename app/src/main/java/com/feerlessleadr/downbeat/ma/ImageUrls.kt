package com.feerlessleadr.downbeat.ma

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Artwork addresses. The server writes its own internal address into image URLs, so the host is always replaced by
 * the address the user typed. Verified form: <server>/imageproxy/<proxy_id>?size=512&fmt=jpg
 */
object ImageUrls {
    /**
     * The server refuses odd sizes: size=156 gave HTTP 400 on the owner's server (2026-11-27), while 256 and 512 work.
     * So every request is rounded to 256 (small thumbnails) or 512 (anything bigger).
     */
    fun allowedSize(requested: Int): Int = if (requested <= 256) 256 else 512

    /** Builds an artwork URL from an image reference, or null when the image has no proxy id. */
    fun forImage(serverUrl: String, image: MediaItemImage?, size: Int = 512): String? {
        image ?: return null
        val allowed = allowedSize(size)
        val base = serverUrl.trim().toHttpUrlOrNull() ?: return null
        val proxyId = image.proxyId
        if (proxyId.isNullOrBlank()) return null
        return base.newBuilder()
            .encodedPath("/imageproxy/$proxyId")
            .query(null)
            .addQueryParameter("size", allowed.toString())
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
