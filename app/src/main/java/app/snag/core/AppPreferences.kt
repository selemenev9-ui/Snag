package app.snag.core

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

object AppPreferences {
    private val mutableTheme = MutableStateFlow("system")
    val theme = mutableTheme.asStateFlow()
    private fun prefs(context: Context) = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
    fun init(context: Context) { mutableTheme.value = prefs(context).getString("theme", "system") ?: "system" }
    fun setTheme(context: Context, value: String) {
        require(value in setOf("system", "light", "dark"))
        prefs(context).edit().putString("theme",value).apply()
        mutableTheme.value = value
    }
    fun language(context: Context): String = if (Build.VERSION.SDK_INT >= 33)
        context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags().ifBlank { "system" }
    else prefs(context).getString("language", "system") ?: "system"

    fun localized(context: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return context
        val tag = language(context)
        if (tag == "system") return context
        val config = Configuration(context.resources.configuration)
        config.setLocales(LocaleList(Locale.forLanguageTag(tag)))
        return context.createConfigurationContext(config)
    }
    @Suppress("DEPRECATION")
    fun setLanguage(activity: Activity, value: String) {
        require(value in setOf("system", "en", "ru"))
        if (Build.VERSION.SDK_INT >= 33) {
            activity.getSystemService(LocaleManager::class.java).applicationLocales =
                if(value == "system") LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(value)
        } else {
            prefs(activity).edit().putString("language",value).commit()
            val config = Configuration(activity.application.resources.configuration)
            config.setLocales(if(value == "system") android.content.res.Resources.getSystem().configuration.locales
                else LocaleList.forLanguageTags(value))
            activity.application.resources.updateConfiguration(config,activity.application.resources.displayMetrics)
            activity.recreate()
        }
    }
}
