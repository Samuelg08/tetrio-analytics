# TETR.IO Competitive Analytics

MVP universitario para consultar, almacenar y analizar estadísticas competitivas de jugadores de TETR.IO.

## Stack y versiones acordadas (C1)

| Componente | Tecnología | Versión obligatoria |
|---|---|---|
| Frontend | SvelteKit + TypeScript | Node.js **22 LTS** |
| Backend principal | Java + Spring Boot + Maven Wrapper | Java **21 LTS** (`./mvnw` / `mvnw.cmd`) |
| Documentación API | springdoc-openapi | Alineada con `docs/API_CONTRACTS.md` (v0.2) |
| Base de datos | MongoDB Atlas (desarrollo compartido) | MongoDB **8.x** |
| Microservicio de IA | Python + FastAPI + scikit-learn | Python **3.13** (`venv`) |
| Explicabilidad | SHAP | Cuando resulte viable para el modelo seleccionado |
| Control de versiones | Git + GitHub | Ramas cortas + PR + GitHub Actions |
| Contenedores | **Sin Docker** en desarrollo local | — |

Verificar la compatibilidad exacta de dependencias antes de inicializar cada proyecto ejecutable en `frontend/`, `backend/` y `ai_service/`.

## Arquitectura

El navegador (SvelteKit) se comunica exclusivamente con Spring Boot mediante HTTP y CORS con lista explícita de orígenes permitidos (`CORS_ALLOWED_ORIGINS`). Spring Boot aplica las reglas de negocio deterministas, expone errores con `ProblemDetail` (RFC 9457), persiste datos en MongoDB Atlas, ejecuta el pipeline de extracción masiva reanudable y llama al microservicio de IA (FastAPI en `127.0.0.1`) por HTTP cuando necesita inferencias. El backend consume únicamente endpoints públicos documentados y permitidos de TETRA CHANNEL (`https://ch.tetr.io/api/`).

Consulta [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md), [`docs/API_CONTRACTS.md`](docs/API_CONTRACTS.md), [`docs/DATA_MODEL.md`](docs/DATA_MODEL.md), [`docs/DATA_PIPELINE.md`](docs/DATA_PIPELINE.md), [`docs/ROADMAP.md`](docs/ROADMAP.md) y [`docs/WORK_SPLIT.md`](docs/WORK_SPLIT.md) antes de implementar funcionalidades.

## Estructura

```text
tetrio-analytics/
├── frontend/                 # SvelteKit + TypeScript (Node 22 LTS)
├── backend/                  # Java 21 LTS + Spring Boot + Maven Wrapper
├── ai_service/               # Python 3.13 + FastAPI + scikit-learn
├── data/
│   ├── raw/                  # Payloads crudos de extracción (excluido de Git)
│   └── processed/            # Datasets normalizados y manifest.json (excluido de Git)
├── docs/                     # Contratos API v0.2, arquitectura, modelo de datos, roadmap
├── scripts/                  # Scripts auxiliares separados por módulo
├── .github/                  # Plantilla de PR y propuesta de GitHub Actions
├── AGENTS.md                 # Reglas globales e inmutables para agentes
├── GEMINI.md                 # Instrucciones para Gemini Antigravity
├── CODEX.md                  # Instrucciones para Codex
├── .gitattributes            # Normalización de finales de línea (LF)
├── .env.example              # Índice de variables de referencia
└── .gitignore
```

## Requisitos locales e instalación previa

Antes de generar o ejecutar los proyectos, cada desarrollador debe verificar en su máquina:

1. **Java 21 LTS:** `java -version` debe reportar versión 21.x (configurar `JAVA_HOME` apuntando a JDK 21 si convive con otras versiones). No es necesario instalar Maven globalmente una vez generado el Maven Wrapper (`./mvnw` / `mvnw.cmd`) en `backend/`.
2. **Node.js 22 LTS:** `node --version` (v22.x) y `npm --version`.
3. **Python 3.13:** `python --version` o `py -3.13 --version` en Windows. Crear entorno virtual dentro de `ai_service/.venv`.
4. **MongoDB Atlas (8.x):** acceso al clúster compartido de desarrollo con usuario propio de privilegios mínimos (`readWrite` sobre la base de datos de desarrollo/test).

