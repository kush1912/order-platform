# Prod Issues Journal

A running log of production/deployment issues I hit, how I investigated them,
what I ruled out, the actual root cause, and the fix. Written so the next
person (or future me) can follow the reasoning, not just the answer.

---

## 1. order-service disappeared from Grafana after a deploy

**Date:** 2026-08-06
**Service:** order-service (Spring Boot 3.5, on kind k8s)
**Severity:** Medium — no user impact, but we were flying blind on the busiest service.

### Symptom
order-service had been reporting into Grafana fine all day. Then, right after a
deploy, its HTTP dashboards **flatlined around 6 PM** — data up to 6 PM, nothing
after. Every other service (api-gateway, inventory, notification) kept reporting
normally. This is the one service I most need visibility on, so I couldn't just
ignore it. The fact that data existed *before* 6 PM and stopped exactly at a
deploy told me this was a real regression, not a dashboard glitch.

### Investigation & what I ruled out
I worked down the pipeline, eliminating one layer at a time:

1. **Is the pod even up?** `kubectl get pods` — order-service 2/2 Running. Ruled out crash/restart loop.

2. **Is Prometheus scraping it?** Checked `/api/v1/targets` — `order-service`
   target was `up`, no scrape errors. So the scrape path was healthy.
   → Ruled out: bad target, wrong port, network policy, DNS.

3. **Does Prometheus have *any* order-service data?** Queried `up{job="order-service"}`
   over the last few hours — solid `1` the whole time, no gaps at 6 PM.
   → Ruled out: "scraping broke at 6 PM" theory. The pipeline never broke; it was
   specifically the `http_server_requests` metric that stopped.

4. **Is it a "no traffic" problem?** The pod restarted at the 6 PM deploy, and the
   `http_server_requests` counter resets on restart and only reappears after the
   first request. So maybe nobody had hit it since. I sent a request myself
   (a 404 + a health check) and re-checked.
   → **Still no `http_server_requests`.** Ruled out "no traffic."

5. **Is it a stale image?** The running image was fresh, but to be safe I rebuilt
   from current source and redeployed.
   → **Still no `http_server_requests`.** Ruled out stale artifact.

6. **Is the config different from the working services?** Diffed order-service's
   `application.yml` and `MonitoringConfiguration` (the `MeterFilter`) against
   inventory-service. **Identical** management/metrics blocks. inventory has the
   exact same cardinality-limit MeterFilter and *does* emit the metric.
   → Ruled out: config, MeterFilter, actuator exposure.

7. **Is the instrumentation actually wired at runtime?** Temporarily exposed all
   actuator endpoints and read `/actuator/conditions` and `/actuator/beans`:
   - `WebMvcObservationAutoConfiguration#webMvcObservationFilter` → **positive**
     (the `ServerHttpObservationFilter` bean exists).
   - The meter observation handler + ObservationRegistry beans all present.
   - Yet `tomcat_global_request_seconds_count` showed hundreds of requests served,
     while `/actuator/metrics/http.server.requests` returned **404**.
   So: the filter runs, requests flow, but the meter is never registered.

### Root cause
Scanning the startup logs, I found a cluster of warnings:

> `Bean 'net.devh...GrpcClientAutoConfiguration' is not eligible for getting
>  processed by all BeanPostProcessors ... currently created BeanPostProcessor
>  [grpcClientBeanPostProcessor]`

The 6 PM deploy was the one that shipped the new synchronous gRPC inventory
check — which made order-service the **only** service using the net.devh
**grpc-client** starter. Its `grpcClientBeanPostProcessor` gets instantiated very
early and eagerly drags a cascade of beans (including the `MeterRegistry`) up with
it — *before* Micrometer's observation/meter post-processing has run. The net
result: observation-based meters like `http.server.requests` are silently dropped,
while directly-registered meters (JVM, Tomcat, Kafka, gRPC) keep working. That's
exactly the pattern I saw, and it explains why data existed before the deploy and
vanished right after.

This is a known issue: **grpc-spring #859 / #992**. The trigger is
**constructor-injecting** the `@GrpcClient` stub. My `InventoryAvailabilityClient`
did exactly that.

### Fix
Switch the `@GrpcClient` stub from **constructor injection** to **field injection**
(the maintainer-recommended workaround). This defers the gRPC client init so it
no longer front-runs Micrometer.

```java
// Before — constructor injection (breaks http.server.requests)
public InventoryAvailabilityClient(@GrpcClient("inventory") ...Stub stub) { ... }

// After — field injection (metrics restored, gRPC metrics still work)
@GrpcClient("inventory")
private ...Stub availabilityStub;
```

Rebuilt → redeployed → verified:
```
http_server_requests_seconds_count{application="order-service",
  uri="/api/v1/orders/{orderId}",status="404",...} 3
```
Prometheus picked it up on the next scrape and the Grafana panels started filling in.

### Lessons / takeaways
- **Correlate the outage with a deploy first.** "Data until 6 PM, gone after" +
  a deploy at 6 PM immediately framed this as a regression, not a monitoring bug.
- **Debug down the pipeline, not randomly.** Scrape → storage → query → dashboard.
  Proving `up==1` with no gaps killed half the hypotheses in one shot.
- **"No data" can mean the metric was never registered, not that collection broke.**
  `tomcat_global_request_seconds_count` proving traffic + `http.server.requests`
  being 404 was the decisive clue.
- **BeanPostProcessor ordering is a real, silent footgun.** A library BPP that
  initializes eagerly can knock out unrelated auto-config (metrics, tracing).
- **Repo rule going forward:** never constructor-inject net.devh `@GrpcClient`
  stubs — use field/setter injection.
