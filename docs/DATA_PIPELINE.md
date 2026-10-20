# Pipeline de extracción y preparación de datos (C7)

Este documento especifica el diseño del pipeline automatizado, reanudable y reproducible de extracción desde la API pública de TETRA CHANNEL (`https://ch.tetr.io/api/`) hacia `data/raw/`, MongoDB Atlas y `data/processed/`.

## 1. Principios y condiciones obligatorias

1. **Solo endpoints públicos documentados:** queda estrictamente prohibido utilizar la API interna del juego (`https://tetr.io/api/`).
2. **Sin arranque automático:** el pipeline nunca se ejecuta al iniciar el servidor web de Spring Boot. Requiere invocación explícita por línea de comandos con el perfil `extraction` (`SPRING_PROFILES_ACTIVE=extraction`, `EXTRACTION_ENABLED=true`, `spring.main.web-application-type=none`).
3. **Mecanismo único de rate limiting y operador único:**
   - El pipeline reutiliza el mismo `TetraChannelClient` y `TetraChannelRateLimiter` que las consultas normales del backend (`TETRA_CHANNEL_MIN_INTERVAL_MS=1100` ms por defecto, nunca superior a $1\text{ req/s}$).
   - **Importante:** un limitador en memoria de un proceso local no coordina varios ordenadores entre sí. Durante el MVP, **solo un desarrollador (operador único)** puede ejecutar el pipeline en un momento dado y **ambos ordenadores deben evitar peticiones en vivo concurrentes** desde la aplicación web mientras dure una extracción masiva.
4. **Honestidad de datos:**
   - Los centinelas `-1` de TETRA CHANNEL y el rango `"z"` se transforman en `null` en los datos procesados.
   - Nunca se inventan registros ni se sustituyen valores ausentes por `0`.
   - Ningún dataset se declara completo sin generar su `manifest.json` con los recuentos reales obtenidos.
5. **Exclusión de Git:** tanto `data/raw/` como `data/processed/` están excluidos en `.gitignore`. Nunca se suben payloads crudos ni datasets procesados al repositorio.

---

## 2. Fases del flujo de extracción

El pipeline se divide en fases independientes y seleccionables por parámetro (`EXTRACTION_PHASES`), de modo que cada fase pueda ejecutarse, pausarse y reanudarse por separado.

### Fase 1 — Recorrido completo del ranking (`leaderboard_league`)
- **Endpoint:** `GET /users/by/league?limit=100` (y cursor `after=<prisecter>` en las páginas siguientes).
- **Cabecera `X-Session-ID`:** se genera un UUID único al iniciar la fase (`sessionId`) y se envía en **todas** las solicitudes de paginación de esa fase para garantizar que el balanceador de TETRA CHANNEL mantenga el mismo worker y una vista consistente.
- **Paginación con `prisecter`:**
  - En cada respuesta `data.entries`, se toma el campo `p` (`{ pri, sec, ter }`) del **último** elemento recibido y se formatea como `"${pri}:${sec}:${ter}"` para pasarlo en el parámetro `after` de la siguiente página.
  - La fase termina cuando una página devuelve `entries` vacío o menos entradas que `limit` y sin nuevo cursor, o cuando se alcanza un límite configurado (`EXTRACTION_MAX_PAGES`, opcional para pruebas).
- **Datos obtenidos ya en Fase 1:** `_id`, `username`, `country`, `xp`, `gametime`, `ar`, `gamesplayed`, `gameswon` y el subobjeto `league` (`tr`, `glicko`, `rd`, `gxe`, `rank`, `bestrank`, `apm`, `pps`, `vs`, `decaying`, `gamesplayed`, `gameswon`). Estos campos cubren todas las variables permitidas del esquema base `tr-cross-v1`.

### Fase 2 — Enriquecimiento por jugador en lotes (`player_enrichment`)
- **Entrada:** lista deduplicada de `playerId` (`_id`) obtenida en la Fase 1 (filtrable por rango, percentil o muestra estratificada según la configuración del lote).
- **Endpoints por jugador (configurables):**
  - Modo compacto (1 solicitud/jugador): `GET /users/{playerId}/summaries` (o `/summaries/40l` / `/summaries/blitz` según objetivo).
  - Modo separado (2 solicitudes/jugador): `GET /users/{playerId}` + `GET /users/{playerId}/summaries/league` (o récords pertinentes).
- **Cabecera `X-Session-ID`:** de acuerdo con la documentación de TETRA CHANNEL (*"Don't use a X-Session-ID header for requests that are not related"*), en solicitudes a jugadores distintos no se reutiliza el `X-Session-ID` de paginación del ranking o se rota por jugador/lote.
- **Respeto de ventana de caché:** antes de solicitar un jugador, se comprueba si ya existe en MongoDB o en el lote actual dentro de su ventana de caché efectiva (`cacheValidUntil`); si está vigente, se omite la llamada HTTP y se contabiliza como `skippedCached`.