## Configuración por módulo (C4)

No se asume que Spring Boot ni SvelteKit carguen automáticamente el archivo `.env` de la raíz:

1. Cada módulo incluye ya su archivo de plantilla sin credenciales reales (`backend/.env.example`, `frontend/.env.example`, `ai_service/.env.example`), además del índice en `.env.example`.
2. En **Spring Boot**, definir las variables en el entorno del sistema/IDE o importar explícitamente un archivo local ignorado por Git (`backend/.env`). Variables clave:
   - `SPRING_PROFILES_ACTIVE=local`
   - `SERVER_PORT=8080`
   - `MONGODB_URI` y `MONGODB_DATABASE` (credenciales individuales de Atlas, nunca en Git)
   - `CORS_ALLOWED_ORIGINS=http://localhost:5173`
   - `TETRA_CHANNEL_BASE_URL=https://ch.tetr.io/api`
   - `TETRA_CHANNEL_MIN_INTERVAL_MS=1100`
   - `CACHE_TTL_SECONDS=300`
   - `AI_SERVICE_URL=http://127.0.0.1:8000`
   - `EXTRACTION_ENABLED=false` (el pipeline nunca arranca automáticamente con el servidor web)
3. En **SvelteKit**, copiar `frontend/.env.example` a `frontend/.env` con `PUBLIC_API_BASE_URL=http://localhost:8080/api`. Al generar el proyecto en `FE-001`, es obligatorio versionar `frontend/package-lock.json`.
4. En **FastAPI**, usar las variables de `ai_service/.env.example` (`AI_HOST=127.0.0.1`, `AI_PORT=8000`, `AI_MODEL_PATH`, `AI_MODEL_CARD_PATH`).
5. Nunca subas `.env`, credenciales de Atlas, tokens ni datasets crudos/procesados al repositorio.

## Flujo Git y protección de `main` (C11)

- `main`: rama estable. Configurar en GitHub (manualmente por los administradores del repositorio) protección de rama con pull request obligatorio, al menos 1 aprobación humana y checks de CI en verde antes de fusionar.
- Cada tarea se desarrolla en una rama corta (`feature/be-001-skeleton`, `feature/fe-001-skeleton`, etc.).
- No trabajar directamente en `main`.
- Evitar que ambos agentes editen simultáneamente los mismos archivos; respetar la propiedad definida en [`docs/WORK_SPLIT.md`](docs/WORK_SPLIT.md).
- Cualquier cambio en archivos compartidos (`docs/**`, `README.md`, `.github/**`, `.gitattributes`, `.gitignore`) debe ir en un PR pequeño y coordinado.

## Definición de terminado

Consulta [`docs/DEFINITION_OF_DONE.md`](docs/DEFINITION_OF_DONE.md). Una tarea está terminada solo cuando cumple los criterios de aceptación, maneja errores con `ProblemDetail`, incluye pruebas ejecutadas con resultados reales reportados y mantiene la documentación sincronizada.

## Nota sobre datos, extracción y API externa (C6, C7)

- Utilizar exclusivamente endpoints públicos documentados de TETRA CHANNEL (`https://ch.tetr.io/api/`). Queda prohibido usar la API principal del juego (`https://tetr.io/api/`).
- Respetar el ritmo máximo recomendado (~1 solicitud/segundo), las ventanas de caché (`cache.cached_until`) y el encabezado `X-Session-ID`.
- Tratar los centinelas `-1` de TETRA CHANNEL como `null` (ausentes); nunca sustituir valores ausentes por `0`.
- En Machine Learning, el objetivo principal es la regresión transversal de `tr` evitando variables que filtren el rating (`glicko`, `rd`, `gxe`, `rank`, `standing`, `percentile`). No declarar completo un dataset sin reportar páginas, jugadores, registros y tasas de nulos reales.
