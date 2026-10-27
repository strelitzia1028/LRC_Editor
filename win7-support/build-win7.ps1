param(
    [string]$Jdk8Home = "",
    [string]$FfmpegExe = "",
    [string]$JflacJar = "",
    [string]$IconFile = "",
    [switch]$SkipSmokeTests
)

$ErrorActionPreference = "Stop"
$supportRoot = $PSScriptRoot
$workspaceRoot = [IO.Path]::GetFullPath((Join-Path $supportRoot ".."))
$workspacePrefix = $workspaceRoot.TrimEnd("\") + "\"

if ([string]::IsNullOrWhiteSpace($Jdk8Home)) {
    $Jdk8Home = Join-Path $workspaceRoot "target\jdk8-win7\zulu8.96.0.205-ca-fx-jdk8.0.504-win_x64"
}
if ([string]::IsNullOrWhiteSpace($FfmpegExe)) {
    $FfmpegExe = Join-Path $workspaceRoot "target\ffmpeg-win7\ffmpeg-4.4.1-essentials_build\bin\ffmpeg.exe"
}
if ([string]::IsNullOrWhiteSpace($JflacJar)) {
    $JflacJar = Join-Path $workspaceRoot "target\compile-libs\jflac-codec-1.5.2.jar"
}
if ([string]::IsNullOrWhiteSpace($IconFile)) {
    $IconFile = Join-Path $workspaceRoot "LRC-Editor.ico"
}

$javac = Join-Path $Jdk8Home "bin\javac.exe"
$jarTool = Join-Path $Jdk8Home "bin\jar.exe"
$packager = Join-Path $Jdk8Home "bin\javapackager.exe"
foreach ($required in @($javac, $jarTool, $packager, $FfmpegExe, $JflacJar, $IconFile)) {
    if (-not (Test-Path -LiteralPath $required)) {
        throw "Missing build input: $required"
    }
}

function Assert-WorkspacePath([string]$path) {
    $full = [IO.Path]::GetFullPath($path)
    if (-not $full.StartsWith($workspacePrefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to modify a path outside the workspace: $full"
    }
    return $full
}

function Reset-Directory([string]$path) {
    $full = Assert-WorkspacePath $path
    if (Test-Path -LiteralPath $full) {
        Remove-Item -LiteralPath $full -Recurse -Force
    }
    New-Item -ItemType Directory -Path $full -Force | Out-Null
    return $full
}

$workRoot = Reset-Directory (Join-Path $workspaceRoot "target\win7-build")
$classesDir = Join-Path $workRoot "classes"
$fxJarDir = Join-Path $workRoot "fxjar"
$stageDir = Join-Path $workRoot "stage"
$deployDir = Join-Path $workRoot "deploy"
New-Item -ItemType Directory -Path $classesDir, $fxJarDir, $stageDir, $deployDir -Force | Out-Null

Write-Host "Compiling Java 8 sources..."
$javaSources = Get-ChildItem -LiteralPath (Join-Path $supportRoot "src\main\java") -Recurse -Filter "*.java" |
    ForEach-Object FullName
& $javac -encoding UTF-8 -source 8 -target 8 -Xlint:-options -Xmaxerrs 500 `
    -cp $JflacJar -d $classesDir $javaSources
if ($LASTEXITCODE -ne 0) {
    throw "JDK 8 compilation failed."
}

Copy-Item -Path (Join-Path $supportRoot "src\main\resources\*") -Destination $classesDir -Recurse -Force
Copy-Item -LiteralPath $JflacJar -Destination (Join-Path $workRoot "jflac-codec-1.5.2.jar") -Force

$env:JAVA_HOME = $Jdk8Home
$env:Path = (Join-Path $Jdk8Home "bin") + ";" + $env:Path

Write-Host "Creating JavaFX application JAR..."
Push-Location $workRoot
try {
    & $packager -createjar -appclass com.lrceditor.LrcEditorApp `
        -srcdir $classesDir -outdir $fxJarDir -outfile "LRC-Editor-Win7" `
        -classpath "jflac-codec-1.5.2.jar" -nocss2bin
    if ($LASTEXITCODE -ne 0) {
        throw "javapackager -createjar failed."
    }
} finally {
    Pop-Location
}

$appJar = Join-Path $fxJarDir "LRC-Editor-Win7.jar"
$jarEntries = & $jarTool tf $appJar
if ($LASTEXITCODE -ne 0 -or
        $jarEntries -notcontains "styles/app.css" -or
        $jarEntries -notcontains "styles/dark-theme.css" -or
        $jarEntries -contains "styles/app.bss") {
    throw "Application JAR did not retain the JavaFX text CSS resources."
}
Copy-Item -LiteralPath $appJar -Destination $stageDir -Force
Copy-Item -LiteralPath $JflacJar -Destination $stageDir -Force

Write-Host "Building self-contained application image..."
$packagerCommand = '"' + $packager + '" -deploy -native image ' +
    '-name "LRC Editor Win7" -appclass com.lrceditor.LrcEditorApp ' +
    '-srcdir "' + $stageDir + '" -srcfiles "LRC-Editor-Win7.jar" ' +
    '-outdir "' + $deployDir + '" -outfile "LRC Editor Win7" ' +
    '-BmainJar=LRC-Editor-Win7.jar -BappVersion=5.6 ' +
    '-Bicon="' + $IconFile + '"'
& cmd.exe /d /s /c $packagerCommand
if ($LASTEXITCODE -ne 0) {
    throw "javapackager -deploy failed."
}

$bundle = Join-Path $deployDir "bundles\LRC Editor Win7"
if (-not (Test-Path -LiteralPath (Join-Path $bundle "LRC Editor Win7.exe"))) {
    throw "Packaged launcher was not generated."
}

Copy-Item -LiteralPath $JflacJar -Destination (Join-Path $bundle "app\jflac-codec-1.5.2.jar") -Force
$ffmpegTarget = Join-Path $bundle "app\ffmpeg"
New-Item -ItemType Directory -Path $ffmpegTarget -Force | Out-Null
Copy-Item -LiteralPath $FfmpegExe -Destination (Join-Path $ffmpegTarget "ffmpeg.exe") -Force
$ffmpegLicense = Join-Path (Split-Path (Split-Path $FfmpegExe -Parent) -Parent) "LICENSE"
if (Test-Path -LiteralPath $ffmpegLicense) {
    Copy-Item -LiteralPath $ffmpegLicense -Destination (Join-Path $ffmpegTarget "LICENSE-FFmpeg.txt") -Force
}

foreach ($doc in @("README-Win7.txt", "THIRD-PARTY-NOTICES.txt")) {
    $sourceDoc = Join-Path $supportRoot (Join-Path "package" $doc)
    $targetDoc = Join-Path $bundle $doc
    $text = [IO.File]::ReadAllText($sourceDoc, [Text.Encoding]::UTF8)
    [IO.File]::WriteAllText($targetDoc, $text.Replace("`r`n", "`n").Replace("`n", "`r`n"),
        (New-Object Text.UTF8Encoding($true)))
}

if (-not $SkipSmokeTests) {
    Write-Host "Running non-UI smoke tests..."
    $testClasses = Join-Path $workRoot "test-classes"
    New-Item -ItemType Directory -Path $testClasses -Force | Out-Null
    $testSources = Get-ChildItem -LiteralPath (Join-Path $supportRoot "src\test\java") -Recurse -Filter "*.java" |
        ForEach-Object FullName
    $testCp = (Join-Path $bundle "app\LRC-Editor-Win7.jar") + ";" +
        (Join-Path $bundle "app\jflac-codec-1.5.2.jar")
    & $javac -encoding UTF-8 -source 8 -target 8 -Xlint:-options `
        -cp $testCp -d $testClasses $testSources
    if ($LASTEXITCODE -ne 0) {
        throw "Smoke test compilation failed."
    }

    $java = Join-Path $Jdk8Home "bin\java.exe"
    $testRoot = Join-Path $workRoot "smoke"
    New-Item -ItemType Directory -Path $testRoot -Force | Out-Null
    $video = Join-Path $testRoot "scroll-texture.mp4"
    & $java -cp ($testClasses + ";" + $testCp) com.lrceditor.Win7VideoExportSmoke $video
    if ($LASTEXITCODE -ne 0) {
        throw "Video export smoke test failed."
    }
}

$releaseDir = Reset-Directory (Join-Path $workspaceRoot "LRC-Editor-Win7-x64")
Copy-Item -Path (Join-Path $bundle "*") -Destination $releaseDir -Recurse -Force

$zipPath = Assert-WorkspacePath (Join-Path $workspaceRoot "LRC-Editor-Win7-x64.zip")
if (Test-Path -LiteralPath $zipPath) {
    Remove-Item -LiteralPath $zipPath -Force
}

Write-Host "Creating release archive..."
Compress-Archive -Path $releaseDir -DestinationPath $zipPath -CompressionLevel Optimal

$bundleSize = (Get-ChildItem -LiteralPath $releaseDir -Recurse -File |
    Measure-Object Length -Sum).Sum
$zipSize = (Get-Item -LiteralPath $zipPath).Length
Write-Host ("Release folder: " + $releaseDir)
Write-Host ("Release ZIP:    " + $zipPath)
Write-Host ("Unpacked bytes: " + $bundleSize)
Write-Host ("ZIP bytes:      " + $zipSize)
