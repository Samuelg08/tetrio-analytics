param(
    [string]$DatasetVersion = "20261020-sample500",
    [string]$SeasonTestDatasetVersion = "20261028-season1-sample500",
    [switch]$Serve
)

$ErrorActionPreference = "Stop"
$RepoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$AiServiceDir = Join-Path $RepoRoot "ai_service"
$PythonExe = Join-Path $AiServiceDir ".venv\Scripts\python.exe"

Push-Location $AiServiceDir
try {
    if ($SeasonTestDatasetVersion) {
        & $PythonExe -m training.train_tr_cross --dataset-version $DatasetVersion --season-test-dataset-version $SeasonTestDatasetVersion
    } else {
        & $PythonExe -m training.train_tr_cross --dataset-version $DatasetVersion
    }

    if ($Serve) {
        & $PythonExe -m uvicorn app.main:app --host 127.0.0.1 --port 8000
    }
} finally {
    Pop-Location
}
