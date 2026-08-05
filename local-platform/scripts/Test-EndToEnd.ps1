[CmdletBinding()]
param(
    [string]$GatewayBaseUrl = "http://localhost:8080",
    [string]$SmsProviderAdminUrl = "http://localhost:9091",
    [string]$ToxiproxyAdminUrl = "http://localhost:8474"
)

$ErrorActionPreference = "Stop"
$composeFile = Join-Path $PSScriptRoot "..\docker-compose\docker-compose.yml"

function Assert-Equal {
    param(
        [object]$Actual,
        [object]$Expected,
        [string]$Message
    )

    if ($Actual -ne $Expected) {
        throw "$Message. Expected '$Expected', got '$Actual'."
    }
}

function Assert-True {
    param(
        [bool]$Condition,
        [string]$Message
    )

    if (-not $Condition) {
        throw $Message
    }
}

function Write-Pass {
    param([string]$Flow)
    Write-Host "[PASS] $Flow" -ForegroundColor Green
}

function New-Sku {
    param([string]$Prefix)
    return ($Prefix + "-" + [guid]::NewGuid().ToString("N").Substring(0, 12)).ToUpper()
}

function New-Inventory {
    param(
        [string]$Sku,
        [long]$Quantity,
        [string]$Reference
    )

    $body = @{
        onHandQuantity = $Quantity
        reason = "E2E_SETUP"
        sourceReference = $Reference
    } | ConvertTo-Json
    return Invoke-WebRequest `
        -Method Put `
        -Uri "$GatewayBaseUrl/api/v1/inventory/$Sku" `
        -Headers @{"If-None-Match" = "*"} `
        -ContentType "application/json" `
        -Body $body
}

function New-Order {
    param(
        [string]$Sku,
        [int]$Quantity,
        [decimal]$UnitPrice,
        [string]$IdempotencyKey
    )

    $body = @{
        customerId = [guid]::NewGuid()
        currency = "USD"
        items = @(
            @{
                sku = $Sku
                quantity = $Quantity
                unitPrice = $UnitPrice
            }
        )
    } | ConvertTo-Json -Depth 5
    return Invoke-WebRequest `
        -Method Post `
        -Uri "$GatewayBaseUrl/api/v1/orders" `
        -Headers @{"Idempotency-Key" = $IdempotencyKey} `
        -ContentType "application/json" `
        -Body $body
}

function Wait-OrderStatus {
    param(
        [string]$OrderId,
        [string[]]$TerminalStatuses,
        [int]$TimeoutSeconds = 30
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        Start-Sleep -Milliseconds 500
        $order = Invoke-RestMethod -Uri "$GatewayBaseUrl/api/v1/orders/$OrderId"
    } while ($order.status -notin $TerminalStatuses -and (Get-Date) -lt $deadline)

    if ($order.status -notin $TerminalStatuses) {
        throw "Order $OrderId remained in status $($order.status)."
    }
    return $order
}

function Get-HttpFailureStatus {
    param([scriptblock]$Request)

    try {
        & $Request | Out-Null
        return 0
    } catch {
        return [int]$_.Exception.Response.StatusCode
    }
}

