# Compile Java project with robust error handling
$ErrorActionPreference = "Stop"
Set-Location -LiteralPath $PSScriptRoot

Write-Host "========================================" -ForegroundColor Magenta
Write-Host "   JAVA COMPILATION SCRIPT" -ForegroundColor Magenta
Write-Host "========================================" -ForegroundColor Magenta
Write-Host ""

# Step 1: Ensure bin directory exists
Write-Host "Step 1: Preparing build directory..." -ForegroundColor Cyan
if (-not (Test-Path "bin")) {
    New-Item -ItemType Directory -Path "bin" -Force | Out-Null
    Write-Host "  ✓ Created bin directory"
} else {
    Write-Host "  ✓ bin directory exists"
}

# Verify bin is writable
if (-not (Test-Path "bin")) {
    Write-Host "  ✗ FATAL: Cannot create or access bin directory!" -ForegroundColor Red
    exit 1
}

Get-ChildItem "bin" -Filter "*.class" -Recurse -ErrorAction SilentlyContinue |
    Remove-Item -Force -ErrorAction SilentlyContinue

# Step 2: Build classpath
Write-Host "Step 2: Building classpath..." -ForegroundColor Cyan
$libs = @()
$lib_files = Get-ChildItem "lib/*.jar" -ErrorAction SilentlyContinue
if ($lib_files) {
    $lib_files | ForEach-Object { 
        $libs += $_.FullName
        Write-Host "  ✓ Found: $($_.Name)"
    }
} else {
    Write-Host "  ! No JAR files found in lib/ (optional)" -ForegroundColor Yellow
}

$compile_cp_parts = @(".") + $libs
$compile_cp = $compile_cp_parts -join ";"
Write-Host "  Classpath set ($(($libs.Count)) libraries)" -ForegroundColor Gray

# Step 3: Find Java source files
Write-Host "Step 3: Locating Java source files..." -ForegroundColor Cyan
$java_files = Get-ChildItem -Path "." -Filter "*.java" -File -ErrorAction SilentlyContinue
if (-not $java_files) {
    Write-Host "  ✗ FATAL: No .java files found!" -ForegroundColor Red
    exit 1
}
Write-Host "  ✓ Found $(($java_files | Measure-Object).Count) Java files"

# Step 4: Compile
Write-Host "Step 4: Compiling Java sources..." -ForegroundColor Cyan
$source_paths = $java_files | ForEach-Object { $_.FullName }
$compile_output = javac -encoding UTF-8 -cp $compile_cp -d bin $source_paths 2>&1
$compile_exit = $LASTEXITCODE

if ($compile_exit -eq 0) {
    Write-Host "  ✓ Compilation successful!" -ForegroundColor Green
    
    # Verify classes were created
    $class_count = (Get-ChildItem bin -Filter "*.class" -Recurse -ErrorAction SilentlyContinue | Measure-Object).Count
    Write-Host ""
    Write-Host "Step 5: Verification..." -ForegroundColor Cyan
    Write-Host "  ✓ Generated $class_count .class files" -ForegroundColor Green
    
    if ($class_count -eq 0) {
        Write-Host "  ✗ WARNING: No .class files were generated!" -ForegroundColor Yellow
        exit 1
    }
    
    # List key compiled classes
    $key_classes = @("LoginForm.class", "MainMenuFrame.class", "UiTheme.class")
    Write-Host ""
    Write-Host "  Key classes:" -ForegroundColor Gray
    $key_classes | ForEach-Object {
        if (Test-Path "bin/$_") {
            Write-Host "    ✓ $_"
        } else {
            Write-Host "    ✗ $_ (MISSING)" -ForegroundColor Yellow
        }
    }
    
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Green
    Write-Host "  ✓ BUILD COMPLETE - READY TO RUN" -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Green
} else {
    Write-Host "  ✗ Compilation FAILED!" -ForegroundColor Red
    Write-Host ""
    Write-Host "Compiler output:" -ForegroundColor Yellow
    Write-Host $compile_output -ForegroundColor Red
    exit $compile_exit
}

