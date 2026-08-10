/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.tabarchive

import android.util.Log

/**
 * Lightweight event recording for the tab archive feature.
 *
 * Events are buffered in memory and flushed by the caller at natural
 * boundaries (end of an archive pass, screen teardown) to avoid emitting one
 * ping per tab.
 */
class TabArchiveTelemetry(
    private val maxBufferSize: Int = DEFAULT_MAX_BUFFER,
    private val sink: (List<Event>) -> Unit = { events -> events.forEach { Log.d(TAG, it.toString()) } },
) {

    /** A single archive-related event. */
    sealed class Event {
        data class Archived(val count: Int, val passDurationMs: Long) : Event()
        data class Restored(val ageMs: Long) : Event()
        data class SearchPerformed(val resultCount: Int) : Event()
        object Pruned : Event()
    }

    private val buffer = ArrayDeque<Event>()
    private val lock = Any()

    /** Records an event, flushing first if the buffer is full. */
    fun record(event: Event) {
        val toFlush: List<Event>?
        synchronized(lock) {
            if (buffer.size >= maxBufferSize) {
                toFlush = buffer.toList()
                buffer.clear()
            } else {
                toFlush = null
            }
            buffer.addLast(event)
        }
        toFlush?.let(sink)
    }

    /** Flushes any buffered events to the sink. */
    fun flush() {
        val toFlush: List<Event>
        synchronized(lock) {
            if (buffer.isEmpty()) return
            toFlush = buffer.toList()
            buffer.clear()
        }
        sink(toFlush)
    }

    companion object {
        private const val TAG = "TabArchiveTelemetry"
        private const val DEFAULT_MAX_BUFFER = 32
    }
}
