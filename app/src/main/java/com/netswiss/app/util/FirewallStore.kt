package com.netswiss.app.util

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object FirewallStore {
    private const val PREFS = "firewall_prefs"
    private const val KEY_BLOCKED = "blocked_packages"

    private val _blockedPackages = MutableStateFlow<Set<String>>(emptySet())
    val blockedPackages: StateFlow<Set<String>> = _blockedPackages
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        _blockedPackages.value = load(context)
        initialized = true
    }

    fun setBlocked(context: Context, pkg: String, blocked: Boolean) {
        val current = _blockedPackages.value
        val next = if (blocked) current + pkg else current - pkg
        _blockedPackages.value = next
        save(context, next)
    }

    private fun load(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_BLOCKED, emptySet())?.toSet() ?: emptySet()
    }

    private fun save(context: Context, blocked: Set<String>) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(KEY_BLOCKED, blocked).apply()
    }
}
