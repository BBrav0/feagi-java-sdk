# Minimal Agent Example

This example demonstrates the most basic usage of the FEAGI Java SDK - connecting, sending sensory data, and polling motor commands.

## Features

- Configure `BrainInput` and `BrainOutput` singletons
- Connect to FEAGI server
- Send sensory byte data
- Poll motor commands
- Proper resource cleanup

## Running the Example

```bash
# 1. Set environment variables
export FEAGI_HOST=localhost
export FEAGI_REGISTRATION_PORT=30001
export FEAGI_SENSORY_PORT=5555
export FEAGI_MOTOR_PORT=5564
export FEAGI_AGENT_ID=minimal-agent-001

# 2. Run
gradle run
```

## Code Walkthrough

### 1. Configure BrainInput and BrainOutput

```java
BrainInput brainInput = BrainInput.getInstance();
BrainOutput brainOutput = BrainOutput.getInstance();

brainInput.configure(BrainInputConfig.create()
    .feagiHost(feagiHost)
    .feagiPort(sensoryPort)
    .build());

brainOutput.configure(BrainOutputConfig.create()
    .feagiHost(feagiHost)
    .agentId(agentId)
    .registrationPort(registrationPort)
    .motorPort(motorPort)
    .build());
```

### 2. Connect and Run

```java
brainInput.connect();
brainOutput.connect();

while (running) {
    // Send sensory data
    brainInput.send(sensoryData);

    // Receive motor commands
    MotorDataFrame motorData = brainOutput.receive();
    if (motorData != null) {
        processMotorCommand(motorData);
    }
}
```

## Key APIs

| Method | Description |
|--------|-------------|
| `BrainInput.getInstance()` | Get singleton instance |
| `BrainOutput.getInstance()` | Get singleton instance |
| `configure()` | Set connection parameters |
| `connect()` | Connect to FEAGI |
| `send(byte[])` | Send sensory data |
| `receive()` | Poll motor commands (non-blocking) |
| `close()` | Release resources |

## Sample Output

```
[INFO] BrainInput configured: localhost:5555
[INFO] BrainOutput configured: localhost:5564
[INFO] Connected to FEAGI
[INFO] Iteration 1/100 - Sent sensory data 29440 bytes
[INFO] Iteration 2/100 - Sent sensory data 29440 bytes
...
[INFO] Completed 100 iterations
[INFO] Disconnected from FEAGI
```

## Next Steps

- See [`../vision-agent/`](../vision-agent/) for vision input processing
- See [`../motor-agent/`](../motor-agent/) for motor output processing
- See [`../servo-motor/`](../servo-motor/) for detailed servo motor control
