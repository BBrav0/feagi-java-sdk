# Motor Agent Example

This example demonstrates how to use the FEAGI Java SDK for motor output processing.

## Features

- Configure `MotorCapability`
- Use `ServoMotor` and `RotaryMotor` output devices
- Poll and parse motor commands
- Control multiple motors

## Running the Example

```bash
# 1. Set environment variables
export FEAGI_HOST=localhost
export FEAGI_REGISTRATION_PORT=30001
export FEAGI_SENSORY_PORT=5555
export FEAGI_MOTOR_PORT=5564
export FEAGI_AGENT_ID=motor-agent-001

# 2. Run
gradle run
```

## Code Walkthrough

### 1. Configure Motor Capability

```java
MotorCapability motor = MotorCapability.builder()
    .modality("motor")
    .outputCount(4)  // 4 motor outputs
    .build();

AgentCapabilities capabilities = AgentCapabilities.builder()
    .motor(motor)
    .build();
```

### 2. Use Motor Devices

```java
// Servo motor (position control)
ServoMotor headServo = ServoMotor.builder()
    .angleRange(0.0, 180.0)
    .encoding(ServoMotor.Encoding.ABSOLUTE)
    .build();

// Rotary motor (speed control)
RotaryMotor wheelMotor = RotaryMotor.builder()
    .maxSpeed(100.0)
    .build();

// Read motor commands
double angle = headServo.getAngle();
double speed = wheelMotor.getSpeed();
```

## Sample Output

```
[INFO] Motor agent starting
[INFO] Head servo registered: 0-180 degrees
[INFO] Arm servo registered: -90 to +90 degrees
[INFO] Connected to FEAGI
[INFO] Iteration 1/100 - Head servo=90.0 deg, Arm servo=0.0 deg
[INFO] Iteration 2/100 - Head servo=95.0 deg, Arm servo=5.0 deg
...
```

## Next Steps

- See [`../servo-motor/`](../servo-motor/) for detailed servo motor control
- See [`../vision-agent/`](../vision-agent/) for vision input processing
