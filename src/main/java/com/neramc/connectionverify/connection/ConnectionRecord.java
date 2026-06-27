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

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An immutable-once-built snapshot of a single connection attempt, organised
 * into ordered, titled {@link Section}s of key/value detail.
 *
 * <p>A record is populated while a login/join event is handled, assigned a
 * connection number by the {@link ConnectionRegistry}, and later rendered to a
 * file (in text or JSON) when an operator runs {@code /cnt}. Every field is
 * captured through {@link Section#add(String, ValueSupplier)} so a getter that
 * is unavailable on the running server build is recorded as
 * {@code (unavailable: ...)} rather than aborting the whole snapshot.</p>
 */
public final class ConnectionRecord {

    /** Outcome of a connection attempt. */
    public enum Status {
        SUCCESS,
        FAILED
    }

    /** Rendered in place of a {@code null} or empty value. */
    public static final String UNKNOWN = "(unknown)";

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneOffset.UTC);

    private final Status status;
    private final Instant recordedAt;
    private final List<Section> sections = new ArrayList<>();

    private String number = "----";

    public ConnectionRecord(Status status) {
        this.status = status;
        this.recordedAt = Instant.now();
    }

    /** Formats an instant as a UTC timestamp with its epoch-millisecond value. */
    public static String formatTimestamp(Instant instant) {
        return TIMESTAMP.format(instant) + " UTC (epoch ms: " + instant.toEpochMilli() + ")";
    }

    public Status status() {
        return status;
    }

    public Instant recordedAt() {
        return recordedAt;
    }

    public String number() {
        return number;
    }

    /** Package-private: only the registry assigns the connection number. */
    void setNumber(String number) {
        this.number = number;
    }

    /** Starts a new titled section and returns it so entries can be chained on. */
    public Section section(String title) {
        Section section = new Section(title);
        sections.add(section);
        return section;
    }

    /** An unmodifiable, ordered view of the sections in this record. */
    public List<Section> sections() {
        return Collections.unmodifiableList(sections);
    }

    /** A titled, ordered group of key/value entries. */
    public static final class Section {

        private final String title;
        private final Map<String, String> entries = new LinkedHashMap<>();

        private Section(String title) {
            this.title = title;
        }

        public String title() {
            return title;
        }

        /** An unmodifiable, ordered view of this section's entries. */
        public Map<String, String> entries() {
            return Collections.unmodifiableMap(entries);
        }

        /** Adds an entry from a direct value. {@code null} renders as {@code (unknown)}. */
        public Section add(String key, Object value) {
            entries.put(key, normalize(value == null ? null : String.valueOf(value)));
            return this;
        }

        /**
         * Adds an entry whose value is produced lazily and defensively. If the
         * supplier throws - for example an API method or field absent on this
         * server build - the failure is captured as {@code (unavailable: ...)}
         * instead of aborting the snapshot. {@code null} renders as
         * {@code (unknown)}.
         */
        public Section add(String key, ValueSupplier supplier) {
            String value;
            try {
                Object result = supplier.get();
                value = result == null ? null : String.valueOf(result);
            } catch (Throwable throwable) {
                value = "(unavailable: " + throwable.getClass().getSimpleName() + ")";
            }
            entries.put(key, normalize(value));
            return this;
        }

        private static String normalize(String value) {
            return (value == null || value.isEmpty()) ? UNKNOWN : value;
        }
    }

    /**
     * A value producer that is allowed to fail; used by
     * {@link Section#add(String, ValueSupplier)} to isolate each captured field.
     */
    @FunctionalInterface
    public interface ValueSupplier {
        Object get() throws Exception;
    }
}
