# Arquitectura

## Componentes

### Frontend — SvelteKit
Presenta perfiles, estadísticas, récords, snapshots, comparaciones y rankings. Llama únicamente a Spring Boot. Gestiona estados de carga, vacío y error; no calcula reglas de negocio sensibles ni accede a MongoDB.

### Backend — Spring Boot
API principal. Responsabilidades:
- Validación y contratos REST.
- Cliente de TETRA CHANNEL con timeout, caché y manejo de límites.
- Reglas deterministas y métricas derivadas.
- Persistencia MongoDB.
- Autenticación/autorización local si entra en el alcance MVP.
- Llamadas al microservicio de IA.
- Registro de inferencias y versión del modelo.

### Base de datos — MongoDB
Almacena documentos normalizados en colecciones separadas. Los payloads originales solo se conservan si aportan valor y se respeta la política de uso de la fuente.

### Servicio de IA — FastAPI
Servicio interno que recibe características validadas, ejecuta modelos previamente entrenados y devuelve predicciones, versión e información de explicación disponible. No accede directamente a MongoDB en el MVP.

## Principios
- Sin Docker durante el desarrollo inicial.
- Configuración por variables de entorno.
- No hay acceso del frontend a MongoDB ni a FastAPI.
- El backend no debe depender de que la API externa esté disponible para leer datos ya almacenados.
- Toda inferencia debe identificar el modelo utilizado.
- Separar hechos observados, métricas derivadas y predicciones.

## Diagrama lógico

```text
SvelteKit -> Spring Boot -> MongoDB
                    |----> TETRA CHANNEL API
                    |----> FastAPI AI Service -> scikit-learn / SHAP
```

## Riesgos
- Disponibilidad real de historiales y métricas para entrenar modelos.
- Cambios o límites de la API externa.
- Fuga de datos al dividir capturas del mismo jugador.
- Incompatibilidad de entornos locales entre ordenadores.
- Alcance excesivo para cuatro semanas.
