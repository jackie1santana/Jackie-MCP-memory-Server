param(
    [string]$BaseUrl = "http://127.0.0.1:8080"
)

Write-Host "Checking actuator health at $BaseUrl/actuator/health ..."
$health = Invoke-RestMethod "$BaseUrl/actuator/health"
$health | ConvertTo-Json -Depth 10

Write-Host "Checking local runtime status at $BaseUrl/internal/status ..."
$status = Invoke-RestMethod "$BaseUrl/internal/status"
$status | ConvertTo-Json -Depth 10

