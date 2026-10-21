# AI Service — TETR.IO Competitive Analytics

Microservicio interno de inferencia y entrenamiento offline en **Python 3.13 + FastAPI + scikit-learn**.
Escucha exclusivamente en `127.0.0.1:8000` (nunca se expone directamente al navegador) y no accede directamente a MongoDB.

## Requisitos y configuración local
1. Crear y activar el entorno virtual con Python 3.13 desde `ai_service/`:
   ```powershell
   py -3.13 -m venv .venv
   .\.venv\Scripts\Activate.ps1
   python -m pip install --upgrade pip
   pip install -r requirements.txt
   ```
2. Copiar `.env.example` a `.env` (ignorado por Git) si se desean personalizar rutas.

## Comandos principales (desde `ai_service/`)

### Ejecutar pruebas automatizadas (`pytest`)
```powershell
.\.venv\Scripts\python.exe -m pytest -v
```

### Arrancar el servidor interno de inferencia
```powershell
.\.venv\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 8000
```

## Endpoints del contrato interno (`docs/API_CONTRACTS.md` §5)
- `GET /health` — Estado del servicio y del modelo cargado (`modelLoaded`, `modelName`, `modelVersion`, `featureSchemaVersion`).
- `GET /v1/model-card` — Ficha del modelo entrenado o `503 MODEL_UNAVAILABLE` (`application/problem+json`) si no existen artefactos entrenados.
- `POST /v1/tr-estimate` — Inferencia transversal bajo el esquema `tr-cross-v1` (`apm`, `pps`, `vs`, `gamesPlayed`), rechazando variables prohibidas por fuga de datos (`422 VALIDATION_FAILED`), esquemas incompatibles (`422 FEATURE_SCHEMA_MISMATCH`) y variables requeridas en `null` (`422 PREDICTION_NOT_APPLICABLE`, sin imputar `0`).
