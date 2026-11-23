package com.hibernador.app.data

import android.app.ActivityManager
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.util.Locale

data class MemorySnapshot(val total: Long, val available: Long) {
    val used: Long get() = (total - available).coerceAtLeast(0)
    val usedFraction: Float get() = if (total > 0) used.toFloat() / total else 0f
}

object MemoryReader {
    fun read(context: Context): MemorySnapshot {
        val am = context.getSystemService(ActivityManager::class.java)
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return MemorySnapshot(total = info.totalMem, available = info.availMem)
    }

    /** Emite a memória atual periodicamente (tempo real). */
    fun flow(context: Context, periodMs: Long = 1000): Flow<MemorySnapshot> = flow {
        val app = context.applicationContext
        while (true) {
            emit(read(app))
            delay(periodMs)
        }
    }.flowOn(Dispatchers.Default)
}

fun formatBytes(bytes: Long): String {
    val b = bytes.coerceAtLeast(0)
    val mb = b / (1024.0 * 1024.0)
    return if (mb >= 1024) String.format(Locale.getDefault(), "%.2f GB", mb / 1024.0)
    else String.format(Locale.getDefault(), "%.0f MB", mb)
}
