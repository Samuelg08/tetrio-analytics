## Resumen
<!-- ¿Qué cambia, por qué y a qué tarea (p. ej. PR-000, BE-001, FE-001, AI-001) corresponde? -->

## Responsable y alcance
- [ ] Frontend (`frontend/**`) — Codex
- [ ] Backend (`backend/**`) — Antigravity
- [ ] Servicio de IA (`ai_service/**`) — Antigravity
- [ ] Archivos compartidos (`docs/**`, `README.md`, `.github/**`, raíz) — Coordinación de ambos desarrolladores

## Verificación y Definition of Done
- [ ] Cumple los criterios de aceptación de la tarea
- [ ] Pruebas automatizadas ejecutadas localmente (indicar comandos y resultados exactos abajo)
- [ ] Manejo de estados de error (`ProblemDetail` RFC 9457), valores ausentes (`null`, nunca `-1` ni `0` inventado) y entradas inválidas
- [ ] Contratos en `docs/API_CONTRACTS.md` y OpenAPI alineados si aplica
- [ ] Diff revisado: sin archivos fuera de alcance, sin reformateos masivos ni dependencias no autorizadas
- [ ] Sin secretos, `.env`, credenciales de MongoDB Atlas ni datos de `data/raw/` o `data/processed/`

## Comandos ejecutados y resultados reales
```text
# Pegar aquí los comandos ejecutados y el resumen real de salida
```

## Notas, limitaciones y bloqueos
<!-- Indicar qué se verificó manualmente, qué no pudo verificarse o si algún contrato depende de otra tarea. -->
