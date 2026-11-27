package com.feerlessleadr.downbeat.data

import com.feerlessleadr.downbeat.ma.ProviderConfig
import com.feerlessleadr.downbeat.ma.ProviderManifest

/** One line of the providers screen. */
data class ProviderRow(
    val instanceId: String,
    val name: String,
    /** What it is, for example "Music source" or a short description from its manifest. */
    val detail: String,
    val status: ProviderStatus,
    /** The server's error text when the provider is not working, else null. */
    val error: String?,
    val domain: String = "",
    /** music, player, metadata, plugin. */
    val type: String = "",
    val enabled: Boolean = true,
    /** The manifest allows turning it off. False when unknown, so nothing risky is offered by guesswork. */
    val canTurnOff: Boolean = false,
    /** It is not built into the server (a built-in provider cannot be removed). False when unknown. */
    val canRemove: Boolean = false,
    /** A music source that is on, so its library can be synced. */
    val canSync: Boolean = false,
)

enum class ProviderStatus(val label: String) {
    Working("Working"),
    Starting("Starting"),
    Off("Off"),
    SignInNeeded("Sign-in needed"),
    Incompatible("Not compatible"),
    Problem("Problem"),
}

/** Providers of one kind, in the order the screen shows them. */
data class ProviderGroup(val title: String, val rows: List<ProviderRow>)

private val GROUPS = listOf(
    "music" to "Music sources",
    "player" to "Players",
    "metadata" to "Metadata",
    "plugin" to "Plugins",
)

fun providerStatus(config: ProviderConfig): ProviderStatus = when {
    !config.enabled || config.status == "disabled" -> ProviderStatus.Off
    config.status == "loaded" -> ProviderStatus.Working
    config.status == "loading" -> ProviderStatus.Starting
    config.status == "auth_required" -> ProviderStatus.SignInNeeded
    config.status == "incompatible" -> ProviderStatus.Incompatible
    config.status == "error" || config.lastError != null -> ProviderStatus.Problem
    // No status reported: an enabled provider with no error is taken to be working.
    else -> ProviderStatus.Working
}

/**
 * Groups the configured providers by kind (music sources first), sorted by name, using the manifests for a short
 * description. Kinds the app does not name (core, audio analysis, unknown) are left out: nothing to manage there.
 */
fun providerGroups(configs: List<ProviderConfig>, manifests: List<ProviderManifest>): List<ProviderGroup> {
    val byDomain = manifests.associateBy { it.domain }
    return GROUPS.mapNotNull { (type, title) ->
        val rows = configs.filter { it.type == type }.map { c ->
            val manifest = byDomain[c.domain]
            val status = providerStatus(c)
            ProviderRow(
                instanceId = c.instanceId,
                name = c.name?.takeIf { it.isNotBlank() } ?: c.defaultName?.takeIf { it.isNotBlank() }
                    ?: manifest?.name?.takeIf { it.isNotBlank() } ?: c.domain,
                detail = manifest?.description?.takeIf { it.isNotBlank() } ?: title.removeSuffix("s"),
                status = status,
                error = c.lastError?.message?.takeIf { it.isNotBlank() && status == ProviderStatus.Problem },
                domain = c.domain,
                type = c.type,
                enabled = c.enabled && c.status != "disabled",
                canTurnOff = manifest?.allowDisable == true,
                canRemove = manifest != null && !manifest.builtin,
                canSync = c.type == "music" && c.enabled && c.status != "disabled",
            )
        }.sortedBy { it.name.lowercase() }
        if (rows.isEmpty()) null else ProviderGroup(title, rows)
    }
}
