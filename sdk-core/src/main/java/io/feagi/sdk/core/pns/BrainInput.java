/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core.pns;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * BrainInput-style global input manager.
 *
 * <p>Callers explicitly configure host, port, and transport, then register inputs and invoke
 * {@link #send()} to encode and flush all inputs as one payload.
 */
public final class BrainInput implements AutoCloseable {
    private static final BrainInput GLOBAL = new BrainInput();

    private final List<RegisteredInput> inputs = new ArrayList<>();
    private BrainInputConfig config;
    private BrainInputTransport transport;
    private boolean connected;

    /**
     * Return the global singleton instance.
     */
    public static BrainInput global() {
        return GLOBAL;
    }

    /**
     * Create a fresh BrainInput instance with the provided transport.
     */
    public static BrainInput create(BrainInputTransport transport) {
        return new BrainInput(transport);
    }

    /**
     * Create a fresh BrainInput instance with no transport yet attached.
     */
    public static BrainInput create() {
        return new BrainInput();
    }

    private BrainInput() {
    }

    private BrainInput(BrainInputTransport transport) {
        this.transport = Objects.requireNonNull(transport, "transport must not be null");
    }

    /**
     * Configure the singleton/global input manager.
     */
    public synchronized BrainInput configure(String host, int port, BrainInputTransportType transportType) {
        return configure(new BrainInputConfig(host, port, transportType));
    }

    /**
     * Configure the input manager.
     */
    public synchronized BrainInput configure(BrainInputConfig config) {
        if (connected) {
            throw new IllegalStateException("cannot reconfigure while connected");
        }
        this.config = Objects.requireNonNull(config, "config must not be null");
        return this;
    }

    /**
     * Attach a concrete transport implementation.
     */
    public synchronized BrainInput useTransport(BrainInputTransport transport) {
        if (connected) {
            throw new IllegalStateException("cannot replace transport while connected");
        }
        this.transport = Objects.requireNonNull(transport, "transport must not be null");
        return this;
    }

    /**
     * Register a named input source.
     */
    public synchronized BrainInput registerInput(String name, BrainInputInput input) {
        Objects.requireNonNull(name, "name must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        Objects.requireNonNull(input, "input must not be null");
        inputs.add(new RegisteredInput(name, input));
        return this;
    }

    /**
     * Register multiple input sources in order.
     */
    public synchronized BrainInput registerInputs(RegisteredInput... inputs) {
        Objects.requireNonNull(inputs, "inputs must not be null");
        for (RegisteredInput input : inputs) {
            Objects.requireNonNull(input, "registered input must not be null");
            registerInput(input.name(), input.input());
        }
        return this;
    }

    /**
     * Connect the configured transport.
     */
    public synchronized void connect() {
        requireConfigured();
        if (transport == null) {
            throw new IllegalStateException("transport implementation must be attached before connect()");
        }
        transport.connect(config.host(), config.port(), config.transport());
        connected = true;
    }

    /**
     * Encode all registered inputs into one payload and send it.
     */
    public synchronized void send() {
        if (!connected) {
            throw new IllegalStateException("connect() must be called before send()");
        }
        if (inputs.isEmpty()) {
            throw new IllegalStateException("at least one input must be registered before send()");
        }
        transport.send(encodePayload());
    }

    /**
     * Return whether the manager is connected.
     */
    public synchronized boolean connected() {
        return connected;
    }

    /**
     * Return the configured value object.
     */
    public synchronized BrainInputConfig config() {
        return config;
    }

    /**
     * Return a snapshot of registered inputs.
     */
    public synchronized List<RegisteredInput> registeredInputs() {
        return List.copyOf(inputs);
    }

    /**
     * Disconnect and release transport resources.
     */
    @Override
    public synchronized void close() {
        if (transport != null) {
            transport.close();
        }
        connected = false;
    }

    private void requireConfigured() {
        if (config == null) {
            throw new IllegalStateException("configure() must be called before connect()");
        }
    }

    private byte[] encodePayload() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.writeBytes(ByteBuffer.allocate(4).putInt(inputs.size()).array());
        for (RegisteredInput input : inputs) {
            byte[] nameBytes = input.name().getBytes(StandardCharsets.UTF_8);
            byte[] payload = Objects.requireNonNull(
                    input.input().encode(),
                    "input encode() must not return null");
            output.writeBytes(ByteBuffer.allocate(4).putInt(nameBytes.length).array());
            output.writeBytes(nameBytes);
            output.writeBytes(ByteBuffer.allocate(4).putInt(payload.length).array());
            output.writeBytes(payload);
        }
        return output.toByteArray();
    }

    /**
     * Named registered input descriptor.
     */
    public record RegisteredInput(String name, BrainInputInput input) {
        public RegisteredInput {
            Objects.requireNonNull(name, "name must not be null");
            if (name.isBlank()) {
                throw new IllegalArgumentException("name must not be blank");
            }
            Objects.requireNonNull(input, "input must not be null");
        }
    }
}