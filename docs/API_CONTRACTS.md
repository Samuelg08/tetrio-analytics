# Contratos API — v0.2

| Campo | Valor |
|---|---|
| Versión | 0.2 (PR-000) |
| Fecha | 2026-10-20 |
| Estado | Aprobado para implementación. Los elementos marcados como **a verificar** deben confirmarse con payloads reales antes de fijar mapeos. Los marcados como **borrador** se cerrarán en una versión posterior. |
| Cambios | Ver «Historial de cambios» al final. |

Estos contratos no afirman la disponibilidad de todos los datos externos. Este documento es el contrato revisado por humanos. El OpenAPI generado por springdoc-openapi debe coincidir con él; cualquier discrepancia es un defecto que se corrige en el código o, con coordinación de ambos desarrolladores, en este documento.

## 1. Servicios y URLs locales

| Servicio | URL base local | Accesible desde el navegador |
|---|---|---|
| Spring Boot — API pública | `http://localhost:8080/api` | Sí (CORS con lista explícita) |
| Spring Boot — OpenAPI JSON | `http://localhost:8080/v3/api-docs` | Sí, solo desarrollo |
| Spring Boot — Swagger UI | `http://localhost:8080/swagger-ui.html` | Sí, solo desarrollo |
| FastAPI — IA interna | `http://127.0.0.1:8000` | **No** |
| SvelteKit — desarrollo | `http://localhost:5173` | — |

Las rutas de OpenAPI son las predeterminadas de springdoc (`/v3/api-docs` y `/swagger-ui.html`), confirmadas en BE-001.

## 2. Convenciones

- JSON en UTF-8 con nombres de campo en `camelCase`.
- Fechas en ISO 8601 UTC con sufijo `Z`.
- **Valores ausentes:** `null`. Nunca `0` ni `-1` como sustituto. Los centinelas `-1` de TETRA CHANNEL se convierten en `null`. El rango `z` (sin clasificar) se expone como `rank: null` y `ranked: false`.
- **Identidad:** `playerId` es el `_id` de TETR.IO (MongoID, patrón `^[0-9a-f]{24}$`). El username no es identidad estable.
- **Usernames:** el backend aplica `trim` y minúsculas (TETRA CHANNEL documenta usernames en minúsculas). Patrón provisional tras normalizar: `^[a-z0-9_-]{3,16}$` — **a verificar** contra las reglas oficiales de TETR.IO en BE-001. El frontend puede validar que el campo no esté vacío, pero la validación de referencia es la del backend.
- **Paginación de listas locales:** parámetros `page` (base 0, por defecto 0) y `size` (por defecto 20, máximo 100). Respuesta con el envoltorio `Page<T>` (sección 2.2).
- **Trazabilidad:** el backend acepta la cabecera `X-Request-Id` si cumple `^[A-Za-z0-9-]{8,64}$`; si no, genera un UUID. Siempre la devuelve en la respuesta y en `traceId` de los errores.
- **CORS:** orígenes leídos de `CORS_ALLOWED_ORIGINS` (lista separada por comas, sin `*`). Métodos `GET`, `POST`, `OPTIONS`. Cabeceras permitidas `Content-Type`, `X-Request-Id`. Cabeceras expuestas `X-Request-Id`, `Retry-After`. Sin credenciales (no hay autenticación en el MVP).
- **Procedencia:** las respuestas con datos externos incluyen `dataSource` y `lastSyncedAt`.

### 2.1 `dataSource`

| Valor | Significado |
|---|---|
| `live` | Obtenido de TETRA CHANNEL durante esta petición. |
| `cache` | Leído de MongoDB dentro de la ventana de caché efectiva. |
| `stale` | Leído de MongoDB fuera de la ventana porque TETRA CHANNEL no estaba disponible o limitó las solicitudes. |

**Ventana de caché efectiva y semántica de refresco:**
Para recursos compuestos que combinan más de un endpoint de TETRA CHANNEL (como `/users/:user` para perfil y `/users/:user/summaries/league` para el resumen competitivo), sea $t_{\text{sync}}$ el instante UTC en que el backend completa la sincronización (`lastSyncedAt`) y $\text{TTL}_{\text{local}} = \text{CACHE\_TTL\_SECONDS} \times 1000$:

