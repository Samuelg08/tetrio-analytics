# Arquitectura

## Componentes

### Frontend — SvelteKit
Presenta perfiles, estadísticas, récords, snapshots, comparaciones y rankings. Llama únicamente a Spring Boot, directamente desde el navegador (C3), usando `PUBLIC_API_BASE_URL`. Gestiona estados de carga, vacío, error y éxito; decide su comportamiento ante errores por el campo `code` de ProblemDetail. No calcula reglas de negocio sensibles ni accede a MongoDB o FastAPI.

### Backend — Spring Boot
API principal. Responsabilidades:
- Validación y contratos REST (`docs/API_CONTRACTS.md`), publicados también con springdoc-openapi (C9).
- Errores en ProblemDetail (RFC 9457) con `code` estable, `traceId` y errores de validación (C5).
- CORS con lista explícita de orígenes (`CORS_ALLOWED_ORIGINS`), sin comodines (C3).
- Cliente único de TETRA CHANNEL (`TetraChannelClient`) con rate limiter compartido, timeout, caché, `X-Session-ID` y reintentos con pausa progresiva.
- Reglas deterministas y métricas derivadas.
- Persistencia en MongoDB e idempotencia de snapshots (`cacheWindowKey` obligatorio).
- Pipeline de extracción masiva bajo demanda como proceso de línea de comandos (`docs/DATA_PIPELINE.md`). **Nunca se inicia automáticamente al arrancar el servidor web.**
- Llamadas al microservicio de IA y registro de inferencias y versión del modelo.
- Autenticación: fuera del MVP salvo requisito obligatorio del curso (C8). No existen endpoints administrativos expuestos sin protección.

### Base de datos — MongoDB Atlas
Base compartida de desarrollo en MongoDB Atlas (C2), con un usuario independiente por desarrollador y privilegios mínimos (`readWrite` solo sobre las bases del proyecto). Almacena documentos normalizados en colecciones separadas (`docs/DATA_MODEL.md`). Los payloads originales de la extracción masiva se guardan en archivos de `data/raw/`, no en Atlas. Verificar los límites de almacenamiento de la capa de Atlas elegida.

### Servicio de IA — FastAPI
Servicio interno que recibe características validadas, ejecuta modelos previamente entrenados y devuelve predicciones, versión e información de explicación disponible. Escucha solo en `127.0.0.1`. No accede a MongoDB en el MVP. El entrenamiento es un proceso offline separado de la inferencia (`ai_service/training/`) que lee exclusivamente datasets versionados de `data/processed/`.

## Configuración por módulo (C4)

Cada módulo dispone de su propio `.env.example` con valores ficticios y sin credenciales reales:

| Módulo | Archivo | Carga |
|---|---|---|
| Raíz | `.env.example` | Índice de referencia general del repositorio. |
| Backend | `backend/.env.example` | Spring Boot **no** carga `.env` automáticamente. Usar variables de entorno del sistema o del IDE, o importar explícitamente un archivo local ignorado por Git (`spring.config.import=optional:file:.env[.properties]`). |
| Frontend | `frontend/.env.example` | Vite/SvelteKit lee `frontend/.env`. Solo las variables con prefijo `PUBLIC_` llegan al navegador; nunca poner secretos en ellas. |
| IA | `ai_service/.env.example` | Variables de entorno del sistema o carga explícita en `ai_service` (documentada en AI-001). |

## Mecanismo único de límite de solicitudes y coordinación (C7)

### Rate limiter compartido en proceso
- Todas las llamadas salientes a `https://ch.tetr.io/api/` —tanto las consultas en vivo originadas por peticiones HTTP de usuarios como las del pipeline de extracción— pasan obligatoriamente por una única instancia de `TetraChannelRateLimiter` integrada en `TetraChannelClient`.
- El limitador impone un intervalo mínimo entre solicitudes consecutivas configurable por variable de entorno (`TETRA_CHANNEL_MIN_INTERVAL_MS`, por defecto `1100` ms, es decir, $\le 0{,}91\text{ req/s}$, nunca superior a $1\text{ req/s}$) y un presupuesto de espera máxima para consultas interactivas (`TETRA_CHANNEL_ACQUIRE_TIMEOUT_MS`).
- Ante respuestas HTTP `429`, timeouts repetidos o errores `5xx` de TETRA CHANNEL, el limitador entra en estado de **enfriamiento (*cooldown*)** aplicando espera exponencial con *jitter* y respetando la cabecera `Retry-After` si el proveedor la envía. Tras alcanzar el umbral de fallos consecutivos (`EXTRACTION_MAX_CONSECUTIVE_ERRORS`), el pipeline guarda su checkpoint y pasa a estado `paused`.

