# =============================================================================
# smoke-test-api.ps1 - Kiem tra nhanh API tren MongoDB (dung cho demo/bao cao)
#
#   powershell -NoProfile -ExecutionPolicy Bypass -File tools\smoke-test-api.ps1
#
# Yeu cau: MongoDB 27018 (tools\mongo-dev-start.bat) + app dang chay
#          (tools\run-app.bat) + da tao tai khoan demo
#          (tools\mongo-demo-accounts.bat)
#
# Kiem tra:
#   1. GET  /api/health                    -> app + MongoDB
#   2. GET  /api/health/detailed           -> ping {ping:1} toi MongoDB
#   3. GET  /api/books                     -> doc that tu collection books
#   4. POST /api/auth/login-jwt (SELLER)   -> access token + role SELLER
#   5. POST /api/auth/login-jwt (ADMIN)    -> access token + role ADMIN
#   6. GET  /api/seller/me/shop   (Bearer) -> shop cua seller (APPROVED)
#   7. GET  /api/books/seller/me  (Bearer) -> so sach thuoc shop
# =============================================================================
$ErrorActionPreference = "Stop"
$base        = "http://localhost:8080"
$sellerEmail = "shop_nha_nam@gmail.com"
$sellerPass  = "Nhanam123@"
$adminEmail  = "admin@gmail.com"
$adminPass   = "Admin123@"

function Step($n, $text) {
    Write-Host ""
    Write-Host "=== $n) $text" -ForegroundColor Cyan
}

function Login($email, $password) {
    $body = @{ username = $email; password = $password } | ConvertTo-Json
    try {
        return Invoke-RestMethod -Uri "$base/api/auth/login-jwt" -Method Post `
            -ContentType "application/json" -Body $body
    } catch {
        $resp = $_.Exception.Response
        $msg = (New-Object System.IO.StreamReader($resp.GetResponseStream())).ReadToEnd()
        Write-Host ("  LOGIN THAT BAI - HTTP " + [int]$resp.StatusCode + " : " + $msg) -ForegroundColor Red
        return $null
    }
}

Step 1 "GET /api/health"
(Invoke-RestMethod -Uri "$base/api/health") | ConvertTo-Json -Compress

Step 2 "GET /api/health/detailed (MongoDB ping)"
$detail = Invoke-RestMethod -Uri "$base/api/health/detailed"
Write-Host ("  database = " + ($detail.database | ConvertTo-Json -Compress))

Step 3 "GET /api/books (doc that tu collection books)"
$books = Invoke-RestMethod -Uri "$base/api/books?page=0&size=2"
Write-Host ("  totalElements = " + $books.totalElements +
    " | sach dau: " + $books.content[0].title +
    " | imageUrl = " + $books.content[0].imageUrl)

Step 4 "LOGIN SELLER ($sellerEmail)"
$seller = Login $sellerEmail $sellerPass
if ($seller) {
    Write-Host ("  OK | role = " + $seller.role + " | sellerId = " + $seller.sellerId +
        " | token = " + $seller.accessToken.Substring(0, 24) + "...") -ForegroundColor Green
}

Step 5 "LOGIN ADMIN ($adminEmail)"
$admin = Login $adminEmail $adminPass
if ($admin) {
    Write-Host ("  OK | role = " + $admin.role + " | userId = " + $admin.userId +
        " | token = " + $admin.accessToken.Substring(0, 24) + "...") -ForegroundColor Green
}

if ($seller) {
    $headers = @{ Authorization = "Bearer " + $seller.accessToken }

    Step 6 "GET /api/seller/me/shop (JWT seller)"
    try {
        $shop = Invoke-RestMethod -Uri "$base/api/seller/me/shop" -Headers $headers
        Write-Host ("  shop = " + $shop.shopName + " | slug = " + $shop.slug +
            " | status = " + $shop.approvalStatus) -ForegroundColor Green
    } catch {
        Write-Host ("  loi: " + $_.Exception.Message) -ForegroundColor Red
    }

    Step 7 "GET /api/books/seller/me (JWT seller)"
    try {
        $mine = Invoke-RestMethod -Uri "$base/api/books/seller/me?page=0&size=5" -Headers $headers
        Write-Host ("  tong sach cua shop = " + $mine.totalElements) -ForegroundColor Green
    } catch {
        Write-Host ("  loi: " + $_.Exception.Message) -ForegroundColor Red
    }
}

Write-Host ""
Write-Host "=== XONG ===" -ForegroundColor Cyan
