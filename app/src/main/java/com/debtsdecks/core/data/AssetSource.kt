package com.debtsdecks.core.data

/**
 * Core-side asset-reading abstraction. Keeps `core/` free of `android.content.Context` per
 * docs/CONVENTIONS.md Architecture Rule #1 — mirrors the [com.debtsdecks.core.i18n.Localizer]
 * split. [DataLoader] depends only on this interface; the Android-backed implementation,
 * [com.debtsdecks.gdx.data.AndroidAssetSource], lives outside `core/` and reads from
 * `Context.assets`.
 */
interface AssetSource {
    fun readCards(): String
    fun readEnemies(): String
    fun readRunSequence(): String
    fun readDistricts(): String
}
