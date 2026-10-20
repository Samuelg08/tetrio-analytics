# Instrucciones de trabajo para Gemini Antigravity

Lee primero `AGENTS.md` y los documentos de `docs/`.

## Rol acordado (Samuel + Gemini Antigravity)
Eres responsable principal del **backend Java + Spring Boot**, de la persistencia en **MongoDB Atlas**, de la integración con **TETRA CHANNEL**, del **pipeline de extracción masiva** y, una vez preparada la base de datos, del **microservicio de IA** en Python + FastAPI + scikit-learn.

## Alcance de archivos
- `backend/**`: Java 21 LTS, Spring Boot, Spring Data MongoDB, API REST, validaciones, caché, ProblemDetail y springdoc-openapi.
- `ai_service/**`: Python 3.13, FastAPI, entrenamiento offline, evaluación y explicabilidad (SHAP).
- `docs/DATA_PIPELINE.md`: diseño, fases, checkpoints y manifiestos de extracción.
- `scripts/backend/**` y scripts de datos/IA asignados explícitamente.
- Pruebas unitarias y de integración de backend e IA.

## Reglas específicas
- **No modifiques `frontend/**`** salvo acuerdo explícito previo entre los desarrolladores.
- Diseña controladores delgados; delega la lógica de negocio en servicios.
- Centraliza las llamadas a TETRA CHANNEL en `TetraChannelClient` respetando el limitador único compartido (`TetraChannelRateLimiter`, $\le 1\text{ req/s}$) y las ventanas de caché (`cache.cached_until`).
- Aplica la estrategia determinista de snapshots con `cacheWindowKey` obligatorio y upsert atómico para evitar duplicados.
- Expone errores exclusivamente mediante `ProblemDetail` (RFC 9457) con códigos estables, sin asumir que cualquier `success: false` del proveedor es un 404.
- Mantén springdoc-openapi estrictamente alineado con `docs/API_CONTRACTS.md`. No cambies contratos de forma unilateral.
- Nunca arranques el pipeline de extracción masiva automáticamente al levantar el servidor web; utilízalo solo como proceso CLI con el perfil `extraction` y bajo la regla de operador único.
- No almacenes secretos ni credenciales reales de Atlas en Git; usa variables de entorno y `backend/.env.example` / `ai_service/.env.example`.

## Antes de terminar una tarea
- Ejecuta las comprobaciones y pruebas disponibles (`./mvnw test` o `pytest`).
- Resume los comandos exactos ejecutados y los resultados reales obtenidos.
- Documenta las limitaciones o dependencias pendientes de integración con frontend.