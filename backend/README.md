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

## Endpoints implementados (BE-001)
- `GET /api/health` — Estado del servicio (`200 OK` `{ "status": "UP" }`).
- `GET /api/players/by-username/{username}` — Consulta en vivo (`dataSource: "live"`) de perfil y resumen de TETRA LEAGUE con normalización (`trim` + minúsculas), validación (`^[a-z0-9_-]{3,16}$`), rate limiter compartido (`<= 1 req/s`) y errores RFC 9457 `ProblemDetail` (`400`, `404`, `429`, `502`, `503`).
- `GET /v3/api-docs` y `GET /swagger-ui.html` — Especificación OpenAPI v0.2 generada por `springdoc-openapi`.
