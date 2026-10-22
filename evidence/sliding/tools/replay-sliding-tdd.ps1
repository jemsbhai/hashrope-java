param(
    [string]$EvidenceRoot=(Split-Path $PSScriptRoot),
    [string[]]$Cycle,
    [string]$Maven='mvn',
    [string]$JavaHome=$env:JAVA_HOME,
    [string]$Repository
)
$ErrorActionPreference='Stop'
$evidence=(Resolve-Path -LiteralPath $EvidenceRoot).Path
$scratch=Join-Path ([System.IO.Path]::GetTempPath()) ('hashrope-sliding-replay-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $scratch | Out-Null
if(-not $Repository){$Repository=Join-Path $scratch 'm2'}
$settings=Join-Path $scratch 'settings.xml'
'<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0"/>' | Set-Content -Encoding utf8 $settings
if($JavaHome){$env:JAVA_HOME=$JavaHome}
$runs=Get-ChildItem -Directory -LiteralPath $evidence | Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'exit-code.txt') }
if($Cycle){$runs=$runs | Where-Object { $_.Name -in $Cycle }}
if(-not $runs){throw 'No matching evidence snapshots'}
foreach($run in $runs){
    $work=Join-Path $scratch $run.Name
    New-Item -ItemType Directory -Path $work | Out-Null
    Copy-Item -Recurse -LiteralPath (Join-Path $run.FullName 'src') -Destination (Join-Path $work 'src')
    Copy-Item -LiteralPath (Join-Path $run.FullName 'pom.xml') -Destination $work
    & $Maven -B -ntp -s $settings -gs $settings "-Dmaven.repo.local=$Repository" -f (Join-Path $work 'pom.xml') test -Dtest=SlidingWindowTest *> (Join-Path $work 'replay.log')
    $actual=$LASTEXITCODE
    $expected=[int](Get-Content -Raw -LiteralPath (Join-Path $run.FullName 'exit-code.txt'))
    Write-Output "$($run.Name): expected exit $expected; observed exit $actual"
    if($actual -ne $expected){throw "Replay mismatch; inspect $work/replay.log"}
}
Write-Output "Replay outputs: $scratch"
