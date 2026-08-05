[CmdletBinding()]
param(
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
$namespace = "order-platform-k8s"
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$manifest = Join-Path $PSScriptRoot "order-platform.yaml"
$composeConfigRoot = Join-Path $repositoryRoot "local-platform\docker-compose"

function Invoke-Native {
    param(
        [string]$Command,
        [string[]]$Arguments
    )

    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$Command failed with exit code $LASTEXITCODE."
    }
}

function Set-ConfigMap {
    param(
        [string]$Name,
        [string[]]$Sources
    )

    $arguments = @(
        "create",
        "configmap",
        $Name,
        "--namespace",
        $namespace,
        "--dry-run=client",
        "--output=yaml"
    ) + $Sources

    $yaml = & kubectl @arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Could not generate ConfigMap $Name."
    }
    $yaml | kubectl apply -f -
    if ($LASTEXITCODE -ne 0) {
        throw "Could not apply ConfigMap $Name."
    }
}

Invoke-Native "docker" @("info")

$currentContext = kubectl config current-context 2>$null
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($currentContext)) {
    throw "No Kubernetes context is configured. Enable Kubernetes in Docker Desktop first."
}
Invoke-Native "kubectl" @("cluster-info")

if (-not $SkipBuild) {
    foreach ($service in @(
        "api-gateway",
        "order-service",
        "inventory-service",
        "notification-service"
    )) {
        $serviceRoot = Join-Path $repositoryRoot $service
        Invoke-Native "docker" @(
            "build",
            "--tag",
            "order-platform/${service}:local",
            "--file",
            (Join-Path $serviceRoot "deploy\docker\Dockerfile"),
            $serviceRoot
        )
    }
}

$namespaceYaml = kubectl create namespace $namespace --dry-run=client --output=yaml
if ($LASTEXITCODE -ne 0) {
    throw "Could not generate namespace $namespace."
}
$namespaceYaml | kubectl apply -f -
if ($LASTEXITCODE -ne 0) {
    throw "Could not apply namespace $namespace."
}

$postgresInit = Join-Path $composeConfigRoot `
    "postgres\init\01-create-service-databases.sql"
$prometheusConfig = Join-Path $composeConfigRoot `
    "prometheus\prometheus.yml"
$prometheusRules = Join-Path $composeConfigRoot `
    "prometheus\rules\service-recording-rules.yml"
$grafanaDatasource = Join-Path $composeConfigRoot `
    "grafana\provisioning\datasources\prometheus.yml"
$grafanaDashboardProvider = Join-Path $composeConfigRoot `
    "grafana\provisioning\dashboards\dashboards.yml"
$grafanaDashboard = Join-Path $composeConfigRoot `
    "grafana\dashboards\service-overview.json"
$smsMappings = Join-Path $composeConfigRoot "sms-provider\mappings"
$whatsappMappings = Join-Path $composeConfigRoot "whatsapp-provider\mappings"

Set-ConfigMap "postgres-init" @(
    "--from-file=01-create-service-databases.sql=$postgresInit"
)
Set-ConfigMap "prometheus-config" @(
    "--from-file=prometheus.yml=$prometheusConfig"
)
Set-ConfigMap "prometheus-rules" @(
    "--from-file=service-recording-rules.yml=$prometheusRules"
)
Set-ConfigMap "grafana-datasource" @(
    "--from-file=prometheus.yml=$grafanaDatasource"
)
Set-ConfigMap "grafana-dashboard-provider" @(
    "--from-file=dashboards.yml=$grafanaDashboardProvider"
)
Set-ConfigMap "grafana-dashboard" @(
    "--from-file=service-overview.json=$grafanaDashboard"
)
Set-ConfigMap "sms-provider-mappings" @(
    "--from-file=$smsMappings"
)
Set-ConfigMap "whatsapp-provider-mappings" @(
    "--from-file=$whatsappMappings"
)

Invoke-Native "kubectl" @(
    "delete",
    "job",
    "kafka-init",
    "toxiproxy-init",
    "--namespace",
    $namespace,
    "--ignore-not-found=true"
)
Invoke-Native "kubectl" @("apply", "--filename", $manifest)
Invoke-Native "kubectl" @(
    "wait",
    "--namespace",
    $namespace,
    "--for=condition=complete",
    "job/kafka-init",
    "--timeout=300s"
)
Invoke-Native "kubectl" @(
    "wait",
    "--namespace",
    $namespace,
    "--for=condition=complete",
    "job/toxiproxy-init",
    "--timeout=600s"
)
Invoke-Native "kubectl" @(
    "rollout",
    "status",
    "deployment",
    "--selector=app.kubernetes.io/part-of=order-platform",
    "--namespace",
    $namespace,
    "--timeout=600s"
)

kubectl get pods,services,persistentvolumeclaims,jobs --namespace $namespace

Write-Host ""
Write-Host "Kubernetes order platform is ready." -ForegroundColor Green
Write-Host "API Gateway: http://localhost:30080"
Write-Host "Grafana:     http://localhost:30300"
Write-Host "Prometheus:  http://localhost:30090"
Write-Host "Jaeger:      http://localhost:30686"
Write-Host "Mailpit:     http://localhost:30825"
Write-Host "Mailpit SMTP localhost:31025"
Write-Host "SMS mock:    http://localhost:30911"
Write-Host "WhatsApp:    http://localhost:30912"
Write-Host "Toxiproxy:   http://localhost:30474"
