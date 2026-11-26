param(
    [string]$AndroidJar = 'C:\AndroidTools\platforms\android-34\android.jar',
    [string]$D8 = 'C:\AndroidTools\build-tools\34.0.0\d8.bat',
    [Parameter(Mandatory = $true)][string]$GameModClassesDir,
    [Parameter(Mandatory = $true)][string]$OutputDex
)

$ErrorActionPreference = 'Stop'
$sourceDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$dependencyDir = Join-Path $sourceDir '.deps'
$buildDir = Join-Path $sourceDir 'build'
$classesDir = Join-Path $buildDir 'classes'
$dexDir = Join-Path $buildDir ("dex-" + [DateTime]::UtcNow.Ticks)
$compressJar = Join-Path $dependencyDir 'commons-compress-1.27.1.jar'
$ioJar = Join-Path $dependencyDir 'commons-io-2.18.0.jar'
$d8Jar = Join-Path (Split-Path -Parent $D8) 'lib\d8.jar'

if (-not (Test-Path -LiteralPath $AndroidJar)) { throw "Android platform jar not found: $AndroidJar" }
if (-not (Test-Path -LiteralPath $d8Jar)) { throw "D8 jar not found: $d8Jar" }
if (-not (Test-Path -LiteralPath (Join-Path $GameModClassesDir 'uk\playdrop\cherrytree_idletextrpg\ModMenu.class'))) {
    throw "ModMenu.class not found in game-mod classes: $GameModClassesDir"
}
New-Item -ItemType Directory -Force -Path $dependencyDir, $classesDir, $dexDir | Out-Null

$dependencies = @(
    @{
        Name = 'commons-compress-1.27.1.jar'
        Path = $compressJar
        Url = 'https://repo.maven.apache.org/maven2/org/apache/commons/commons-compress/1.27.1/commons-compress-1.27.1.jar'
        Sha256 = '293d80f54b536b74095dcd7ea3cf0a29bbfc3402519281332495f4420d370d16'
    },
    @{
        Name = 'commons-io-2.18.0.jar'
        Path = $ioJar
        Url = 'https://repo.maven.apache.org/maven2/commons-io/commons-io/2.18.0/commons-io-2.18.0.jar'
        Sha256 = 'f3ca0f8d63c40e23a56d54101c60d5edee136b42d84bfb85bc7963093109cf8b'
    }
)

foreach ($dependency in $dependencies) {
    if (-not (Test-Path -LiteralPath $dependency.Path)) {
        Invoke-WebRequest -Uri $dependency.Url -OutFile $dependency.Path
    }
    $actualHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $dependency.Path).Hash.ToLowerInvariant()
    if ($actualHash -ne $dependency.Sha256) { throw "Dependency checksum failed: $($dependency.Name)" }
}

$javaSources = @(
    (Join-Path $sourceDir 'GameUpdater.java'),
    (Join-Path $sourceDir 'BsdiffPatch.java')
)
$classPath = "$AndroidJar;$compressJar;$ioJar"
& javac -encoding UTF-8 -source 8 -target 8 -classpath $classPath -d $classesDir @javaSources
if ($LASTEXITCODE -ne 0) { throw 'javac failed while compiling the update client.' }

$classFiles = @(Get-ChildItem -LiteralPath $classesDir -Filter '*.class' -File -Recurse | ForEach-Object FullName)
$gameModClassFiles = @(Get-ChildItem -LiteralPath $GameModClassesDir -Filter '*.class' -File -Recurse | ForEach-Object FullName)
$d8Args = @('--min-api', '28', '--lib', $AndroidJar, '--output', $dexDir) + $classFiles + $gameModClassFiles + @($compressJar, $ioJar)
& java -cp $d8Jar com.android.tools.r8.D8 @d8Args
if ($LASTEXITCODE -ne 0) { throw 'D8 failed while building the update-client DEX.' }

$dexFiles = @(Get-ChildItem -LiteralPath $dexDir -Filter '*.dex' -File)
if ($dexFiles.Count -ne 1 -or $dexFiles[0].Name -ne 'classes.dex') {
    throw "Expected one updater DEX; D8 produced $($dexFiles.Count) dex files."
}
$resolvedOutput = [System.IO.Path]::GetFullPath($OutputDex)
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $resolvedOutput) | Out-Null
Copy-Item -LiteralPath $dexFiles[0].FullName -Destination $resolvedOutput -Force
Write-Output "Built updater DEX: $resolvedOutput ($((Get-Item -LiteralPath $resolvedOutput).Length) bytes)"
