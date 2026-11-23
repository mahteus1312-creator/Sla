package com.hibernador.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Guarda a RAM liberada na última hibernação (exibida na tela principal). */
object LastResultStore {
    private lateinit var prefs: SharedPreferences
    private val _lastFreed = MutableStateFlow(0L)
    val lastFreed: StateFlow<Long> = _lastFreed.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences("hibernador", Context.MODE_PRIVATE)
        _lastFreed.value = prefs.getLong(KEY, 0L)
    }

    fun save(freedBytes: Long) {
        _lastFreed.value = freedBytes
        prefs.edit().putLong(KEY, freedBytes).apply()
    }

    private const val KEY = "last_freed_bytes"
}