- **Estrategia conjunta (refresco simultáneo de perfil y resumen):**
  Como perfil y resumen se consultan y persisten juntos para formar el perfil consolidado, la ventana efectiva debe cubrir las expiraciones de ambos recursos de upstream y el TTL local. Se toma el **máximo** de esas expiraciones para evitar reconsultas prematuras a TETRA CHANNEL mientras cualquiera de los dos recursos permanezca dentro de su ventana de caché en el proveedor:
  - `effectiveCachedAt` = $\min(\text{cached\_at}_{\text{user}}, \text{cached\_at}_{\text{league}})$ sobre los valores `cache.cached_at` presentes (si ambos faltan, se usa $t_{\text{sync}}$).
  - `upstreamCachedUntil` = $\max(\text{cached\_until}_{\text{user}}, \text{cached\_until}_{\text{league}})$ sobre los valores `cache.cached_until` presentes reportados por TETRA CHANNEL.
  - `effectiveCacheUntil` = $\max(\text{upstreamCachedUntil}, t_{\text{sync}} + \text{TTL}_{\text{local}})$. Si el proveedor no informa `cached_until` en ninguno, `effectiveCacheUntil` = $t_{\text{sync}} + \text{TTL}_{\text{local}}$.

  Dentro del intervalo $[t_{\text{sync}}, \text{effectiveCacheUntil})$ el backend sirve la información desde MongoDB (`dataSource: "cache"`) y no vuelve a solicitar el mismo jugador a TETRA CHANNEL.

- **Estrategia de refresco independiente (desacoplada por recurso):**
  Si una implementación contempla refrescar perfil y resumen de manera independiente (bajo demanda separada), documenta y mantiene una expiración individual por recurso (`userCachedUntil` y `leagueCachedUntil`), evaluando para cada uno $\text{effectiveCacheUntil}_{i} = \max(\text{cached\_until}_{i}, t_{\text{sync}} + \text{TTL}_{\text{local}})$. En tal caso, cada endpoint upstream se reconsulta únicamente al expirar su propia ventana. En el MVP se adopta la **estrategia conjunta** para garantizar coherencia atómica entre las estadísticas competitivas y los metadatos de usuario en cada snapshot.

### 2.2 `Page<T>`

```json
{
  "items": [],
  "page": 0,
  "size": 20,
  "totalItems": 0,
  "totalPages": 0
}
```

## 3. Errores — ProblemDetail (RFC 9457)

Todas las respuestas de error usan `Content-Type: application/problem+json`.

```json
{
  "type": "/problems/validation-failed",
  "title": "Validation failed",
  "status": 400,
  "detail": "Uno o más parámetros no son válidos.",
  "instance": "/api/players/by-username/a",
  "code": "VALIDATION_FAILED",
  "traceId": "00000000-0000-0000-0000-000000000000",
  "errors": [
    { "field": "username", "message": "Formato de username no válido" }
  ]
}
```

- `type`: referencia URI relativa y estable `/problems/{code-en-kebab-case}`. No necesita ser resoluble en el MVP.
- `code`: extensión estable. **El frontend decide su comportamiento por `code`**, nunca por `detail` ni `title`.
- `errors`: solo en `VALIDATION_FAILED`.
- Prohibido incluir trazas internas, mensajes crudos del proveedor, secretos o valores rechazados sensibles.

| HTTP | `code` | Cuándo |
|---|---|---|
| 400 | `VALIDATION_FAILED` | Parámetros o cuerpo con formato o rango inválido. |
| 400 | `MALFORMED_REQUEST` | JSON ilegible o tipo incorrecto. |
| 404 | `PLAYER_NOT_FOUND` | TETRA CHANNEL confirma explícitamente que el jugador no existe (p. ej. HTTP 404 o `success: false` con motivo de usuario no encontrado) y no existe en MongoDB, o no está almacenado en un endpoint solo local. |
| 404 | `RESOURCE_NOT_FOUND` | Ruta o recurso local inexistente. |
| 422 | `PREDICTION_NOT_APPLICABLE` | Entradas fuera del dominio del modelo (p. ej., jugador sin clasificar o variables requeridas ausentes). |
| 429 | `RATE_LIMITED` | Límite de ritmo propio del backend alcanzado (presupuesto compartido de solicitudes a TETRA CHANNEL agotado) y no hay datos en caché/stale para responder. Incluye `Retry-After`. |
| 502 | `UPSTREAM_BAD_RESPONSE` | TETRA CHANNEL devolvió un JSON malformado, un esquema incompatible, un error HTTP 5xx o un `success: false` por error interno/desconocido del proveedor, sin datos almacenados que servir. |
| 503 | `UPSTREAM_UNAVAILABLE` | Timeout de conexión/lectura, fallo de red o HTTP 429 impuesto por TETRA CHANNEL, sin datos almacenados que servir. Incluye `Retry-After` si se conoce. |
| 503 | `AI_SERVICE_UNAVAILABLE` | El servicio de IA no responde o excede el timeout. |
| 500 | `INTERNAL_ERROR` | Error inesperado en el backend. |