### Limitación del rate limiter local y regla de operador único
- **Un rate limiter en memoria de un proceso local no coordina por sí mismo varios ordenadores ni múltiples procesos JVM simultáneos.**
- Para el MVP se establece la siguiente regla operativa obligatoria:
  1. **Operador único de extracción:** solo uno de los dos desarrolladores puede ejecutar el pipeline de extracción en un momento dado, avisando previamente al compañero.
  2. **Sin arranque automático:** el runner de extracción está deshabilitado por defecto (`EXTRACTION_ENABLED=false`) y solo se activa ejecutando el perfil CLI dedicado (`SPRING_PROFILES_ACTIVE=extraction` con `EXTRACTION_ENABLED=true` y sin levantar el servidor HTTP).
  3. **Evitar peticiones en vivo concurrentes:** mientras el pipeline masivo esté en ejecución, ningún ordenador del equipo debe realizar consultas en vivo contra TETRA CHANNEL desde la aplicación web (el desarrollo o prueba de UI durante esa ventana debe usar datos ya cacheados en MongoDB o fixtures sintéticos).

### Semántica de caché y ventana efectiva (perfil y resumen)
- **Estrategia conjunta (refresco simultáneo):**
  Como en el flujo interactivo el perfil (`GET /users/:user`) y el resumen de liga (`GET /users/:user/summaries/league`) se refrescan juntos para construir el snapshot consolidado del jugador, la ventana de caché efectiva debe cubrir las expiraciones de ambos recursos de upstream y el TTL local.
  Por ello, la estrategia conjunta calcula el límite de la ventana tomando el **máximo** entre las expiraciones de ambos recursos y el TTL local configurado ($t_{\text{sync}} + \text{CACHE\_TTL\_SECONDS} \times 1000$):
  $$\text{effectiveCacheUntil} = \max(\text{cached\_until}_{\text{user}}, \text{cached\_until}_{\text{league}}, t_{\text{sync}} + \text{CACHE\_TTL\_SECONDS} \times 1000)$$
  Esto garantiza que el backend no envíe solicitudes prematuras a TETRA CHANNEL mientras cualquiera de los dos recursos continúe dentro de su ventana de caché del proveedor, evitando respuestas redundantes o datos inconsistentes.
- **Estrategia de refresco independiente (desacoplada):**
  Si una evolución futura contempla refrescar perfil y resumen de forma desacoplada bajo demanda independiente, la arquitectura documenta y mantiene una expiración por recurso (`userCachedUntil`, `leagueCachedUntil`), revalidando cada endpoint upstream de forma aislada según su propio ciclo de vida. Para el MVP se implementa la **estrategia conjunta** para preservar la atomicidad del snapshot.

## Pipeline de extracción de datos (C7)

El diseño completo de fases, paginación `prisecter`, checkpoints atómicos, reanudación, deduplicación, manifiesto y métricas de calidad se especifica en [`docs/DATA_PIPELINE.md`](DATA_PIPELINE.md).

