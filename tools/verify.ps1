param([string]$Maven='mvn',[string]$JavaHome=$env:JAVA_HOME,[string]$Repository)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot
if($JavaHome){$env:JAVA_HOME=$JavaHome}
if(!$Repository){$Repository=Join-Path $root '.m2'}
$common=@('-B','-ntp','-s',"$PSScriptRoot/isolated-settings.xml",'-gs',"$PSScriptRoot/isolated-settings.xml","-Dmaven.repo.local=$Repository")
& $Maven @common -f "$root/java/pom.xml" clean install
if($LASTEXITCODE -ne 0){throw 'Library verification failed'}
& $Maven @common -f "$root/consumer-smoke/pom.xml" clean verify
if($LASTEXITCODE -ne 0){throw 'Independent consumer verification failed'}