### 3.1 Clasificación de errores de TETRA CHANNEL (no todo `success: false` es 404)

El cliente de TETRA CHANNEL **no debe asumir** que cualquier respuesta con `success: false` equivale a `404 PLAYER_NOT_FOUND`. Debe clasificar la respuesta combinando el código HTTP, las cabeceras y el objeto `error` del envoltorio:

1. **Jugador inexistente (`404 PLAYER_NOT_FOUND`):** únicamente cuando el proveedor devuelve HTTP 404 o `success: false` cuyo `error.msg` / código identifica inequívocamente que el usuario no existe (verificado en BE-001: HTTP 404 con `"No such user! | Either you mistyped something, or the account no longer exists."`). En este caso no se reintenta.
2. **Límite de solicitudes del proveedor o local:**
   - Si el rate limiter compartido local rechaza o expira la espera → `429 RATE_LIMITED` (o `200` con `dataSource: "stale"` si el jugador ya está en MongoDB).
   - Si TETRA CHANNEL devuelve HTTP 429 o `success: false` indicando exceso de solicitudes → activar pausa/backoff en el cliente y devolver `503 UPSTREAM_UNAVAILABLE` con `Retry-After` (o `200` con `dataSource: "stale"` si hay copia local).
3. **Timeout o fallo de red (`503 UPSTREAM_UNAVAILABLE`):** `HttpTimeoutException`, `ConnectException` o corte de conexión. Si hay copia local en MongoDB, degradar a `200` con `dataSource: "stale"`.
4. **Error interno o payload inválido del proveedor (`502 UPSTREAM_BAD_RESPONSE`):** HTTP 5xx de TETRA CHANNEL, cuerpo no parseable como JSON, ausencia de `success`/`data`, o `success: false` por fallo interno del proveedor distinto de «usuario no encontrado» y de «rate limit». Si hay copia local, degradar a `200` con `dataSource: "stale"`.

Las pruebas unitarias de `BE-001` deben cubrir por separado estos cuatro escenarios (`success: false` usuario inexistente → 404; `success: false` error interno / HTTP 5xx → 502; HTTP 429 del proveedor y timeout → 503; agotamiento del rate limiter local → 429).

## 4. Endpoints públicos — Spring Boot

| Método | Ruta | Estado | Tarea prevista |
|---|---|---|---|
| GET | `/api/health` | Definido | BE-001 |
| GET | `/api/players/by-username/{username}` | Definido | BE-001 (en vivo), BE-002 (persistencia) |
| GET | `/api/players/{playerId}` | Definido | BE-002 |
| GET | `/api/players/{playerId}/snapshots` | Definido | BE-003 |
| GET | `/api/players/{playerId}/statistics` | Borrador | Semana 2 |
| GET | `/api/players/{playerId}/records` | Borrador | Semana 2 |
| GET | `/api/rankings/local` | Definido | Semana 2 |
| POST | `/api/comparisons` | Definido | Semana 2 |
| GET | `/api/players/{playerId}/tr-estimate` | Borrador | Semana 3 |

No existen endpoints administrativos públicos en el MVP. La extracción masiva se ejecuta como proceso de línea de comandos (ver `docs/ARCHITECTURE.md`).

### 4.1 `GET /api/health`

`200 OK`:

```json
{ "status": "UP" }
```

Indica que el proceso responde. No comprueba TETRA CHANNEL ni el servicio de IA.

### 4.2 `GET /api/players/by-username/{username}`

