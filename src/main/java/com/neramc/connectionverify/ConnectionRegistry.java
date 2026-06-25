package com.neramc.connectionverify;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * In-memory store that maps a randomly generated 4-digit connection number to the
 * {@link ConnectionRecord} captured for that connection attempt.
 *
 * <p>Records are kept for the lifetime of the server session so that an operator
 * can save any of them to disk later with {@code cnt <number>}. The store is
 * accessed from both the asynchronous login thread and the main server thread,
 * therefore all mutating access is synchronized.</p>
 */
public final class ConnectionRegistry {

    /** Upper bound (exclusive) for generated numbers: yields 0000-9999. */
    private static final int RANGE = 10_000;

    private final Map<String, ConnectionRecord> records = new ConcurrentHashMap<>();

    /**
     * Assigns a unique 4-digit number to the given record and stores it.
     *
     * @param record the record to register
     * @return the assigned 4-digit number (zero padded, e.g. {@code "0421"})
     */
    public synchronized String register(ConnectionRecord record) {
        String number;
        // With at most a few thousand records this practically never loops, but we
        // still guard against the (astronomically unlikely) case of a full table.
        int attempts = 0;
        do {
            number = String.format("%04d", ThreadLocalRandom.current().nextInt(RANGE));
            attempts++;
        } while (records.containsKey(number) && attempts < RANGE);

        record.setNumber(number);
        records.put(number, record);
        return number;
    }

    /** Returns the record for a number, or {@code null} if none is known. */
    public ConnectionRecord get(String number) {
        return records.get(number);
    }

    /** Returns a sorted, read-only snapshot of every known connection number. */
    public Set<String> numbers() {
        return Collections.unmodifiableSet(new TreeSet<>(records.keySet()));
    }

    /** Returns how many connection records are currently held in memory. */
    public int size() {
        return records.size();
    }
}
