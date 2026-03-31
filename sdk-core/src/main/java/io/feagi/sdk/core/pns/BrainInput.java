/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core.pns;

import io.feagi.sdk.core.TransportMode;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * BrainInput-style global input manager.
 *
 * <p>Wire format:
 * <ul>
 *   <li>4 ASCII magic bytes: {@code FBIN}</li>
 *   <li>1 protocol version byte: {@value #PROTOCOL_VERSION}</li>
 *   <li>32-bit big-endian input count</li>
 *   <li>Per input: 32-bit big-endian UTF-8 name length, name bytes, 32-bit big-endian payload
 *       length, payload bytes</li>
 * </ul>
 *
 * <p>Callers explicitly configure host, port, and transport, then register inputs and invoke
 * {@link #send()} to encode and flush all inputs as one payload.
 */
public final class BrainInput implements AutoCloseable {
    public static final int PROTOCOL_VERSION = 1;
    private static final byte[] MAGIC = new byte[]{'F', 'B', 'I', 'N'};
    private static final BrainInput GLOBAL = new BrainInput(true);

    private final boolean globalInstance;
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
        if (transport == null) {
            throw new IllegalArgumentException("transport must not be null");
        }
        return new BrainInput(false, transport);
    }

    /**
     * Create a fresh BrainInput instance with no transport yet attached.
     */
    public static BrainInput create() {
        return new BrainInput(false);
    }

    private BrainInput(boolean globalInstance) {
        this(globalInstance, null);
    }

    private BrainInput(boolean globalInstance, BrainInputTransport transport) {
        this.globalInstance = globalInstance;
        this.transport = transport;
    }

    /**
     * Configure the singleton/global input manager.
     */
    public synchronized BrainInput configure(String host, int port, TransportMode transportMode) {
        return configure(new BrainInputConfig(host, port, transportMode));
    }

    /**
     * Configure the input manager.
     */
    public synchronized BrainInput configure(BrainInputConfig config) {
        if (connected) {
            throw new IllegalStateException("cannot reconfigure while connected");
        }
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        this.config = config;
        return this;
    }

    /**
     * Attach a concrete transport implementation.
     */
    public synchronized BrainInput useTransport(BrainInputTransport transport) {
        if (connected) {
            throw new IllegalStateException("cannot replace transport while connected");
        }
        if (transport == null) {
            throw new IllegalArgumentException("transport must not be null");
        }
        this.transport = transport;
        return this;
    }

    /**
     * Register a named input source.
     */
    public synchronized BrainInput registerInput(String name, BrainInputSource input) {
        return registerInputInternal(name, input);
    }

    /**
     * Register multiple input sources in order.
     */
    public synchronized BrainInput registerInputs(RegisteredInput... registrations) {
        if (registrations == null) {
            throw new IllegalArgumentException("registrations must not be null");
        }
        for (RegisteredInput registration : registrations) {
            if (registration == null) {
                throw new IllegalArgumentException("registered input must not be null");
            }
            registerInputInternal(registration.name(), registration.input());
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
     *
     * <p>Caller-owned instances are fully reset. The global singleton intentionally keeps its
     * registration state to avoid one caller breaking another shared reference.
     */
    @Override
    public synchronized void close() {
        if (transport != null) {
            transport.close();
        }
        connected = false;
        if (!globalInstance) {
            config = null;
            transport = null;
            inputs.clear();
        }
    }

    private BrainInput registerInputInternal(String name, BrainInputSource input) {
        if (name == null) {
            throw new IllegalArgumentException("name must not be null");
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (input == null) {
            throw new IllegalArgumentException("input must not be null");
        }
        boolean duplicateName = inputs.stream().anyMatch(existing -> existing.name().equals(name));
        if (duplicateName) {
            throw new IllegalArgumentException("input name already registered: " + name);
        }
        inputs.add(new RegisteredInput(name, input));
        return this;
    }

    private void requireConfigured() {
        if (config == null) {
            throw new IllegalStateException("configure() must be called before connect()");
        }
    }

    private byte[] encodePayload() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteBuffer intBuffer = ByteBuffer.allocate(Integer.BYTES);
        output.writeBytes(MAGIC);
        output.write(PROTOCOL_VERSION);
        writeInt(output, intBuffer, inputs.size());
        for (RegisteredInput input : inputs) {
            byte[] nameBytes = input.name().getBytes(StandardCharsets.UTF_8);
            byte[] payload = Objects.requireNonNull(
                    input.input().encode(),
                    "input encode() must not return null");
            writeInt(output, intBuffer, nameBytes.length);
            output.writeBytes(nameBytes);
            writeInt(output, intBuffer, payload.length);
            output.writeBytes(payload);
        }
        return output.toByteArray();
    }

    private static void writeInt(ByteArrayOutputStream output, ByteBuffer intBuffer, int value) {
        intBuffer.clear();
        intBuffer.putInt(value);
        output.writeBytes(intBuffer.array());
    }

    /**
     * Named registered input descriptor.
     */
    public record RegisteredInput(String name, BrainInputSource input) {
        public RegisteredInput {
            if (name == null) {
                throw new IllegalArgumentException("name must not be null");
            }
            if (name.isBlank()) {
                throw new IllegalArgumentException("name must not be blank");
            }
            if (input == null) {
                throw new IllegalArgumentException("input must not be null");
            }
        }
    }
}
