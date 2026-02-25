/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.cli;

import java.nio.file.Path;
import java.util.OptionalLong;

/**
 * Typed process status returned by {@link FeagiProcessManager#getStatus()}
 * and {@link BvProcessManager#getStatus()}.
 */
record ProcessStatus(boolean running, OptionalLong pid, Path pidFile) {}
