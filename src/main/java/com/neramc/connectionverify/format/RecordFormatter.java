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

import java.time.Instant;

/** Renders a {@link ConnectionRecord} into the textual form written to disk. */
public interface RecordFormatter {

    /**
     * Formats a record.
     *
     * @param record  the record to render
     * @param savedAt the moment the file is being written
     * @return the full file content
     */
    String format(ConnectionRecord record, Instant savedAt);

    /** File extension (without dot) for this format, e.g. {@code "txt"}. */
    String extension();
}