Comportamiento:
1. Normaliza y valida `username` → `400 VALIDATION_FAILED` si no cumple.
2. Si el jugador está almacenado y dentro de la ventana de caché efectiva → `dataSource: "cache"`.
3. Si no, consulta `GET /users/:user` y `GET /users/:user/summaries/league` en TETRA CHANNEL, persiste (desde BE-002) y responde con `dataSource: "live"`.
4. Si TETRA CHANNEL falla: con datos almacenados → `stale`; sin datos → `502`/`503` según la tabla de errores. Usuario inexistente → `404 PLAYER_NOT_FOUND`.

`200 OK` — `PlayerProfile` (valores sintéticos, solo de formato):

```json
{
  "playerId": "000000000000000000000000",
  "username": "example",
  "country": null,
  "lastSyncedAt": "2026-01-01T12:00:00Z",
  "dataSource": "live",
  "league": {
    "gamesPlayed": 0,
    "gamesWon": 0,
    "ranked": false,
    "tr": null,
    "glicko": null,
    "rd": null,
    "gxe": null,
    "rank": null,
    "bestRank": null,
    "apm": null,
    "pps": null,
    "vs": null,
    "standing": null,
    "percentile": null,
    "decaying": false
  }
}
```

| Campo | Tipo | Origen en TETRA CHANNEL | Notas |
|---|---|---|---|
| `playerId` | string | `/users/:user` → `_id` | MongoID. |
| `username` | string | `/users/:user` → `username` | Último observado. |
| `country` | string \| null | `/users/:user` → `country` | ISO 3166-1; la documentación indica que existen banderas «vanity». |
| `league` | object \| null | `/users/:user/summaries/league` | `null` si el resumen no está disponible. |
| `league.gamesPlayed` | int | `gamesplayed` | Partidas de TETRA LEAGUE. |
| `league.gamesWon` | int | `gameswon` | |
| `league.ranked` | bool | derivado de `rank` | `false` si el proveedor devuelve `z`. |
| `league.tr` | number \| null | `tr` | `-1` → `null`. |
| `league.glicko` | number \| null | `glicko` | `-1` → `null`. |
| `league.rd` | number \| null | `rd` | `-1` → `null`. La documentación indica que RD > 100 implica no clasificado. |
| `league.gxe` | number \| null | `gxe` | `-1` → `null`. Escala verificada en BE-001: `0–100` (porcentaje). |
| `league.rank` | string \| null | `rank` | `z` → `null`. |
| `league.bestRank` | string \| null | `bestrank` | `z` o ausente → `null`. |
| `league.apm` | number \| null | `apm` | Ataque por minuto; media de las últimas 10 partidas. |
| `league.pps` | number \| null | `pps` | Piezas por segundo; media de las últimas 10 partidas. |
| `league.vs` | number \| null | `vs` | Versus score; media de las últimas 10 partidas. |
| `league.standing` | int \| null | `standing` | Posición global; `-1` → `null`. |
| `league.percentile` | number \| null | `percentile` | 0 = mejor, 1 = peor. |
| `league.decaying` | bool | `decaying` | RD en aumento por inactividad. |

### 4.3 `GET /api/players/{playerId}`

Misma respuesta y misma política de sincronización que 4.2, usando el ID (`^[0-9a-f]{24}$`; formato inválido → `400`).

### 4.4 `GET /api/players/{playerId}/snapshots?from=&to=&page=&size=`

- `from` y `to` opcionales (ISO 8601). `from > to` → `400`.
- Jugador no almacenado → `404 PLAYER_NOT_FOUND`.
- Respuesta `Page<Snapshot>` en orden cronológico ascendente.

```json
{
  "items": [
    {
      "capturedAt": "2026-01-01T12:00:00Z",
      "tr": null,
      "glicko": null,
      "rd": null,
      "gxe": null,
      "rank": null,
      "apm": null,
      "pps": null,
      "vs": null,
      "gamesPlayed": 0,
      "gamesWon": 0,
      "standing": null
    }
  ],
  "page": 0,
  "size": 20,
  "totalItems": 1,
  "totalPages": 1
}
```

Política (C12): como máximo un snapshot por jugador y ventana de caché efectiva. Solo se crean snapshots cuando hay una sincronización real (`live`), nunca al servir desde caché.

### 4.5 `GET /api/players/{playerId}/statistics` — borrador

Métricas derivadas calculadas por el backend. Cada métrica debe documentar fórmula, unidades y campos de origen antes de implementarse. Ejemplo candidato: `winRate = gamesWon / gamesPlayed`, `null` si `gamesPlayed = 0`.

