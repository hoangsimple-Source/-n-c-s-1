# Clean build artifacts
$ErrorActionPreference = "Stop"
Set-Location -LiteralPath $PSScriptRoot

Write-Host "Cleaning build artifacts..." -ForegroundColor Cyan

$class_files = Get-ChildItem "bin" -Filter "*.class" -ErrorAction SilentlyContinue
if ($class_files) {
    Remove-Item $class_files -Force -ErrorAction SilentlyContinue
    Write-Host "✓ Removed $(($class_files | Measure-Object).Count) .class files" -ForegroundColor Green
} else {
    Write-Host "No class files found to clean"
}

$subdirs = Get-ChildItem "bin" -Directory -ErrorAction SilentlyContinue | Where-Object { $_.Name -notin @('.metadata', '.vscode', 'lib', 'GUIBuilder') }
if ($subdirs) {
    foreach ($dir in $subdirs) {
        $classes_in_dir = Get-ChildItem $dir -Filter "*.class" -Recurse -ErrorAction SilentlyContinue
        if ($classes_in_dir) {
            Remove-Item $classes_in_dir -Force -ErrorAction SilentlyContinue
            Write-Host "✓ Removed classes from $(($classes_in_dir | Measure-Object).Count) files in $($dir.Name)" -ForegroundColor Green
        }
    }
}

Write-Host "Clean complete!" -ForegroundColor Green
