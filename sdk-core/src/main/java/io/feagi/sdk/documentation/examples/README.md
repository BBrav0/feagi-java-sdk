# FEAGI Java SDK Examples

This directory contains runnable examples using the FEAGI Java SDK. All examples are kept in sync with the latest SDK APIs and mirror the Python SDK examples.

## Requirements

- **Java**: 17 or higher
- **Gradle**: 8.0 or higher (or use Maven)
- **FEAGI**: Running FEAGI server
- **Native Library**: `feagi-java-ffi` shared library

## Quick Start

```bash
# 1. Set environment variables
export FEAGI_HOST=localhost
export FEAGI_REGISTRATION_PORT=30001
export FEAGI_SENSORY_PORT=5555
export FEAGI_MOTOR_PORT=5564
export FEAGI_AGENT_ID=my-agent-001

# 2. Build and run example
cd examples/minimal-agent
../gradlew run
```

## Examples List

| Example | Description | Difficulty |
|---------|-------------|------------|
| [`minimal-agent`](./minimal-agent/) | Minimal connect + send sensory + poll motor using BrainInput/BrainOutput | Beginner |
| [`vision-agent`](./vision-agent/) | Vision input agent using VideoStreamAgent | Intermediate |
| [`motor-agent`](./motor-agent/) | Motor output agent using BrainOutput | Intermediate |
| [`servo-motor`](./servo-motor/) | Servo motor control with multiple servos | Beginner |
| [`observability`](./observability/) | Metrics collection and data logging | Intermediate |

## Configuration

All examples use environment variables for configuration (no hardcoded defaults):

| Variable | Description | Default |
|----------|-------------|---------|
| `FEAGI_HOST` | FEAGI server host | None (required) |
| `FEAGI_REGISTRATION_PORT` | Registration service port | 30001 |
| `FEAGI_SENSORY_PORT` | Sensory data port | 5555 |
| `FEAGI_MOTOR_PORT` | Motor command port | 5564 |
| `FEAGI_API_PORT` | API port | 8080 |
| `FEAGI_AGENT_ID` | Unique agent identifier | None (required) |

## Build Instructions

### Using Gradle

Each example directory contains its own `build.gradle` file:

```bash
cd examples/minimal-agent
gradle build
gradle run
```

### Project-wide Build

From SDK root:

```bash
./gradlew :examples:build
```

## Troubleshooting

### Native Library Not Found

```
UnsatisfiedLinkError: no feagi_java_ffi in java.library.path
```

Set library path:

```bash
# Linux
export LD_LIBRARY_PATH=/path/to/feagi-java-ffi/target/release:$LD_LIBRARY_PATH

# macOS
export DYLD_LIBRARY_PATH=/path/to/feagi-java-ffi/target/release:$DYLD_LIBRARY_PATH

# Windows
set PATH=%PATH%;C:\path\to\feagi-java-ffi\target\release
```

### Connection Refused

Ensure FEAGI server is running:

```bash
feagi status
# or
feagi start
```

## Comparison with Python SDK Examples

These examples mirror the Python SDK examples' functionality:

| Java Example | Python Equivalent |
|--------------|-------------------|
| `minimal-agent` | `mixed_transport_agent.py` |
| `vision-agent` | `video_streamer/example_video_simple.py` |
| `motor-agent` | `simple_robot/example_simple_robot.py` |
| `servo-motor` | `servo_motor/example_servo_motor.py` |
| `observability/BasicMetrics` | `observability/01_basic_metrics.py` |
| `observability/DataLogging` | `observability/02_data_logging.py` |

## Next Steps

See individual example directories for detailed instructions.
