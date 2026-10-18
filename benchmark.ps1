param(
    [string]$JdkPath = $env:JAVA_HOME,
    [switch]$SkipBenchmark
)

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
    $javacOnPath = Get-Command javac -ErrorAction SilentlyContinue
    if ($javacOnPath) {
        $JdkPath = Split-Path (Split-Path $javacOnPath.Source)
    }
}
if (-not $JdkPath -or -not (Test-Path -LiteralPath (Join-Path $JdkPath 'bin\javac.exe'))) {
    throw 'Укажите путь к JDK 17 или новее: .\benchmark.ps1 -JdkPath C:\path\to\jdk'
}

$jars = @(
    @{ Name = 'jmh-core-1.37.jar'; Url = 'https://repo1.maven.org/maven2/org/openjdk/jmh/jmh-core/1.37/jmh-core-1.37.jar' },
    @{ Name = 'jmh-generator-annprocess-1.37.jar'; Url = 'https://repo1.maven.org/maven2/org/openjdk/jmh/jmh-generator-annprocess/1.37/jmh-generator-annprocess-1.37.jar' },
    @{ Name = 'jopt-simple-5.0.4.jar'; Url = 'https://repo1.maven.org/maven2/net/sf/jopt-simple/jopt-simple/5.0.4/jopt-simple-5.0.4.jar' },
    @{ Name = 'commons-math3-3.6.1.jar'; Url = 'https://repo1.maven.org/maven2/org/apache/commons/commons-math3/3.6.1/commons-math3-3.6.1.jar' }
)

Push-Location $PSScriptRoot
try {
    Write-Host 'Этап 1 из 5. Скачиваем JMH, если библиотек ещё нет.'
    New-Item -ItemType Directory -Force -Path 'lib' | Out-Null
    $ProgressPreference = 'SilentlyContinue'
    foreach ($jar in $jars) {
        $target = Join-Path 'lib' $jar.Name
        if (-not (Test-Path -LiteralPath $target)) {
            Invoke-WebRequest -UseBasicParsing -Uri $jar.Url -OutFile $target
        }
    }

    Write-Host 'Этап 2 из 5. Компилируем программу и проверки.'
    New-Item -ItemType Directory -Force -Path 'out' | Out-Null
    $sources = Get-ChildItem -Path 'src\main\java', 'src\test\java' -Recurse -Filter '*.java' |
        Select-Object -ExpandProperty FullName
    & (Join-Path $JdkPath 'bin\javac.exe') --release 17 -encoding UTF-8 -d out $sources
    if ($LASTEXITCODE -ne 0) { throw 'Не удалось скомпилировать программу' }

    Write-Host 'Этап 3 из 5. Запускаем автоматические проверки.'
    & (Join-Path $JdkPath 'bin\java.exe') '-Dfile.encoding=UTF-8' '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' -cp out ru.music.StatisticsTest
    if ($LASTEXITCODE -ne 0) { throw 'Проверки завершились с ошибкой' }

    Write-Host 'Этап 4 из 5. Компилируем замеры JMH.'
    $classpath = (@('out') + ($jars | ForEach-Object { Join-Path 'lib' $_.Name })) -join ';'
    $processorPath = @(
        (Join-Path 'lib' 'jmh-generator-annprocess-1.37.jar'),
        (Join-Path 'lib' 'jmh-core-1.37.jar')
    ) -join ';'
    $benchmarks = Get-ChildItem -Path 'src\benchmark\java' -Recurse -Filter '*.java' |
        Select-Object -ExpandProperty FullName
    & (Join-Path $JdkPath 'bin\javac.exe') --release 17 -encoding UTF-8 -proc:full `
        -cp $classpath -processorpath $processorPath `
        -processor org.openjdk.jmh.generators.BenchmarkProcessor `
        -d out $benchmarks
    if ($LASTEXITCODE -ne 0) { throw 'Не удалось скомпилировать замеры JMH' }

    if ($SkipBenchmark) {
        Write-Host 'Замеры пропущены.'
        return
    }

    Write-Host 'Этап 5 из 5. Запускаем JMH и ищем размер, где последовательный и параллельный стримы сравниваются по времени.'
    & (Join-Path $JdkPath 'bin\java.exe') '-Dfile.encoding=UTF-8' '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' -cp $classpath ru.music.benchmark.BenchmarkMain
    if ($LASTEXITCODE -ne 0) { throw 'Не удалось выполнить замеры JMH' }
} finally {
    Pop-Location
}
