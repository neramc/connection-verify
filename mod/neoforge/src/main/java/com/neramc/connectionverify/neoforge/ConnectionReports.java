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
package com.neramc.connectionverify.neoforge;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Thread-safe in-memory store of connection reports, keyed by a random 4-digit
 * connection number. Client-companion details arrive after the join and are
 * merged into the matching report.
 */
public final class ConnectionReports {

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneOffset.UTC);
    private static final int MAX_IN_MEMORY = 1000;

    private static final class Entry {
        final Instant time = Instant.now();
        final Map<String, String> fields = new LinkedHashMap<>();
        volatile String clientInfo;
    }

    private final LinkedHashMap<String, Entry> byNumber = new LinkedHashMap<>();
    private final Map<UUID, String> byUuid = new HashMap<>();

    /** Opens a numbered report for a connection and returns the assigned number. */
    public synchronized String open(UUID uuid, Map<String, String> fields) {
        String number;
        do {
            number = String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
        } while (byNumber.containsKey(number));

        Entry entry = new Entry();
        entry.fields.putAll(fields);
        byNumber.put(number, entry);
        byUuid.put(uuid, number);

        Iterator<Map.Entry<String, Entry>> it = byNumber.entrySet().iterator();
        while (byNumber.size() > MAX_IN_MEMORY && it.hasNext()) {
            it.next();
            it.remove();
        }
        return number;
    }

    /** Merges companion-supplied client details into the connection's report. */
    public synchronized void attachClientInfo(UUID uuid, String data) {
        String number = byUuid.get(uuid);
        if (number == null) {
            return;
        }
        Entry entry = byNumber.get(number);
        if (entry != null) {
            entry.clientInfo = data;
        }
    }

    /** Renders the report for a number, or {@code null} if unknown. */
    public synchronized String render(String number) {
        Entry entry = byNumber.get(number);
        if (entry == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("=========================================================\n");
        sb.append(" Connection Verify (NeoForge) - report ").append(number).append('\n');
        sb.append(" Recorded at: ").append(TIMESTAMP.format(entry.time)).append(" UTC\n");
        sb.append("=========================================================\n\n");
        sb.append("== Connection ==\n");
        for (Map.Entry<String, String> field : entry.fields.entrySet()) {
            sb.append("  ").append(field.getKey()).append(": ").append(field.getValue()).append('\n');
        }
        sb.append("\n== Client (companion) ==\n");
        sb.append(entry.clientInfo == null
                ? "  (client companion mod not installed, or no data received)\n"
                : indent(entry.clientInfo));
        sb.append("\n== Server / System ==\n").append(indent(SystemInfo.text()));
        return sb.toString();
    }

    /** A sorted, read-only snapshot of every known connection number. */
    public synchronized Collection<String> numbers() {
        return new TreeSet<>(byNumber.keySet());
    }

    private static String indent(String text) {
        StringBuilder sb = new StringBuilder();
        for (String line : text.split("\n", -1)) {
            sb.append("  ").append(line).append('\n');
        }
        return sb.toString();
    }
}
