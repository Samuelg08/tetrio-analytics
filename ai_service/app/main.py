import math
import re
import uuid
from typing import Any
from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from app.config import Settings
from app.model_registry import ModelRegistry
from app.schemas import (
    ALLOWED_FEATURES,
    FORBIDDEN_LEAKAGE_FEATURES,
    FieldValidationError,
    HealthResponse,
    ModelCardResponse,
    REQUIRED_FEATURES,
    SUPPORTED_FEATURE_SCHEMA_VERSION,
    TrEstimateRequest,
    TrEstimateResponse,
)

REQUEST_ID_HEADER = "X-Request-Id"
REQUEST_ID_PATTERN = re.compile(r"^[A-Za-z0-9-]{8,64}$")
PROBLEM_MEDIA_TYPE = "application/problem+json"


class ApiProblemError(Exception):
    def __init__(
        self,
        status: int,
        code: str,
        title: str,
        detail: str,
        errors: list[FieldValidationError] | None = None,
    ) -> None:
        super().__init__(detail)
        self.status = status
        self.code = code
        self.title = title
        self.detail = detail
        self.errors = errors or []


def resolve_trace_id(request: Request) -> str:
    state_trace_id = getattr(request.state, "trace_id", None)
    if isinstance(state_trace_id, str) and state_trace_id:
        return state_trace_id
    incoming = request.headers.get(REQUEST_ID_HEADER, "").strip()
    if incoming and REQUEST_ID_PATTERN.match(incoming):
        return incoming
    return str(uuid.uuid4())


def build_problem_response(
    request: Request,
    status: int,
    code: str,
    title: str,
    detail: str,
    errors: list[FieldValidationError] | None = None,
) -> JSONResponse:
    trace_id = resolve_trace_id(request)
    kebab = code.lower().replace("_", "-")
    body: dict[str, Any] = {
        "type": f"/problems/{kebab}",
        "title": title,
        "status": status,
        "detail": detail,
        "instance": request.url.path,
        "code": code,
        "traceId": trace_id,
    }
    if code == "VALIDATION_FAILED" and errors:
        body["errors"] = [e.model_dump() for e in errors]

    return JSONResponse(
        status_code=status,
        content=body,
        media_type=PROBLEM_MEDIA_TYPE,
        headers={REQUEST_ID_HEADER: trace_id},
    )


