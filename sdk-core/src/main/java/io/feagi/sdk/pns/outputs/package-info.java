/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

/**
 * FEAGI PNS (Peripheral Nervous System) Output Types.
 *
 * <p>This package provides output type implementations for the FEAGI framework,
 * corresponding to the Python SDK's {@code feagi.pns.outputs} module. Output types
 * are responsible for receiving and processing data that flows out from the
 * neuromorphic system to external actuators and devices.</p>
 *
 * <h2>Output Types</h2>
 *
 * <ul>
 *   <li>{@link io.feagi.sdk.pns.outputs.BaseOutput} - Abstract base class for all outputs</li>
 *   <li>{@link io.feagi.sdk.pns.outputs.ServoMotor} - Servo motor output for positional control</li>
 *   <li>{@link io.feagi.sdk.pns.outputs.RotaryMotor} - Rotary/DC motor output for speed control</li>
 *   <li>{@link io.feagi.sdk.pns.outputs.OutputNumericStream} - Generic numeric stream output</li>
 *   <li>{@link io.feagi.sdk.pns.outputs.OutputTextStream} - Text output for language generation</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Servo motor for arm control
 * ServoMotor armServo = ServoMotor.builder()
 *     .angleRange(0.0, 180.0)
 *     .encoding(ServoMotor.Encoding.ABSOLUTE)
 *     .build();
 * armServo._registerWithCache();
 *
 * // Rotary motors for differential drive
 * RotaryMotor motorLeft = RotaryMotor.builder()
 *     .bidirectional(true)
 *     .build();
 * RotaryMotor motorRight = RotaryMotor.builder()
 *     .bidirectional(true)
 *     .build();
 * motorLeft._registerWithCache();
 * motorRight._registerWithCache();
 *
 * // Numeric stream for trading signals
 * OutputNumericStream tradingSignal = OutputNumericStream.builder()
 *     .dimensions(3)  // buy, sell, hold
 *     .build();
 * tradingSignal._registerWithCache();
 *
 * // Text output for chatbot
 * OutputTextStream chatOutput = OutputTextStream.builder()
 *     .maxTextLength(256)
 *     .build();
 * chatOutput._registerWithCache();
 * }</pre>
 *
 * <h2>Motor Command Processing</h2>
 *
 * <p>All motor outputs receive normalized values from FEAGI in the range [-1.0, 1.0]:</p>
 * <ul>
 *   <li>{@link ServoMotor} - Maps to angle range using absolute or incremental mode</li>
 *   <li>{@link RotaryMotor} - Uses directly as speed (bidirectional) or maps to [0, 1] (unidirectional)</li>
 * </ul>
 *
 * @see io.feagi.sdk.pns.inputs
 */
package io.feagi.sdk.pns.outputs;
