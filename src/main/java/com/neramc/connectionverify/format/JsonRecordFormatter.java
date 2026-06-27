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
package com.neramc.connectionverify.format;

import com.neramc.connectionverify.connection.ConnectionRecord;
import com.neramc.connectionverify.connection.ConnectionRecord.Section;

import java.time.Instant;
import java.util.Iterator;
import java.util.Map;

/**
 * Renders a record as pretty-printed JSON for machine ingestion (log
 * pipelines, SIEMs, dashboards). All values are emitted as strings to mirror
 * the captured text exactly. No external JSON library is used.
 */
public final class JsonRecordFormatter implements RecordFormatter {

    private static final String INDENT = "  ";

    @Override
    public String extension() {
        return "json";
    }

    @Override
    public String format(ConnectionRecord record, Instant savedAt) {
        String nl = System.lineSeparator();
        StringBuilder sb = new StringBuilder(1024);
        sb.append('{').append(nl);
        field(sb, 1, "connectionNumber", record.number(), true);
        field(sb, 1, "status", record.status().name(), true);
        field(sb, 1, "recordedAt", record.recordedAt().toString(), true);
        field(sb, 1, "recordedAtEpochMillis", Long.toString(record.recordedAt().toEpochMilli()), false);
        sb.append(',').append(nl);
        field(sb, 1, "savedAt", savedAt.toString(), true);
        field(sb, 1, "savedAtEpochMillis", Long.toString(savedAt.toEpochMilli()), false);
        sb.append(',').append(nl);

        sb.append(INDENT).append('"').append("sections").append("\": {").append(nl);
        for (Iterator<Section> it = record.sections().iterator(); it.hasNext(); ) {
            Section section = it.next();
            sb.append(INDENT.repeat(2)).append(quote(section.title())).append(": {").append(nl);
            for (Iterator<Map.Entry<String, String>> e = section.entries().entrySet().iterator(); e.hasNext(); ) {
                Map.Entry<String, String> entry = e.next();
                sb.append(INDENT.repeat(3))
                  .append(quote(entry.getKey())).append(": ").append(quote(entry.getValue()));
                sb.append(e.hasNext() ? "," : "").append(nl);
            }
            sb.append(INDENT.repeat(2)).append('}').append(it.hasNext() ? "," : "").append(nl);
        }
        sb.append(INDENT).append('}').append(nl);
        sb.append('}').append(nl);
        return sb.toString();
    }

    /** Writes {@code "key": value,} where value is quoted unless it is raw JSON. */
    private void field(StringBuilder sb, int depth, String key, String value, boolean quoted) {
        sb.append(INDENT.repeat(depth)).append(quote(key)).append(": ");
        sb.append(quoted ? quote(value) : value);
        if (quoted) {
            sb.append(',').append(System.lineSeparator());
        }
    }

    private static String quote(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder(value.length() + 2);
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }
}
