package com.example.multidict.data

import com.example.multidict.R
import com.example.multidict.model.*
import com.example.multidict.data.*
import com.example.multidict.viewmodel.*
import com.example.multidict.ui.*
import com.example.multidict.ui.adapter.*
import com.example.multidict.ui.widget.*

import android.content.Context

/**
 * Small helper class managing first-run onboarding and coach-mark tour state in SharedPreferences.
 */
object OnboardingPrefs {

    private const val PREFS_NAME = "multidict_prefs"
    private const val KEY_ONBOARDING_COMPLETED = "onboardingCompleted"
    private const val KEY_COACH_MARKS_COMPLETED = "coachMarksCompleted"
    private const val KEY_COACH_MARK_STEP = "coachMarkCurrentStep"

    fun isOnboardingCompleted(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ONBOARDING_COMPLETED, false)
    }

    fun setOnboardingCompleted(context: Context, completed: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ONBOARDING_COMPLETED, completed)
            .apply()
    }

    fun isCoachMarksCompleted(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_COACH_MARKS_COMPLETED, false)
    }

    fun setCoachMarksCompleted(context: Context, completed: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_COACH_MARKS_COMPLETED, completed)
            .apply()
    }

    fun getCoachMarkStep(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_COACH_MARK_STEP, 0)
    }

    fun setCoachMarkStep(context: Context, step: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_COACH_MARK_STEP, step)
            .apply()
    }

    /**
     * Resets both flags so the welcome carousel and coach-mark tour can be replayed.
     */
    fun resetForReplay(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ONBOARDING_COMPLETED, false)
            .putBoolean(KEY_COACH_MARKS_COMPLETED, false)
            .putInt(KEY_COACH_MARK_STEP, 0)
            .apply()
    }
}
