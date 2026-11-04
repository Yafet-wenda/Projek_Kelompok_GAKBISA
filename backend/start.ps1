# Menjalankan backend GAKBISA dengan satu perintah (dari folder mana pun):
#   .\run.cmd          database lokal (backend\.env)
#   .\run.cmd supa     database Supabase milik Nando (backend\.env.supabase)
# Untuk mode lokal, cluster PostgreSQL dev ikut dinyalakan kalau port-nya belum hidup.

param([ValidateSet('lokal', 'supa')][string]$Mode = 'lokal')

$ErrorActionPreference = 'Stop'

$backendDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $backendDir

$envFile = if ($Mode -eq 'supa') { '.env.supabase' } else { '.env' }
$envPath = Join-Path $backendDir $envFile
if (-not (Test-Path $envPath)) {
    Write-Host "backend\$envFile belum ada. Isinya sama seperti backend\.env.example, kredensialnya diambil dari Nando." -ForegroundColor Red
    exit 1
}

# Nilai env tidak boleh muncul di layar, jadi hanya key yang dipakai yang dibaca.
$cfg = @{}
foreach ($baris in Get-Content $envPath -Encoding UTF8) {
    if ($baris -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$') {
        $cfg[$matches[1]] = $matches[2].Trim()
    }
}

$dbHost = $cfg['DB_HOST']
$dbPort = $cfg['DB_PORT']

if (-not $dbHost -or $dbHost.StartsWith('<') -or $dbHost -like '*GANTI*') {
    Write-Host "DB_HOST di backend\$envFile belum diisi ($dbHost)." -ForegroundColor Red
    Write-Host 'Ambil connection string dari Nando: Supabase > Settings > Database > Connection string.'
    Write-Host 'Isi DB_HOST, DB_PASSWORD, lalu jalankan lagi.'
    exit 1
}

Write-Host ''
Write-Host 'GAKBISA backend'
Write-Host ("  Mode       : {0} (berkas {1})" -f $Mode, $envFile)
Write-Host ("  Basis data : {0}:{1}/{2}" -f $dbHost, $dbPort, $cfg['DB_NAME'])

$pgBin = 'C:\Program Files\PostgreSQL\16\bin'
$dataDir = 'C:\Users\Mahasiswa\tools\postgres-gakbisa\data'

if ($dbHost -in @('localhost', '127.0.0.1') -and $dbPort) {
    & "$pgBin\pg_isready.exe" -h 127.0.0.1 -p $dbPort *> $null
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  DB dev     : sudah hidup di 127.0.0.1:$dbPort"
    } elseif (Test-Path $dataDir) {
        Write-Host "  DB dev     : menyalakan cluster di port $dbPort ..."
        $logPath = Join-Path (Split-Path -Parent $dataDir) 'server.log'
        # pg_ctl di Windows tidak keluar selama postmaster hidup dan ia mewarisi handle
        # stdout pemanggilnya, jadi prosesnya dilepas lalu kesiapannya dipantau sendiri.
        $argLine = '-D "{0}" -o "-p {1}" -l "{2}" start' -f $dataDir, $dbPort, $logPath
        Start-Process -FilePath (Join-Path $pgBin 'pg_ctl.exe') -ArgumentList $argLine -WindowStyle Hidden
        $siap = $false
        for ($coba = 0; $coba -lt 40; $coba++) {
            & "$pgBin\pg_isready.exe" -h 127.0.0.1 -p $dbPort *> $null
            if ($LASTEXITCODE -eq 0) { $siap = $true; break }
            Start-Sleep -Milliseconds 500
        }
        if (-not $siap) {
            Write-Host "Cluster dev gagal menyala. Periksa isi $logPath" -ForegroundColor Red
            exit 1
        }
        Write-Host "  DB dev     : siap di 127.0.0.1:$dbPort"
    } else {
        Write-Host "Port $dbPort belum hidup dan cluster dev tidak ada di $dataDir." -ForegroundColor Red
        Write-Host 'Nyalakan database-mu sendiri dulu, atau baca README bagian Database lokal.'
        exit 1
    }
}

$env:GAKBISA_ENV = $envFile
Write-Host '  Backend    : memulai, http://localhost:8080 siap setelah beberapa detik (Ctrl+C untuk berhenti)'
& (Join-Path $backendDir 'mvnw.cmd') -q spring-boot:run
