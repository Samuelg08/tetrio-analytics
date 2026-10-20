# Roadmap de cuatro semanas

Fechas relativas al inicio del desarrollo tras fusionar PR-000. Cada tarea tiene un único responsable (ver `docs/WORK_SPLIT.md`).

## Semana 1 — Base ejecutable

| Día | Común / humanos | Antigravity (backend → IA) | Codex (frontend) |
|---|---|---|---|
| 1 | Revisar y fusionar PR-000. Configurar Atlas (usuarios individuales) y la protección de `main`. | PR-000 (documentación). Verificar JDK 21 y acceso a Atlas. | Verificar Node 22 y entorno frontend. |
| 2–3 | — | **BE-001:** esqueleto Spring Boot, cliente TETRA CHANNEL, `GET /api/players/by-username/{username}` en vivo, ProblemDetail, CORS, OpenAPI. | **FE-001:** esqueleto SvelteKit, cliente API tipado, búsqueda y perfil con fixtures sintéticos. |
| 4 | — | **BE-002:** persistencia de `players` y `snapshots`, índices, ventana de caché efectiva, `dataSource`. | **FE-002:** pruebas de componentes, accesibilidad, páginas de error y estados vacíos. |
| 5 | **Integración:** frontend contra el backend real. | Corrección de fallos de integración / soporte API. | Conexión con Spring Boot y validación de flujos. |
| 6 | — | **BE-003:** endpoint de snapshots; fase 1 del pipeline (leaderboard completo, reanudable). | **FE-003:** vista de evolución (snapshots). |
| 7 | Revisión, retrospectiva y ajuste del roadmap. | Informe de la primera ejecución del pipeline (cifras reales) y preparación de entorno IA. | Pruebas de componentes, a11y y estados vacíos/error. |

**Gate:** jugador real visible desde MongoDB en el frontend.

## Semana 2 — MVP de producto y dataset
- Estadísticas disponibles y métricas derivadas documentadas.
- Récords (tras verificar los payloads de 40 LINES y BLITZ).
- Snapshots y evolución.
- Comparación descriptiva.
- Rankings locales con metadatos del universo.
- Pipeline: enriquecimiento por lotes, histórico por temporada, manifiesto con métricas de calidad.
- Integración de springdoc-openapi verificada frente a `docs/API_CONTRACTS.md`.

**Gate:** el recorrido principal funciona sin IA y existe un dataset versionado con cifras reales (páginas, jugadores, registros, campos y tasas de nulos).

## Semana 3 — Dataset e IA
- Auditoría y limpieza del dataset (sin sustituir ausentes por cero).
- Baseline de regresión transversal del TR con el esquema `tr-cross-v1`.
- Evaluación sin fuga de datos: partición por jugador y, si es posible, validación por temporada; documentar la limitación si no lo es.
- Segundo modelo solo si mejora al baseline.
- FastAPI con modelo real y contrato interno; endpoint público `tr-estimate`.
- SHAP si es compatible con el modelo y el tiempo.
- H2H solo si existen datos y etiquetas suficientes (baseline Glicko-2).

**Gate:** al menos una inferencia real evaluada e integrada, o un informe claro que documente el bloqueo por datos.

## Semana 4 — Calidad y entrega
- Integración.
- Pruebas unitarias y de extremo a extremo.
- Registro de versiones y predicciones.
- Documentación y demostración.
- Simulador H2H solo con etiquetas y ejemplos válidos.

## Priorización
Must-have: arranque reproducible, perfil, estadísticas disponibles, MongoDB, snapshots, pipeline de extracción reanudable con métricas de calidad, pruebas y al menos un baseline de ML evaluado si los datos lo permiten.
Should-have: récords, comparación, rankings locales y filtros, SHAP.
Stretch: historial completo de partidas, clasificación H2H avanzada y panel administrativo completo.