### Fase 3 — Histórico de temporadas (`season_history`)
- **Endpoint:** `GET /users/history/league/{season}?limit=100` paginado con `after=<prisecter>` y su propio `X-Session-ID` por temporada.
- **Propósito:** obtener instantáneas finales de temporadas pasadas para validar el modelo de regresión fuera de la temporada actual cuando los campos estén disponibles (`tr`, `glicko`, `rd`, `apm`, `pps`, `vs`, `gamesplayed`, `gameswon`, `rank`).

### Fase 4 — Opcional para Head-to-Head (`h2h_records`)
- Solo se ejecuta si se activa explícitamente para evaluar el objetivo secundario H2H (C6):
  - `GET /users/{playerId}/records/league/recent` y/o `GET /labs/leagueflow/{playerId}`.

---

## 3. Estimación de volumen y tiempo

Tomando como referencia la medición real del 2026-10-20 en `GET /general/stats` (`rankedcount = 36227` jugadores clasificados):

| Operación | Solicitudes estimadas | Tiempo estimado a $1\text{ req/s}$ ($1.000\text{ ms}$) | Tiempo estimado a $1.100\text{ ms/req}$ |
|---|---:|---:|---:|
| **Fase 1:** Ranking completo (`GET /users/by/league`, `limit=100`) | $\approx 363$ solicitudes | $\approx 6{,}1\text{ min}$ | $\approx 6{,}7\text{ min}$ |
| **Fase 2 (opción A):** 1 solicitud adicional por jugador para los $36.227$ jugadores | $\approx 36.227$ solicitudes | $\approx 10{,}1\text{ horas}$ | $\approx 11{,}1\text{ horas}$ |
| **Fase 2 (opción B):** Perfil + resumen por separado (2 solicitudes/jugador) para los $36.227$ jugadores | $\approx 72.454$ solicitudes | $\approx 20{,}1\text{ horas}$ | $\approx 22{,}1\text{ horas}$ |
| **Fase 3:** 1 temporada histórica completa (~36.000 jugadores, `limit=100`) | $\approx 360$ solicitudes | $\approx 6{,}0\text{ min}$ | $\approx 6{,}6\text{ min}$ |

> **Nota importante:** estas cifras son **estimaciones** basadas en `rankedcount = 36227`. El número real de páginas, jugadores y tiempo debe medirse en cada ejecución y quedar registrado en el `manifest.json`. Dado que consultar perfil y resumen por separado para todos los jugadores requiere ~72.454 solicitudes (~20 horas), la Fase 1 constituye el dataset base obligatorio y la Fase 2 se ejecuta en lotes reanudables (`EXTRACTION_BATCH_SIZE`).

---

## 4. Checkpoints atómicos y reanudación

Cada ejecución se identifica con un `runId` (p. ej. `20261020T120000Z-league`) y mantiene su estado tanto en la colección `extraction_runs` de MongoDB como en el archivo local `data/raw/tetra-channel/<runId>/checkpoint.json`.

### Escritura atómica del checkpoint
Tras completar cada página del ranking (Fase 1/3) o cada lote de N jugadores (Fase 2):
1. Se escribe primero el payload crudo en disco (`page-0001.json` o `batch-0001.jsonl`) y se hace `fsync`.
2. Se escribe el nuevo estado en `checkpoint.json.tmp` y se renombra atómicamente a `checkpoint.json`.
3. Se actualiza el documento en la colección `extraction_runs` de MongoDB.

### Estructura de `checkpoint.json`
```json
{
  "runId": "20261020T120000Z-league",
  "status": "running",
  "phase": "leaderboard_league",
  "sessionId": "00000000-0000-0000-0000-000000000000",
  "lastCursor": "24500.12:0:0",
  "pagesCompleted": 12,
  "seenPlayerIdsFile": "seen_player_ids.txt",
  "counters": {
    "requestsSent": 12,
    "playersSeen": 1200,
    "uniquePlayers": 1200,
    "duplicatesSkipped": 0,
    "cachedSkipped": 0,
    "rejectedRecords": 0,
    "httpErrorsByStatus": {}
  },
  "updatedAt": "2026-10-20T12:15:00Z"
}
```

### Cómo se reanuda sin duplicar datos
- Al iniciar con `EXTRACTION_RESUME_RUN_ID=<runId>`, el proceso lee `checkpoint.json` y el conjunto `seen_player_ids.txt`.
- En Fase 1/3, retoma la paginación exactamente desde `after=<lastCursor>` usando el mismo `sessionId` (si la caché del worker sigue vigente) o registrando un cambio de sesión en el manifiesto si expiró.
- En Fase 2, descarta cualquier `playerId` ya presente en `seen_player_ids.txt` o cuya ventana `cacheValidUntil` siga activa en MongoDB.

---

## 5. Condiciones para pausar, detener y reintentar

