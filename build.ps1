$ErrorActionPreference = 'Stop'

$GradleVersion = '8.10.2'
$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$DistRoot = Join-Path $ProjectRoot '.gradle-dist'
$ZipPath = Join-Path $DistRoot "gradle-$GradleVersion-bin.zip"
$GradleRoot = Join-Path $DistRoot "gradle-$GradleVersion"
$GradleBat = Join-Path $GradleRoot 'bin\gradle.bat'

New-Item -ItemType Directory -Force -Path $DistRoot | Out-Null

if (-not (Test-Path $GradleBat)) {
    if (-not (Test-Path $ZipPath)) {
        $Url = "https://services.gradle.org/distributions/gradle-$GradleVersion-bin.zip"
        Write-Host "Downloading Gradle $GradleVersion..."
        Invoke-WebRequest -Uri $Url -OutFile $ZipPath
    }

    Write-Host "Extracting Gradle $GradleVersion..."
    Expand-Archive -Path $ZipPath -DestinationPath $DistRoot -Force
}

Push-Location $ProjectRoot
try {
    & $GradleBat --no-daemon clean build
} finally {
    Pop-Location
}
