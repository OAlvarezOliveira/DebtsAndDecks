package com.debtsdecks.gdx.data

import android.content.Context
import com.debtsdecks.core.data.AssetSource

/**
 * Android-backed [AssetSource] adapter reading from `Context.assets`. Registered in
 * [com.debtsdecks.di.gdxModule] and injected wherever [com.debtsdecks.core.data.DataLoader] needs
 * to read game data. Lives outside `core/` so the core package stays free of `android.*` imports
 * per docs/CONVENTIONS.md Architecture Rule #1.
 */
class AndroidAssetSource(private val context: Context) : AssetSource {
    override fun readCards(): String = readAsset("cards/all.json")
    override fun readEnemies(): String = readAsset("enemies/all.json")
    override fun readRunSequence(): String = readAsset("run/sequence.json")
    override fun readDistricts(): String = readAsset("districts/all.json")

    private fun readAsset(path: String): String =
        context.assets.open(path).reader().use { it.readText() }
}