| Evento observado | Acción del pipeline |
|---|---|
| Respuesta `200 OK` con `success: true` | Procesar página/registro, respetar `TETRA_CHANNEL_MIN_INTERVAL_MS` antes de la siguiente solicitud. |
| Recurso ya solicitado antes de `cache.cached_until` | No re-solicitar; usar copia local (`cachedSkipped++`). |
| HTTP `429` o `success: false` por límite de solicitudes | Respetar `Retry-After` si viene en cabecera; si no, aplicar backoff exponencial ($5\text{ s}, 15\text{ s}, 45\text{ s}$ + *jitter*). Si ocurre 3 veces seguidas, guardar checkpoint y cambiar estado a `paused`. |
| Timeout de red o HTTP `502`/`503`/`504` | Reintentar hasta `EXTRACTION_MAX_RETRIES` (por defecto 3) con espera exponencial ($2\text{ s}, 6\text{ s}, 18\text{ s}$ + *jitter*). |
| `EXTRACTION_MAX_CONSECUTIVE_ERRORS` alcanzado (p. ej. 5 fallos seguidos) | Guardar checkpoint atómico, registrar causa y detener el proceso con estado `paused` (código de salida distinto de 0). |
| Señal `SIGINT` / `SIGTERM` (parada manual del operador) | Completar la escritura del archivo en curso, guardar checkpoint con estado `paused` y terminar limpiamente. |
| `success: false` por usuario inexistente en Fase 2 | No reintentar; registrar en `rejected.jsonl` con motivo `USER_NOT_FOUND` y continuar con el siguiente jugador. |

---

## 6. Deduplicación y validación de esquemas

1. **Deduplicación en extracción (`data/raw` → `data/processed`):**
   - Durante el recorrido de un leaderboard, un jugador puede desplazarse entre páginas si juega partidas mientras se pagina. Se mantiene un conjunto en memoria y disco de `externalPlayerId` (`_id` de 24 hex). Si un `_id` aparece por segunda vez en el mismo `runId`, se conserva la primera ocurrencia y se incrementa `duplicatesSkipped`.
   - En MongoDB (`players` y `snapshots`), la deduplicación está garantizada por el índice único `externalPlayerId` en `players` y por el índice único `{ playerId: 1, cacheWindowKey: 1 }` en `snapshots` (ver `docs/DATA_MODEL.md`).
2. **Validación de esquemas y registro de rechazados (`rejected.jsonl`):**
   - Se valida que `_id` cumpla `^[0-9a-f]{24}$`, que `username` no sea vacío y que las métricas numéricas presentes sean finitas ($\ge 0$ tras convertir `-1` a `null`).
   - Cualquier registro que incumpla estas reglas no se inserta en el dataset procesado ni se rellena con ceros: se escribe en `data/processed/<datasetVersion>/rejected.jsonl` indicando `playerId`, campo inválido, valor original y motivo, y se incrementa `rejectedRecords`.

---

## 7. Estructura de `data/processed/<datasetVersion>/` y `manifest.json`

Cada ejecución completada (o consolidada) genera un directorio inmutable `data/processed/<datasetVersion>/` con:
- `players_league.jsonl` (o `.csv` / `.parquet` acordado entre backend e IA): registros normalizados listos para partición por `playerId`.
- `rejected.jsonl`: registros descartados por validación con su causa.
- `manifest.json`: metadatos obligatorios y métricas de calidad del dataset.

### Esquema obligatorio de `manifest.json`
```json
{
  "datasetVersion": "20261020-v1",
  "runId": "20261020T120000Z-league",
  "createdAt": "2026-10-20T12:30:00Z",
  "extractionWindow": {
    "startedAt": "2026-10-20T12:00:00Z",
    "finishedAt": "2026-10-20T12:07:10Z",
    "durationSeconds": 430
  },
  "sources": [
    {
      "endpoint": "GET /users/by/league",
      "baseUrl": "https://ch.tetr.io/api",
      "limit": 100,
      "sessionIdUsed": true
    }
  ],
  "featureSchemaVersion": "tr-cross-v1",
  "transformationsApplied": [
    "sentinel_-1_to_null",
    "rank_z_to_null_and_ranked_false",
    "username_trim_lowercase",
    "deduplicate_by_external_player_id"
  ],
  "counts": {
    "upstreamRankedCountAtStart": 36227,
    "pagesFetched": 0,
    "rawEntriesRead": 0,
    "uniquePlayersExported": 0,
    "duplicatesSkipped": 0,
    "cachedSkipped": 0,
    "rejectedRecords": 0,
    "httpErrorsByStatus": {}
  },
  "fields": {
    "tr": { "type": "float", "nullable": true, "nullCount": 0, "nullRate": 0.0 },
    "apm": { "type": "float", "nullable": true, "nullCount": 0, "nullRate": 0.0 },
    "pps": { "type": "float", "nullable": true, "nullCount": 0, "nullRate": 0.0 },
    "vs": { "type": "float", "nullable": true, "nullCount": 0, "nullRate": 0.0 },
    "gamesPlayed": { "type": "int", "nullable": false, "nullCount": 0, "nullRate": 0.0 }
  }
}
```

Los valores numéricos del ejemplo son ilustrativos de estructura. El script de entrenamiento (`ai_service/training/`) verificará la existencia de `manifest.json` antes de entrenar cualquier modelo y copiará `datasetVersion` y `featureSchemaVersion` en la ficha del modelo (`model-card`).
