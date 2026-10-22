param([Parameter(Mandatory=$true)][string]$Cycle,[Parameter(Mandatory=$true)][ValidateSet('red','green','refactor')][string]$Phase)
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$record = Join-Path $root "evidence/$Cycle-$Phase"
if (Test-Path -LiteralPath $record) { throw "Evidence already exists: $record" }
New-Item -ItemType Directory -Path "$record/snapshot", "$record/classes" | Out-Null
Copy-Item -LiteralPath "$root/src", "$root/test" -Destination "$record/snapshot" -Recurse
$sources = @(Get-ChildItem -LiteralPath "$record/snapshot" -Recurse -Filter *.java | ForEach-Object {$_.FullName})
$start = [DateTime]::UtcNow.ToString('o')
$ErrorActionPreference = 'Continue'
& 'C:/Program Files/Java/jdk-24/bin/javac.exe' -encoding UTF-8 -Xlint:all -d "$record/classes" @sources 2>&1 | Tee-Object -FilePath "$record/compile.log"
$compileExit = $LASTEXITCODE
$testExit = $null
if ($compileExit -eq 0) {
    & 'C:/Program Files/Java/jdk-24/bin/java.exe' -ea -cp "$record/classes" hashrope.PolynomialHashTest 2>&1 | Tee-Object -FilePath "$record/test.log"
    $testExit = $LASTEXITCODE
}
$ErrorActionPreference = 'Stop'
$passed = ($compileExit -eq 0 -and $testExit -eq 0)
$manifest = [ordered]@{cycle=$Cycle; phase=$Phase; startedUtc=$start; finishedUtc=[DateTime]::UtcNow.ToString('o'); compileExit=$compileExit; testExit=$testExit; passed=$passed; sourceHashes=@(Get-ChildItem -LiteralPath "$record/snapshot" -Recurse -Filter *.java | ForEach-Object { @{path=$_.FullName.Substring($record.Length+1); sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLower()} })}
$manifest | ConvertTo-Json -Depth 8 | Set-Content -Encoding utf8 "$record/manifest.json"
if ($Phase -eq 'red' -and $passed) {throw 'Expected red but tests passed'}
if ($Phase -ne 'red' -and -not $passed) {throw 'Expected passing tests'}
Write-Output "Observed $Cycle $Phase : compile=$compileExit test=$testExit"
