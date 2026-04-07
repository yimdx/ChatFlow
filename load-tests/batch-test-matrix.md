# Assignment 3 Batch Test Matrix

Use this matrix to execute and compare write-behind settings in `consumer-v3`.

| Run | Batch Size | Flush Interval | DB Writers | Consumer Threads | Notes |
|---|---:|---:|---:|---:|---|
| 1 | 100 | 100ms | 4 | 20 | low latency baseline |
| 2 | 500 | 100ms | 4 | 20 | balanced low flush |
| 3 | 1000 | 500ms | 4 | 20 | recommended default |
| 4 | 5000 | 500ms | 4 | 20 | throughput focus |
| 5 | 5000 | 1000ms | 4 | 20 | max batching |

Record results to `load-tests/results/`.