function Send-KafkaMessage {
    param(
        [string]$Topic,
        [string]$Payload
    )

    $Payload | docker exec -i order-platform-kafka `
        /opt/kafka/bin/kafka-console-producer.sh `
        --bootstrap-server kafka:9092 `
        --topic $Topic
    if ($LASTEXITCODE -ne 0) {
        throw "Could not publish message to $Topic."
    }
}

function Add-SmsMapping {
    param([hashtable]$Mapping)

    Invoke-RestMethod `
        -Method Post `
        -Uri "$SmsProviderAdminUrl/__admin/mappings" `
        -ContentType "application/json" `
        -Body ($Mapping | ConvertTo-Json -Depth 12) | Out-Null
}

function Get-SmsRequestCount {
    param([string]$ClientReference)

    $pattern = @{
        method = "POST"
        urlPath = "/v1/messages"
        bodyPatterns = @(
            @{
                matchesJsonPath = @{
                    expression = "$.clientReference"
                    equalTo = $ClientReference
                }
            }
        )
    }
    return (Invoke-RestMethod `
        -Method Post `
        -Uri "$SmsProviderAdminUrl/__admin/requests/count" `
        -ContentType "application/json" `
        -Body ($pattern | ConvertTo-Json -Depth 8)).count
}

function Get-DeadLetterCount {
    param([string]$PayloadMarker)

    $escaped = $PayloadMarker.Replace("'", "''")
    $result = docker exec order-platform-postgres `
        psql -U notification_service -d notification_db -t -A `
        -c "SELECT count(*) FROM notification_dead_letters WHERE payload LIKE '%$escaped%';"
    if ($LASTEXITCODE -ne 0) {
        throw "Could not query notification dead letters."
    }
    return [int]($result | Select-Object -Last 1)
}

$unhealthy = docker compose -f $composeFile ps --format json |
    ConvertFrom-Json |
    Where-Object { $_.Health -and $_.Health -ne "healthy" }
if ($unhealthy) {
    throw "The local platform contains unhealthy services."
}

# 1. Place Order
$placeSku = New-Sku "PLACE"
New-Inventory $placeSku 100 "place-order" | Out-Null
$placedResponse = New-Order $placeSku 2 19.99 ("place-" + [guid]::NewGuid())
Assert-Equal $placedResponse.StatusCode 201 "Order creation status"
$placed = $placedResponse.Content | ConvertFrom-Json
$confirmed = Wait-OrderStatus $placed.id @("CONFIRMED", "REJECTED")
Assert-Equal $confirmed.status "CONFIRMED" "Placed order terminal status"
Write-Pass "1. Place Order"

# 2. Cancel Order
$beforeCancel = Invoke-RestMethod -Uri "$GatewayBaseUrl/api/v1/inventory/$placeSku"
$cancelResponse = Invoke-WebRequest `
    -Method Post `
    -Uri "$GatewayBaseUrl/api/v1/orders/$($placed.id)/cancellations" `
    -Headers @{"Idempotency-Key" = "cancel-" + [guid]::NewGuid()}
Assert-Equal $cancelResponse.StatusCode 202 "Cancellation acceptance status"
$cancelled = Wait-OrderStatus $placed.id @("CANCELLED")
$afterCancel = Invoke-RestMethod -Uri "$GatewayBaseUrl/api/v1/inventory/$placeSku"
Assert-Equal $cancelled.status "CANCELLED" "Cancelled order status"
Assert-Equal $beforeCancel.reservedQuantity 2 "Reserved quantity before cancellation"
Assert-Equal $afterCancel.reservedQuantity 0 "Reserved quantity after cancellation"
Write-Pass "2. Cancel Order"

# 3. Get Order
$retrieved = Invoke-RestMethod -Uri "$GatewayBaseUrl/api/v1/orders/$($placed.id)"
Assert-Equal $retrieved.id $placed.id "Retrieved order identifier"
Assert-Equal $retrieved.status "CANCELLED" "Retrieved order status"
Write-Pass "3. Get Order"

# 4. Update Inventory
$updateSku = New-Sku "UPDATE"
$createdInventory = New-Inventory $updateSku 20 "inventory-update"
$etag = [string]($createdInventory.Headers.ETag | Select-Object -First 1)
$updateBody = @{
    onHandQuantity = 35
    reason = "E2E_UPDATE"
    sourceReference = "inventory-update-2"
} | ConvertTo-Json
$updatedInventory = Invoke-WebRequest `
    -Method Put `
    -Uri "$GatewayBaseUrl/api/v1/inventory/$updateSku" `
    -Headers @{"If-Match" = $etag} `
    -ContentType "application/json" `
    -Body $updateBody
$staleStatus = Get-HttpFailureStatus {
    Invoke-WebRequest `
        -Method Put `
        -Uri "$GatewayBaseUrl/api/v1/inventory/$updateSku" `
        -Headers @{"If-Match" = $etag} `
        -ContentType "application/json" `
        -Body $updateBody
}
Assert-Equal $updatedInventory.StatusCode 200 "Inventory update status"
Assert-Equal $staleStatus 412 "Stale inventory update status"
Write-Pass "4. Update Inventory"

