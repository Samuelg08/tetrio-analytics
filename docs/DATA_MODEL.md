# Modelo de datos MongoDB

Los esquemas son propuestas iniciales; ajustarlos después de inspeccionar payloads reales. Los valores ausentes se guardan como `null` o se omiten; los centinelas `-1` de TETRA CHANNEL no se almacenan como valores numéricos válidos.

## players
- `_id`: ObjectId interno
- `externalPlayerId`: ID estable del proveedor (`_id` de TETR.IO, MongoID de 24 hex); índice único
- `username`: nombre observado
- `usernameLower`: username normalizado para búsquedas; índice (no único: un username puede reasignarse con el tiempo)
- `country`: opcional
- `profile`: campos normalizados disponibles
- `league`: último resumen de TETRA LEAGUE normalizado (mismos campos que `PlayerProfile.league` en `docs/API_CONTRACTS.md`)
- `lastSyncedAt`: fecha UTC de la última sincronización exitosa (`live`)
- `cacheWindowStartAt`: inicio UTC de la ventana de caché efectiva vigente (obligatorio tras la primera sincronización)
- `cacheValidUntil`: fin UTC de la ventana de caché efectiva vigente (obligatorio tras la primera sincronización)
- `activeCacheWindowKey`: clave determinista de la ventana de caché efectiva vigente (obligatorio tras la primera sincronización)
- `sourceSchemaVersion`: versión del adaptador de datos

No usar el username como identidad estable.

## snapshots y estrategia determinista de unicidad (C12)

### Campos del documento `snapshots`
- `_id`: ObjectId interno
- `playerId`: `externalPlayerId` del jugador (`NOT NULL`)
- `cacheWindowKey`: clave determinista de idempotencia por jugador y ventana de caché efectiva (`string`, **obligatorio / `NOT NULL`**, nunca vacío)
- `windowStartAt`: inicio UTC de la ventana de caché efectiva (`NOT NULL`)
- `windowUntilAt`: fin UTC de la ventana de caché efectiva (`NOT NULL`)
- `capturedAt`: instante UTC de captura (`NOT NULL`)
- `rating`, `glicko`, `rd`, `rank` cuando estén disponibles (en la API pública `rating` se expone como `tr`)
- `metrics`: métricas observadas/derivadas (`apm`, `pps`, `vs`, `gxe`, `gamesPlayed`, `gamesWon`, `standing`, `percentile`)
- `source`: metadatos de los endpoints combinados (`userCachedAt`, `userCachedUntil`, `leagueCachedAt`, `leagueCachedUntil`, `sessionId`)
- `schemaVersion`: versión del esquema

### Combinación de marcas temporales de múltiples endpoints y cálculo de `cacheWindowKey`

Una sincronización en vivo de jugador combina `GET /users/:user` (perfil) y `GET /users/:user/summaries/league` (resumen competitivo). Cada respuesta de TETRA CHANNEL incluye un bloque `cache` con `cached_at` y `cached_until` en milisegundos epoch UTC, pero estos valores pueden diferir entre ambos endpoints o faltar ante cambios del proveedor:

1. **Instante de sincronización:** sea $t_{\text{sync}}$ el tiempo epoch en milisegundos UTC cuando el backend recibe ambas respuestas, y $W = \text{CACHE\_TTL\_SECONDS} \times 1000$.
2. **Reutilización de ventana activa:** si el documento en `players` ya tiene `cacheValidUntil` con $t_{\text{sync}} < \text{cacheValidUntil}$, la sincronización cae dentro de la ventana de caché efectiva ya abierta; se reutilizan `windowStartMs`, `windowUntilMs` y `activeCacheWindowKey` existentes.
3. **Cálculo de una nueva ventana efectiva** (cuando no hay ventana activa o ya expiró):
   - `windowStartMs` (inicio determinista):
     - Si el resumen de liga trae `cache.cached_at` válido (`leagueCachedAt`), se toma `leagueCachedAt` (porque las métricas del snapshot provienen de ese resumen).
     - En su defecto, si el perfil trae `cache.cached_at` válido (`userCachedAt`), se toma `userCachedAt`.
     - Si ambos faltan, se discretiza $t_{\text{sync}}$ al cubo determinista de tamaño $W$: $\lfloor t_{\text{sync}} / W \rfloor \times W$.
   - `windowUntilMs` (fin determinista de la ventana efectiva):
     - **Estrategia conjunta (refresco simultáneo de perfil y resumen):** dado que perfil y resumen se refrescan juntos para construir el jugador consolidado y su snapshot, la ventana efectiva debe cubrir las expiraciones de ambos recursos de upstream y el TTL local. Se toma el **máximo** de los valores `cache.cached_until` presentes:
       $\text{upstreamUntilMs} = \max(\text{leagueCachedUntil}, \text{userCachedUntil})$ (sobre los valores reportados).
       El fin determinista de la ventana efectiva se calcula cubriendo tanto upstream como el TTL local:
       $\text{windowUntilMs} = \max(\text{upstreamUntilMs}, t_{\text{sync}} + W)$. Si el proveedor no informa `cached_until` en ninguno, $\text{windowUntilMs} = t_{\text{sync}} + W$.
     - **Estrategia de refresco independiente (alternativa desacoplada):** si una implementación futura contempla refrescar perfil y resumen por separado de forma autónoma, el modelo documenta y mantiene una expiración por recurso en `players` (`userValidUntil` y `leagueValidUntil`), calculadas individualmente como $\max(\text{cached\_until}_{i}, t_{\text{sync}} + W)$, invalidando cada recurso solo cuando venza su respectiva ventana. Para el MVP se utiliza la **estrategia conjunta** con el fin de preservar la atomicidad y coherencia entre datos de usuario y estadísticas de liga.
