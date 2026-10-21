from typing import Any, Literal
from pydantic import BaseModel, ConfigDict, Field

SUPPORTED_FEATURE_SCHEMA_VERSION = "tr-cross-v1"
REQUIRED_FEATURES: tuple[str, ...] = ("apm", "pps", "vs", "gamesPlayed")
ALLOWED_FEATURES: frozenset[str] = frozenset(REQUIRED_FEATURES)
FORBIDDEN_LEAKAGE_FEATURES: frozenset[str] = frozenset(
    {
        "tr",
        "glicko",
        "rd",
        "gxe",
        "rank",
        "bestRank",
        "standing",
        "standing_local",
        "percentile",
        "percentile_rank",
        "next_rank",
        "prev_rank",
        "next_at",
        "prev_at",
        "gamesWon",
        "winRate",
        "ar",
    }
)


class FieldValidationError(BaseModel):
    field: str
    message: str


class HealthResponse(BaseModel):
    status: Literal["ok"] = "ok"
    modelLoaded: bool
    modelName: str | None = None
    modelVersion: str | None = None
    featureSchemaVersion: str | None = None


class ModelCardResponse(BaseModel):
    model_config = ConfigDict(extra="allow")

    modelName: str
    modelVersion: str
    datasetVersion: str
    featureSchemaVersion: str
    trainedAt: str
    metrics: dict[str, float]
    status: str = "ready"


class TrEstimateRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    requestId: str = Field(..., min_length=1)
    featureSchemaVersion: str = Field(..., min_length=1)
    features: dict[str, Any]


class FeatureContribution(BaseModel):
    feature: str
    contribution: float


class TrPredictionValue(BaseModel):
    tr: float


class TrEstimateResponse(BaseModel):
    status: Literal["ok"] = "ok"
    prediction: TrPredictionValue
    modelName: str
    modelVersion: str
    featureSchemaVersion: str
    explanation: list[FeatureContribution] = Field(default_factory=list)
    explanationMethod: str | None = None
