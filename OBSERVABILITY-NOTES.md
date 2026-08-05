# Order Platform — Observability & CLI Notes

Hands-on learning notes for observing the **Kubernetes** deployment of the Order
Platform: the Grafana **Service Overview** dashboard, the everyday **kubectl**
commands, and answers to the questions that came up while exploring it.

The platform runs on a local **kind** cluster (context `kind-order-platform`,
namespace `order-platform-k8s`).

**Contents**
1. [Where to observe (the 3 layers)](#where-to-observe-the-3-layers)
2. [Grafana Service Overview — every panel explained](#grafana-service-overview--every-panel-explained)
3. [Running the CLI yourself](#running-the-cli-yourself)
4. [Common kubectl commands (with results)](#common-kubectl-commands-with-results)
5. [Common questions (Q&A)](#common-questions-qa)
6. [Local endpoints](#local-endpoints)

---

## Where to observe (the 3 layers)

| Layer | What you inspect | Where |
|---|---|---|
| **Infrastructure** (Kubernetes) | pods, restarts, logs, health | `kubectl` |
| **Metrics** (numbers over time) | request rate, errors, latency, JVM, DB pool | Prometheus + Grafana |
| **Application behavior** | orders, notifications, emails, mock providers | Gateway API, Mailpit, WireMock, Jaeger |

**Data flow behind the dashboards:**

```
each pod (/actuator/prometheus)  ──scraped by──▶  Prometheus (TSDB, prometheus-data PVC)  ──queried by──▶  Grafana panels
```

---

## Grafana Service Overview — every panel explained

Open Grafana at http://localhost:30300 (`admin` / `local-grafana-password`) →
**Dashboards → Order Platform → Service Overview**.

Example numbers in parentheses are readings captured while `order-service` was
idle just after startup. Data only exists from when the pods started, so earlier
time in each graph is empty.

### Dashboard controls (top bar)

| Control | Meaning |
|---|---|
| **Application** dropdown | Switches the whole dashboard between services: `api-gateway`, `order-service`, `inventory-service`, `notification-service`. Every panel re-queries for the selected app. |
| **Time range** ("Last 30 minutes") | The window shown. |
| **Refresh** (15s) | Auto-refresh interval. Lower it to watch live load. |

### Row 1 — KPI "stat" panels (at-a-glance health)

![Grafana KPI row plus latency and heap panels](docs/images/grafana-service-overview-kpis.png)

**1. Service Up (1)** — Is Prometheus successfully scraping the service? `1` = up,
`0` = down. **Healthy:** `1` (green). **Alarm:** `0` — pod down/unreachable; check
this first because every other panel goes empty when it's `0`.

**2. Request Rate (0.37 req/s)** — HTTP requests handled per second (throughput),
via `rate(http_server_requests_seconds_count[...])`. Rises under load, ~0 when
idle. Read it *with* latency and errors: high rate + rising latency = saturation.

**3. 5xx Error Rate (0.00%)** — Percentage of responses that are server errors
(HTTP 500–599). **Healthy:** `0%`. **Alarm:** any sustained non-zero value — the
service is failing requests. Usually the first panel to check when something is
wrong.

**4. CPU Usage — process (2.6%) / system (2.6%)** — `process` = CPU used by this
JVM; `system` = CPU used by the whole node. Sustained process CPU near 100% =
CPU-bound.

**5. Heap Usage (9.2%)** — Percentage of the JVM's max heap in use
(`jvm_memory_used_bytes / jvm_memory_max_bytes`). **Healthy:** stable, well below
100%. **Alarm:** creeping toward 100% = possible memory leak → `OutOfMemoryError`
and restarts.

**6. Tomcat Thread Usage (0.5%)** — Percentage of the web server worker pool in
use. **Alarm:** near 100% = server saturated; new requests queue or get rejected.

### Row 2 — Latency & Heap detail

**7. HTTP Latency Percentiles — P50 / P95 / P99** — How long requests take. A
percentile means "X% of requests were faster than this."
- **P50 (3.09 ms):** median — the typical request.
- **P95 (14.6 ms):** the slow-ish tail.
- **P99 (22.2 ms):** the worst 1% users feel.
- **`Last` vs `Max` columns:** `Last` = most recent; `Max` = worst spike in the
  window (P99 Max 186 ms was the startup / JIT warm-up spike).
- **Read it:** Watch **P95/P99**, not the average — averages hide the slow tail
  that actually annoys users.

**8. JVM Heap — Eden / Survivor / Tenured** — The three Java heap generations.
- **Eden (19.9 MiB):** where new objects are allocated; a "young GC" clears it,
  producing the normal sawtooth.
- **Survivor (581 KiB):** objects that survived one GC wait here briefly.
- **Tenured / Old Gen (39.0 MiB):** long-lived objects promoted from young gen.
- **Read it:** Sawtooth Eden = healthy. Tenured that only ever grows and is never
  reclaimed = classic memory-leak signature.

### Row 3 — Threads & GC

![Grafana JVM threads, thread states, GC pause, Tomcat and DB pool panels](docs/images/grafana-service-overview-jvm.png)

**9. JVM Threads — live (35) / peak (36) / daemon (26)** — Threads alive now,
the historical max, and background threads. **Healthy:** flat/stable. **Alarm:**
`live` climbing without bound = thread leak.

**10. Thread States — runnable (10) / blocked (0) / new (0)** — `runnable` =
running or ready (normal); `blocked` = waiting on a lock; `new` = not yet started.
**Key rule:** **`blocked` should stay at 0.** Rising `blocked` = lock contention,
which kills throughput.

**11. GC Pause — P95 GC pause (8.18 ms, max 13.7 ms)** — How long the app is
paused ("stop-the-world") for garbage collection. Pauses add directly to request
latency. **Healthy:** single-digit to low double-digit **ms**. **Alarm:** pauses
growing into hundreds of ms / seconds = GC pressure.

### Row 4 — Capacity pools

**12. Tomcat Executor Threads — busy (1) / current (10) / configured max (200)** —
Worker threads actively handling requests, threads currently in the pool, and the
ceiling. **Key rule:** **watch `busy` approaching `max`.** When busy ≈ max, every
worker is occupied and requests queue → latency spikes, then rejections.

**13. Database Connection Pool (HikariCP) — active (0) / idle (2) / pending (0)** —
Connections running a query, open-and-idle connections, and threads **waiting**
for a connection. **Key rule:** **`pending` must stay at 0.** `pending > 0` = pool
exhausted; requests stall waiting for a DB connection (a very common latency cause
under load).

### The 4 numbers to check for "is it healthy?"

1. **5xx Error Rate** → `0%`
2. **Thread States → blocked** → `0`
3. **DB Connection Pool → pending** → `0`
4. **Tomcat busy vs configured max** → busy far below max

If all four are good, the service is almost certainly healthy regardless of load.

### Make the graphs move (see it react)

Most panels are flat when idle. Generate activity, then watch:

```powershell
# From the repository root
.\local-platform\kubernetes\Test-KubernetesFlow.ps1   # one happy-path order flow
.\local-platform\scripts\Test-EndToEnd.ps1            # 8 scenarios incl. failures & retries
```

Then use the **Application** dropdown to compare the four services.

---

## Running the CLI yourself

- Open a **new** terminal — **PowerShell 7** or **Windows Terminal** (open it
  *after* Docker Desktop / kind were installed so it has the updated PATH).
  `docker`, `kubectl`, and `kind` are already on PATH — no prefix needed.
- If a terminal says `kubectl: command not found`, it's an **old window** opened
  before install — close and reopen it.

**Point at the right cluster and set a default namespace (one time, persistent):**

```powershell
kubectl config current-context                                   # expect: kind-order-platform
kubectl config set-context --current --namespace=order-platform-k8s
```

After that you can drop `-n order-platform-k8s` from most commands.

**Optional `order` shortcut** (PowerShell only) — saves typing the namespace:

```powershell
Add-Content -Path $PROFILE -Value "`nfunction order { kubectl -n order-platform-k8s @args }"
. $PROFILE          # reload now (new terminals load it automatically)
order get pods      # == kubectl get pods -n order-platform-k8s
```

> Note: this is a **shell function**, not a kubectl feature. Typed directly it
> lasts for the session; added to `$PROFILE` it becomes permanent. It does not
> work in `cmd.exe` (that would need a `doskey` macro).

---

## Common kubectl commands (with results)

Mental model of any command:

```
kubectl  <verb>   <resource>[/name]   [-n namespace]   [flags]
         get      pods                -n order-platform-k8s   -w
         logs     deployment/order-service                    --follow
```

- **verbs:** `get` (list), `describe` (details + events), `logs`, `exec` (shell
  in), `delete`.
- **namespace (`-n`):** a virtual folder grouping resources. Everything for this
  platform lives in `order-platform-k8s`.

### Check the tool and which cluster you're on

```powershell
kubectl version --client            # local CLI version (does not contact cluster)
kubectl config current-context      # which cluster/user/namespace you're targeting
```

![kubectl version and current-context output](docs/images/cli-version-context.png)

### List the pods

```powershell
kubectl get pods -n order-platform-k8s          # snapshot
kubectl get pods -n order-platform-k8s -w       # -w/--watch: stream live changes (Ctrl+C to exit)
```

![kubectl get pods showing 14 running pods and 2 completed jobs](docs/images/cli-get-pods.png)

Reading it: `READY 1/1` = 1-of-1 containers ready · `RESTARTS 0` = stable ·
`Completed` = the one-time init Jobs (`kafka-init`, `toxiproxy-init`) that
finished and exited (that's why 16 total but only **14 Running**).

### Other everyday commands

| Command | Used for |
|---|---|
| `kubectl get nodes` | The cluster's node(s); should be `Ready`. |
| `kubectl get svc -n order-platform-k8s` | Services and their NodePorts (how you reach apps from `localhost`). |
| `kubectl get all -n order-platform-k8s` | Pods, services, deployments in one shot. |
| `kubectl describe pod <name> -n order-platform-k8s` | **Why** a pod is unhealthy — read the **Events** at the bottom. |
| `kubectl logs -n order-platform-k8s deployment/order-service --follow` | Stream a service's container logs (`-f` = keep tailing). |
| `kubectl exec -it -n order-platform-k8s deployment/order-service -- sh` | Open a debugging shell inside a pod. |
| `kubectl get namespaces` | List all namespaces. |
| `kubectl config get-contexts` | List clusters/contexts (`*` marks current). |

---

## Common questions (Q&A)

**Q: Is the request count per server or per service, and where is it stored — in
the API Gateway?**
The counter lives **inside each app pod** (an in-memory Micrometer counter at
`/actuator/prometheus`), so it is **per-pod**. It's also broken down per endpoint
by `uri`, `method`, and `status` (e.g. `POST /api/v1/orders`,
`GET /actuator/health/**`). Since each service runs **1 replica**, per-pod ==
per-service; with multiple replicas the dashboard's `application` filter **sums
across pods**. Storage is **Prometheus's own TSDB** (on the `prometheus-data`
volume) — **not** the API Gateway. The gateway just has its own separate counter.

**Q: How many pods are running, and where can I see them?**
**14 Running** components = 4 apps (`api-gateway`, `order-service`,
`inventory-service`, `notification-service`) + 10 infra (`postgres`, `kafka`,
`redis`, `prometheus`, `grafana`, `jaeger`, `mailpit`, `sms-provider`,
`whatsapp-provider`, `toxiproxy`). Plus **2 `Completed`** init Jobs (16 total).
See them via: `kubectl get pods -n order-platform-k8s`, Prometheus
`http://localhost:30090/targets`, the Grafana **Service Up** panel, or Docker
Desktop (which shows the single kind node container, not the pods inside it).

**Q: What is a "context"?**
A saved pointer to *which cluster + user + namespace* kubectl talks to. You have
`kind-order-platform` (this platform) and `docker-desktop`. `kubectl config
current-context` shows it; `use-context` switches it. The current context decides
where every command goes.

**Q: Do I have to set the namespace every time I open a terminal?**
**No.** `kubectl config set-context --current --namespace=...` is saved in
`~/.kube/config` and is **per-context and persistent** — it survives new
terminals and reboots. You can still override per command with `-n`.

**Q: What if I have other services / namespaces / clusters?**
The default namespace is **per-context**, so other clusters (contexts) are
unaffected. Reach other namespaces anytime with `-n <ns>` or everything with
`--all-namespaces` (`-A`). Orient yourself with `kubectl config get-contexts`,
`kubectl get ns`.

**Q: Can I give the namespace a short name like `order`?**
A namespace **can't be renamed in place** (the name is hard-coded across the
manifest and scripts). For less typing, use the **default namespace** trick above
and/or the PowerShell `order` function — both are set from the terminal.

**Q: `-w` does what?**
`-w` = `--watch`: after the initial snapshot it **stays open and streams live
changes** (status flips, restarts, new/deleted pods). It blocks the terminal;
press `Ctrl+C` to exit. On an idle cluster it just sits quietly — not a hang.

**Q (gotcha): "No resources found in ... namespace" — why?**
Almost always a **wrong namespace or context**, not "nothing is running."
kubectl doesn't validate the namespace name, so a typo like `order-platform-k8`
(missing the `s`) silently returns empty results. Double-check the exact name
`order-platform-k8s` and your current context.

![kubectl namespace typo returning No resources found](docs/images/cli-namespace-typo.png)

---

## Local endpoints

| Component | URL | Notes |
|---|---|---|
| API Gateway | http://localhost:30080 | Application entry point; `/actuator/health` |
| Grafana | http://localhost:30300 | `admin` / `local-grafana-password` |
| Prometheus | http://localhost:30090 | PromQL; `/targets` shows scrape health |
| Jaeger | http://localhost:30686 | Traces (apps not yet instrumented, so sparse) |
| Mailpit | http://localhost:30825 | Emails the platform "sent" |
| SMS mock | http://localhost:30911/__admin/requests | SMS provider call journal |
| WhatsApp mock | http://localhost:30912/__admin/requests | WhatsApp provider call journal |

> These are explicit **local-development** credentials and must never be reused in
> a deployed environment.
