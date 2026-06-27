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
package com.neramc.connectionverify.io;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.function.Consumer;

/**
 * Writes connection-log files, optionally off the main server thread so disk
 * I/O never stalls the tick loop. The completion callback always runs back on
 * the main thread, making it safe to message players or touch the Bukkit API.
 */
public final class LogWriter {

    /** Outcome of a write attempt. */
    public record Result(boolean success, int bytes, IOException error) {

        static Result ok(int bytes) {
            return new Result(true, bytes, null);
        }

        static Result failed(IOException error) {
            return new Result(false, 0, error);
        }
    }

    private final JavaPlugin plugin;

    public LogWriter(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Writes {@code content} to {@code file}, creating parent directories.
     *
     * @param file     destination file
     * @param content  bytes to write
     * @param async    write off the main thread when {@code true}
     * @param callback invoked with the result on the main thread
     */
    public void write(File file, byte[] content, boolean async, Consumer<Result> callback) {
        if (async && plugin.isEnabled()) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                Result result = doWrite(file, content);
                if (plugin.isEnabled()) {
                    plugin.getServer().getScheduler().runTask(plugin, () -> callback.accept(result));
                } else {
                    callback.accept(result);
                }
            });
        } else {
            callback.accept(doWrite(file, content));
        }
    }

    private Result doWrite(File file, byte[] content) {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new IOException("Could not create directory: " + parent.getAbsolutePath());
            }
            Files.write(file.toPath(), content);
            return Result.ok(content.length);
        } catch (IOException exception) {
            return Result.failed(exception);
        }
    }
}
