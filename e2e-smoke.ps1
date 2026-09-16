# ============================================================================
# InspectIQ — end-to-end smoke test (Phase 6)
# ----------------------------------------------------------------------------
# Exercises the fully dockerised stack through public ports only:
#   * dashboard + nginx reverse proxy      (http://localhost:4200)
#   * ingestion-service                    (http://localhost:8081)
#   * analytics-service                    (http://localhost:8082)
#   * alert-service                        (http://localhost:8083)
#
# Flow: proxy smoke -> API-key enforcement -> create batch -> post 200
#       inspections (170 PASS / 30 FAIL) -> verify analytics -> verify batch
#       list -> alert breach -> recovery batch (200 PASS) -> alert recovery.
#
# Run with:  powershell -NoProfile -ExecutionPolicy Bypass -File .\e2e-smoke.ps1
#
# NOTE: JSON bodies are built with ConvertTo-Json so this file needs no
#       embedded double-quote characters at all (keeps the script portable
#       across shells/tooling that mangle quoting).
# ============================================================================

$ErrorActionPreference = 'Stop'
$ts = (Get-Date).ToString('yyyyMMdd-HHmmss')

$ing  = 'http://localhost:8081'
$ana  = 'http://localhost:8082'
$alt  = 'http://localhost:8083'
$dash = 'http://localhost:4200'

$key = 'inspectiq-dev-key'
$headers = @{ 'X-Device-Api-Key' = $key }

# ---------------------------------------------------------------- 1. proxy ---
Write-Output '=== 1. dashboard + reverse-proxy smoke ==='
$dashStatus = (Invoke-WebRequest -Uri ($dash + '/') -UseBasicParsing).StatusCode
Write-Output ('DASH_STATUS=' + $dashStatus)

$pIng = Invoke-RestMethod -Uri ($dash + '/api/ingestion/api/v1/batches?page=0&size=1')
$pAna = Invoke-RestMethod -Uri ($dash + '/api/analytics/api/v1/dashboard/summary')
$pAlt = Invoke-RestMethod -Uri ($dash + '/api/alerts/api/v1/alerts/status')
Write-Output ('PROXY_OK batches=' + $pIng.totalElements + ' totalUnits=' + $pAna.totalUnits + ' alertState=' + $pAlt.state)

# ------------------------------------------------------- 2. API-key guard ---
Write-Output '=== 2. API-key enforcement (expect 401 without key) ==='
$unauthBody = @{ batchCode = 'NO-AUTH-BATCH'; productName = 'x'; status = 'IN_PROGRESS' } | ConvertTo-Json -Compress
try {
    Invoke-RestMethod -Uri ($ing + '/api/v1/batches') -Method Post -ContentType 'application/json' -Body $unauthBody | Out-Null
    Write-Output 'NO_KEY_STATUS=NO_401_RAISED'
} catch {
    Write-Output ('NO_KEY_STATUS=' + [int]$_.Exception.Response.StatusCode)
}

# ------------------------------------------------------- 3. create batch ---
Write-Output '=== 3. create batch 1 (target: 170 PASS + 30 FAIL = 85% yield) ==='
$b1Body = @{ batchCode = ('E2E-BATCH-' + $ts); productName = 'PCB-A-mainboard'; status = 'IN_PROGRESS' } | ConvertTo-Json -Compress
$b1 = Invoke-RestMethod -Uri ($ing + '/api/v1/batches') -Method Post -Headers $headers -ContentType 'application/json' -Body $b1Body
Write-Output ('BATCH1_ID=' + $b1.id + ' code=' + $b1.batchCode)