4. **Clave de idempotencia obligatoria (`cacheWindowKey`):**
   - Se construye siempre con el formato canónico `"${playerId}:${windowStartMs}"`.
   - El servicio valida que `playerId` y `cacheWindowKey` no sean `null` ni vacíos antes de escribir en MongoDB.

### Cómo se impide cualquier duplicado dentro de la ventana efectiva
1. **Corte previo por caché:** mientras $t_{\text{now}} < \text{players.cacheValidUntil}$, el backend sirve desde MongoDB (`dataSource: "cache"`) y no invoca la creación de snapshots.
2. **Misma versión cacheada del proveedor:** si la ventana local expiró pero TETRA CHANNEL devuelve exactamente la misma respuesta cacheada (`leagueCachedAt` idéntico), `windowStartMs` produce el mismo `cacheWindowKey`.
3. ** Escritura idempotente en MongoDB:** la inserción del snapshot se realiza mediante upsert atómico con filtro `{ playerId, cacheWindowKey }` y operador `$setOnInsert`. Si dos hilos concurrentes o un reintento evalúan la misma ventana, el segundo upsert es un *no-op*.

### Índices obligatorios en `snapshots`
- `{ playerId: 1, cacheWindowKey: 1 }` **único** (sin `sparse` ni exclusión de nulos, ya que `cacheWindowKey` es obligatorio).
- `{ playerId: 1, capturedAt: -1 }` para consultas de evolución temporal paginadas.

## records
- `_id`
- `playerId`
- `mode`
- `recordType`
- `value`
- `achievedAt` si está disponible
- `source`

Índice recomendado: `{ playerId: 1, mode: 1, recordType: 1 }`.

Estructura pendiente de verificar con los resúmenes de 40 LINES y BLITZ; la documentación de TETRA CHANNEL advierte que los Records pueden cambiar de estructura.

## matches
Solo crear documentos si se obtienen registros reales con campos suficientes y uso permitido:
- identificador de partida externo
- fecha
- participantes
- resultado
- métricas previas/al finalizar, distinguidas con claridad
- fuente y versión del esquema

No reconstruir ni inventar partidas que la API no proporcione. Cada partida aparece en el historial de ambos jugadores: deduplicar por identificador externo.

## predictions
- `_id`
- `playerId` o referencias de participantes
- `task`: `tr_cross_regression` o `head_to_head`
- `features` efectivamente utilizadas
- `featureSchemaVersion`
- `prediction`
- `modelName`, `modelVersion`
- `createdAt`
- `evaluationContext` si aplica
- `explanation` opcional

## model_versions
- `_id`
- `modelName`
- `version`
- `trainedAt`
- `datasetVersion`
- `featureSchemaVersion`
- `metrics`
- `artifactPath` o referencia de artefacto
- `status`

Se registra a partir de la ficha del modelo (`GET /v1/model-card` del servicio de IA).

## extraction_runs
Una entrada por ejecución del pipeline de extracción (ver detalle en `docs/DATA_PIPELINE.md`):
- `_id` / `runId`
- `startedAt`, `updatedAt`, `finishedAt`
- `status`: `running`, `paused`, `failed`, `completed`
- `phase` y `cursor` actuales (p. ej. último `prisecter` procesado o índice de lote)
- `sessionId`: UUID de sesión usado para paginación consistente
- `parameters`: fases activas, intervalo mínimo entre solicitudes, tamaño de lote
- `counters`: páginas, jugadores únicos, duplicados descartados, rechazados, errores HTTP por código
- `rawPath`: ruta relativa en `data/raw/`
- `datasetVersion` generado, si aplica

El checkpoint de reanudación se escribe también de forma atómica como archivo (`checkpoint.json`) en `data/raw/tetra-channel/<runId>/` para poder reanudar aunque MongoDB no esté disponible.

## Dataset procesado (archivos, fuera de Git)
Contrato entre el pipeline (backend) y el entrenamiento (servicio de IA). Ubicación: `data/processed/<datasetVersion>/`.
- Datos tabulares normalizados, una fila por jugador y ejecución de extracción, con `playerId` para la partición por jugador.
- `manifest.json` con: `datasetVersion`, `runId`, fuentes (endpoints), fechas de extracción, lista de campos con tipo y unidad, transformaciones aplicadas, recuentos (páginas, jugadores, registros, rechazados, duplicados) y tasa de nulos por campo.

El formato concreto de archivo y la lista de columnas se detallan en `docs/DATA_PIPELINE.md`.

## users
Fuera del MVP salvo requisito obligatorio del curso (C8). Si se incorpora:
- identificador
- email/username normalizado
- hash de contraseña generado por algoritmo de password hashing apropiado
- roles
- timestamps

Nunca almacenar contraseñas en texto plano ni guardar secretos de proveedor en documentos de usuario.