### 4.6 `GET /api/players/{playerId}/records` — borrador

Récords personales por modo (`40l`, `blitz`). Estructura **a verificar** tras inspeccionar `/users/:user/summaries/40l` y `/users/:user/summaries/blitz`. La documentación advierte que la estructura de los Records puede cambiar.

### 4.7 `GET /api/rankings/local?metric=&country=&page=&size=`

- `metric` ∈ {`tr`, `apm`, `pps`, `vs`}; otro valor → `400`.
- `country` opcional (ISO 3166-1 alfa-2).
- Los jugadores con la métrica en `null` se excluyen. Para `metric=tr` solo se incluyen jugadores con `ranked = true`.

```json
{
  "scope": "local",
  "metric": "tr",
  "universe": {
    "description": "Jugadores almacenados en la base de datos del proyecto. No es la clasificación global de TETR.IO.",
    "playersIncluded": 0,
    "playersExcludedNullMetric": 0,
    "dataAsOf": "2026-01-01T12:00:00Z"
  },
  "items": [
    {
      "position": 1,
      "playerId": "000000000000000000000000",
      "username": "example",
      "country": null,
      "value": null,
      "lastSyncedAt": "2026-01-01T12:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalItems": 1,
  "totalPages": 1
}
```

### 4.8 `POST /api/comparisons`

Entrada:

```json
{
  "playerAId": "000000000000000000000001",
  "playerBId": "000000000000000000000002"
}
```

- IDs con formato válido y distintos → si no, `400`.
- Ambos jugadores deben estar almacenados → si no, `404 PLAYER_NOT_FOUND` (el `detail` indica cuál).

```json
{
  "playerA": { "...": "PlayerProfile" },
  "playerB": { "...": "PlayerProfile" },
  "differences": {
    "tr": null,
    "glicko": null,
    "gxe": null,
    "apm": null,
    "pps": null,
    "vs": null
  },
  "headToHead": null,
  "generatedAt": "2026-01-01T12:00:00Z"
}
```

`differences` = A − B; `null` si cualquiera de los dos valores es `null`. `headToHead` permanece `null` hasta que exista un modelo H2H evaluado y aplicable (C6).

### 4.9 `GET /api/players/{playerId}/tr-estimate` — borrador

Estimación transversal del TR a partir de variables de rendimiento. No es un pronóstico temporal ni una relación causal.

```json
{
  "status": "model_unavailable",
  "playerId": "000000000000000000000000",
  "estimatedTr": null,
  "observedTr": null,
  "modelName": null,
  "modelVersion": null,
  "featureSchemaVersion": "tr-cross-v1",
  "featuresUsed": {},
  "explanation": [],
  "explanationMethod": null,
  "disclaimer": "Estimación estadística transversal; no es un pronóstico ni implica causalidad.",
  "generatedAt": "2026-01-01T12:00:00Z"
}
```

- `status` ∈ {`ok`, `model_unavailable`}. Sin modelo entrenado → `200` con `model_unavailable` y `estimatedTr: null`.
- `observedTr` se muestra solo como referencia; **nunca** es una entrada del modelo.
- Variables requeridas ausentes → `422 PREDICTION_NOT_APPLICABLE`. Servicio de IA caído → `503 AI_SERVICE_UNAVAILABLE`.
- Cada inferencia se registra en la colección `predictions`.

## 5. Contrato interno Spring Boot → FastAPI

- Base URL: `AI_SERVICE_URL` (local `http://127.0.0.1:8000`). FastAPI escucha solo en `127.0.0.1` y no se expone al navegador.
- Timeout de Spring configurable (`AI_SERVICE_TIMEOUT_MS`).
- Los errores usan el mismo formato ProblemDetail de la sección 3.

### 5.1 `GET /health`

```json
{
  "status": "ok",
  "modelLoaded": false,
  "modelName": null,
  "modelVersion": null,
  "featureSchemaVersion": null
}
```

### 5.2 `GET /v1/model-card`

Devuelve la ficha del modelo cargado (nombre, versión, `datasetVersion`, `featureSchemaVersion`, `trainedAt`, métricas de test reales). Sin modelo → `503` con `code: "MODEL_UNAVAILABLE"`. Spring Boot usa esta ficha para registrar `model_versions`.

### 5.3 `POST /v1/tr-estimate`