# --------------------------------------------------- 4. post inspections ---
Write-Output '=== 4. post 200 inspections to batch 1 ==='
$failCodes = @('SOLDER_BRIDGE', 'MISALIGNMENT', 'SCRATCH')
for ($n = 0; $n -lt 200; $n++) {
    if ($n -lt 170) {
        $body = @{ batchId = $b1.id; result = 'PASS' } | ConvertTo-Json -Compress
    } else {
        $code = $failCodes[$n % 3]
        $body = @{ batchId = $b1.id; result = 'FAIL'; defectTypeCode = $code } | ConvertTo-Json -Compress
    }
    Invoke-RestMethod -Uri ($ing + '/api/v1/inspections') -Method Post -Headers $headers -ContentType 'application/json' -Body $body | Out-Null
}
Write-Output 'POSTED=200 inspections to batch 1'

# ------------------------------------------------- 5. analytics verification ---
Write-Output '=== 5. analytics verification (expect 85% yield, 30 fails) ==='
$sum = Invoke-RestMethod -Uri ($ana + '/api/v1/dashboard/summary')
Write-Output ('SUMMARY totalUnits=' + $sum.totalUnits + ' totalPasses=' + $sum.totalPasses + ' totalFails=' + $sum.totalFails + ' yieldPercent=' + $sum.yieldPercent + ' totalBatches=' + $sum.totalBatches)

$def = Invoke-RestMethod -Uri ($ana + '/api/v1/metrics/defects')
foreach ($d in $def) {
    Write-Output ('DEFECT code=' + $d.code + ' count=' + $d.count + ' percentOfFails=' + $d.percentOfFails)
}

$yld = Invoke-RestMethod -Uri ($ana + '/api/v1/metrics/yield?groupBy=day')
foreach ($y in $yld) {
    Write-Output ('YIELD_DAY ' + $y.bucket + ' units=' + $y.totalUnits + ' pass=' + $y.passCount + ' fail=' + $y.failCount + ' yield=' + $y.yieldPercent)
}

# ------------------------------------------------------------- 6. batch list ---
Write-Output '=== 6. ingestion batch list ==='
$lst = Invoke-RestMethod -Uri ($ing + '/api/v1/batches?page=0&size=10')
foreach ($b in $lst.content) {
    Write-Output ('BATCH ' + $b.batchCode + ' total=' + $b.totalInspections + ' pass=' + $b.passCount + ' fail=' + $b.failCount + ' passRate=' + $b.passRatePercent)
}

# ------------------------------------------------------------ 7. alert breach ---
Write-Output '=== 7. alert breach check (expect ALERT: 85 < 95) ==='
$st = Invoke-RestMethod -Uri ($alt + '/api/v1/alerts/check') -Method Post -ContentType 'application/json'
Write-Output ('ALERT state=' + $st.state + ' yield=' + $st.lastYieldPercent + ' sample=' + $st.lastSampleUnits + ' threshold=' + $st.yieldThresholdPercent)

# ------------------------------------------------------ 8. recovery + alert OK ---
Write-Output '=== 8. recovery: batch 2 with 200 PASS (expect alert returns to OK) ==='
$b2Body = @{ batchCode = ('E2E-RECOVERY-' + $ts); productName = 'PCB-B'; status = 'COMPLETED' } | ConvertTo-Json -Compress
$b2 = Invoke-RestMethod -Uri ($ing + '/api/v1/batches') -Method Post -Headers $headers -ContentType 'application/json' -Body $b2Body
Write-Output ('BATCH2_ID=' + $b2.id + ' code=' + $b2.batchCode)

for ($n = 0; $n -lt 200; $n++) {
    $body = @{ batchId = $b2.id; result = 'PASS' } | ConvertTo-Json -Compress
    Invoke-RestMethod -Uri ($ing + '/api/v1/inspections') -Method Post -Headers $headers -ContentType 'application/json' -Body $body | Out-Null
}
Write-Output 'POSTED=200 inspections to batch 2'

$st2 = Invoke-RestMethod -Uri ($alt + '/api/v1/alerts/check') -Method Post -ContentType 'application/json'
Write-Output ('ALERT_AFTER_RECOVERY state=' + $st2.state + ' yield=' + $st2.lastYieldPercent + ' sample=' + $st2.lastSampleUnits)

Write-Output '=== e2e smoke complete ==='