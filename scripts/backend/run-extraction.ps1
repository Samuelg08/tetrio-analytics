param(
    [string]$Phases = "leaderboard_league",
    [int]$MaxPages = 5,
    [string]$DatasetVersion = "",
    [string]$ResumeRunId = ""
)

$ErrorActionPreference = "Stop"
$RepoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")

if (-not $env:JAVA_HOME) {
    $env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
}
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$env:SPRING_PROFILES_ACTIVE = "extraction"
$env:EXTRACTION_ENABLED = "true"
$env:EXTRACTION_PHASES = $Phases
$env:EXTRACTION_MAX_PAGES = "$MaxPages"
$env:EXTRACTION_DATASET_VERSION = $DatasetVersion
$env:EXTRACTION_RESUME_RUN_ID = $ResumeRunId

Push-Location (Join-Path $RepoRoot "backend")
try {
    .\mvnw.cmd -B spring-boot:run "-Dspring-boot.run.jvmArguments=-Djavax.net.ssl.trustStoreType=Windows-ROOT"
} finally {
    Pop-Location
}
