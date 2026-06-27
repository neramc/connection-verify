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
package com.neramc.connectionverify.connection;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Thread-safe, in-memory store mapping generated connection numbers to the
 * {@link ConnectionRecord} captured for that attempt.
 *
 * <p>Records are accessed from both asynchronous login threads and the main
 * server thread, so every operation is synchronized. The store enforces an
 * optional maximum size (evicting the oldest entries first) and an optional
 * time-based expiry.</p>
 */
public final class ConnectionRegistry {

    /** A record together with the moment it was registered. */
    private record Holder(ConnectionRecord record, long createdAtMillis) {
    }

    private final LinkedHashMap<String, Holder> records = new LinkedHashMap<>();

    private int numberLength;
    private boolean leadingZeros;
    private int maxInMemory;
    private long expireAfterMillis;

    public ConnectionRegistry(int numberLength, boolean leadingZeros, int maxInMemory, long expireAfterMillis) {
        configure(numberLength, leadingZeros, maxInMemory, expireAfterMillis);
    }

    /** Re-applies settings (used on {@code /connectionverify reload}). */
    public synchronized void configure(int numberLength, boolean leadingZeros,
                                       int maxInMemory, long expireAfterMillis) {
        this.numberLength = Math.min(9, Math.max(1, numberLength));
        this.leadingZeros = leadingZeros;
        this.maxInMemory = Math.max(0, maxInMemory);
        this.expireAfterMillis = Math.max(0L, expireAfterMillis);
        enforceCapacity();
    }

    /**
     * Assigns a unique connection number to the record and stores it.
     *
     * @return the assigned connection number
     */
    public synchronized String register(ConnectionRecord record) {
        purgeExpired();

        int bound = pow10(numberLength);
        int floor = leadingZeros ? 0 : pow10(numberLength - 1);
        String number;
        int attempts = 0;
        int capacity = bound - floor;
        do {
            int value = floor + ThreadLocalRandom.current().nextInt(bound - floor);
            number = leadingZeros ? padded(value, numberLength) : Integer.toString(value);
        } while (records.containsKey(number) && ++attempts < Math.max(capacity, 1));

        record.setNumber(number);
        records.put(number, new Holder(record, System.currentTimeMillis()));
        enforceCapacity();
        return number;
    }

    /** Returns the record for a number, or {@code null} if unknown or expired. */
    public synchronized ConnectionRecord get(String number) {
        purgeExpired();
        Holder holder = records.get(number);
        return holder == null ? null : holder.record();
    }

    /** A sorted, read-only snapshot of every known connection number. */
    public synchronized Set<String> numbers() {
        purgeExpired();
        return new TreeSet<>(records.keySet());
    }

    /** Number of records currently held. */
    public synchronized int size() {
        purgeExpired();
        return records.size();
    }

    /** Drops all stored records. */
    public synchronized void clear() {
        records.clear();
    }

    /** Removes records older than the configured expiry. No-op when disabled. */
    public synchronized void purgeExpired() {
        if (expireAfterMillis <= 0L) {
            return;
        }
        long cutoff = System.currentTimeMillis() - expireAfterMillis;
        Iterator<Map.Entry<String, Holder>> it = records.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().createdAtMillis() < cutoff) {
                it.remove();
            } else {
                // LinkedHashMap preserves insertion order, so the first kept
                // entry means every later entry is newer too.
                break;
            }
        }
    }

    private void enforceCapacity() {
        if (maxInMemory <= 0) {
            return;
        }
        Iterator<Map.Entry<String, Holder>> it = records.entrySet().iterator();
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
        if (raw.length() >= length) {
            return raw;
        }
        return "0".repeat(length - raw.length()) + raw;
    }
}
