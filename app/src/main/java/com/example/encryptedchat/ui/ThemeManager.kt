package com.example.encryptedchat.ui

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

object ThemeManager {
    private const val KEY = "dark_mode"

    fun isDark(ctx: Context) = ctx.getSharedPreferences("app", 0).getBoolean(KEY, true)

    fun setDark(ctx: Context, dark: Boolean) {
        ctx.getSharedPreferences("app", 0).edit().putBoolean(KEY, dark).apply()
        apply()
    }

    fun apply() {
        AppCompatDelegate.setDefaultNightMode(
            if (isDark(android.app.Application().getApplicationContext()))
                AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )
    }

    // 临时修复：需要Context的版本
    fun apply(ctx: Context) {
        AppCompatDelegate.setDefaultNightMode(
            if (isDark(ctx)) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )
    }
}