### Estimación de volumen y tiempo (referencia inicial; medir en cada ejecución)
El 2026-10-20, `GET /general/stats` informó `rankedcount = 36227` jugadores clasificados en TETRA LEAGUE. Esto es una **estimación**; el volumen real debe medirse y registrarse en el `manifest.json` de cada ejecución:
- **Recorrido del ranking (`GET /users/by/league` con `limit=100`):** aproximadamente **363 solicitudes** ($\approx 6\text{–}7\text{ minutos}$ a $1\text{ req/s}$). Cada entrada del ranking ya incluye las métricas principales de liga (`tr`, `glicko`, `rd`, `gxe`, `rank`, `bestrank`, `apm`, `pps`, `vs`, `gamesplayed`, `gameswon`, `xp`, `gametime`, `ar`).
- **Solicitudes adicionales de enriquecimiento por jugador:**
  - Si se consultan **perfil (`/users/:user`) y resumen (`/users/:user/summaries/league` o `/summaries`) por separado** para los $36.227$ jugadores, se requieren aproximadamente **$72.454$ solicitudes adicionales**, lo que supone **cerca de $20{,}1\text{ horas}$** al ritmo de $1\text{ solicitud/s}$ (unas $22\text{ horas}$ a $1.100\text{ ms/solicitud}$).
  - Si se consulta un único endpoint adicional por jugador (p. ej. `/users/:user/summaries`), serían aproximadamente **$36.227$ solicitudes adicionales** ($\approx 10\text{ horas}$ a $1\text{ req/s}$).
- Por esta razón, el pipeline separa la **Fase 1 (ranking completo, ~363 solicitudes)** —que ya proporciona las variables del esquema base `tr-cross-v1`— de la **Fase 2 (enriquecimiento por jugador en lotes reanudables con checkpoints)**.

### Términos de uso
Revisados el 2026-10-20: la documentación de TETRA CHANNEL fija reglas de ritmo, caché y `X-Session-ID`. Los Términos de Servicio de TETR.IO (actualizados el 2020-03-11) no contienen cláusulas específicas sobre volumen de extracción o conservación de datos, pero prohíben usos ilícitos o contrarios a los términos. Los datos incluyen usernames, IDs y países de jugadores: no publicar datasets crudos y conservar solo lo necesario para el proyecto.

## Flujo de entrenamiento e inferencia
1. El pipeline genera un dataset versionado en `data/processed/<datasetVersion>/` junto con su `manifest.json`.
2. `ai_service/training/` entrena y evalúa (baseline primero, partición por jugador) y guarda el artefacto y la ficha del modelo en `ai_service/artifacts/` (excluido de Git).
3. FastAPI carga el artefacto y lo expone mediante el contrato interno.
4. Spring Boot construye las variables permitidas, llama a FastAPI, registra la predicción en `predictions` y la versión en `model_versions`.

## Principios
- Sin Docker durante el desarrollo local.
- Configuración por variables de entorno; secretos solo en archivos locales ignorados por Git.
- No hay acceso del frontend a MongoDB ni a FastAPI.
- El backend no debe depender de que la API externa esté disponible para leer datos ya almacenados.
- Toda inferencia debe identificar el modelo utilizado.
- Separar hechos observados, métricas derivadas y predicciones.
- `docs/API_CONTRACTS.md` y el OpenAPI generado deben coincidir.

## Diagrama lógico

```text
Navegador (SvelteKit :5173) --HTTP + CORS--> Spring Boot :8080 --> MongoDB Atlas
                                                  |---> TETRA CHANNEL API (ch.tetr.io/api)
                                                  |---> FastAPI 127.0.0.1:8000 --> scikit-learn / SHAP

Pipeline de extracción (Spring Boot CLI, perfil extraction, operador único, sin servidor HTTP)
  TETRA CHANNEL --> data/raw/ --> normalización + validación --> data/processed/ + manifest.json
                                                                       |
  ai_service/training (offline) <--------------------------------------+
       --> ai_service/artifacts/ --> FastAPI
```

## Riesgos
- Disponibilidad real de historiales y métricas para entrenar modelos.
- Cambios o límites de la API externa (la documentación advierte que puede cambiar sin aviso y que la estructura de los Records puede variar).
- Falta de coordinación entre ordenadores si se lanzan peticiones en vivo mientras corre una extracción masiva (mitigado por la regla de operador único).
- Fuga de datos al dividir capturas del mismo jugador o al usar variables derivadas del rating.
- Sesgo de selección: el leaderboard solo contiene jugadores clasificados.
- Incompatibilidad de entornos locales entre ordenadores.
- Pruebas de integración con MongoDB sin Docker (ver `.github/workflows/ci.yml` y `README.md`).
- Alcance excesivo para cuatro semanas.