# 5. Bulk Order Placement
$bulkSku = New-Sku "BULK"
$stock = 12
$orderCount = 30
New-Inventory $bulkSku $stock "bulk-orders" | Out-Null
$requests = 1..$orderCount | ForEach-Object {
    @{
        customerId = [guid]::NewGuid().ToString()
        idempotencyKey = "bulk-" + [guid]::NewGuid()
    }
}
$bulkOrders = $requests | ForEach-Object -Parallel {
    $body = @{
        customerId = $_.customerId
        currency = "USD"
        items = @(
            @{
                sku = $using:bulkSku
                quantity = 1
                unitPrice = 5.00
            }
        )
    } | ConvertTo-Json -Depth 5
    Invoke-RestMethod `
        -Method Post `
        -Uri "$using:GatewayBaseUrl/api/v1/orders" `
        -Headers @{"Idempotency-Key" = $_.idempotencyKey} `
        -ContentType "application/json" `
        -Body $body
} -ThrottleLimit 30
$bulkStates = $bulkOrders | ForEach-Object {
    Wait-OrderStatus $_.id @("CONFIRMED", "REJECTED")
}
$bulkInventory = Invoke-RestMethod -Uri "$GatewayBaseUrl/api/v1/inventory/$bulkSku"
Assert-Equal @($bulkStates | Where-Object status -eq "CONFIRMED").Count $stock `
    "Confirmed bulk order count"
Assert-Equal @($bulkStates | Where-Object status -eq "REJECTED").Count ($orderCount - $stock) `
    "Rejected bulk order count"
Assert-Equal $bulkInventory.reservedQuantity $stock "Bulk reserved quantity"
Write-Pass "5. Bulk Order Placement"

# 6. Order Timeout and Circuit Breaker
$toxicPayload = @{
    name = "e2e-order-latency"
    type = "latency"
    stream = "downstream"
    toxicity = 1.0
    attributes = @{
        latency = 3500
        jitter = 0
    }
} | ConvertTo-Json -Compress -Depth 4
try {
    curl.exe --silent --fail `
        --header "Content-Type: application/json" `
        --data $toxicPayload `
        "$ToxiproxyAdminUrl/proxies/order-service/toxics" | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Could not add the order-service latency toxic."
    }

    $failureStatuses = 1..6 | ForEach-Object {
        Get-HttpFailureStatus {
            Invoke-WebRequest `
                -Uri "$GatewayBaseUrl/api/v1/orders/$($placed.id)" `
                -TimeoutSec 10
        }
    }
} finally {
    curl.exe --silent `
        --request DELETE `
        "$ToxiproxyAdminUrl/proxies/order-service/toxics/e2e-order-latency" | Out-Null
}
Assert-True ($failureStatuses -contains 504) "Gateway did not return a timeout response."
Assert-True ($failureStatuses -contains 503) "Gateway circuit breaker did not open."
Start-Sleep -Seconds 16
$recovered = Invoke-WebRequest -Uri "$GatewayBaseUrl/api/v1/orders/$($placed.id)"
Assert-Equal $recovered.StatusCode 200 "Gateway recovery status"
Write-Pass "6. Order Timeout and Circuit Breaker"