def create_app(
    settings: Settings | None = None,
    registry: ModelRegistry | None = None,
) -> FastAPI:
    active_settings = settings or Settings()
    active_registry = registry or ModelRegistry(active_settings)

    app = FastAPI(
        title="TETR.IO Competitive Analytics — Internal AI Service",
        version="0.2.0",
    )
    app.state.settings = active_settings
    app.state.registry = active_registry

    @app.middleware("http")
    async def attach_request_id(request: Request, call_next: Any) -> Any:
        trace_id = resolve_trace_id(request)
        request.state.trace_id = trace_id
        response = await call_next(request)
        response.headers[REQUEST_ID_HEADER] = trace_id
        return response

    @app.exception_handler(ApiProblemError)
    async def handle_api_problem(request: Request, exc: ApiProblemError) -> JSONResponse:
        return build_problem_response(
            request=request,
            status=exc.status,
            code=exc.code,
            title=exc.title,
            detail=exc.detail,
            errors=exc.errors,
        )

    @app.exception_handler(RequestValidationError)
    async def handle_request_validation(
        request: Request, exc: RequestValidationError
    ) -> JSONResponse:
        field_errors: list[FieldValidationError] = []
        for err in exc.errors():
            loc = [str(part) for part in err.get("loc", []) if part != "body"]
            field_name = ".".join(loc) if loc else "request"
            field_errors.append(
                FieldValidationError(field=field_name, message=err.get("msg", "Valor inválido"))
            )
        return build_problem_response(
            request=request,
            status=422,
            code="VALIDATION_FAILED",
            title="Validation failed",
            detail="Uno o más campos de la solicitud de inferencia no son válidos.",
            errors=field_errors,
        )

    @app.get("/health", response_model=HealthResponse)
    def get_health() -> HealthResponse:
        reg: ModelRegistry = app.state.registry
        card = reg.model_card
        if not reg.is_loaded or card is None:
            return HealthResponse(
                status="ok",
                modelLoaded=False,
                modelName=None,
                modelVersion=None,
                featureSchemaVersion=None,
            )
        return HealthResponse(
            status="ok",
            modelLoaded=True,
            modelName=card.modelName,
            modelVersion=card.modelVersion,
            featureSchemaVersion=card.featureSchemaVersion,
        )

    @app.get("/v1/model-card", response_model=ModelCardResponse)
    def get_model_card() -> ModelCardResponse:
        reg: ModelRegistry = app.state.registry
        if not reg.is_loaded or reg.model_card is None:
            raise ApiProblemError(
                status=503,
                code="MODEL_UNAVAILABLE",
                title="Model unavailable",
                detail="No hay ningún modelo entrenado cargado actualmente en el servicio de IA.",
            )
        return reg.model_card

    @app.post("/v1/tr-estimate", response_model=TrEstimateResponse)
    def post_tr_estimate(payload: TrEstimateRequest) -> TrEstimateResponse:
        reg: ModelRegistry = app.state.registry
        expected_schema = (
            reg.model_card.featureSchemaVersion
            if reg.is_loaded and reg.model_card is not None
            else SUPPORTED_FEATURE_SCHEMA_VERSION
        )

        if payload.featureSchemaVersion != expected_schema:
            raise ApiProblemError(
                status=422,
                code="FEATURE_SCHEMA_MISMATCH",
                title="Feature schema mismatch",
                detail=f"Se esperaba featureSchemaVersion '{expected_schema}'.",
            )

        validation_errors: list[FieldValidationError] = []
        for key in payload.features:
            if key in FORBIDDEN_LEAKAGE_FEATURES:
                validation_errors.append(
                    FieldValidationError(
                        field=f"features.{key}",
                        message="Variable prohibida por fuga directa del objetivo.",
                    )
                )
            elif key not in ALLOWED_FEATURES:
                validation_errors.append(
                    FieldValidationError(
                        field=f"features.{key}",
                        message="Variable desconocida o no permitida en el esquema activo.",
                    )
                )

        if validation_errors:
            raise ApiProblemError(
                status=422,
                code="VALIDATION_FAILED",
                title="Validation failed",
                detail="Se detectaron variables desconocidas o prohibidas en features.",
                errors=validation_errors,
            )

        for req_field in REQUIRED_FEATURES:
            if req_field not in payload.features or payload.features[req_field] is None:
                raise ApiProblemError(
                    status=422,
                    code="PREDICTION_NOT_APPLICABLE",
                    title="Prediction not applicable",
                    detail=f"La variable requerida '{req_field}' está ausente o es null; no se imputa 0.",
                )

        numeric_features: dict[str, float | int] = {}
        for req_field in REQUIRED_FEATURES:
            raw_val = payload.features[req_field]
            if isinstance(raw_val, bool) or not isinstance(raw_val, (int, float)):
                validation_errors.append(
                    FieldValidationError(
                        field=f"features.{req_field}",
                        message="El valor debe ser numérico finito y mayor o igual a 0.",
                    )
                )
                continue
            float_val = float(raw_val)
            if not math.isfinite(float_val) or float_val < 0.0:
                validation_errors.append(
                    FieldValidationError(
                        field=f"features.{req_field}",
                        message="El valor debe ser numérico finito y mayor o igual a 0.",
                    )
                )
                continue
            numeric_features[req_field] = raw_val

        if validation_errors:
            raise ApiProblemError(
                status=422,
                code="VALIDATION_FAILED",
                title="Validation failed",
                detail="Uno o más valores de entrada están fuera del rango permitido.",
                errors=validation_errors,
            )

        if not reg.is_loaded:
            raise ApiProblemError(
                status=503,
                code="MODEL_UNAVAILABLE",
                title="Model unavailable",
                detail="No hay ningún modelo entrenado cargado actualmente en el servicio de IA.",
            )

        return reg.predict_tr(numeric_features)

    return app


app = create_app()
