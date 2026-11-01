param(
    [string]$JdkPath = $env:JAVA_HOME,
    [switch]$SkipBenchmark
)
$ErrorActionPreference = 'Stop'
& (Join-Path $PSScriptRoot 'benchmark.ps1') -JdkPath $JdkPath -Lab3 -SkipBenchmark:$SkipBenchmark
