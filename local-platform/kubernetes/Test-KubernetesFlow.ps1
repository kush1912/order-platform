[CmdletBinding()]
param(
    [string]$GatewayBaseUrl = "http://localhost:30080",
    [string]$SmsProviderAdminUrl = "http://localhost:30911",
    [string]$Namespace = "order-platform-k8s"
)

$ErrorActionPreference = "Stop"
$flowId = [guid]::NewGuid().ToString("N").Substring(0, 12)
$sku = ("K8S-" + $flowId).ToUpper()
$startedAt = Get-Date

$inventoryBody = @{
    onHandQuantity = 25
    reason = "KUBERNETES_DEMO"
    sourceReference = $flowId
} | ConvertTo-Json

$inventoryResponse = Invoke-WebRequest `
    -Method Put `
    -Uri "$GatewayBaseUrl/api/v1/inventory/$sku" `
    -Headers @{"If-None-Match" = "*"} `
    -ContentType "application/json" `
    -Body $inventoryBody

if ($inventoryResponse.StatusCode -ne 201) {
    throw "Inventory creation returned $($inventoryResponse.StatusCode)."
}

$orderBody = @{
    customerId = [guid]::NewGuid()
    currency = "USD"
    items = @(
        @{
            sku = $sku
            quantity = 3
            unitPrice = 19.99
        }
    )
} | ConvertTo-Json -Depth 5

$order = Invoke-RestMethod `
    -Method Post `
    -Uri "$GatewayBaseUrl/api/v1/orders" `
    -Headers @{"Idempotency-Key" = "k8s-$flowId"} `
    -ContentType "application/json" `
    -Body $orderBody

$deadline = (Get-Date).AddSeconds(30)
do {
    Start-Sleep -Milliseconds 500
    $current = Invoke-RestMethod `
        -Uri "$GatewayBaseUrl/api/v1/orders/$($order.id)"
} while (
    $current.status -notin @("CONFIRMED", "REJECTED") -and
    (Get-Date) -lt $deadline
)

if ($current.status -ne "CONFIRMED") {
    throw "Order $($order.id) ended in status $($current.status)."
}

$inventory = Invoke-RestMethod -Uri "$GatewayBaseUrl/api/v1/inventory/$sku"
if ($inventory.reservedQuantity -ne 3) {
    throw "Expected 3 reserved units, got $($inventory.reservedQuantity)."
}

$clientReference = "k8s-order-$($order.id)"
$notification = @{
    eventId = [guid]::NewGuid()
    clientReference = $clientReference
    channel = "SMS"
    email = $null
    phoneNumber = "+15550001234"
    subject = $null
    message = "Kubernetes order $($order.id) confirmed"
    whatsappTemplateName = $null
} | ConvertTo-Json -Compress

$notification | kubectl exec `
    --stdin `
    --namespace $Namespace `
    deployment/kafka `
    -- `
    /opt/kafka/bin/kafka-console-producer.sh `
    --bootstrap-server kafka:9092 `
    --topic notifications.requested.v1
if ($LASTEXITCODE -ne 0) {
    throw "Could not publish the notification event."
}

$requestPattern = @{
    method = "POST"
    urlPath = "/v1/messages"
    bodyPatterns = @(
        @{
            matchesJsonPath = @{
                expression = "$.clientReference"
                equalTo = $clientReference
            }
        }
    )
} | ConvertTo-Json -Depth 8

$providerCalls = 0
$notificationDeadline = (Get-Date).AddSeconds(20)
do {
    Start-Sleep -Milliseconds 500
    $providerCalls = (Invoke-RestMethod `
        -Method Post `
        -Uri "$SmsProviderAdminUrl/__admin/requests/count" `
        -ContentType "application/json" `
        -Body $requestPattern).count
} while ($providerCalls -lt 1 -and (Get-Date) -lt $notificationDeadline)

if ($providerCalls -lt 1) {
    throw "The SMS provider did not receive the notification."
}

[pscustomobject]@{
    FlowId = $flowId
    OrderId = $order.id
    OrderStatus = $current.status
    Sku = $sku
    ReservedQuantity = $inventory.reservedQuantity
    SmsProviderCalls = $providerCalls
    DurationMilliseconds = [int]((Get-Date) - $startedAt).TotalMilliseconds
} | Format-List

Write-Host "Kubernetes request flow passed." -ForegroundColor Green
