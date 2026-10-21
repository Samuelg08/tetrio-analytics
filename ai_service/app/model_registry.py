import json
from pathlib import Path
from typing import Any
import joblib
import numpy as np
from app.config import Settings
from app.schemas import (
    FeatureContribution,
    ModelCardResponse,
    REQUIRED_FEATURES,
    TrEstimateResponse,
    TrPredictionValue,
)


class ModelRegistry:
    """Manages loading and querying the trained TR cross-regression model.

    Never fabricates a dummy model when artifacts are missing.
    """

    def __init__(self, settings: Settings, base_dir: Path | None = None) -> None:
        self._settings = settings
        self._base_dir = base_dir
        self._model: Any | None = None
        self._model_card: ModelCardResponse | None = None
        self.reload()

    def reload(self) -> None:
        model_path = self._settings.resolve_model_path(self._base_dir)
        card_path = self._settings.resolve_model_card_path(self._base_dir)

        if not model_path.is_file() or not card_path.is_file():
            self._model = None
            self._model_card = None
            return

        with card_path.open("r", encoding="utf-8") as fh:
            raw_card = json.load(fh)

        self._model_card = ModelCardResponse.model_validate(raw_card)
        self._model = joblib.load(model_path)

    @property
    def is_loaded(self) -> bool:
        return self._model is not None and self._model_card is not None

    @property
    def model_card(self) -> ModelCardResponse | None:
        return self._model_card

    def predict_tr(self, features: dict[str, float | int]) -> TrEstimateResponse:
        if not self.is_loaded or self._model is None or self._model_card is None:
            raise RuntimeError("No hay ningún modelo cargado en memoria.")

        feature_vector = np.array(
            [[float(features[col]) for col in REQUIRED_FEATURES]],
            dtype=np.float64,
        )
        raw_pred = float(self._model.predict(feature_vector)[0])

        return TrEstimateResponse(
            status="ok",
            prediction=TrPredictionValue(tr=raw_pred),
            modelName=self._model_card.modelName,
            modelVersion=self._model_card.modelVersion,
            featureSchemaVersion=self._model_card.featureSchemaVersion,
            explanation=[],
            explanationMethod=None,
        )
