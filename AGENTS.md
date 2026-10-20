# Instrucciones globales para agentes de código

## Misión
Implementar TETR.IO Competitive Analytics como un MVP mantenible y verificable en cuatro semanas. Priorizar integración, corrección y pruebas frente a cantidad de código.

## Stack inmutable salvo aprobación humana
- Frontend: SvelteKit + TypeScript.
- Backend: Java + Spring Boot + Maven.
- Base de datos: MongoDB.
- Servicio de IA: Python + FastAPI + scikit-learn.
- No introducir Docker durante el desarrollo inicial.
- No sustituir tecnologías ni agregar servicios/infraestructura sin aprobación humana.

## Arquitectura
- El frontend llama exclusivamente a la API REST de Spring Boot.
- Spring Boot es dueño de la lógica de negocio, autenticación, sincronización externa y persistencia.
- Spring Boot llama a FastAPI por HTTP para inferencias.
- FastAPI no accede directamente a MongoDB en el MVP; recibe entradas validadas y devuelve resultados.
- Las reglas deterministas deben permanecer en el backend, no duplicarse en frontend ni servicio de IA.
- Mantener separación por funcionalidad y evitar abstracciones innecesarias.

## Datos y honestidad
- Usa solo endpoints documentados y permitidos de TETRA CHANNEL.
- No inventes campos, endpoints, datos de partidas, resultados ni métricas de modelos.
- Verifica las respuestas reales antes de codificar mapeos.
- Trata datos externos como no confiables: valida tipos, rangos, campos ausentes y errores.
- No confundas correlación/SHAP con causalidad o recomendaciones de entrenamiento demostradas.
- Evita data leakage: toda variable debe estar disponible en el momento de la predicción.
- No afirmes que una prueba pasó si no fue ejecutada.
- No reemplaces valores ausentes por cero sin una justificación explícita.

## Seguridad
- Nunca escribas secretos en código, documentación, commits, logs o prompts guardados en el repositorio.
- Usa variables de entorno y `.env.example` con valores ficticios.
- No relajes CORS, autenticación ni validaciones para ocultar errores.
- No ejecutes comandos destructivos, borres datos ni cambies permisos amplios sin autorización humana.
- Para MongoDB compartido, usa credenciales individuales y privilegios mínimos.

## Forma de trabajar
1. Lee `README.md` y los documentos relevantes antes de cambiar código.
2. Antes de una tarea grande, resume el plan y los archivos que pretendes modificar.
3. Implementa una tarea acotada cada vez.
4. No modifiques archivos fuera del alcance sin explicar por qué.
5. Añade o actualiza pruebas para cada cambio de comportamiento.
6. Ejecuta las pruebas disponibles y reporta los comandos exactos y resultados.
7. Revisa el diff para detectar archivos accidentales, secretos, cambios de formato y dependencias innecesarias.
8. Actualiza la documentación si cambian contratos, configuración o comandos.
9. Termina con un resumen: cambios, pruebas ejecutadas, resultados y pendientes.

## Colaboración
- No trabajes directamente en `main`.
- Respeta la propiedad de archivos acordada en `docs/WORK_SPLIT.md`.
- No reformatees archivos completos ni cambies convenciones ajenas a la tarea.
- No alteres contratos API sin actualizar `docs/API_CONTRACTS.md` y coordinar con el otro desarrollador.
- Si necesitas un cambio que afecta al otro módulo, propone primero el contrato y espera aprobación.

## Definición de terminado
- Cumple criterios de aceptación.
- Validaciones y errores claros.
- Pruebas relevantes añadidas y ejecutadas.
- Sin secretos ni datos sensibles.
- Documentación sincronizada.
- Sin dependencias no justificadas.
