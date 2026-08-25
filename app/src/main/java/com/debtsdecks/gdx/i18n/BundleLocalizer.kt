package com.debtsdecks.gdx.i18n

import com.badlogic.gdx.utils.I18NBundle
import com.debtsdecks.core.i18n.Localizer

/**
 * GDX-backed [Localizer] adapter over LibGDX [I18NBundle]. Registered in [com.debtsdecks.di.gdxModule]
 * and injected into the core-domain classes that only declare [Localizer]. Lives outside `core/` so
 * the core package stays free of `com.badlogic.gdx.*` imports per docs/CONVENTIONS.md Architecture
 * Rule #1, while the same bundle still drives the renderer's UI strings directly.
 */
class BundleLocalizer(private val bundle: I18NBundle) : Localizer {
    override fun get(key: String): String = bundle.get(key)
    override fun format(key: String, vararg args: Any?): String = bundle.format(key, *args)
}
