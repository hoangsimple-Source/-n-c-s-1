# Run LoginForm with automatic compilation
$ErrorActionPreference = "Stop"
Set-Location -LiteralPath $PSScriptRoot

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "   JAVA APPLICATION LAUNCHER" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Step 1: Compile
Write-Host "Step 1: Compiling Java sources..." -ForegroundColor Yellow
& "$PSScriptRoot\compile.ps1"

if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "✗ Compilation failed - cannot run" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "Step 2: Building runtime classpath..." -ForegroundColor Yellow
$libs = @()
Get-ChildItem "lib/*.jar" -ErrorAction SilentlyContinue | ForEach-Object { 
    $libs += $_.FullName 
}

$runtime_cp_parts = @("bin") + $libs
$runtime_cp = $runtime_cp_parts -join ";"
Write-Host "  ✓ Classpath configured ($(($libs.Count)) libraries)"

# Step 3: Verify LoginForm.class exists
Write-Host ""
Write-Host "Step 3: Verifying compiled classes..." -ForegroundColor Yellow
if (-not (Test-Path "bin/LoginForm.class")) {
    Write-Host "  ✗ FATAL: LoginForm.class not found!" -ForegroundColor Red
    exit 1
}
Write-Host "  ✓ LoginForm.class verified"

# Step 4: Start application
Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "  ▶ Starting application..." -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""

java -cp $runtime_cp LoginForm

$run_exit = $LASTEXITCODE
if ($run_exit -ne 0) {
    Write-Host ""
    Write-Host "Application exited with code: $run_exit" -ForegroundColor Yellow
}

