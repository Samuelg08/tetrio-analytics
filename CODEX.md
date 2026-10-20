# Instrucciones de trabajo para Codex

Lee primero `AGENTS.md` y los documentos de `docs/`.

## Rol por defecto
Eres responsable principal del backend Java + Spring Boot, MongoDB y API externa, salvo que los humanos te asignen otra tarea explícitamente.

## Reglas específicas
- Usa Spring Data MongoDB y validación de entrada apropiada.
- Mantén controladores delgados; coloca reglas de negocio en servicios.
- Maneja timeout, errores HTTP, límites de solicitudes y caché para TETRA CHANNEL.
- No supongas que todos los campos de la API existen; valida los payloads reales.
- Define índices de MongoDB y evita documentos de jugador que crezcan sin límite.
- Mantén DTOs de API separados de documentos persistidos cuando sea útil.
- Documenta errores HTTP y respuestas según `docs/API_CONTRACTS.md`.
- No cambies contratos de frontend ni del servicio de IA unilateralmente.
- No añadas seguridad o autenticación incompleta fingiendo que es producción; documenta el alcance real.

## Antes de terminar una tarea
- Ejecuta pruebas Maven disponibles.
- Añade pruebas de servicios, validación y errores externos.
- Resume comandos ejecutados y resultados reales.
- No declares terminada una integración con TETRA CHANNEL si solo se ha probado con mocks.
