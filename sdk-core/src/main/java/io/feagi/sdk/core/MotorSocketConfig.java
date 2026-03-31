/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core;

/**
 * Motor socket configuration aligned with FEAGI agent settings.
 *
 * <p>Used with a ZMQ {@code PULL} socket in {@link io.feagi.sdk.core.transport.ZmqTransport}. ZMQ
 * {@code CONFLATE} is not supported on {@code PULL} sockets in libzmq/jeromq, so this type only
 * carries options that apply to the current transport shape ({@code rcvHwm}, {@code lingerMs}).
 */
public final class MotorSocketConfig {
    private final int rcvHwm;
    private final int lingerMs;

    /**
     * Create a motor socket configuration.
     *
     * @param rcvHwm ZMQ receive high-water mark (must be >= 0)
     * @param lingerMs linger duration in ms (must be >= 0)
     */
    public MotorSocketConfig(int rcvHwm, int lingerMs) {
        if (rcvHwm < 0) {
            throw new IllegalArgumentException("rcvHwm must be >= 0");
        }
        if (lingerMs < 0) {
            throw new IllegalArgumentException("lingerMs must be >= 0");
        }
        this.rcvHwm = rcvHwm;
        this.lingerMs = lingerMs;
    }

    /**
     * Return receive high-water mark.
     */
    public int rcvHwm() {
        return rcvHwm;
    }

    /**
     * Return linger duration in milliseconds.
     */
    public int lingerMs() {
        return lingerMs;
    }
}
