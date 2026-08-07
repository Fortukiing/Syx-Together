[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$excludedRoots = @((Join-Path $root '.git'), (Join-Path $root 'build'))
$files = Get-ChildItem -LiteralPath $root -Recurse -File -Force | Where-Object {
    $path = $_.FullName
    -not ($excludedRoots | Where-Object { $path.StartsWith($_ + '\', [StringComparison]::OrdinalIgnoreCase) })
}

$forbiddenExtensions = @('.class', '.jar', '.zip', '.save', '.dmp', '.dump')
$forbidden = $files | Where-Object {
    $_.Extension.ToLowerInvariant() -in $forbiddenExtensions -or
    $_.Name -match '^(coop-log|error|crash).*\.txt$' -or
    $_.Name -match '(\.bak($|\.)|~$)'
}
if ($forbidden) {
    throw "Forbidden generated or diagnostic files found:`n$($forbidden.FullName -join [Environment]::NewLine)"
}

$textFiles = $files | Where-Object { $_.Extension -in @('.java', '.md', '.txt', '.gradle', '.yml', '.yaml', '.ps1', '.gitignore', '.gitattributes') }
$privateMatches = $textFiles | Select-String -Pattern 'C:\\Users\\pedro|C:\\Users\\anaso' -CaseSensitive:$false
if ($privateMatches) {
    throw "Personal filesystem paths found:`n$($privateMatches -join [Environment]::NewLine)"
}

$throwableCatches = Get-ChildItem -LiteralPath (Join-Path $root 'src') -Recurse -Filter '*.java' |
    Select-String -Pattern 'catch\s*\(\s*Throwable\b'
if ($throwableCatches) {
    throw "Do not catch Throwable:`n$($throwableCatches -join [Environment]::NewLine)"
}

$mainCount = (Get-ChildItem -LiteralPath (Join-Path $root 'src\main\java') -Recurse -Filter '*.java').Count
$overrideCount = (Get-ChildItem -LiteralPath (Join-Path $root 'src\game-overrides\java') -Recurse -Filter '*.java').Count
$testCount = (Get-ChildItem -LiteralPath (Join-Path $root 'src\test\java') -Recurse -Filter '*.java').Count
if ($mainCount -eq 0 -or $overrideCount -eq 0 -or $testCount -eq 0) {
    throw 'One or more expected Java source groups are empty.'
}

Write-Host 'Repository hygiene: PASS'
Write-Host "Project sources: $mainCount"
Write-Host "Game overrides: $overrideCount"
Write-Host "Tests: $testCount"
