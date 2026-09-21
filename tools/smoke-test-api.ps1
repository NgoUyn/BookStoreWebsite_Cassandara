# =============================================================================
# smoke-test-api.ps1 - Kiem tra nhanh API tren MongoDB (dung cho demo/bao cao)
#
#   powershell -NoProfile -ExecutionPolicy Bypass -File tools\smoke-test-api.ps1
#
# Yeu cau: MongoDB 27018 (tools\mongo-dev-start.bat) + app dang chay
#          (tools\run-app.bat)
#
# Kiem tra:
#   1. GET  /api/health            -> app + ket noi MongoDB
#   2. GET  /api/health/detailed   -> ping {ping:1} toi MongoDB
#   3. GET  /api/books             -> doc that tu collection books (JSON contract)
#   4. POST /api/auth/otp/request  -> sinh OTP
#   5. POST /api/auth/otp/verify   -> xac thuc OTP
#   6. POST /api/auth/register     -> GHI that vao MongoDB (id do counters cap)
# =============================================================================
$ErrorActionPreference = "Stop"
$base = "http://localhost:8080"
$email = "smoke.test@example.com"
$avatar = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAAC0lEQVR42mP8/x8AAwMCAO+ip1sAAAAASUVORK5CYII="

Write-Host "=== 1) GET /api/health"
$health = Invoke-RestMethod -Uri "$base/api/health" -Method Get
Write-Host ($health | ConvertTo-Json -Compress)

Write-Host "=== 2) GET /api/health/detailed"
$detail = Invoke-RestMethod -Uri "$base/api/health/detailed" -Method Get
Write-Host ("database = " + ($detail.database | ConvertTo-Json -Compress))

Write-Host "=== 3) GET /api/books (doc that tu MongoDB)"
$books = Invoke-RestMethod -Uri "$base/api/books?page=0&size=2" -Method Get
Write-Host ("totalElements = " + $books.totalElements + " | sach dau: " + $books.content[0].title +
    " | imageUrl = " + $books.content[0].imageUrl)

Write-Host "=== 4) POST /api/auth/otp/request"
$otpResp = Invoke-RestMethod -Uri "$base/api/auth/otp/request" -Method Post `
    -ContentType "application/json" -Body (@{ email = $email } | ConvertTo-Json)
$otp = $otpResp.otp
Write-Host ("OTP = " + $otp)
if (-not $otp) { throw "Khong lay duoc OTP (khi da cau hinh SMTP thi OTP duoc gui qua email)" }

Write-Host "=== 5) POST /api/auth/otp/verify"
$verify = Invoke-RestMethod -Uri "$base/api/auth/otp/verify" -Method Post `
    -ContentType "application/json" -Body (@{ email = $email; otp = $otp } | ConvertTo-Json)
Write-Host $verify

Write-Host "=== 6) POST /api/auth/register (ghi vao MongoDB -> Long id tu counters)"
$body = @{
    username            = $email
    password            = "Test1234!"
    avatarUrl           = $avatar
    favoriteCategoryIds = @(1, 2)
} | ConvertTo-Json
try {
    $reg = Invoke-WebRequest -Uri "$base/api/auth/register" -Method Post `
        -ContentType "application/json" -Body $body -UseBasicParsing
    Write-Host ("HTTP " + $reg.StatusCode + " - " + $reg.Content)
} catch {
    $resp = $_.Exception.Response
    Write-Host ("HTTP " + [int]$resp.StatusCode)
    (New-Object System.IO.StreamReader($resp.GetResponseStream())).ReadToEnd()
}
