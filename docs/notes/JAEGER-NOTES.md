# Order Platform — Jaeger & Distributed Tracing Notes

Hands-on learning notes for the **Jaeger** tracing UI as wired into the Order
Platform's local **kind** cluster (context `kind-order-platform`, namespace
`order-platform-k8s`). Every example, tag value, and number here was captured
**live** against the running platform, so it's real, not illustrative.

- **Jaeger UI:** http://localhost:30686
- **Grafana (for the metrics ↔ traces workflow):** http://localhost:30300 (`admin` / `local-grafana-password`)
- **Prometheus:** http://localhost:30090

**Contents**
1. [What Jaeger is (and when to reach for it)](#what-jaeger-is-and-when-to-reach-for-it)
2. [The 4 words: trace, span, tags, process](#the-4-words-trace-span-tags-process)
3. [Current state on this platform](#current-state-on-this-platform)
4. [The UI, screen by screen](#the-ui-screen-by-screen)
5. [A real single trace, field by field](#a-real-single-trace-field-by-field)
6. [A real cross-service waterfall](#a-real-cross-service-waterfall)
7. [The Grafana → Jaeger pivot (the key workflow)](#the-grafana--jaeger-pivot-the-key-workflow)
8. [Driving traffic to generate traces](#driving-traffic-to-generate-traces)
9. [The HTTP API (no UI needed)](#the-http-api-no-ui-needed)
10. [How the services were instrumented](#how-the-services-were-instrumented)
11. [Common questions (Q&A)](#common-questions-qa)
12. [Local endpoints](#local-endpoints)

---

## What Jaeger is (and when to reach for it)

Jaeger is a **distributed tracing** backend + UI. A *trace* follows **one single
request** as it travels through your services; each unit of work along the way is
a *span* with a start time and a duration.

The one-line difference from the tools already in these notes:

| Tool | Question it answers | Granularity |
|---|---|---|
| **Grafana / Prometheus** (metrics) | "Are requests *in general* slow / failing?" | Aggregated (rates, percentiles) |
| **Jaeger** (traces) | "Where did *this one* request spend its time, and why?" | A single request, span by span |
| **Logs** | "What exactly happened at this line?" | A single event |

**Reach for Jaeger when** a dashboard tells you *something* is slow or erroring and
you now need to know **which step** and **which request** is responsible. Metrics
point at the symptom; traces point at the cause.

---

## The 4 words: trace, span, tags, process

```
Trace   = one request end-to-end            (identified by a traceID)
 └─ Span = one unit of work inside it        (an HTTP handler, an outbound call, a job)
     ├─ tags    = key/value facts about the span   (status=404, method=GET)
     └─ Process = which service/SDK emitted the span
```

### Trace
- The whole journey of one request. Identified by a **traceID** (e.g.
  `79d7d9d972735f2c574a17590c6b06c3`). Every span of that request shares the
  traceID — that's the glue.
- **When used:** someone says *"my request failed at 16:30"* → grab its traceID
  from the logs, paste it into Jaeger Search, and jump straight to that one
  request. It's also the unit each search-result row represents.

### Span
- One unit of work, with a **name**, **start time**, and **duration**.
- **`span.kind`** tells you its role — worth memorising:

  | kind | meaning | example on this platform |
  |---|---|---|
  | `server` | received an inbound request | the GET handler in order-service |
  | `client` | made an outbound call | the gateway calling order-service |
  | `internal` | in-process work | the `outbox-publisher` @Scheduled job |
  | `producer` / `consumer` | Kafka send / receive | order → inventory events |

- **When used:** in the waterfall, **the longest bar is your bottleneck**. Spans
  answer *"which step ate the time?"*

### Tags
- Key/value facts on a span. Real tags from our live 404 trace:
  ```
  method   = GET
  uri      = /api/v1/orders/{orderId}          ← route TEMPLATE  (low-cardinality → good for grouping/searching)
  http.url = /api/v1/orders/8e7a91a6-...        ← the ACTUAL url  (high-cardinality → the exact request)
  status   = 404
  outcome  = CLIENT_ERROR
  exception= none
  ```
  Two that matter a lot:
  - **`uri` (template) vs `http.url` (actual)** — the template groups every
    order-lookup together for filtering; the actual url tells you the exact ID
    requested.
  - **`outcome`**: `CLIENT_ERROR` = 4xx (caller's fault — e.g. our 404 for a
    missing order; the service is healthy). `SERVER_ERROR` = 5xx (the service
    broke — these carry `error=true` and an `exception`).
- **When used:** tags are what you **search and diagnose on**. Type `status=500`
  or `error=true` in the Search box to pull up only failures, then read the
  `exception` tag.

### Process
- Metadata about the emitter. Live from order-service:
  ```
  serviceName            = order-service
  telemetry.sdk.name     = opentelemetry
  telemetry.sdk.language = java
  telemetry.sdk.version  = 1.49.0
  ```
- **When used:** in a multi-service waterfall each span is colour-coded by
  Process, so you instantly see *"this slow bar is inventory-service, not
  order-service."*

> Also seen but minor: **Logs** (timestamped events *inside* a span, if the app
> adds them) and **Warnings** (e.g. clock skew between services).

---

## Current state on this platform

All four Spring Boot services are now instrumented with OpenTelemetry and export
to Jaeger. Confirmed live:

```
GET /api/services  →  ["order-service","api-gateway","notification-service","inventory-service","jaeger-all-in-one"]
```

- The `jaeger-all-in-one` entry is **Jaeger tracing itself** — ignore it.
- Jaeger runs as **`jaegertracing/all-in-one:1.71.0`** with **in-memory storage**
  → **all traces disappear when the Jaeger pod restarts.** It's a dev tool, not a
  system of record.
- **Sampling probability is `1.0`** (100%) locally, so *every* request is traced.
  In a real deployment you'd lower this (e.g. `0.1`) to protect storage.

---

## The UI, screen by screen

Open **http://localhost:30686**. Top nav: **Search · Compare · System Architecture · Monitor**.

### 1. Search — the screen you'll live in

Left-hand form — what each field does and **when** you set it:

| Field | What it does | When to set it |
|---|---|---|
| **Service** | Whose spans to search | Always first — e.g. `order-service` |
| **Operation** | Narrow to one endpoint/task, e.g. `http get /api/v1/orders/{orderId}` | When you know which route you're chasing |
| **Tags** | Filter by span tags: `status=404`, `error=true` | To see **only failures** or one status code |
| **Lookback** | Time window (last hour / custom) | Widen if recent traffic isn't showing |
| **Min/Max Duration** | Only traces slower/faster than a bound, e.g. `Min=100ms` | **Hunting slow requests** — the killer feature |
| **Limit Results** | Max traces returned (default 20) | Raise on a busy operation |

Hit **Find Traces**.

**Results panel (right):**
- A **scatter plot**: X = time, Y = duration. Each dot = one trace. **Slow
  outliers sit at the top → click a high dot to open the slow one.** Fastest way
  to catch a latency spike.
- Below, a **list**: trace name, span count, total duration, age, colored service
  badges. **Error traces are flagged red.**

### 2. Trace detail — the waterfall (the heart of Jaeger)

- Each **horizontal bar = a span**; length = duration; indent = parent→child
  nesting. **The longest bar is the bottleneck.**
- Click a span to expand: **Tags** (diagnostics), **Process** (which service/SDK),
  **Logs** (events inside the span), **Warnings** (e.g. clock skew).
- Top-right **View Options** re-render the same trace as: **Timeline**
  (waterfall), **Graph** (node diagram), **Trace Statistics** (time % per
  service/op), **Span Table**, **Flamegraph**. Use **Statistics** to answer *"what
  % of time went where."*

### 3. Compare

Paste **two traceIDs** → Jaeger diffs their structure (fast vs slow shows which
span appeared or ballooned). Use after you've found one good + one bad example.

### 4. System Architecture

A **service dependency graph (DAG)** built from trace data — who calls whom, how
often. Now that all four services are instrumented, this fills in (previously it
was empty with only one service).

### 5. Monitor (SPM)

RED metrics (Rate/Errors/Duration) derived from spans — needs an extra metrics
store wired in; treat as *"not configured here"* and use Grafana for metrics.

---

## A real single trace, field by field

Captured from a GET we sent through the gateway:

```
operation : http get /api/v1/orders/{orderId}
span.kind : server
duration  : 7.08 ms
tags:
  method   = GET
  uri      = /api/v1/orders/{orderId}
  http.url = /api/v1/orders/8e7a91a6-...-10c35bde4d20
  status   = 404
  outcome  = CLIENT_ERROR
  exception= none
```

**How to read it:** the request hit the `{orderId}` handler, returned **404** in
**7 ms**, classified `CLIENT_ERROR` (client asked for a missing order; service is
fine). If it were a **500 / `SERVER_ERROR`**, the span would carry `error=true`
and an `exception` — *that's* when you drill in.

**Auto-captured without any code from us:** actuator probe requests
(`http get /actuator/health/**`, `http get /actuator/prometheus`) and the
`@Scheduled` `outbox-publisher.publish-pending-events` job (an `internal` span
every 500 ms). Filter these out with the **Operation** dropdown when you want only
real API traffic.

---

## A real cross-service waterfall

Once the gateway, inventory, and notification services were instrumented too, one
GET produced a **3-span, 2-service** trace — context propagated across the hop:

```
traceID 79d7d9d972735f2c574a17590c6b06c3   (services: api-gateway, order-service)

 [api-gateway ] http get                              57.82 ms   ← inbound at gateway   (server)
 [api-gateway ]   HTTP GET                            48.74 ms   ← gateway's outbound call (client)
 [order-service]    http get /api/v1/orders/{orderId} 22.82 ms   ← the actual handler    (server)
```

**This is the whole point of distributed tracing.** Read it top-down: the request
entered the gateway (57 ms total), the gateway spent 48 ms calling downstream, and
order-service did its 22 ms of real work inside that. If order-service were slow,
you'd *see* its bar dominate — no guessing which service is at fault.

It only works because the traceID is **created at the edge (gateway)** and
**propagated** (via W3C `traceparent` headers) to each downstream service so their
spans join the same trace. That's why *every* hop must be instrumented — a single
un-instrumented service breaks the chain and its work becomes invisible.

---

## The Grafana → Jaeger pivot (the key workflow)

Grafana and Jaeger are **not auto-linked** on this platform (no *exemplars*
configured — that's the "click the spike → jump to the trace" feature, a natural
future upgrade). Today you pivot on **shared attributes**: the same `uri` / `status`
exist as **labels** in Prometheus and as **tags** in Jaeger.

Verified live — the HTTP metric carries these labels:
`uri, status, method, outcome, exception, error, application, instance`.

### Scenario A — "an endpoint is throwing 500s, which one?"

The dashboard panel aggregates, but the underlying metric has a `uri` label, so
break it down yourself in **Grafana → Explore** (or Prometheus):

```promql
sum by (uri, exception) (
  rate(http_server_requests_seconds_count{status="500"}[5m])
)
```

→ names the **exact endpoint** and even the Java exception class. Then in Jaeger:
Service = that service, Operation = that `uri`, Tags = `status=500` → open a trace
→ read the `exception` tag / span logs for the stack trace and the exact
`http.url` that failed.

**Metrics name the endpoint; Jaeger shows the failing request's guts.**

### Scenario B — "p99 latency spiked, which exact request?"

Metrics genuinely **can't** answer this — a p99 is a statistical summary with **no
request id**. Jaeger *is* the bridge, via **Min Duration**:

1. Grafana gives the number + time (e.g. *p99 ≈ 800 ms around 16:30*).
2. Jaeger Search: Service = `order-service`, **Min Duration = `800ms`**, Lookback
   covering 16:30 → **Find Traces**.
3. Only the slow outliers come back — each a real trace with a **traceID** and the
   actual `http.url`. Open it → the waterfall shows which span ate the 800 ms.

### The mental model to keep

| Grafana (metrics) | Jaeger (traces) |
|---|---|
| *That* something's wrong, *when*, *how often* | *Which* request, *which* span, *why* |
| Break down by `uri` / `status` **label** to name the endpoint | Filter by `uri` / `status` **tag** + **Min Duration** to find the exact request |
| No request IDs | traceID + exact `http.url` + exception |

They connect through **shared attributes**, not an automatic click (yet).

---

## Driving traffic to generate traces

Jaeger only shows what actually happened, so make something happen. From the host
(gateway NodePort `30080`):

```powershell
# GETs for missing orders -> traced spans (no body needed; quick way to populate Jaeger)
1..8 | ForEach-Object {
  $id = [guid]::NewGuid()
  try { Invoke-WebRequest "http://localhost:30080/api/v1/orders/$id" -UseBasicParsing -TimeoutSec 8 }
  catch { $_.Exception.Response.StatusCode.value__ }
}
```

Then in the UI: **Service** `order-service` → **Find Traces**. New traces appear
within seconds (sampling is `1.0`, so every request is captured).

---

## The HTTP API (no UI needed)

The same data the UI shows is available as JSON — handy for scripting and for
confirming instrumentation from the terminal:

```powershell
# Which services report to Jaeger?
Invoke-WebRequest "http://localhost:30686/api/services" -UseBasicParsing | Select -Expand Content

# Which operations does a service have?
Invoke-WebRequest "http://localhost:30686/api/operations?service=order-service" -UseBasicParsing

# Fetch traces (filter by operation, duration, lookback)
$op = [uri]::EscapeDataString("http get /api/v1/orders/{orderId}")
Invoke-WebRequest "http://localhost:30686/api/traces?service=order-service&operation=$op&minDuration=5ms&limit=20&lookback=2h" -UseBasicParsing
```

Key `/api/traces` params: `service`, `operation`, `tags` (JSON), `minDuration` /
`maxDuration` (e.g. `100ms`), `limit`, `lookback` (`1h`, `2h`), `start`/`end`
(microsecond epochs).

---

## How the services were instrumented

The same 4-step recipe applied to `order-service`, `inventory-service`,
`notification-service`, and `api-gateway`:

1. **`<service>/build.gradle.kts`** — add the OpenTelemetry bridge + exporter
   (versions managed by the Spring Boot BOM, so no versions):
   ```kotlin
   implementation("io.micrometer:micrometer-tracing-bridge-otel")
   implementation("io.opentelemetry:opentelemetry-exporter-otlp")
   ```
2. **`<service>/src/main/resources/application.yml`** — under the existing
   `management:` block:
   ```yaml
   tracing:
     sampling:
       probability: ${TRACING_SAMPLING_PROBABILITY:1.0}
   otlp:
     tracing:
       endpoint: ${OTLP_TRACING_ENDPOINT:http://localhost:4318/v1/traces}
   ```
3. **`local-platform/kubernetes/order-platform.yaml`** — add the endpoint env to
   each service's Deployment (Jaeger's in-cluster OTLP-HTTP port is `4318`):
   ```yaml
   - name: OTLP_TRACING_ENDPOINT
     value: http://jaeger:4318/v1/traces
   ```
4. Rebuild → load into kind → roll (pods use `imagePullPolicy: Never`, so the
   image **must** be loaded into the kind node):
   ```powershell
   docker build --tag order-platform/<service>:local `
     --file <service>\deploy\docker\Dockerfile <service>
   kind load docker-image order-platform/<service>:local --name order-platform
   kubectl apply -f local-platform\kubernetes\order-platform.yaml
   kubectl rollout restart deployment/<service> -n order-platform-k8s
   ```

---

## Common questions (Q&A)

**Q: Why did early traces have only one span?**
Only `order-service` was instrumented at first. A span is created by the service
that does the work; before the gateway/inventory/notification were instrumented,
their work was invisible and traces couldn't chain across services.

**Q: How does one traceID span multiple services?**
The gateway creates the traceID and passes it downstream in W3C `traceparent`
headers; each instrumented service continues the same trace. One un-instrumented
hop breaks the chain.

**Q: Why do I see `/actuator/health` and `/actuator/prometheus` traces I never sent?**
k8s liveness/readiness probes and Prometheus scrapes are ordinary HTTP requests,
so auto-instrumentation traces them too. Filter them out with the **Operation**
dropdown.

**Q: My traces vanished — bug?**
No. Jaeger all-in-one stores traces **in memory**; a pod restart wipes them.
Expected for local dev.

**Q: `CLIENT_ERROR` vs `SERVER_ERROR`?**
`CLIENT_ERROR` = 4xx (caller's fault, e.g. our 404 — service healthy).
`SERVER_ERROR` = 5xx (service failed) — those carry `error=true` + an `exception`
and are the ones to open.

**Q: Metrics vs traces — which first?**
Grafana first (*is* something wrong, and when?), then Jaeger (which request, which
span, why). Complementary, not competing.

**Q: How do I jump to one specific request?**
Copy its `traceId` from the service logs (it's in the MDC once instrumented) and
paste it into Jaeger Search.

---

## Local endpoints

| Component | URL | Notes |
|---|---|---|
| Jaeger UI | http://localhost:30686 | Search / Compare / System Architecture / Monitor |
| Jaeger OTLP HTTP | `http://jaeger:4318/v1/traces` (in-cluster) | Where the apps send spans |
| Jaeger OTLP gRPC | `jaeger:4317` (in-cluster) | Alternative span ingest |
| API Gateway | http://localhost:30080 | Entry point; drive traffic here |
| Grafana | http://localhost:30300 | `admin` / `local-grafana-password` (metrics view) |
| Prometheus | http://localhost:30090 | PromQL; break metrics down by `uri` / `status` |

> These are explicit **local-development** endpoints and credentials and must
> never be reused in a deployed environment.
