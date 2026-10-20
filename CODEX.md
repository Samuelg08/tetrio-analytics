# Instrucciones de trabajo para Codex

Lee primero `AGENTS.md` y los documentos de `docs/`.

## Rol acordado (Compañero + Codex)
Eres responsable principal del **frontend SvelteKit + TypeScript**, de la interfaz de usuario, navegación, consumo de la API REST del backend, componentes visuales, gráficos y accesibilidad básica.

## Alcance de archivos
- `frontend/**`: SvelteKit, TypeScript, componentes, páginas, cliente HTTP, gestión de estados y visualizaciones.
- `scripts/frontend/**`.
- Pruebas unitarias y de componentes de frontend.

## Reglas específicas
- **No modifiques `backend/**` ni `ai_service/**`** sin autorización explícita previa.
- Consume exclusivamente los contratos documentados en `docs/API_CONTRACTS.md`. No cambies contratos de backend unilateralmente.
- Durante el desarrollo inicial de UI, puedes usar fixtures sintéticos claramente marcados en `src/lib/fixtures/`; no los presentes como datos reales.
- El frontend se comunica directamente con Spring Boot usando `PUBLIC_API_BASE_URL` (CORS configurado en el backend); no te conectes directamente a MongoDB ni a FastAPI.
- Mantén los tipos TypeScript sincronizados con los esquemas de respuesta del contrato (incluyendo `ProblemDetail` RFC 9457 para errores).
- Trata los valores ausentes (`null`) de forma explícita (p. ej. "No disponible"); nunca muestres `null` o `-1` como `0`.
- Incluye siempre los cuatro estados visuales: carga, vacío, error y éxito.
- Evita componentes gigantes: separa páginas, componentes reutilizables y clientes API.
- No añadas librerías de gráficos, estado o componentes sin justificar su necesidad técnica.
- Al inicializar el proyecto en `FE-001`, asegúrate de incluir y versionar `frontend/package-lock.json` para garantizar builds reproducibles en CI.

## Antes de terminar una tarea
- Ejecuta las comprobaciones de frontend disponibles (`npm run check`, `npm test -- --run`, `npm run build`).
- Revisa accesibilidad básica (semántica HTML, navegación por teclado), errores de consola y estados vacíos.
- Informa con honestidad qué se verificó con fixtures y qué queda pendiente de integración real con Spring Boot.