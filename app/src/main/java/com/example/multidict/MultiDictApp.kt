package com.example.multidict

import com.example.multidict.data.AppearancePrefs

import android.app.Application

/**
 * Custom Application class that applies the saved theme mode as early as possible
 * before any Activity is created, eliminating startup flicker.
 */
class MultiDictApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppearancePrefs.applyOnStartup(this)
    }
}
