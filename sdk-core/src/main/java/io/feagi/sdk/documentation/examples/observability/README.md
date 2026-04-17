# Observability Examples

This directory contains examples for FEAGI Java SDK observability features.

## Examples List

| Example | Description |
|---------|-------------|
| [`BasicMetrics`](#basicmetrics) | Metrics collection, export to JSON/CSV |
| [`DataLogging`](#datalogging) | Data logging in JSONL/CSV formats |

## BasicMetrics

Demonstrates how to use `MetricsCollector` to collect PNS data flow statistics.

### Features

- Collect input/output packet statistics
- Calculate data rates and latencies
- Export to JSON and CSV formats

### Run

```bash
export FEAGI_HOST=localhost
export FEAGI_REGISTRATION_PORT=30001
export FEAGI_SENSORY_PORT=5555
export FEAGI_MOTOR_PORT=5564
export FEAGI_AGENT_ID=metrics-agent-001

gradle run
```

### Sample Output

```json
{
  "input": {
    "total_packets": 100,
    "total_bytes": 2944000,
    "data_rate_mbps": 12.5
  },
  "output": {
    "total_commands": 100,
    "avg_latency_ms": 2.5
  }
}
```

## DataLogging

Demonstrates how to use `DataLogger` to log sensory and motor data.

### Features

- JSONL format (one JSON object per line)
- CSV format (tabular data)
- Optional data sampling

### Run

```bash
export FEAGI_HOST=localhost
export FEAGI_REGISTRATION_PORT=30001
export FEAGI_SENSORY_PORT=5555
export FEAGI_MOTOR_PORT=5564
export FEAGI_AGENT_ID=logging-agent-001

gradle run
```

### Output Files

- `data_log.jsonl` - JSON Lines format log
- `data_log.csv` - CSV format log

### Analyze Logs

```bash
# Use jq to view JSONL
cat data_log.jsonl | jq '.'

# Use Excel to open CSV
# or view directly
cat data_log.csv
```

## API Reference

### MetricsCollector

```java
MetricsCollector metrics = new MetricsCollector();

// Attach to PNS events
brainInput.attachMonitor(metrics);
brainOutput.attachMonitor(metrics);

// Get statistics
InputStatistics inputStats = metrics.getInputStatistics();
OutputStatistics outputStats = metrics.getOutputStatistics();

// Export
metrics.exportJson("metrics.json");
metrics.exportCsv("metrics.csv");
```

### DataLogger

```java
DataLogger logger = new DataLogger.Builder()
    .outputFile("agent_data.jsonl")
    .format(DataLogger.Format.JSONL)
    .logInputs(true)
    .logOutputs(true)
    .sampleRate(1.0)
    .build();

// Attach logger
brainInput.attachMonitor(logger);
brainOutput.attachMonitor(logger);

// Close after running
logger.close();
```

## Next Steps

- See [`MetricsCollector`](../../sdk-core/src/main/java/io/feagi/sdk/observability/MetricsCollector.java) source code
- See [`DataLogger`](../../sdk-core/src/main/java/io/feagi/sdk/observability/DataLogger.java) source code
