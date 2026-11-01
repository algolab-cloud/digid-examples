package tz.co.digid.example.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import tz.co.digid.sdk.Lang
import java.util.Locale

/**
 * Shows [content] in the app's chosen language, whatever the phone's language is: the app's
 * own strings come from res/values or res/values-sw, and the SDK's guidance text is asked for
 * in the same [lang]. (Apps that follow the system language can drop this.)
 */
@Composable
fun ProvideLang(lang: Lang, content: @Composable () -> Unit) {
    val base = LocalContext.current
    val localized = remember(base, lang) {
        val config = Configuration(base.resources.configuration).apply { setLocale(lang.locale) }
        val resources = base.createConfigurationContext(config).resources
        // A wrapper, so the Activity is still reachable from LocalContext.
        object : ContextWrapper(base) {
            override fun getResources(): Resources = resources
        }
    }
    CompositionLocalProvider(
        LocalContext provides localized,
        LocalResources provides localized.resources,
        LocalConfiguration provides localized.resources.configuration,
        content = content,
    )
}

val Lang.locale: Locale get() = if (this == Lang.SW) Locale.forLanguageTag("sw") else Locale.ENGLISH

/** The Activity behind a (possibly wrapped) context. */
tailrec fun Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
