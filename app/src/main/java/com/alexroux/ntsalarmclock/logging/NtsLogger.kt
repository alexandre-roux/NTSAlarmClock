package com.alexroux.ntsalarmclock.logging

import android.util.Log

/**
 * Small logging wrapper for app-level diagnostic events.
 *
 * The wrapper keeps ViewModel logging safe in local JVM tests, where
 * android.util.Log is a stub unless explicitly mocked.
 */
object NtsLogger {

    fun d(tag: String, message: String) {
        runCatching { Log.d(tag, message) }
    }

    fun w(tag: String, message: String) {
        runCatching { Log.w(tag, message) }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        runCatching {
            if (throwable == null) {
                Log.e(tag, message)
            } else {
                Log.e(tag, message, throwable)
            }
        }
    }
}
