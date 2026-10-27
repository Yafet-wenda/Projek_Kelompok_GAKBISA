# Setup sekali jalan untuk PostgreSQL lokal (Opsi B):
#   1. cek password user "postgres"
#   2. buat database "gakbisa" bila belum ada
#   3. simpan password ke baris DB_PASSWORD di file .env
#
# Jalankan dari terminal apa pun (PowerShell/CMD/terminal VS Code):
#   cd "F:\wenda\Projek GAKBISA\backend"
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\setup-local-db.ps1
#
# Password diketik di prompt (tidak ditampilkan dan tidak tersimpan di riwayat terminal).

$ErrorActionPreference = 'Stop'

$psqlPath = 'C:\Program Files\PostgreSQL\16\bin\psql.exe'
$envPath = Join-Path $PSScriptRoot '.env'

if (-not (Test-Path $psqlPath)) {
    Write-Host "psql tidak ditemukan di: $psqlPath" -ForegroundColor Red
    Write-Host 'Kalau PostgreSQL-mu bukan versi 16, ubah jalur di baris $psqlPath script ini.'
    exit 1
}
if (-not (Test-Path $envPath)) {
    Write-Host "File .env tidak ditemukan di: $envPath" -ForegroundColor Red
    exit 1
}

$secure = Read-Host 'Password user postgres' -AsSecureString
$bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
try {
    $password = [Runtime.InteropServices.Marshal]::PtrToStringAuto($bstr)
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
}

if ([string]::IsNullOrEmpty($password)) {
    Write-Host 'Password kosong, batal.' -ForegroundColor Red
    exit 1
}

$env:PGPASSWORD = $password
try {
    $probe = & $psqlPath -h localhost -p 5432 -U postgres -tAc "select 1 from pg_database where datname='gakbisa'" 2>$null
    if ($LASTEXITCODE -ne 0) {
        Write-Host 'Gagal terhubung ke PostgreSQL. Biasanya karena password salah atau service-nya mati.' -ForegroundColor Red
        Write-Host 'Cek: tekan Win+R > services.msc > postgresql-x64-16 harus Running.'
        exit 1
    }

    if ($probe) {
        Write-Host 'Database gakbisa sudah ada, tidak dibuat ulang.'
    } else {
        & $psqlPath -h localhost -p 5432 -U postgres -c 'CREATE DATABASE gakbisa' | Out-Null
        if ($LASTEXITCODE -ne 0) {
            Write-Host 'Gagal membuat database gakbisa.' -ForegroundColor Red
            exit 1
        }
        Write-Host 'Database gakbisa berhasil dibuat.'
    }
} finally {
    Remove-Item Env:PGPASSWORD
}

# Baca eksplisit UTF-8: tanpa ini PowerShell 5.1 menyangkanya ANSI dan merusak karakter komentar.
$lines = @(Get-Content $envPath -Encoding UTF8)
$written = $false
for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -like 'DB_PASSWORD=*') {
        $lines[$i] = 'DB_PASSWORD=' + $password
        $written = $true
    }
}
if (-not $written) {
    Write-Host 'Baris DB_PASSWORD tidak ada di .env, tambahkan secara manual.' -ForegroundColor Red
    exit 1
}

# Tulis ulang tanpa BOM supaya tetap terbaca sebagai properties oleh Spring.
[System.IO.File]::WriteAllText($envPath, (($lines -join "`r`n") + "`r`n"), (New-Object System.Text.UTF8Encoding($false)))

Write-Host ''
Write-Host 'SELESAI. Tabel-tabel GAKBISA dibuat otomatis oleh Flyway saat aplikasi start.' -ForegroundColor Green
Write-Host 'Login demo setelah start: yafet@gakbisa.test / yafet123  (admin: admin@gakbisa.test / admin123)'
