# Frontend — TETR.IO Competitive Analytics

Frontend en SvelteKit + TypeScript. Consume exclusivamente la API pública de Spring Boot; no llama a MongoDB ni al servicio interno de IA.

## Requisitos

- Node.js 22 LTS y npm.
- Spring Boot en `http://localhost:8080` para consultar perfiles reales.

## Configuración y ejecución local

Desde `frontend/`:

```powershell
Copy-Item .env.example .env
npm install
npm run dev -- --host 127.0.0.1
```

Abre `http://localhost:5173`. `PUBLIC_API_BASE_URL` debe apuntar a la base `/api` de Spring Boot. La variable se expone al navegador y no debe contener secretos.

## Flujos implementados

### FE-001 — Búsqueda y perfil

- Buscar un jugador con `GET /api/players/by-username/{username}`.
- Mostrar perfil, país, procedencia (`live`, `cache` o `stale`) y métricas disponibles de TETRA LEAGUE.
- Representar como “Sin dato” los valores `null`; no se convierten en cero.
- Manejar carga, jugador no encontrado, errores de servicio y perfil sin resumen de liga.
- Usar “Ver perfil de demostración” cuando el backend no esté disponible para presentar la interfaz. El aviso identifica esos valores como sintéticos.

### FE-003 — Historial de TR

- Consultar `GET /api/players/{playerId}/snapshots` con filtro opcional por fechas y paginación de 100 registros.
- Graficar únicamente los snapshots que tengan TR; los valores ausentes permanecen como “Sin dato”.
- Incluir tabla accesible de observaciones, estados de carga/error/vacío y paginación.
- En modo demostración, usar fixtures identificados explícitamente como sintéticos; no enviar solicitudes de historial al backend.

## Comandos

```powershell
npm run check
npm test -- --run
npm run build
```
