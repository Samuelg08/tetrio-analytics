# Backend — TETR.IO Competitive Analytics

Aplicación Java 21 LTS + Spring Boot 3.5.16 + Maven Wrapper (`./mvnw`).

## Requisitos previos
- **Java 21 LTS** configurado en `JAVA_HOME` (verificado `21.0.3 LTS`).
- No requiere instalación global de Maven (utiliza `./mvnw` / `mvnw.cmd`).
- *(Nota para Windows con antivirus que inspeccionan TLS como Avast):* definir `MAVEN_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT` para que la JVM utilice el almacén nativo de certificados del sistema operativo.

## Configuración local
1. Copia `backend/.env.example` a `backend/.env` (ignorado por Git) o define las variables de entorno en el sistema/IDE:
   ```powershell
   Copy-Item .env.example .env
   ```
2. `application.yml` importa opcionalmente `optional:file:.env[.properties]` y aplica valores por defecto seguros para desarrollo local (`SERVER_PORT=8080`, `CORS_ALLOWED_ORIGINS=http://localhost:5173`, `TETRA_CHANNEL_MIN_INTERVAL_MS=1100`).

## Comandos principales (desde `backend/`)

### Ejecutar pruebas automatizadas (deterministas, sin llamadas a red externa)
```powershell
./mvnw.cmd -B test
```
En Linux/macOS/CI:
```bash
./mvnw -B test
```

### Ejecutar smoke test en vivo contra TETRA CHANNEL (opcional, bajo demanda)
```powershell
$env:RUN_LIVE_TETRA_TESTS="true"; ./mvnw.cmd -B -Dtest=PlayerLiveSmokeTest "-DargLine=-Djavax.net.ssl.trustStoreType=Windows-ROOT" test
```

### Arrancar el servidor en desarrollo local
```powershell
./mvnw.cmd spring-boot:run
```

### Ejecutar el pipeline CLI de extracción (Fase 1 `leaderboard_league`, perfil `extraction`)
> **Importante:** el pipeline nunca se ejecuta al levantar el servidor web normal. Solo debe ejecutarse bajo la regla de operador único y con `SPRING_PROFILES_ACTIVE=extraction` + `EXTRACTION_ENABLED=true`.

```powershell
$env:SPRING_PROFILES_ACTIVE="extraction"
$env:EXTRACTION_ENABLED="true"
$env:EXTRACTION_MAX_PAGES="0" # 0 = ranking completo (~363 páginas); usar p. ej. 2 para prueba corta
./mvnw.cmd spring-boot:run
```
Para reanudar una ejecución pausada:
```powershell
$env:SPRING_PROFILES_ACTIVE="extraction"
$env:EXTRACTION_ENABLED="true"
$env:EXTRACTION_RESUME_RUN_ID="<runId>"
./mvnw.cmd spring-boot:run
```

## Endpoints implementados (BE-001 + BE-002 + BE-003)
- `GET /api/health` — Estado del servicio (`200 OK` `{ "status": "UP" }`).
- `GET /api/players/by-username/{username}` — Consulta de perfil y resumen de TETRA LEAGUE con normalización (`trim` + minúsculas), validación (`^[a-z0-9_-]{3,16}$`), caché local en MongoDB (`dataSource: "cache"` si `now < cacheValidUntil`), sincronización con TETRA CHANNEL (`dataSource: "live"`), degradación controlada (`dataSource: "stale"` si TETRA CHANNEL o el rate limiter rechazan la consulta pero existe copia local) y errores RFC 9457 `ProblemDetail` (`400`, `404`, `429`, `502`, `503`).
- `GET /api/players/{playerId}` — Consulta equivalente por MongoID de 24 caracteres hexadecimales (`^[0-9a-f]{24}$`).
- `GET /api/players/{playerId}/snapshots?from=&to=&page=&size=` — Historial paginado (`PageResponse<SnapshotItemDto>`) de snapshots del jugador en orden cronológico ascendente (`capturedAt ASC`).
- `GET /v3/api-docs` y `GET /swagger-ui.html` — Especificación OpenAPI v0.2 generada por `springdoc-openapi`.

## Persistencia e índices (BE-002)
- Colección `players`: índice único `{ externalPlayerId: 1 }` e índice de búsqueda `{ usernameLower: 1 }`.
- Colección `snapshots`: índice único compuesto no-sparse `{ playerId: 1, cacheWindowKey: 1 }` e índice de historial `{ playerId: 1, capturedAt: -1 }`.
- Upsert idempotente: `PlayerPersistenceService` actualiza `players` mediante `$set` y persiste `snapshots` con `$setOnInsert` bajo la clave determinista `cacheWindowKey = "${playerId}:${windowStartMs}"`.
