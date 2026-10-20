# Contratos API iniciales

Estos contratos son una propuesta inicial, no una afirmación de disponibilidad de todos los datos externos. Mantenerlos sincronizados con OpenAPI una vez implementados.

Base URL local de backend: `http://localhost:8080/api`

## Convenciones
- JSON en UTF-8.
- Fechas en ISO 8601 UTC.
- Errores con estructura consistente.
- Paginación explícita en listas.
- Los identificadores externos de TETR.IO no se sustituyen por usernames.
- Si un campo no está disponible, usar `null` o no incluirlo según el contrato; no inventar valores.

## GET /players/{username}
Busca un jugador local y, según la política de caché, puede sincronizarlo desde TETRA CHANNEL.

Respuesta conceptual:
```json
{
  "playerId": "external-player-id",
  "username": "example",
  "country": null,
  "lastSyncedAt": "2026-01-01T12:00:00Z",
  "league": {
    "rating": null,
    "glicko": null,
    "rd": null,
    "rank": null
  },
  "dataSource": "cache"
}
```

Los valores son ilustrativos, no datos reales. Ajustar los campos tras verificar los payloads oficiales.

## GET /players/{playerId}/statistics
Devuelve métricas disponibles por modo. Incluir metadatos de origen y fecha cuando sean útiles. Las métricas derivadas deben documentar fórmula y unidades.

## GET /players/{playerId}/snapshots?from=&to=&limit=
Devuelve capturas históricas ordenadas cronológicamente y paginadas/limitadas.

## GET /players/{playerId}/records
Devuelve récords personales disponibles, separados por modo.

## GET /rankings?mode=&metric=&page=&size=
Ranking local basado exclusivamente en datos almacenados y elegibles. Documentar el universo de jugadores incluidos; no afirmar que es global si no contiene la clasificación global completa.

## POST /comparisons
Entrada conceptual:
```json
{
  "playerAId": "id-a",
  "playerBId": "id-b"
}
```
Devuelve comparación descriptiva. Añadir probabilidad H2H solo cuando el modelo y los datos hayan sido evaluados y la predicción sea aplicable.

## POST /internal/ai/rating-prediction
Contrato interno propuesto entre Spring Boot y FastAPI. No exponer directamente al navegador.

Entrada conceptual:
```json
{
  "playerId": "id-a",
  "features": {
    "pps": 1.2,
    "apm": 50.0,
    "vs": 100.0
  }
}
```

Respuesta conceptual:
```json
{
  "prediction": null,
  "modelVersion": "baseline-not-trained",
  "explanation": [],
  "status": "model_unavailable"
}
```

Los números son ejemplos de formato, no rangos garantizados ni predicciones. Definir validaciones según los datos reales. Mientras no exista un modelo entrenado, devolver estado explícito; no simular predicciones como si fueran reales.

## Errores
Formato propuesto:
```json
{
  "code": "PLAYER_NOT_FOUND",
  "message": "No se encontró el jugador solicitado",
  "traceId": "request-id"
}
```

Códigos sugeridos: `400`, `404`, `422`, `429`, `502`, `503`. No filtrar trazas internas, secretos ni respuestas sensibles del proveedor.
