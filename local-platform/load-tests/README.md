# k6 load tests

Four workflow-oriented scripts are provided:

| Script | Behavior |
|---|---|
| `place-order.js` | Builds weighted multi-item carts, places orders, and waits for the Kafka inventory saga. |
| `cancel-order.js` | Creates and confirms an order, applies user think time, cancels it, and waits for inventory release. |
| `get-order.js` | Creates a reusable confirmed-order pool and reads randomly selected orders. |
| `update-inventory.js` | Gives each VU an inventory item, reads its ETag, and performs conditional warehouse updates. |

## Run

From `local-platform\docker-compose`:

```powershell
docker compose --profile load-test run --rm -e TEST_TYPE=smoke k6 run /scripts/place-order.js
docker compose --profile load-test run --rm -e TEST_TYPE=load k6 run /scripts/cancel-order.js
docker compose --profile load-test run --rm -e TEST_TYPE=stress k6 run /scripts/get-order.js
docker compose --profile load-test run --rm -e TEST_TYPE=spike k6 run /scripts/update-inventory.js
docker compose --profile load-test run --rm -e TEST_TYPE=soak k6 run /scripts/place-order.js
```

When running k6 directly on the host:

```powershell
k6 run -e BASE_URL=http://localhost:8080 -e TEST_TYPE=smoke .\place-order.js
```

Supported variables include `BASE_URL`, `TEST_TYPE`, `SAGA_TIMEOUT_SECONDS`,
`ORDER_POOL_SIZE`, `STRESS_MAX_VUS`, and `SOAK_VUS`.

## Test profiles and production risks

| Profile | Shape | Intended production issue |
|---|---|---|
| Smoke | 1 VU for 30 seconds | Broken deployment, routing, schema, migration, or basic saga integration. |
| Load | 10 to 50 to 100 VUs with steady plateaus | Normal-capacity latency growth, inefficient queries, undersized pools, or Kafka lag. |
| Stress | Progressive growth to `STRESS_MAX_VUS` | The capacity ceiling and whether failure is controlled or cascading. |
| Spike | 10 to 500 VUs almost immediately | Burst queueing, autoscaling delay, circuit-breaker behavior, and connection storms. |
| Soak | Moderate load for 30 minutes | Memory/resource leaks, pool leaks, backlog accumulation, and database bloat. |

## Latency expectations

- **P50** should remain comparatively flat. A rising P50 means the whole workload is
  slowing rather than only a small tail.
- **P95** normally rises before P50 as queues form. Under the normal load test it
  should remain below the configured 1-second threshold.
- **P99** is the first place to see GC pauses, lock contention, Kafka waits, and
  connection acquisition delays. Short spikes are expected during ramp changes;
  sustained growth or a widening P99-to-P50 gap indicates saturation.
- Stress and spike tests permit higher thresholds because their purpose is to find
  the failure boundary, not certify normal service-level objectives.

## What to observe

### Smoke test

- **Grafana/Prometheus:** all four applications remain up; request/error rates match
  the single-user traffic; no unexplained 5xx responses.
- **Jaeger:** one trace should show gateway, order, Kafka, and inventory timing.
  Application tracing is not currently instrumented, so Jaeger will not provide this
  view until OpenTelemetry is added.
- **JVM:** stable heap after startup, negligible GC pauses, low CPU.
- **Tomcat:** busy threads close to one and no queueing.
- **HikariCP/PostgreSQL:** low active connections and no acquisition waits.
- **Kafka:** reservation events are consumed immediately with near-zero lag.
- **Redis:** no change is expected because Redis is currently provisioned but unused.

### Load test

- **Grafana:** compare throughput, error ratio, P50/P95/P99, CPU, heap, and GC at each
  10/50/100-VU plateau.
- **Prometheus:** latency should stabilize at each plateau rather than continue
  climbing after load stops increasing.
- **JVM/Tomcat:** busy threads and CPU may rise, but should fall or stabilize without
  reaching configured maxima.
- **HikariCP/PostgreSQL:** active connections may approach the pool size; pending
  acquisition and query duration should remain near zero/steady.
- **Kafka:** consumer lag should return to baseline at every plateau. Persistent lag
  means event throughput is below request throughput.
- **Jaeger:** inspect slow traces for database, lock, and Kafka boundaries once tracing
  is enabled.

### Stress test

- Record the first VU level where error rate, P95, or P99 violates its threshold.
- **Thread exhaustion:** Tomcat busy/max ratio approaches 1, request latency rises
  sharply, and CPU may remain below 100% because requests are waiting.
- **Database pool exhaustion:** Hikari active equals max, pending grows, acquisition
  time rises, followed by timeouts and 5xx responses.
- **Kafka bottleneck:** producer latency/errors or consumer lag rises continuously;
  orders remain `PENDING` longer while synchronous HTTP may still look healthy.
- **CPU saturation:** CPU remains near available cores, run queue/latency rises, and
  throughput stops increasing despite more VUs.

### Spike test

- Watch gateway timeouts, circuit-breaker state, rejected connections, Tomcat thread
  usage, Hikari pending connections, and Kafka lag during the 10-to-500 jump.
- Healthy behavior is controlled degradation followed by quick recovery. A failure
  that continues after VUs drop indicates a queue, retry storm, or circuit-breaker
  recovery problem.

### Soak test

- **Memory leak:** post-GC heap baseline rises across repeated GC cycles, old-generation
  occupancy grows, GC becomes more frequent, or containers approach their memory
  limit.
- **Thread/connection leak:** Tomcat threads, Hikari active connections, PostgreSQL
  sessions, or open sockets trend upward without returning to baseline.
- **Kafka leak/backlog:** lag increases slowly despite constant traffic.
- **Database bloat:** storage, dead tuples, query latency, or checkpoint pressure rises
  during otherwise constant throughput.

## Useful PromQL

```promql
service:http_latency_seconds:p50_5m
service:http_latency_seconds:p95_5m
service:http_latency_seconds:p99_5m
service:http_error_ratio:rate5m
service:tomcat_thread_usage:ratio
hikaricp_connections_active
hikaricp_connections_pending
rate(jvm_gc_pause_seconds_sum[5m])
service:jvm_heap_usage:ratio
rate(process_cpu_seconds_total[5m])
k6_http_req_duration_p95
k6_http_req_failed_rate
```

The current Prometheus configuration does not scrape PostgreSQL, Kafka, or Redis
exporters, and the applications do not emit traces to Jaeger. Those systems must be
instrumented before their detailed metrics or traces can be evaluated; k6 metrics are
sent to Prometheus through its remote-write receiver.
