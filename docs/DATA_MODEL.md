# Modelo de datos MongoDB

Los esquemas son propuestas iniciales; ajustarlos después de inspeccionar payloads reales.

## players
- `_id`: ObjectId interno
- `externalPlayerId`: ID estable del proveedor; índice único cuando esté disponible
- `username`: nombre observado
- `country`: opcional
- `profile`: campos normalizados disponibles
- `lastSyncedAt`: fecha UTC
- `sourceSchemaVersion`: versión del adaptador de datos

No usar el username como identidad estable.

## snapshots
- `_id`
- `playerId`
- `capturedAt`
- `rating`, `glicko`, `rd`, `rank` cuando estén disponibles
- `metrics`: métricas observadas/derivadas
- `source`: proveedor/endpoints usados
- `schemaVersion`

Índice recomendado: `{ playerId: 1, capturedAt: -1 }`.

## records
- `_id`
- `playerId`
- `mode`
- `recordType`
- `value`
- `achievedAt` si está disponible
- `source`

Índice recomendado: `{ playerId: 1, mode: 1, recordType: 1 }`.

## matches
Solo crear documentos si se obtienen registros reales con campos suficientes y uso permitido:
- identificador de partida externo
- fecha
- participantes
- resultado
- métricas previas/al finalizar, distinguidas con claridad
- fuente y versión del esquema

No reconstruir ni inventar partidas que la API no proporcione.

## predictions
- `_id`
- `playerId` o referencias de participantes
- `task`: `rating_regression` o `head_to_head`
- `features` efectivamente utilizadas
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

## users
Solo si entra en el MVP:
- identificador
- email/username normalizado
- hash de contraseña generado por algoritmo de password hashing apropiado
- roles
- timestamps

Nunca almacenar contraseñas en texto plano ni guardar secretos de proveedor en documentos de usuario.
