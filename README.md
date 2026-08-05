# Order Platform

A production-oriented order-processing platform built with Spring Boot,
PostgreSQL, Kafka, transactional outboxes, idempotent consumers, Prometheus,
Grafana, and Docker Compose.

## Services

- **API Gateway** routes order and inventory APIs and applies circuit breaking.
- **Order Service** manages order creation, idempotency, cancellation, and saga
  state.
- **Inventory Service** manages stock, reservations, releases, and optimistic
  concurrency.
- **Notification Service** dispatches email, SMS, and WhatsApp notifications
  with retry and dead-letter handling.

## Local platform

The Compose environment includes PostgreSQL, Kafka, Redis, Mailpit, WireMock
providers, Toxiproxy, Prometheus, Grafana, Jaeger, and optional k6 load tests.

## Prerequisites

Install these tools before starting:

1. **Git** - https://git-scm.com/downloads
2. **Docker Desktop** - https://www.docker.com/products/docker-desktop/
3. **PowerShell 7** - https://github.com/PowerShell/PowerShell/releases

Docker provides Java, PostgreSQL, Kafka, and the other service runtimes, so they
do not need to be installed separately for the normal local workflow.

Recommended capacity is at least 4 logical CPUs, 8 GB of memory available to
Docker, and 10 GB of free disk space.

## Start the application

### 1. Clone the repository

```powershell
git clone https://github.com/kush1912/order-platform.git
Set-Location .\order-platform
```

### 2. Start Docker Desktop

Open Docker Desktop and wait until the Docker engine reports that it is
running. Confirm it from PowerShell:

```powershell
docker version
docker compose version
```

### 3. Build and start the complete platform

Run this command from the repository root:

```powershell
docker compose -f .\local-platform\docker-compose\docker-compose.yml up --build --detach --wait
```

The first start downloads base images, builds the four Spring Boot
applications, creates Kafka topics, and runs PostgreSQL migrations. It can take
several minutes.

### 4. Check container health

```powershell
docker compose -f .\local-platform\docker-compose\docker-compose.yml ps --all
```

The long-running containers should report `running` and configured health
checks should report `healthy`. `kafka-init` and `toxiproxy-init` should show
`Exited (0)` because they are successful one-time initialization jobs.

Check the API Gateway:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

Expected status:

```json
{"status":"UP"}
```

### 5. Run the end-to-end simulation

Use PowerShell 7:

```powershell
.\local-platform\scripts\Test-EndToEnd.ps1
```

The script simulates:

1. Order placement and inventory confirmation
2. Order cancellation and inventory release
3. Order retrieval
4. Inventory updates with ETag concurrency protection
5. Concurrent orders against limited stock
6. Gateway timeout and circuit-breaker behavior
7. Idempotent order replay and conflict detection
8. Notification retries, recovery, dead-lettering, and poison messages

Successful completion prints:

```text
All end-to-end flows passed.
```

### 6. Observe the system

Open Grafana while the simulation runs to watch request rate, errors, latency,
CPU, memory, JVM, Tomcat, and database-pool metrics.

## Local endpoints

| Component | URL | Local credentials/notes |
|---|---|---|
| API Gateway | http://localhost:8080 | Public application entry point |
| Grafana | http://localhost:3000 | `admin` / `local-grafana-password` |
| Prometheus | http://localhost:9090 | Metrics and PromQL |
| Prometheus targets | http://localhost:9090/targets | Application targets should be `UP` |
| Jaeger | http://localhost:16686 | Applications are not yet instrumented |
| Mailpit | http://localhost:8025 | Local email inbox |
| SMS WireMock | http://localhost:9091/__admin/requests | Mock SMS request journal |
| WhatsApp WireMock | http://localhost:9092/__admin/requests | Mock WhatsApp request journal |

Default credentials and passwords in the Compose configuration are explicitly
local-development values and must not be reused in deployed environments.

## Stop or reset

Stop containers while retaining named-volume data:

```powershell
docker compose -f .\local-platform\docker-compose\docker-compose.yml down
```

Completely remove containers and local volume data:

```powershell
docker compose -f .\local-platform\docker-compose\docker-compose.yml down --volumes
```

The second command permanently deletes local PostgreSQL, Kafka, Prometheus,
Grafana, Redis, and Mailpit volume data.

## Documentation

- [Docker Desktop and Order Platform Notes](DOCKER-DESKTOP-NOTES.md)
- [Load-test guide](local-platform/load-tests/README.md)
