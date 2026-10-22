param(
    [Parameter(Mandatory=$true)][string]$Wheel,
    [string]$Python = 'python'
)
$ErrorActionPreference = 'Stop'
$env:PYTHONDONTWRITEBYTECODE = '1'
$env:HASHROPE_REFERENCE_WHEEL = (Resolve-Path -LiteralPath $Wheel).Path
$evidenceRoot = $PSScriptRoot
$redOutput = & $Python -B -m unittest discover -s "$evidenceRoot\snapshots\red\tests" -v 2>&1
$redExit = $LASTEXITCODE
$redOutput
if ($redExit -eq 0 -or ($redOutput -join "`n") -notmatch 'FileNotFoundError') {
    throw 'Red snapshot did not fail for the expected missing generator'
}
$greenOutput = & $Python -B -m unittest discover -s "$evidenceRoot\snapshots\green\tests" -v 2>&1
$greenExit = $LASTEXITCODE
$greenOutput
if ($greenExit -ne 0) { throw 'Green snapshot failed' }
$signedRedOutput = & $Python -B -m unittest discover -s "$evidenceRoot\snapshots\signed-red\tests" -v 2>&1
$signedRedExit = $LASTEXITCODE
$signedRedOutput
if ($signedRedExit -eq 0 -or ($signedRedOutput -join "`n") -notmatch 'AssertionError: False is not true') {
    throw 'Signed edge-case red snapshot did not fail for the expected missing cases'
}
$signedGreenOutput = & $Python -B -m unittest discover -s "$evidenceRoot\snapshots\signed-green\tests" -v 2>&1
$signedGreenExit = $LASTEXITCODE
$signedGreenOutput
if ($signedGreenExit -ne 0) { throw 'Signed edge-case green snapshot failed' }
Write-Output 'Confirmed both fixture-generator red -> green cycles'
