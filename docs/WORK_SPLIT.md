# Reparto de trabajo

## Persona A — Antigravity (responsable principal de frontend)
- `frontend/**`
- experiencia de usuario, componentes y gráficos
- cliente HTTP y estados de carga/error/vacío
- pruebas de frontend

## Persona B — Codex (responsable principal de backend)
- `backend/**`
- Spring Boot, MongoDB, cliente TETRA CHANNEL y pruebas Java
- contrato REST y manejo de errores

## Servicio de IA
Asignar un único responsable por tarea o sprint. No permitir que ambos agentes modifiquen simultáneamente `ai_service/**`. Antes de empezar, acordar contrato con backend.

## Archivos compartidos
- `AGENTS.md`
- `README.md`
- `docs/API_CONTRACTS.md`
- `.env.example`
- configuraciones raíz

Estos archivos requieren coordinación y revisión humana antes de cambios.

## Flujo de integración
1. Crear rama de tarea.
2. Implementar y probar.
3. Revisar diff.
4. Abrir pull request.
5. Confirmar que no cambia contratos sin coordinación.
6. Fusionar tras pruebas satisfactorias.

Los agentes no deben asumir que conocen los cambios no fusionados del otro ordenador.
