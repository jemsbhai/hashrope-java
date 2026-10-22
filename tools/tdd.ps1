param([Parameter(Mandatory=$true)][string]$Cycle,[Parameter(Mandatory=$true)][ValidateSet('red','green','refactor')][string]$Phase,[string]$Test='RopeTest')
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot
$output=Join-Path $root "evidence/tdd/$Cycle-$Phase"
if(Test-Path -LiteralPath $output){throw "Evidence already exists: $output"}
New-Item -ItemType Directory -Path $output | Out-Null
Copy-Item -Recurse -LiteralPath (Join-Path $root 'java/src') -Destination (Join-Path $output 'src')
Copy-Item -LiteralPath (Join-Path $root 'java/pom.xml') -Destination $output
$env:JAVA_HOME='C:\Program Files\Java\jdk-24'
$argsList=@('-B','-ntp','-s',"$PSScriptRoot/isolated-settings.xml",'-gs',"$PSScriptRoot/isolated-settings.xml",'-Dmaven.repo.local=C:/Users/User/Documents/Codex/2026-10-22/task/m2','-f',"$root/java/pom.xml",'test',"-Dtest=$Test")
"mvn $argsList" | Set-Content -Encoding utf8 (Join-Path $output 'command.txt')
& 'E:\tools\apache-maven-3.10.0\bin\mvn.cmd' @argsList *> (Join-Path $output 'output.log')
$result=$LASTEXITCODE
"$result" | Set-Content (Join-Path $output 'exit-code.txt')
Get-Content (Join-Path $output 'output.log') -Tail 25
if($Phase -eq 'red' -and $result -eq 0){throw 'Expected failing test, but passed'}
if($Phase -ne 'red' -and $result -ne 0){throw "Expected passing test: exit $result"}
