param($p1, $p2)
$credentials = $p1
$source = $p2

Remove-Item TTP-Dist -Recurse -Force -ErrorAction 'SilentlyContinue' 
mkdir TTP-Dist
Set-Location TTP-Dist
mkdir lib
mkdir build/x64
Copy-Item ../../win64-release/csharp/*_csharp.dll lib
Copy-Item ../../win64-release/csharp/*_wrap.dll build/x64
Copy-Item -Path ../../win64-release/lib/*.dll -Destination build/x64 -Exclude @('charset-1.dll', 'gdalalljni.dll', 'geos.dll', 'lti_dsdk_cdll_9.5.dll')

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

Set-Content -Path build/TTP-Dist.targets '<Project ToolsVersion="4.0" DefaultTargets="Build" xmlns="http://schemas.microsoft.com/developer/msbuild/2003">'
Add-Content -Path build/TTP-Dist.targets '    <ItemGroup Condition="''$(Platform)'' == ''x64''">'
Add-Content -Path build/TTP-Dist.targets '        <NativeLibs Include="$(MSBuildThisFileDirectory)x64\*.dll" />'
Add-Content -Path build/TTP-Dist.targets '        <None Include="@(NativeLibs)">'
Add-Content -Path build/TTP-Dist.targets '            <Link>%(FileName)%(Extension)</Link>'
Add-Content -Path build/TTP-Dist.targets '            <CopyToOutputDirectory>PreserveNewest</CopyToOutputDirectory>'
Add-Content -Path build/TTP-Dist.targets '        </None>'
Add-Content -Path build/TTP-Dist.targets '    </ItemGroup>'
Add-Content -Path build/TTP-Dist.targets '</Project>'

nuget pack TTP-Dist.nuspec
$nuPkgFile = 'TTP-Dist.' + $ttpVersion + '.nupkg'

Write-Host 'Pushing ' $nuPkgFile ' Package to ' $source
nuget push $nuPkgFile $credentials -Source $source
Set-Location ..