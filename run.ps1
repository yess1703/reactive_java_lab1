param([string]$JdkPath = $env:JAVA_HOME)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$OutputEncoding = [Console]::OutputEncoding
if (-not $JdkPath) {
    $candidate = Get-ChildItem -LiteralPath "$env:USERPROFILE\.jdks" -Directory -ErrorAction SilentlyContinue |
        Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'bin\javac.exe') } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if ($candidate) {
        $JdkPath = $candidate.FullName
    }
}
if (-not $JdkPath -or -not (Test-Path -LiteralPath (Join-Path $JdkPath 'bin\javac.exe'))) {
    throw 'Укажите путь к JDK 17 или новее: .\run.ps1 -JdkPath C:\path\to\jdk'
}
Push-Location $PSScriptRoot
try {
    # Общие исходники теперь содержат RxJava: общий скрипт готовит зависимости,
    # компилирует код и выполняет обе группы проверок без замеров JMH.
    & (Join-Path $PSScriptRoot 'benchmark.ps1') -JdkPath $JdkPath -SkipBenchmark
    Write-Host 'Запускаем первую лабораторную и сравниваем время расчётов.'
    & (Join-Path $JdkPath 'bin\java.exe') '-Dfile.encoding=UTF-8' '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' -cp out ru.music.Main
    if ($LASTEXITCODE -ne 0) { throw 'Не удалось выполнить замеры' }
} finally {
    Pop-Location
}
