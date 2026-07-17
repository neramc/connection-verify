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

import java.util.Map;

/** Renders a {@link ConnectionRecord} into a human-readable, log-style report. */
public final class TextReport {

    private TextReport() {
    }

    public static String render(ConnectionRecord record) {
        StringBuilder sb = new StringBuilder(1024);
        sb.append("================================================================\n");
        sb.append(" Connection Verify (Velocity) - report ").append(record.number()).append('\n');
        sb.append(" Status     : ").append(record.status()).append('\n');
        sb.append(" Recorded at: ").append(ConnectionRecord.formatTimestamp(record.recordedAt())).append('\n');
        sb.append("================================================================\n");

        for (ConnectionRecord.Section section : record.sections()) {
            sb.append('\n').append("== ").append(section.title()).append(" ==\n");
            int width = 0;
            for (String key : section.entries().keySet()) {
                width = Math.max(width, key.length());
            }
            for (Map.Entry<String, String> entry : section.entries().entrySet()) {
                sb.append("  ").append(pad(entry.getKey(), width))
                        .append(" : ").append(entry.getValue()).append('\n');
            }
        }
        return sb.toString();
    }

    private static String pad(String value, int width) {
        if (value.length() >= width) {
            return value;
        }
        return value + " ".repeat(width - value.length());
    }
}