# 7. Idempotent Order Creation
$idempotencySku = New-Sku "IDEMPOTENCY"
New-Inventory $idempotencySku 10 "idempotency" | Out-Null
$idempotencyKey = "idempotency-" + [guid]::NewGuid()
$firstResponse = New-Order $idempotencySku 2 7.50 $idempotencyKey
$firstOrder = $firstResponse.Content | ConvertFrom-Json
$duplicateBody = @{
    customerId = $firstOrder.customerId
    currency = "USD"
    items = @(
        @{
            sku = $idempotencySku
            quantity = 2
            unitPrice = 7.50
        }
    )
} | ConvertTo-Json -Depth 5
$duplicateResponse = Invoke-WebRequest `
    -Method Post `
    -Uri "$GatewayBaseUrl/api/v1/orders" `
    -Headers @{"Idempotency-Key" = $idempotencyKey} `
    -ContentType "application/json" `
    -Body $duplicateBody
$duplicateOrder = $duplicateResponse.Content | ConvertFrom-Json
$conflictingBody = @{
    customerId = $firstOrder.customerId
    currency = "USD"
    items = @(
        @{
            sku = $idempotencySku
            quantity = 3
            unitPrice = 7.50
        }
    )
} | ConvertTo-Json -Depth 5
$conflictStatus = Get-HttpFailureStatus {
    Invoke-WebRequest `
        -Method Post `
        -Uri "$GatewayBaseUrl/api/v1/orders" `
        -Headers @{"Idempotency-Key" = $idempotencyKey} `
        -ContentType "application/json" `
        -Body $conflictingBody
}
Assert-Equal $duplicateResponse.StatusCode 200 "Duplicate order response status"
Assert-Equal ([string]($duplicateResponse.Headers."Idempotent-Replayed" | Select-Object -First 1)) `
    "true" "Duplicate replay header"
Assert-Equal $duplicateOrder.id $firstOrder.id "Duplicate order identifier"
Assert-Equal $conflictStatus 409 "Conflicting duplicate response status"
Write-Pass "7. Idempotent Order Creation"

# 8. Notification Retry, DLQ, and Poison Message
$retryReference = "retry-" + [guid]::NewGuid().ToString("N")
$retryScenario = "scenario-" + $retryReference
$retryMatch = @{
    method = "POST"
    urlPath = "/v1/messages"
    bodyPatterns = @(
        @{
            matchesJsonPath = @{
                expression = "$.clientReference"
                equalTo = $retryReference
            }
        }
    )
}
Add-SmsMapping @{
    priority = 1
    scenarioName = $retryScenario
    requiredScenarioState = "Started"
    newScenarioState = "FAILED_ONCE"
    request = $retryMatch
    response = @{status = 500}
}
Add-SmsMapping @{
    priority = 1
    scenarioName = $retryScenario
    requiredScenarioState = "FAILED_ONCE"
    newScenarioState = "RECOVERED"
    request = $retryMatch
    response = @{status = 500}
}
Add-SmsMapping @{
    priority = 1
    scenarioName = $retryScenario
    requiredScenarioState = "RECOVERED"
    request = $retryMatch
    response = @{
        status = 202
        headers = @{"Content-Type" = "application/json"}
        jsonBody = @{
            providerMessageId = "sms-retried"
            status = "ACCEPTED"
            clientReference = $retryReference
        }
    }
}
$retryEvent = @{
    eventId = [guid]::NewGuid()
    clientReference = $retryReference
    channel = "SMS"
    email = $null
    phoneNumber = "+15550001001"
    subject = $null
    message = "Retry notification"
    whatsappTemplateName = $null
} | ConvertTo-Json -Compress
Send-KafkaMessage "notifications.requested.v1" $retryEvent
$deadline = (Get-Date).AddSeconds(20)
do {
    Start-Sleep -Seconds 1
    $retryAttempts = Get-SmsRequestCount $retryReference
} while ($retryAttempts -lt 3 -and (Get-Date) -lt $deadline)
Assert-Equal $retryAttempts 3 "Recovering notification provider attempt count"
Assert-Equal (Get-DeadLetterCount $retryReference) 0 `
    "Recovered notification dead-letter count"

$dlqReference = "dlq-" + [guid]::NewGuid().ToString("N")
$dlqMatch = @{
    method = "POST"
    urlPath = "/v1/messages"
    bodyPatterns = @(
        @{
            matchesJsonPath = @{
                expression = "$.clientReference"
                equalTo = $dlqReference
            }
        }
    )
}
Add-SmsMapping @{
    priority = 1
    request = $dlqMatch
    response = @{status = 500}
}
$dlqEvent = @{
    eventId = [guid]::NewGuid()
    clientReference = $dlqReference
    channel = "SMS"
    email = $null
    phoneNumber = "+15550001002"
    subject = $null
    message = "Dead-letter notification"
    whatsappTemplateName = $null
} | ConvertTo-Json -Compress
$poisonMarker = "poison-" + [guid]::NewGuid().ToString("N")
Send-KafkaMessage "notifications.requested.v1" $dlqEvent
Send-KafkaMessage "notifications.requested.v1" "{$poisonMarker"
$deadline = (Get-Date).AddSeconds(20)
do {
    Start-Sleep -Seconds 1
    $providerDeadLetters = Get-DeadLetterCount $dlqReference
    $poisonDeadLetters = Get-DeadLetterCount $poisonMarker
} while (
    ($providerDeadLetters -lt 1 -or $poisonDeadLetters -lt 1) -and
    (Get-Date) -lt $deadline
)
Assert-Equal (Get-SmsRequestCount $dlqReference) 4 `
    "Failing notification provider attempt count"
Assert-Equal $providerDeadLetters 1 "Provider failure dead-letter count"
Assert-Equal $poisonDeadLetters 1 "Poison message dead-letter count"
Write-Pass "8. Notification Retry, DLQ, and Poison Message"

Write-Host "All end-to-end flows passed." -ForegroundColor Green
