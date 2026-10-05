# =============================================================================
# seed-demo-accounts.ps1 - Tao TAI KHOAN DEMO that (bcrypt that) bang API dang ky
#
#   Vi sao phai qua API: collection `users` luu passwordHash bang bcrypt
#   (work factor 12) - mongosh KHONG bam duoc bcrypt (thieu thu vien jbcrypt).
#   => Phai de Spring Boot bam ho qua dung luong dang ky:
#        POST /api/auth/otp/request -> {otp}   (SMTP chua config: OTP tra ve luon)
#        POST /api/auth/otp/verify  -> xac thuc OTP
#        POST /api/auth/register    -> tao user role BUYER
#
#   Tai khoan tao ra (role BUYER, mat khau Buyer123@):
#        buyer@gmail.com                        (tai khoan demo chinh)
#        buyer01@gmail.com .. buyerNN@gmail.com (de sinh du lieu giao dich)
#
#   Yeu cau: MongoDB 27018 + APP dang chay (tools\run-app.bat)
#   Chay   : tools\mongo-demo-accounts.bat
# =============================================================================

param(
    [string]$BaseUrl     = "http://localhost:8080",
    [string]$Password    = "Buyer123@",
    [int]   $ExtraBuyers = 20,
    [string]$AppLog      = "$PSScriptRoot\..\logs\app-run.log"
)

$ErrorActionPreference = "Continue"

function Post-Json {
    param([string]$Url, [hashtable]$Body)
    $json = $Body | ConvertTo-Json -Depth 5 -Compress
    return Invoke-RestMethod -Uri $Url -Method Post -ContentType "application/json" -Body $json
}

function Test-Login {
    param([string]$Email)
    try {
        $r = Post-Json -Url "$BaseUrl/api/auth/login-jwt" -Body @{ username = $Email; password = $Password }
        if ($r -and $r.accessToken) { return $r }
    } catch { }
    return $null
}

function Get-Otp {
    param([string]$Email)
    # 1) SMTP chua cau hinh -> API tra OTP ngay trong response (che do DEV)
    try {
        $r = Post-Json -Url "$BaseUrl/api/auth/otp/request" -Body @{ email = $Email }
        if ($r -and $r.otp) { return [string]$r.otp }
    } catch {
        Write-Host ("     (otp/request loi: " + $_.Exception.Message + ")") -ForegroundColor DarkYellow
    }
    # 2) Du phong: SMTP da cau hinh -> doc OTP tu log cua app ([OTP GENERATED])
    if (Test-Path $AppLog) {
        $pattern = "\[OTP GENERATED\] Email: " + [regex]::Escape($Email) + " \| OTP: "
        $line = Select-String -Path $AppLog -Pattern $pattern | Select-Object -Last 1
        if ($line -and ($line.Line -match "OTP: (\d{6})")) { return $Matches[1] }
    }
    return $null
}

# -----------------------------------------------------------------------------
Write-Host ""
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host " BOOKOM - Tao tai khoan demo (BUYER) qua API dang ky" -ForegroundColor Cyan
Write-Host ("   App       : " + $BaseUrl)
Write-Host ("   Mat khau  : " + $Password)
Write-Host ("   So luong  : 1 (buyer@gmail.com) + " + $ExtraBuyers + " (buyer01..buyer" + $ExtraBuyers + ")")
Write-Host "============================================================" -ForegroundColor Cyan

# Kiem tra app song
try {
    Invoke-RestMethod -Uri "$BaseUrl/api/health" -TimeoutSec 10 | Out-Null
    Write-Host "  [OK] App dang chay." -ForegroundColor Green
} catch {
    Write-Host ("  [LOI] Khong goi duoc " + $BaseUrl + "/api/health - hay chay tools\run-app.bat truoc.") -ForegroundColor Red
    exit 1
}

$emails = @("buyer@gmail.com")
for ($i = 1; $i -le $ExtraBuyers; $i++) { $emails += ("buyer{0:D2}@gmail.com" -f $i) }

$created = 0; $skipped = 0; $failed = 0
$rows = @()

foreach ($email in $emails) {
    $existing = Test-Login -Email $email
    if ($existing) {
        $skipped++
        $rows += [pscustomobject]@{ Email = $email; Status = "DA CO"; UserId = $existing.userId }
        Write-Host ("  [bo qua ] " + $email + " - da ton tai (userId=" + $existing.userId + ")")
        continue
    }

    $otp = Get-Otp -Email $email
    if (-not $otp) {
        $failed++
        Write-Host ("  [LOI    ] " + $email + " - khong lay duoc OTP") -ForegroundColor Red
        continue
    }

    try {
        Invoke-RestMethod -Uri "$BaseUrl/api/auth/otp/verify" -Method Post -ContentType "application/json" `
            -Body (@{ email = $email; otp = $otp } | ConvertTo-Json -Compress) | Out-Null
    } catch {
        $failed++
        Write-Host ("  [LOI    ] " + $email + " - xac thuc OTP that bai") -ForegroundColor Red
        continue
    }

    # favoriteCategoryIds: 2 danh muc that trong 20 danh muc cua DB (ho so cho dep)
    #   LUU Y: trong PowerShell dau ',' co do uu tien CAO HON '+' -> phai boc
    #   ngoac cho tung phan tu, neu khong se loi "op_Addition".
    $cats = @( (1 + ($created % 20)), (1 + (($created + 7) % 20)) )
    try {
        Invoke-RestMethod -Uri "$BaseUrl/api/auth/register" -Method Post -ContentType "application/json" `
            -Body (@{ username = $email; password = $Password; avatarUrl = ""; favoriteCategoryIds = $cats } |
                    ConvertTo-Json -Compress) | Out-Null
    } catch {
        Write-Host ("  [LOI    ] " + $email + " - dang ky that bai: " + $_.Exception.Message) -ForegroundColor Red
        $failed++
        continue
    }

    # Xac nhan that su dang nhap duoc (bcrypt that, hash $2a$12$)
    $check = Test-Login -Email $email
    if ($check) {
        $created++
        $rows += [pscustomobject]@{ Email = $email; Status = "MOI TAO"; UserId = $check.userId }
        Write-Host ("  [tao moi] " + $email + " -> userId=" + $check.userId +
                    " | role=" + $check.role) -ForegroundColor Green
    } else {
        $failed++
        Write-Host ("  [LOI    ] " + $email + " - dang ky xong nhung khong dang nhap duoc") -ForegroundColor Red
    }
}

Write-Host ""
Write-Host "------------------------------------------------------------"
Write-Host (" Ket qua: tao moi " + $created + " | da co " + $skipped + " | that bai " + $failed)
Write-Host "------------------------------------------------------------"
$rows | Format-Table -AutoSize

Write-Host "Buoc tiep theo: tools\mongo-demo-data.bat  (sinh don hang/review/gio hang cho cac buyer nay)"
Write-Host ""
