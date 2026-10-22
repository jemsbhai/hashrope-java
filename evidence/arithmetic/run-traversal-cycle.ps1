param([Parameter(Mandatory=$true)][ValidateSet('red','green','refactor')][string]$Phase,[string]$Cycle='11-subclass-traversal',[string]$Test='SubstringTraversalTest',[string]$ExpectedFailure='cannot inherit from final hashrope.PolynomialHash')
$ErrorActionPreference='Stop'
$taskRoot=Split-Path $PSScriptRoot
$port=Join-Path $taskRoot 'port'
$output=Join-Path $PSScriptRoot "evidence/$Cycle-$Phase"
if(Test-Path -LiteralPath $output){throw "Evidence already exists: $output"}
New-Item -ItemType Directory -Path "$output/snapshot/src/test/java/hashrope" | Out-Null
Copy-Item -LiteralPath "$port/java/src/main" -Destination "$output/snapshot/src" -Recurse
Copy-Item -LiteralPath "$port/java/src/test/java/hashrope/$Test.java" -Destination "$output/snapshot/src/test/java/hashrope"
Copy-Item -LiteralPath "$port/java/pom.xml" -Destination "$output/snapshot/pom.xml"
Copy-Item -LiteralPath "$port/tools/isolated-settings.xml" -Destination $output
$env:JAVA_HOME='C:\Program Files\Java\jdk-24'
$arguments=@('-B','-ntp','-o','-s',"$output/isolated-settings.xml",'-gs',"$output/isolated-settings.xml","-Dmaven.repo.local=$taskRoot/m2",'-f',"$output/snapshot/pom.xml",'test',"-Dtest=$Test")
$start=[DateTime]::UtcNow.ToString('o')
"mvn $arguments" | Set-Content -Encoding utf8 "$output/command.txt"
$ErrorActionPreference='Continue'
& 'E:\tools\apache-maven-3.10.0\bin\mvn.cmd' @arguments *> "$output/output.log"
$result=$LASTEXITCODE
$ErrorActionPreference='Stop'
$record=[ordered]@{cycle=$Cycle;phase=$Phase;runner='maven';startedUtc=$start;finishedUtc=[DateTime]::UtcNow.ToString('o');exitCode=$result;test=$Test;expectedFailure=$ExpectedFailure;sourceHashes=@(Get-ChildItem -LiteralPath "$output/snapshot/src" -Recurse -Filter *.java | ForEach-Object {@{path=$_.FullName.Substring($output.Length+1);sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLower()}})}
$record | ConvertTo-Json -Depth 8 | Set-Content -Encoding utf8 "$output/maven-manifest.json"
Get-Content -LiteralPath "$output/output.log" -Tail 30
if($Phase -eq 'red' -and ($result -eq 0 -or -not (Select-String -LiteralPath "$output/output.log" -SimpleMatch $ExpectedFailure))){throw "Expected failure: $ExpectedFailure"}
if($Phase -ne 'red' -and $result -ne 0){throw "Expected passing tests: exit $result"}
