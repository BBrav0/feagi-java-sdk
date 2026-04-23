# Vision Agent Example

This example demonstrates how to use the FEAGI Java SDK for vision input processing.

## Features

- Configure `VisionCapability`
- Use `Camera` input device
- Send image frames to FEAGI
- Process vision feedback

## Running the Example

```bash
# 1. Set environment variables
export FEAGI_HOST=localhost
export FEAGI_REGISTRATION_PORT=30001
export FEAGI_SENSORY_PORT=5555
export FEAGI_MOTOR_PORT=5564
export FEAGI_AGENT_ID=vision-agent-001

# 2. Run
gradle run
```

## Code Walkthrough

### 1. Configure Vision Capability

```java
VisionCapability vision = VisionCapability.builder()
    .modality("vision")
    .resolution(640, 480)
    .channels(3)  // RGB
    .build();

AgentCapabilities capabilities = AgentCapabilities.builder()
    .vision(vision)
    .build();
```

### 2. Use Camera Device

```java
Camera camera = Camera.builder()
    .resolution(640, 480)
    .channels(3)
    .encoding("RGB")
    .build();

camera._registerWithCache();

// In main loop
byte[] frameData = captureFrame();
camera.setFrame(frameData);
```

## Sample Output

```
[INFO] Vision agent starting
[INFO] Camera registered: 640x480 RGB
[INFO] Connected to FEAGI
[INFO] Frame 1/500 - Sent 921600 bytes
[INFO] Frame 2/500 - Sent 921600 bytes
...
```

## Next Steps

- See [`../motor-agent/`](../motor-agent/) for motor output processing
- See [`../minimal-agent/`](../minimal-agent/) for basic connection
