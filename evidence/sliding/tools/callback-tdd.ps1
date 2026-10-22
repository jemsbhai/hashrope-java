param([Parameter(Mandatory=$true)][string]$Cycle,[Parameter(Mandatory=$true)][ValidateSet('red','green','refactor')][string]$Phase)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot
$port='C:\Users\User\Documents\Codex\2026-10-22\task\port'
$output=Join-Path $root "evidence/$Cycle-$Phase"
if(Test-Path -LiteralPath $output){throw "Evidence already exists: $output"}
New-Item -ItemType Directory -Path "$output/src/main/java/hashrope","$output/src/test/java/hashrope" | Out-Null
Get-ChildItem -File -LiteralPath "$port/java/src/main/java/hashrope" | Copy-Item -Destination "$output/src/main/java/hashrope"
Copy-Item -LiteralPath "$port/java/src/test/java/hashrope/SlidingWindowTest.java" -Destination "$output/src/test/java/hashrope"
Copy-Item -LiteralPath "$port/java/pom.xml" -Destination $output
$run=Join-Path $root "run-$Cycle-$Phase"
if(Test-Path -LiteralPath $run){throw "Execution directory exists: $run"}
New-Item -ItemType Directory -Path $run | Out-Null
Copy-Item -Recurse -LiteralPath "$output/src" -Destination "$run/src"
Copy-Item -LiteralPath "$output/pom.xml" -Destination $run
$env:JAVA_HOME='C:\Program Files\Java\jdk-24'
$settings="$port/tools/isolated-settings.xml"
$argsList=@('-B','-ntp','-s',$settings,'-gs',$settings,'-Dmaven.repo.local=C:/Users/User/Documents/Codex/2026-10-22/task/m2','-f',"$run/pom.xml",'test','-Dtest=SlidingWindowTest')
"mvn $argsList" | Set-Content -Encoding utf8 "$output/command.txt"
& 'E:\tools\apache-maven-3.10.0\bin\mvn.cmd' @argsList *> "$output/output.log"
$result=$LASTEXITCODE
"$result" | Set-Content "$output/exit-code.txt"
$manifest=Get-ChildItem -Recurse -File -LiteralPath $output | Sort-Object FullName | ForEach-Object { [PSCustomObject]@{path=$_.FullName.Substring($output.Length+1).Replace('\','/');sha256=(Get-FileHash -Algorithm SHA256 -LiteralPath $_.FullName).Hash.ToLower()} }
ConvertTo-Json -InputObject @($manifest) -Depth 3 | Set-Content -Encoding utf8 "$output/sha256.json"
Get-Content "$output/output.log" -Tail 25
if($Phase -eq 'red' -and $result -eq 0){throw 'Expected failing test, but passed'}
if($Phase -ne 'red' -and $result -ne 0){throw "Expected passing test: exit $result"}
