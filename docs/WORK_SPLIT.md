# Reparto de trabajo

Cada tarea tiene un único responsable (C10). Un agente no modifica archivos de otro responsable sin acuerdo previo de los humanos.

## Persona A — Samuel + Gemini Antigravity
- `backend/**`: Spring Boot, MongoDB Atlas, cliente TETRA CHANNEL, rate limiter compartido, persistencia, ProblemDetail, springdoc-openapi y pruebas Java.
- Pipeline de extracción masiva de datos y documentación asociada (`docs/DATA_PIPELINE.md`).
- `ai_service/**`: microservicio de IA (Python, FastAPI), entrenamiento offline, evaluación y explicabilidad (SHAP), una vez preparada la base de datos.
- `scripts/backend/**` y scripts de datos/IA asignados explícitamente.
- Pruebas automatizadas de backend e IA.

## Persona B — Compañero + Codex
- `frontend/**`: SvelteKit, TypeScript, experiencia de usuario, diseño de componentes, páginas, navegación, cliente HTTP y visualizaciones.
- Pruebas del frontend y accesibilidad básica.
- `scripts/frontend/**`.

## Archivos compartidos
Requieren coordinación, un pull request propio (solo documentación o configuración, separado del código) y revisión humana de ambos desarrolladores:
- `AGENTS.md`, `GEMINI.md`, `CODEX.md`, `README.md`
- `docs/API_CONTRACTS.md`, `docs/ARCHITECTURE.md`, `docs/DATA_PIPELINE.md`, `docs/ROADMAP.md`, `docs/WORK_SPLIT.md`, `docs/DEFINITION_OF_DONE.md`
- `docs/DATA_MODEL.md`: diseño de esquemas y contratos de datos.
- `.env.example` (raíz y por módulo: `backend/.env.example`, `frontend/.env.example`, `ai_service/.env.example`), `.gitignore`, `.gitattributes`
- `.github/**` (plantilla de PR y workflows)
- `data/**`: solo estructura (`.gitkeep`). Los datos nunca se suben a Git.

## Contratos entre módulos
| Contrato | Productor | Consumidor | Documento |
|---|---|---|---|
| API REST pública | Antigravity (backend) | Codex (frontend) | `docs/API_CONTRACTS.md` §2–4 |
| Contrato interno de IA | Antigravity (AI service) | Antigravity (backend) | `docs/API_CONTRACTS.md` §5 |
| Dataset procesado | Antigravity (pipeline) | Antigravity (entrenamiento) | `docs/DATA_MODEL.md` / `docs/DATA_PIPELINE.md` |

## Flujo de integración
1. Crear una rama corta de tarea desde `main` actualizado (p. ej. `feature/be-001-skeleton`, `feature/fe-001-skeleton`).
2. Implementar y probar en el entorno asignado.
3. Revisar el diff.
4. Abrir pull request con la plantilla `.github/pull_request_template.md`.
5. Confirmar que no cambia contratos sin coordinación.
6. Fusionar tras pruebas satisfactorias y revisión del otro humano.

Los agentes no deben asumir que conocen los cambios no fusionados del otro ordenador.
