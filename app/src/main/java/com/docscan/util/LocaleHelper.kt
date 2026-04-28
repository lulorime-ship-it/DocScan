package com.docscan.util

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

object LocaleHelper {

    fun applyLanguage(context: Context, language: String): Context {
        val locale = when (language) {
            "zh" -> Locale.SIMPLIFIED_CHINESE
            "en" -> Locale.ENGLISH
            "es" -> Locale("es")
            else -> return context
        }

        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocale(locale)
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            context.createConfigurationContext(config)
        } else {
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
            context
        }
    }

    fun getLanguageCode(context: Context): String {
        val saved = AppSettings.getAppLanguage(context)
        if (saved.isNotEmpty()) return saved
        val systemLocale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.resources.configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            context.resources.configuration.locale
        }
        return when (systemLocale.language) {
            "zh" -> "zh"
            "es" -> "es"
            else -> "en"
        }
    }

    fun onAttach(context: Context): Context {
        val lang = getLanguageCode(context)
        return applyLanguage(context, lang)
    }
}
