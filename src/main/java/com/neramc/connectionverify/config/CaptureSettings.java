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
package com.neramc.connectionverify.config;

/**
 * Immutable view of which detail sections are captured and the privacy options
 * applied while capturing. Derived from {@code config.yml}.
 *
 * @param identity       capture identity (name, UUID, ...)
 * @param network        capture network details (IP, port, brand, ...)
 * @param clientOptions  capture client options (locale, main hand, ...)
 * @param session        capture session history (first join, op, ban, ...)
 * @param playerState    capture live player state (health, xp, ...)
 * @param location       capture location and respawn/death points
 * @param world          capture world context (difficulty, weather, ...)
 * @param server         capture server context (versions, TPS, plugins, ...)
 * @param runtime        capture JVM/OS runtime context
 * @param maskIp         mask player IP addresses in output and logs
 * @param maskIpSegments number of trailing IPv4 segments to mask (1-4)
 * @param hideUuid       replace UUIDs with a redaction marker
 */
public record CaptureSettings(
        boolean identity,
        boolean network,
        boolean clientOptions,
        boolean session,
        boolean playerState,
        boolean location,
        boolean world,
        boolean server,
        boolean runtime,
        boolean maskIp,
        int maskIpSegments,
        boolean hideUuid) {

    /** Marker used when a value is hidden for privacy. */
    public static final String REDACTED = "(redacted)";

    /**
     * Masks an IPv4 address by replacing the configured number of trailing
     * segments with {@code x}. Non-IPv4 input is fully redacted.
     */
    public String maskIpAddress(String ip) {
        if (ip == null) {
            return null;
        }
        if (!maskIp) {
            return ip;
        }
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            return REDACTED;
        }
        int hide = Math.min(4, Math.max(1, maskIpSegments));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            if (i > 0) {
                sb.append('.');
            }
            sb.append(i >= 4 - hide ? "x" : parts[i]);
        }
        return sb.toString();
    }
}
