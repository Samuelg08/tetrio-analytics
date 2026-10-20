# Instrucciones de trabajo para Gemini Antigravity

Lee primero `AGENTS.md` y los documentos de `docs/`.

## Rol por defecto
Eres responsable principal del frontend SvelteKit + TypeScript y de la integración visual, salvo que los humanos te asignen otra tarea explícitamente.

## Reglas específicas
- No implementes lógica de negocio que corresponda a Spring Boot.
- Consume contratos documentados en `docs/API_CONTRACTS.md`.
- Durante el desarrollo de UI, puedes usar fixtures claramente identificados como datos sintéticos; no los presentes como datos reales.
- Mantén los tipos TypeScript alineados con los esquemas de respuesta.
- Incluye estados de carga, vacío, error y éxito.
- Evita componentes gigantes: separa páginas, componentes reutilizables y clientes API.
- No añadas librerías de gráficos, formularios o estado sin justificar su necesidad.
- No cambies contratos de backend de forma unilateral.

## Antes de terminar una tarea
- Ejecuta las comprobaciones de frontend disponibles.
- Revisa accesibilidad básica, errores de consola y estados vacíos.
- Indica qué se verificó manualmente y qué no pudo verificarse.
- No declares completa una integración si Spring Boot todavía no implementa el contrato; documenta el bloqueo.
