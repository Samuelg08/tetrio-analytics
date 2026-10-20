# Roadmap de cuatro semanas

## Semana 1 — Base ejecutable
- Repositorio, convenciones y entornos.
- SvelteKit, Spring Boot y MongoDB.
- Verificar endpoints y payloads de TETRA CHANNEL.
- Primer flujo de búsqueda, persistencia y visualización.
**Gate:** jugador real visible desde MongoDB en frontend.

## Semana 2 — MVP de producto
- Estadísticas disponibles.
- Récords.
- Snapshots y evolución.
- Comparación descriptiva.
- Rankings/filtros básicos si los datos lo permiten.
**Gate:** recorrido principal funciona sin IA.

## Semana 3 — Dataset e IA
- Auditoría y limpieza de dataset.
- Baseline de regresión.
- Evaluación sin fuga de datos.
- Segundo modelo solo si hay mejora.
- FastAPI y contrato interno.
- SHAP si es compatible con el modelo y el tiempo.
**Gate:** al menos una inferencia real evaluada e integrada, o informe claro que documente el bloqueo por datos.

## Semana 4 — Calidad y entrega
- Integración.
- Pruebas unitarias y de extremo a extremo.
- Registro de versiones y predicciones.
- Documentación y demostración.
- Simulador H2H solo con etiquetas y ejemplos válidos.

## Priorización
Must-have: arranque reproducible, perfil, estadísticas disponibles, MongoDB, snapshots, pruebas y al menos un baseline de ML evaluado si los datos lo permiten.
Should-have: récords, comparación, rankings y filtros.
Stretch: historial completo de partidas, clasificación H2H avanzada y panel administrativo completo.
