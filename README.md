# TETR.IO Competitive Analytics

MVP universitario para consultar, almacenar y analizar estadísticas competitivas de jugadores de TETR.IO.

## Stack acordado

- Frontend: SvelteKit + TypeScript
- Backend principal: Java + Spring Boot + Maven
- Base de datos obligatoria: MongoDB
- Microservicio de IA: Python + FastAPI + scikit-learn
- Explicabilidad: SHAP, si resulta viable para el modelo seleccionado
- Desarrollo: Git + GitHub, Gemini Antigravity y Codex
- Desarrollo local inicialmente sin Docker

## Arquitectura

El navegador se comunica exclusivamente con Spring Boot. Spring Boot aplica las reglas de negocio, persiste datos en MongoDB y llama al microservicio de IA por HTTP cuando necesita inferencias. El backend consume únicamente endpoints documentados y permitidos de TETRA CHANNEL.

Consulta `docs/ARCHITECTURE.md`, `docs/API_CONTRACTS.md`, `docs/DATA_MODEL.md` y `docs/ROADMAP.md` antes de implementar funcionalidades.

## Estructura

```text
tetrio-analytics/
├── frontend/                 # SvelteKit + TypeScript
├── backend/                  # Java + Spring Boot
├── ai_service/               # Python + FastAPI
├── docs/
├── scripts/
├── .github/
├── AGENTS.md
├── GEMINI.md                 # Instrucciones complementarias para Antigravity
├── CODEX.md                  # Instrucciones de trabajo para Codex
├── .env.example
└── .gitignore
```

## Requisitos locales

Instala versiones compatibles de Node.js (LTS), Java LTS, Python 3 y MongoDB, o configura una instancia de MongoDB Atlas. Las versiones concretas deben acordarse y documentarse antes de crear los proyectos ejecutables; no las cambies unilateralmente.

## Configuración inicial

1. Copia `.env.example` a `.env` y completa las variables localmente.
2. No subas `.env`, credenciales, tokens ni dumps con información sensible al repositorio.
3. Configura `MONGODB_URI` con una base de datos de desarrollo.
4. Sigue las instrucciones específicas de `frontend/README.md`, `backend/README.md` y `ai_service/README.md` cuando los esqueletos estén generados.

## Flujo Git

- `main`: rama estable.
- Cada tarea se desarrolla en una rama corta, por ejemplo `feature/player-profile` o `feature/ai-baseline`.
- No trabajar directamente en `main`.
- Abrir pull request, revisar el diff y ejecutar pruebas antes de fusionar.
- Evitar que ambos agentes editen simultáneamente los mismos archivos.
- No subir artefactos grandes de entrenamiento ni datos crudos salvo que el equipo haya aprobado expresamente su inclusión.

## Definición de terminado

Una tarea está terminada solo cuando:
- cumple los criterios de aceptación;
- incluye validaciones y manejo de errores;
- tiene pruebas relevantes;
- la documentación afectada está actualizada;
- se han ejecutado las comprobaciones y se informa con honestidad de sus resultados.

## Nota sobre datos y API

No asumir que todos los historiales de partidas o todas las métricas están disponibles. Verifica la documentación vigente de TETRA CHANNEL y guarda ejemplos de respuestas anonimizados o sintéticos para las pruebas. No uses endpoints privados o no autorizados.
