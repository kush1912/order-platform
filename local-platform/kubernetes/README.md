# Local Kubernetes platform

This directory provides a Docker Desktop Kubernetes deployment that is
separate from the existing Docker Compose environment.

## Separation from Docker Compose

- Kubernetes resources use namespace `order-platform-k8s`.
- Compose files are unchanged.
- Kubernetes uses distinct host NodePorts.
- Kubernetes receives its own PersistentVolumeClaims and data.
- Removing the Kubernetes namespace does not delete Compose volumes.

Both environments can exist at the same time, but running both complete stacks
uses substantial CPU and memory.

## Prerequisites

1. Install and start Docker Desktop.
2. Open **Settings > Kubernetes**.
3. Enable Kubernetes and apply the change.
4. Wait for Docker Desktop to report that Kubernetes is running.
5. Confirm access:

```powershell
kubectl cluster-info
kubectl get nodes
```

The Docker Desktop node should report `Ready`.

### Standalone kind alternative

If Docker Desktop's built-in Kubernetes feature is unavailable, install kind:

```powershell
winget install --id Kubernetes.kind --exact
```

Create an equivalent local cluster backed by Docker Desktop:

```powershell
kind create cluster `
  --name order-platform `
  --config .\local-platform\kubernetes\kind-cluster.yaml
```

Build and load the local application images:

```powershell
$services = @(
  "api-gateway",
  "order-service",
  "inventory-service",
  "notification-service"
)

foreach ($service in $services) {
  docker build `
    --tag "order-platform/${service}:local" `
    --file ".\$service\deploy\docker\Dockerfile" `
    ".\$service"

  kind load docker-image `
    --name order-platform `
    "order-platform/${service}:local"
}
```

Then deploy with:

```powershell
.\local-platform\kubernetes\Deploy-LocalKubernetes.ps1 -SkipBuild
```

## Deploy

From the repository root:

```powershell
.\local-platform\kubernetes\Deploy-LocalKubernetes.ps1
```

The script:

1. Checks Docker and Kubernetes access.
2. Builds the four local application images.
3. Creates namespace `order-platform-k8s`.
4. Converts existing Compose configuration files into Kubernetes ConfigMaps.
5. Applies `order-platform.yaml`.
6. Waits for initialization Jobs and all Deployments.
7. Prints the Pods, Services, Jobs, and PersistentVolumeClaims.

Skip rebuilding unchanged application images:

```powershell
.\local-platform\kubernetes\Deploy-LocalKubernetes.ps1 -SkipBuild
```

## Inspect resources

```powershell
kubectl get all -n order-platform-k8s
kubectl get pvc -n order-platform-k8s
kubectl get pods -n order-platform-k8s -w
```

Inspect a Pod:

```powershell
kubectl describe pod -n order-platform-k8s `
  -l app.kubernetes.io/name=inventory-service
```

Follow application logs:

```powershell
kubectl logs -n order-platform-k8s `
  deployment/inventory-service `
  --follow
```

Open a debugging shell:

```powershell
kubectl exec -it -n order-platform-k8s `
  deployment/inventory-service `
  -- sh
```

## Local endpoints

| Component | URL |
|---|---|
| API Gateway | http://localhost:30080 |
| Grafana | http://localhost:30300 |
| Prometheus | http://localhost:30090 |
| Jaeger | http://localhost:30686 |
| Mailpit | http://localhost:30825 |
| Mailpit SMTP | localhost:31025 |
| SMS WireMock | http://localhost:30911 |
| WhatsApp WireMock | http://localhost:30912 |
| Toxiproxy | http://localhost:30474 |

Grafana uses `admin` / `local-grafana-password`.

## Run a request flow

```powershell
.\local-platform\kubernetes\Test-KubernetesFlow.ps1
```

The script:

1. Creates inventory through the Kubernetes API Gateway.
2. Places an order.
3. Waits for the Kafka inventory saga to confirm it.
4. Verifies the reserved inventory.
5. Publishes an SMS notification through the Kafka Pod.
6. Verifies the mock provider received the request.

## Remove

```powershell
.\local-platform\kubernetes\Remove-LocalKubernetes.ps1
```

Deleting the namespace also deletes its namespace-scoped resources and
PersistentVolumeClaims. The existing Docker Compose environment and volumes are
not affected.

Delete the standalone kind cluster with:

```powershell
kind delete cluster --name order-platform
```

## Important limitations

- This setup is intended for local learning, not production.
- The API Gateway, order, inventory, and notification services each run 2
  replicas and are autoscaled (2-5) by HorizontalPodAutoscalers; the stateful
  workloads (PostgreSQL, Kafka, Redis, etc.) use one replica.
- Kafka topics are created with 6 partitions, so each consumer group scales
  effectively up to the HPA ceiling of 5 replicas without idle consumers.
- The HorizontalPodAutoscalers require the Kubernetes metrics-server. Docker
  Desktop and kind do not ship it by default, so install it before expecting
  autoscaling:

  ```powershell
  kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml
  ```

  On Docker Desktop and kind the kubelet uses a self-signed certificate, so the
  metrics-server Deployment must also be patched to skip TLS verification:

  ```powershell
  kubectl patch deployment metrics-server -n kube-system --type=json `
    -p '[{"op":"add","path":"/spec/template/spec/containers/0/args/-","value":"--kubelet-insecure-tls"}]'
  ```

  Without metrics-server the autoscalers stay inert and each service simply
  keeps its 2 configured replicas. Check autoscaler status with
  `kubectl get hpa -n order-platform-k8s`.
- Secrets contain explicit local-development values.
- Services are exposed through fixed NodePorts for convenience.
- Local application images use `imagePullPolicy: Never`.
- PostgreSQL, Kafka, Redis, Prometheus, Grafana, and Mailpit use the cluster's
  default StorageClass (`hostpath` on Docker Desktop or `local-path` on kind).
- Jaeger is deployed, but the applications do not yet emit OpenTelemetry
  traces.
