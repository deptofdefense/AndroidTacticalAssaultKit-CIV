param($p1, $p2)
$credentials = $p1
$source = $p2

Remove-Item TTP-Dist -Recurse -Force -ErrorAction 'SilentlyContinue' 
mkdir TTP-Dist
Set-Location TTP-Dist
mkdir lib
Copy-Item ../../win64-release/csharp/*.dll lib

((Select-String -Path ../gradle/versions.gradle -Pattern 'ttpDistVersion') -match "\d+\.\d+\.\d+")
$ttpVersion = $matches[0]
$ttpVersionString = "    <version>" + $ttpVersion + "</version>"
Set-Content -Path TTP-Dist.nuspec '<?xml version="1.0" encoding="utf-8"?>'
Add-Content -Path TTP-Dist.nuspec '<package xmlns="http://schemas.microsoft.com/packaging/2010/07/nuspec.xsd">'
Add-Content -Path TTP-Dist.nuspec '  <metadata>'
Add-Content -Path TTP-Dist.nuspec '    <id>TTP-Dist</id>'
Add-Content -Path TTP-Dist.nuspec $ttpVersionString
Add-Content -Path TTP-Dist.nuspec '    <authors>TAK Product Center</authors>'
Add-Content -Path TTP-Dist.nuspec '    <requireLicenseAcceptance>false</requireLicenseAcceptance>'
Add-Content -Path TTP-Dist.nuspec '    <description>TAK Third Party Dist C# Wrapper Libraries</description>'
Add-Content -Path TTP-Dist.nuspec '  </metadata>'
Add-Content -Path TTP-Dist.nuspec '</package>'
nuget pack TTP-Dist.nuspec
$nuPkgFile = 'TTP-Dist.' + $ttpVersion + '.nupkg'

Write-Host 'Pushing ' $nuPkgFile ' Package to ' $source
nuget push $nuPkgFile $credentials -Source $source
Set-Location ..