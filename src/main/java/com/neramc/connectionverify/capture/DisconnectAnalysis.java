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

import java.util.Locale;

/**
 * Turns a raw disconnect-reason string into a short, human-readable
 * <em>category</em> and an explanation of the <em>likely cause</em>, so a
 * connection report can say not just <em>that</em> a connection dropped but
 * <em>why</em>.
 *
 * <p>Classification is keyword based and ordered from most specific to most
 * generic, so a precise cause (for example a read timeout) wins over the
 * catch-all "internal exception" bucket. The method is pure and side-effect
 * free, which keeps it trivially testable.</p>
 */
public final class DisconnectAnalysis {

    /** The outcome of analysing a reason string. */
    public record Result(String category, String explanation) {
    }

    private DisconnectAnalysis() {
    }

    /**
     * Classifies a disconnect-reason string.
     *
     * @param reason the raw reason text (may be {@code null} or blank)
     * @return a never-{@code null} category and explanation
     */
    public static Result analyze(String reason) {
        if (reason == null || reason.isBlank()) {
            return new Result("Unknown", "The server provided no reason text for the disconnect.");
        }
        String r = reason.toLowerCase(Locale.ROOT);

        if (has(r, "timed out", "timeout", "readtimeout", "read timed", "write timed")) {
            return new Result("Timeout",
                    "The connection stopped responding and timed out - a slow or dropped network, "
                            + "a frozen client, or a half-open port scan.");
        }
        if (has(r, "connection reset", "forcibly closed", "broken pipe", "reset by peer",
                "an existing connection", "connection abort")) {
            return new Result("Connection reset",
                    "The remote side closed the socket abruptly - a client crash or close, "
                            + "a firewall/NAT, or an unstable network.");
        }
        if (has(r, "please try again or contact an administrator", "connection failed")) {
            return new Result("Early failure",
                    "The socket was dropped before login completed - most often a port scanner or bot, "
                            + "a non-Minecraft client, a proxy/forwarding mismatch, or connection throttling.");
        }
        if (has(r, "throttl", "too fast", "wait before", "connecting too quickly")) {
            return new Result("Throttled",
                    "The client reconnected faster than the connection-throttle setting allows.");
        }
        if (has(r, "already connected", "duplicate_login", "duplicate")) {
            return new Result("Duplicate",
                    "A session for this player or host was already connected.");
        }
        if (has(r, "authent", "auth servers", "session servers", "not verified", "verify username")) {
            return new Result("Authentication",
                    "Online-mode authentication failed - Mojang/Microsoft auth trouble, a cracked client "
                            + "on an online-mode server, or an expired session.");
        }
        if (has(r, "outdated", "incompatible", "unsupported", "wrong version", "version mismatch",
                "please use version")) {
            return new Result("Version mismatch",
                    "The client and server are running incompatible Minecraft versions.");
        }
        if (has(r, "banned")) {
            return new Result("Banned", "The player or their IP address is banned.");
        }
        if (has(r, "whitelist")) {
            return new Result("Not whitelisted",
                    "The server is whitelisted and this connection is not on the list.");
        }
        if (has(r, "server is full", "server full", "is full")) {
            return new Result("Server full", "The server is at its configured player limit.");
        }
        if (has(r, "internal exception")) {
            return new Result("Protocol/IO error",
                    "A low-level network or protocol error occurred while reading from the client; "
                            + "the nested exception names the precise cause.");
        }
        if (has(r, "bad packet", "invalid", "protocol", "unexpected", "illegal", "bad login",
                "malformed", "packet")) {
            return new Result("Protocol error",
                    "Malformed or unexpected protocol data - an incompatible or modified client, "
                            + "a wrong version, or a scanner probing the port.");
        }
        if (has(r, "kick")) {
            return new Result("Kicked", "The connection was kicked by the server or a plugin.");
        }
        if (has(r, "disconnect", "closed", "end of stream", "quit", "left", "channel inactive")) {
            return new Result("Client closed",
                    "The client closed the connection - a normal disconnect, or it went away before "
                            + "completing login.");
        }
        return new Result("Other",
                "Unrecognised reason; see the raw reason text above for the server's own wording.");
    }

    private static boolean has(String haystack, String... needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
