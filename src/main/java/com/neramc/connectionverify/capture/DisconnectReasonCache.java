/*
 * Copyright 2026 neramc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.neramc.connectionverify.capture;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A small, time-bounded cache of disconnect reasons scraped from the server log,
 * keyed by remote host.
 *
 * <p>The {@code PlayerConnectionCloseEvent} that fires for a dropped connection
 * carries no reason, but the server logs a {@code "... lost connection: <reason>"}
 * line for the same socket (on the network thread, just before the event). The
 * network-drop watcher records that reason here; a connection-close snapshot then
 * looks it up by IP so the report can still say <em>why</em> the socket dropped.</p>
 *
 * <p>This is deliberately best-effort: entries expire quickly and a busy IP could
 * in theory shadow another, so lookups are clearly labelled as log-matched. All
 * operations are thread-safe.</p>
 */
public final class DisconnectReasonCache {

    /** A cached reason: the raw text and, when present, an attached exception summary. */
    public record Reason(String text, String thrown) {
    }

    private record Entry(Reason reason, long expiresAtMillis) {
    }

    private static final long TTL_MILLIS = 20_000L;
    private static final int MAX_ENTRIES = 512;

    private static final Map<String, Entry> ENTRIES = new ConcurrentHashMap<>();

    private DisconnectReasonCache() {
    }

    /** Records the most recent disconnect reason seen for a host. */
    public static void remember(String host, String reasonText, String thrown) {
        if (host == null || host.isEmpty() || reasonText == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (ENTRIES.size() >= MAX_ENTRIES) {
            purge(now);
        }
        ENTRIES.put(host, new Entry(new Reason(reasonText, thrown), now + TTL_MILLIS));
    }

    /** Returns the most recent non-expired reason for a host, or {@code null}. */
    public static Reason recent(String host) {
        if (host == null) {
            return null;
        }
        Entry entry = ENTRIES.get(host);
        if (entry == null) {
            return null;
        }
        if (entry.expiresAtMillis() < System.currentTimeMillis()) {
            ENTRIES.remove(host);
            return null;
        }
        return entry.reason();
    }

    /** Drops all cached reasons (called on plugin disable). */
    public static void clear() {
        ENTRIES.clear();
    }

    private static void purge(long now) {
        Iterator<Map.Entry<String, Entry>> it = ENTRIES.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().expiresAtMillis() < now) {
                it.remove();
            }
        }
        // If still saturated with live entries, reset rather than grow unbounded.
        if (ENTRIES.size() >= MAX_ENTRIES) {
            ENTRIES.clear();
        }
    }
}