Entrada (números ilustrativos, no rangos garantizados):

```json
{
  "requestId": "00000000-0000-0000-0000-000000000000",
  "featureSchemaVersion": "tr-cross-v1",
  "features": {
    "apm": 50.0,
    "pps": 1.2,
    "vs": 100.0,
    "gamesPlayed": 120
  }
}
```

Reglas:
- Campos desconocidos o prohibidos → `422` (`VALIDATION_FAILED`, con `errors[]`).
- `featureSchemaVersion` distinta a la del modelo cargado → `422 FEATURE_SCHEMA_MISMATCH`.
- Variables requeridas en `null` → `422 PREDICTION_NOT_APPLICABLE`. No se imputa `0`. Solo se admitirá imputación si el pipeline entrenado la incluye y está documentada en la ficha del modelo.
- Valores finitos y ≥ 0; los límites superiores se definirán a partir del dataset real.

`200 OK`:

```json
{
  "status": "ok",
  "prediction": { "tr": 0.0 },
  "modelName": "tr-cross-baseline",
  "modelVersion": "0.0.0",
  "featureSchemaVersion": "tr-cross-v1",
  "explanation": [
    { "feature": "apm", "contribution": 0.0 }
  ],
  "explanationMethod": "shap"
}
```

Si SHAP no es viable: `explanation: []` y `explanationMethod: null`. Las contribuciones describen el modelo, no causalidad. Sin modelo cargado → `503 MODEL_UNAVAILABLE`; Spring Boot lo traduce a `status: "model_unavailable"` en 4.9.

### 5.4 Esquema de variables `tr-cross-v1`

Objetivo: `tr` en el momento de la extracción.

| Variable | Origen | Estado |
|---|---|---|
| `apm`, `pps`, `vs` | Resumen de liga o leaderboard | Permitida. Ojo: media de las últimas 10 partidas, ventana distinta a la del TR. |
| `gamesPlayed` | Resumen de liga o leaderboard | Permitida. |
| `gamesWon`, `winRate` | Resumen de liga o leaderboard | **Excluida por defecto.** Los resultados alimentan Glicko-2; requiere análisis de ablación y aprobación humana. |
| Mejor marca en 40 LINES / BLITZ | `/users/:user/summaries/40l`, `/blitz` | Candidata; disponibilidad **a verificar**. |
| `xp`, `gametime` | Leaderboard | Candidata. |
| `ar` | Leaderboard | **Excluida por defecto.** Algunos logros otorgan AR por posiciones en clasificaciones. |
| `tr`, `glicko`, `rd`, `gxe`, `rank`, `bestRank`, `standing`, `standing_local`, `percentile`, `percentile_rank`, `next_rank`, `prev_rank`, `next_at`, `prev_at` | — | **Prohibidas**: fuga directa del objetivo. |

Partición: por jugador (`playerId`) y, si existen datos de temporadas pasadas, validación adicional por temporada. Si no es posible una evaluación temporal, documentar la limitación.

### 5.5 Head-to-head — sin contrato

No se define contrato H2H hasta verificar datos y etiquetas. Baseline obligatorio: probabilidad esperada según Glicko-2 con los ratings **previos** a la partida. Las estadísticas de la propia partida son posteriores al resultado y están prohibidas como variables.

## Historial de cambios

### v0.2 — 2026-10-20
- Errores en ProblemDetail (RFC 9457) con `code` estable, `traceId` y `errors[]`; catálogo de códigos.
- `GET /players/{username}` pasa a `GET /api/players/by-username/{username}` para evitar ambigüedad con `/api/players/{playerId}`.
- `league.rating` se sustituye por `tr`; se añaden `gxe`, `apm`, `pps`, `vs`, `gamesPlayed`, `gamesWon`, `ranked`, `bestRank`, `standing`, `percentile`, `decaying`, con su origen.
- Definidos `dataSource`, la ventana de caché efectiva y el envoltorio `Page<T>`.
- Rankings renombrados a `/api/rankings/local` con metadatos del universo.
- Contrato interno movido a FastAPI (`/health`, `/v1/model-card`, `/v1/tr-estimate`) y fuera de la URL base de Spring Boot.
- Esquema de variables `tr-cross-v1` con variables permitidas, candidatas y prohibidas.
- CORS, `X-Request-Id` y rutas de OpenAPI documentados.

### v0.1
- Propuesta inicial.
