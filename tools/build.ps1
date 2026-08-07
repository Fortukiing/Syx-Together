[CmdletBinding()]
param(
    [string]$GameJar = $env:SONGS_OF_SYX_JAR,
    [switch]$SkipPackage
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$build = Join-Path $root 'build'

if ([string]::IsNullOrWhiteSpace($GameJar)) {
    $GameJar = 'C:\Program Files (x86)\Steam\steamapps\common\Songs of Syx\SongsOfSyx.jar'
}
if (-not (Test-Path -LiteralPath $GameJar -PathType Leaf)) {
    throw "SongsOfSyx.jar was not found. Set SONGS_OF_SYX_JAR or pass -GameJar."
}
$GameJar = (Resolve-Path -LiteralPath $GameJar).Path

$javacCommand = Get-Command javac -ErrorAction SilentlyContinue
if ($null -eq $javacCommand) {
    throw 'javac was not found. Install JDK 21 or newer and add it to PATH.'
}
$javac = $javacCommand.Source
$jarCandidate = Join-Path (Split-Path -Parent $javac) 'jar.exe'
$jar = if (Test-Path -LiteralPath $jarCandidate) { $jarCandidate } else { (Get-Command jar -ErrorAction Stop).Source }

if (Test-Path -LiteralPath $build) {
    $resolvedBuild = (Resolve-Path -LiteralPath $build).Path
    $expectedPrefix = $root.TrimEnd('\') + '\'
    if (-not $resolvedBuild.StartsWith($expectedPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to clean a build folder outside the repository: $resolvedBuild"
    }
    Remove-Item -LiteralPath $resolvedBuild -Recurse -Force
}

$classesPath = Join-Path $build 'classes'
$testClassesPath = Join-Path $build 'test-classes'
New-Item -ItemType Directory -Path $classesPath -Force | Out-Null
New-Item -ItemType Directory -Path $testClassesPath -Force | Out-Null

$productionSources = @(
    Get-ChildItem -LiteralPath (Join-Path $root 'src\main\java') -Recurse -Filter '*.java'
    Get-ChildItem -LiteralPath (Join-Path $root 'src\game-overrides\java') -Recurse -Filter '*.java'
) | ForEach-Object FullName
if ($productionSources.Count -eq 0) {
    throw 'No production Java sources were found.'
}

$compileArgs = @('--release', '21', '-encoding', 'UTF-8', '-cp', $GameJar, '-d', $classesPath) + $productionSources
& $javac @compileArgs
if ($LASTEXITCODE -ne 0) { throw "Production compilation failed with exit code $LASTEXITCODE." }

$testSources = Get-ChildItem -LiteralPath (Join-Path $root 'src\test\java') -Recurse -Filter '*.java' |
    ForEach-Object FullName
$testSupportSources = @(
    (Join-Path $root 'src\main\java\coopmod\CoopProtocol.java'),
    (Join-Path $root 'src\main\java\coopmod\CoopSaveTransfer.java')
) + $testSources
$testCompileArgs = @('--release', '21', '-encoding', 'UTF-8', '-cp', $GameJar, '-d', $testClassesPath) + $testSupportSources
& $javac @testCompileArgs
if ($LASTEXITCODE -ne 0) { throw "Test compilation failed with exit code $LASTEXITCODE." }

$gameJava = Join-Path (Split-Path -Parent $GameJar) 'jre\bin\java.exe'
$java = if (Test-Path -LiteralPath $gameJava) { $gameJava } else { (Get-Command java -ErrorAction Stop).Source }
$runtimeClasspath = $testClassesPath + [IO.Path]::PathSeparator + $GameJar
foreach ($testClass in @('coopmod.CoopProtocolTest', 'coopmod.CoopSaveTransferTest')) {
    & $java -cp $runtimeClasspath $testClass
    if ($LASTEXITCODE -ne 0) { throw "$testClass failed with exit code $LASTEXITCODE." }
}

$wrongVersions = New-Object System.Collections.Generic.List[string]
Get-ChildItem -LiteralPath $classesPath -Recurse -Filter '*.class' | ForEach-Object {
    $stream = [IO.File]::OpenRead($_.FullName)
    try {
        $header = New-Object byte[] 8
        if ($stream.Read($header, 0, 8) -ne 8) { throw "Invalid class file: $($_.FullName)" }
        $major = ([int]$header[6] -shl 8) -bor [int]$header[7]
        if ($major -ne 65) { $wrongVersions.Add("$($_.FullName) -> $major") }
    } finally {
        $stream.Dispose()
    }
}
if ($wrongVersions.Count -gt 0) {
    throw "Classes were not compiled for Java 21:`n$($wrongVersions -join [Environment]::NewLine)"
}

if ($SkipPackage) {
    Write-Host 'Compilation and tests passed. Packaging was skipped.'
    Write-Host "Production classes: $((Get-ChildItem -LiteralPath $classesPath -Recurse -Filter '*.class').Count)"
    return
}

$distRoot = New-Item -ItemType Directory -Path (Join-Path $build 'dist\Syx Together\V71\script') -Force
$jarPath = Join-Path $distRoot.FullName 'syx-together.jar'
& $jar --create --file $jarPath -C $classesPath .
if ($LASTEXITCODE -ne 0) { throw "JAR creation failed with exit code $LASTEXITCODE." }

$modRoot = Split-Path -Parent (Split-Path -Parent $distRoot.FullName)
Copy-Item -LiteralPath (Join-Path $root 'mod\_Info.txt') -Destination $modRoot
Copy-Item -LiteralPath (Join-Path $root 'mod\config.txt') -Destination $modRoot

$hash = (Get-FileHash -LiteralPath $jarPath -Algorithm SHA256).Hash
Write-Host "Built: $jarPath"
Write-Host "SHA256: $hash"
Write-Host "Production classes: $((Get-ChildItem -LiteralPath $classesPath -Recurse -Filter '*.class').Count)"
