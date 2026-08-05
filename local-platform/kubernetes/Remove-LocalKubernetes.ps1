[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$namespace = "order-platform-k8s"

kubectl delete namespace $namespace --ignore-not-found=true
if ($LASTEXITCODE -ne 0) {
    throw "Could not delete namespace $namespace."
}

Write-Host "Removed the local Kubernetes order platform and its namespace-scoped data." `
    -ForegroundColor Green
