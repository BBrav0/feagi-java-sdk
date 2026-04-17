# Servo Motor Example

This example demonstrates how to use the FEAGI Java SDK to control servo motors.

## Features

- Configure multiple servo motors with different ranges
- Use `ServoMotor` device for position control
- Read and apply angle commands
- Support for ABSOLUTE and INCREMENTAL encoding modes

## Running the Example

```bash
# 1. Set environment variables
export FEAGI_HOST=localhost
export FEAGI_REGISTRATION_PORT=30001
export FEAGI_SENSORY_PORT=5555
export FEAGI_MOTOR_PORT=5564
export FEAGI_AGENT_ID=servo-agent-001

# 2. Run
gradle run
```

## Code Walkthrough

### 1. Register Servo Motors

```java
// Standard servo (0-180 degrees)
ServoMotor headServo = ServoMotor.builder()
    .angleRange(0.0, 180.0)
    .encoding(ServoMotor.Encoding.ABSOLUTE)
    .build();

// Wide-range servo (0-270 degrees)
ServoMotor armServo = ServoMotor.builder()
    .angleRange(0.0, 270.0)
    .encoding(ServoMotor.Encoding.ABSOLUTE)
    .build();

// Bidirectional servo (-90 to +90 degrees)
ServoMotor tiltServo = ServoMotor.builder()
    .angleRange(-90.0, 90.0)
    .encoding(ServoMotor.Encoding.ABSOLUTE)
    .build();
```

### 2. Read Angles

```java
double headAngle = headServo.getAngle();
double armAngle = armServo.getAngle();
double tiltAngle = tiltServo.getAngle();

// Apply to hardware
setServoAngle(headAngle, armAngle, tiltAngle);
```

## Sample Output

```
[INFO] FEAGI 2.0 - Servo Motor Example
[INFO] Head servo registered (0-180 deg)
[INFO] Arm servo registered (0-270 deg)
[INFO] Tilt servo registered (-90 to +90 deg)
[INFO] Connected to FEAGI
[Loop 0010] Head=  90.0 deg | Arm= 135.0 deg | Tilt= +0.0 deg
[Loop 0020] Head=  95.0 deg | Arm= 140.0 deg | Tilt= +5.0 deg
...
```

## Encoding Modes

### ABSOLUTE Mode

- `-1.0` -> `minAngle`
- `0.0` -> center angle
- `1.0` -> `maxAngle`

### INCREMENTAL Mode

- Value represents incremental change
- Use `incrementalStepRatio` to control step size

## Next Steps

- See [`../motor-agent/`](../motor-agent/) for comprehensive motor control
- See [`../../sdk-core/src/main/java/io/feagi/sdk/core/motor/ServoMotor.java`](../../sdk-core/src/main/java/io/feagi/sdk/core/motor/ServoMotor.java) for API details
