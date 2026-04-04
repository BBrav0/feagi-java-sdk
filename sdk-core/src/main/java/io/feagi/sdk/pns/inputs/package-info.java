/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

/**
 * FEAGI PNS (Peripheral Nervous System) Input Types.
 *
 * <p>This package provides input type implementations for the FEAGI framework,
 * corresponding to the Python SDK's {@code feagi.pns.inputs} module. Input types
 * are responsible for capturing and processing external data that flows into
 * the neuromorphic system.</p>
 *
 * <h2>Input Types</h2>
 *
 * <ul>
 *   <li>{@link io.feagi.sdk.pns.inputs.BaseInput} - Abstract base class for all inputs</li>
 *   <li>{@link io.feagi.sdk.pns.inputs.Camera} - Vision/camera input for image processing</li>
 *   <li>{@link io.feagi.sdk.pns.inputs.NumericStream} - Generic numeric sensor data stream</li>
 *   <li>{@link io.feagi.sdk.pns.inputs.TextStream} - Text input for natural language processing</li>
 *   <li>{@link io.feagi.sdk.pns.inputs.InfraredInput} - Infrared distance sensor input</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Create a camera input
 * Camera camera = Camera.builder()
 *     .resolution(640, 480)
 *     .channels(3)
 *     .encoding("RGB")
 *     .build();
 * camera._registerWithCache();
 *
 * // Create a numeric sensor input
 * NumericStream sensor = NumericStream.builder()
 *     .range(-10.0, 10.0)
 *     .precision(0.001)
 *     .build();
 * sensor._registerWithCache();
 *
 * // Create an infrared distance sensor
 * InfraredInput irSensor = InfraredInput.builder()
 *     .range(0.03, 0.40)
 *     .fieldOfView(25.0)
 *     .position("front")
 *     .build();
 * irSensor._registerWithCache();
 * }</pre>
 *
 * <h2>FEAGI 2.0 Compatibility</h2>
 *
 * <p>These input types are designed to work with the FEAGI 2.0 capability
 * format, particularly with {@link io.feagi.sdk.core.VisionUnitConfig} for
 * vision inputs.</p>
 *
 * @see io.feagi.sdk.core.VisionUnitConfig
 * @see io.feagi.sdk.core.AgentCapabilities
 */
package io.feagi.sdk.pns.inputs;
