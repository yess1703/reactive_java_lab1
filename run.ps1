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
    Write-Host 'Этап 1 из 3. Компилируем исходники Java и проверки.'
    New-Item -ItemType Directory -Force -Path 'out' | Out-Null
    $sources = Get-ChildItem -Path 'src\main\java','src\test\java' -Recurse -Filter '*.java' |
        Select-Object -ExpandProperty FullName
    & (Join-Path $JdkPath 'bin\javac.exe') --release 17 -encoding UTF-8 -d out $sources
    if ($LASTEXITCODE -ne 0) { throw 'Не удалось скомпилировать программу' }
    Write-Host 'Этап 2 из 3. Запускаем автоматические проверки.'
    & (Join-Path $JdkPath 'bin\java.exe') '-Dfile.encoding=UTF-8' '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' -cp out ru.music.StatisticsTest
    if ($LASTEXITCODE -ne 0) { throw 'Проверки завершились с ошибкой' }
    Write-Host 'Этап 3 из 3. Запускаем программу и сравниваем время расчётов.'
    & (Join-Path $JdkPath 'bin\java.exe') '-Dfile.encoding=UTF-8' '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' -cp out ru.music.Main
    if ($LASTEXITCODE -ne 0) { throw 'Не удалось выполнить замеры' }
} finally {
    Pop-Location
}
