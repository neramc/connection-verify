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
package com.neramc.connectionverify.velocity;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Thread-safe, in-memory store mapping generated connection numbers to the
 * {@link ConnectionRecord} captured for that attempt. Records are accessed from
 * proxy event threads and the command thread, so every operation is
 * synchronized. Enforces an optional maximum size (oldest evicted first).
 */
public final class ConnectionNumberRegistry {

    private final LinkedHashMap<String, ConnectionRecord> records = new LinkedHashMap<>();

    private int numberLength;
    private boolean leadingZeros;
    private int maxInMemory;

    public ConnectionNumberRegistry(int numberLength, boolean leadingZeros, int maxInMemory) {
        configure(numberLength, leadingZeros, maxInMemory);
    }

    public synchronized void configure(int numberLength, boolean leadingZeros, int maxInMemory) {
        this.numberLength = Math.min(9, Math.max(1, numberLength));
        this.leadingZeros = leadingZeros;
        this.maxInMemory = Math.max(0, maxInMemory);
        enforceCapacity();
    }

    /** Assigns a unique connection number to the record and stores it. */
    public synchronized String register(ConnectionRecord record) {
        int bound = pow10(numberLength);
        int floor = leadingZeros ? 0 : pow10(numberLength - 1);
        int capacity = Math.max(bound - floor, 1);
        String number;
        int attempts = 0;
        do {
            int value = floor + ThreadLocalRandom.current().nextInt(bound - floor);
            number = leadingZeros ? padded(value, numberLength) : Integer.toString(value);
        } while (records.containsKey(number) && ++attempts < capacity);

        record.setNumber(number);
        records.put(number, record);
        enforceCapacity();
        return number;
    }

    public synchronized ConnectionRecord get(String number) {
        return records.get(number);
    }

    /**
     * Appends a routing entry to a stored record under the registry lock (records
     * are mutated live as players are routed between backends, while {@code /cnt}
     * may render them concurrently).
     */
    public synchronized void addRouting(String number, String key, String value) {
        ConnectionRecord record = records.get(number);
        if (record == null) {
            return;
        }
        ConnectionRecord.Section routing = record.sectionByTitle("Routing");
        if (routing == null) {
            routing = record.section("Routing");
        }
        routing.add(key, value);
    }

    /** Renders a stored record to text under the registry lock, or {@code null} if unknown. */
    public synchronized String renderReport(String number) {
        ConnectionRecord record = records.get(number);
        return record == null ? null : TextReport.render(record);
    }

    public synchronized Set<String> numbers() {
        return new TreeSet<>(records.keySet());
    }

    public synchronized int size() {
        return records.size();
    }

    private void enforceCapacity() {
        if (maxInMemory <= 0) {
            return;
        }
        Iterator<Map.Entry<String, ConnectionRecord>> it = records.entrySet().iterator();
        while (records.size() > maxInMemory && it.hasNext()) {
            it.next();
            it.remove();
        }
    }

    private static int pow10(int exponent) {
        int result = 1;
        for (int i = 0; i < exponent; i++) {
            result *= 10;
        }
        return result;
    }

    private static String padded(int value, int length) {
        String raw = Integer.toString(value);
        return raw.length() >= length ? raw : "0".repeat(length - raw.length()) + raw;
    }
}
