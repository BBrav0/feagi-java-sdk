/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.cli;

/**
 * Parsed network settings from the FEAGI configuration file.
 */
record NetworkSettings(String apiHost, int apiPort, String wsHost, int wsPort) {}
