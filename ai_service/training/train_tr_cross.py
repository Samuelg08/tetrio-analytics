import argparse
import json
import math
from datetime import datetime, timezone
from pathlib import Path
from typing import Any
import joblib
import numpy as np
from sklearn.base import clone
from sklearn.ensemble import RandomForestRegressor
from sklearn.linear_model import Ridge
from sklearn.metrics import mean_absolute_error, mean_squared_error, r2_score
from sklearn.model_selection import GroupKFold
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler
from app.schemas import REQUIRED_FEATURES, SUPPORTED_FEATURE_SCHEMA_VERSION
from training.dataset_loader import load_and_verify_dataset


def _is_valid_non_negative_number(val: Any) -> bool:
    if val is None or isinstance(val, bool) or not isinstance(val, (int, float)):
        return False
    fval = float(val)
    return math.isfinite(fval) and fval >= 0.0


def _compute_regression_metrics(y_true: np.ndarray, y_pred: np.ndarray) -> dict[str, float]:
    mae = float(mean_absolute_error(y_true, y_pred))
    rmse = float(math.sqrt(mean_squared_error(y_true, y_pred)))
    r2 = float(r2_score(y_true, y_pred))
    return {
        "mae": round(mae, 4),
        "rmse": round(rmse, 4),
        "r2": round(r2, 4),
    }


def _evaluate_group_kfold(
    estimator: Any,
    X: np.ndarray,
    y: np.ndarray,
    groups: list[str],
    n_splits: int = 5,
) -> dict[str, float]:
    gkf = GroupKFold(n_splits=n_splits)
    oof_preds = np.zeros_like(y, dtype=np.float64)

    for train_idx, test_idx in gkf.split(X, y, groups):
        fold_model = clone(estimator)
        fold_model.fit(X[train_idx], y[train_idx])
        oof_preds[test_idx] = fold_model.predict(X[test_idx])

    return _compute_regression_metrics(y, oof_preds)


def _evaluate_out_of_season(
    ridge_model: Any,
    rf_model: Any,
    processed_dir: Path,
    season_dataset_version: str,
    train_player_ids: set[str],
) -> dict[str, Any]:
    season_loaded = load_and_verify_dataset(processed_dir, season_dataset_version)
    valid_rows: list[dict[str, Any]] = []
    disjoint_rows: list[dict[str, Any]] = []

    for row in season_loaded.records:
        if not row.get("ranked", False) or not _is_valid_non_negative_number(row.get("tr")):
            continue
        if all(_is_valid_non_negative_number(row.get(col)) for col in REQUIRED_FEATURES):
            valid_rows.append(row)
            if str(row["playerId"]) not in train_player_ids:
                disjoint_rows.append(row)

    result: dict[str, Any] = {
        "seasonDatasetVersion": season_loaded.dataset_version,
        "seasonRunId": season_loaded.run_id,
        "totalSeasonRecords": len(valid_rows),
        "disjointPlayersCount": len(disjoint_rows),
        "overlappingPlayersExcluded": len(valid_rows) - len(disjoint_rows),
    }

    if len(valid_rows) >= 5:
        X_all = np.array([[float(r[col]) for col in REQUIRED_FEATURES] for r in valid_rows], dtype=np.float64)
        y_all = np.array([float(r["tr"]) for r in valid_rows], dtype=np.float64)
        result["allSeasonPlayers"] = {
            "tr-cross-ridge-baseline": _compute_regression_metrics(y_all, ridge_model.predict(X_all)),
            "tr-cross-rf-v1": _compute_regression_metrics(y_all, rf_model.predict(X_all)),
        }

    if len(disjoint_rows) >= 5:
        X_disj = np.array([[float(r[col]) for col in REQUIRED_FEATURES] for r in disjoint_rows], dtype=np.float64)
        y_disj = np.array([float(r["tr"]) for r in disjoint_rows], dtype=np.float64)
        result["disjointPlayersOnly"] = {
            "tr-cross-ridge-baseline": _compute_regression_metrics(y_disj, ridge_model.predict(X_disj)),
            "tr-cross-rf-v1": _compute_regression_metrics(y_disj, rf_model.predict(X_disj)),
        }

    return result


def train_and_evaluate(
    processed_dir: Path,
    dataset_version: str,
    output_dir: Path,
    model_version: str = "0.2.0",
    season_test_dataset_version: str | None = None,
) -> dict[str, Any]:
    """Trains and evaluates the TR cross-regression model without data leakage or zero-imputation."""
    loaded = load_and_verify_dataset(processed_dir, dataset_version)

    valid_rows: list[dict[str, Any]] = []
    excluded_missing = 0
    for row in loaded.records:
        if not row.get("ranked", False) or not _is_valid_non_negative_number(row.get("tr")):
            excluded_missing += 1
            continue
        if all(_is_valid_non_negative_number(row.get(col)) for col in REQUIRED_FEATURES):
            valid_rows.append(row)
        else:
            excluded_missing += 1

    if len(valid_rows) < 10:
        raise ValueError(
            f"Insuficientes registros completos para entrenar ({len(valid_rows)} filas válidas)."
        )

    X = np.array(
        [[float(r[col]) for col in REQUIRED_FEATURES] for r in valid_rows],
        dtype=np.float64,
    )
    y = np.array([float(r["tr"]) for r in valid_rows], dtype=np.float64)
    groups = [str(r["playerId"]) for r in valid_rows]

    n_splits = min(5, len(valid_rows))
    ridge_pipeline = Pipeline(
        [
            ("scaler", StandardScaler()),
            ("ridge", Ridge(alpha=1.0)),
        ]
    )
    rf_candidate = RandomForestRegressor(
        n_estimators=100,
        max_depth=6,
        min_samples_leaf=5,
        random_state=42,
    )

    ridge_metrics = _evaluate_group_kfold(ridge_pipeline, X, y, groups, n_splits=n_splits)
    rf_metrics = _evaluate_group_kfold(rf_candidate, X, y, groups, n_splits=n_splits)

    ridge_pipeline.fit(X, y)
    rf_candidate.fit(X, y)
    scaler: StandardScaler = ridge_pipeline.named_steps["scaler"]
    ridge: Ridge = ridge_pipeline.named_steps["ridge"]

    out_of_season_eval: dict[str, Any] | None = None
    if season_test_dataset_version:
        out_of_season_eval = _evaluate_out_of_season(
            ridge_model=ridge_pipeline,
            rf_model=rf_candidate,
            processed_dir=processed_dir,
            season_dataset_version=season_test_dataset_version,
            train_player_ids=set(groups),
        )

    feature_means = {
        col: round(float(scaler.mean_[idx]), 6)
        for idx, col in enumerate(REQUIRED_FEATURES)
    }
    feature_stds = {
        col: round(float(scaler.scale_[idx]), 6)
        for idx, col in enumerate(REQUIRED_FEATURES)
    }
    linear_weights = {
        col: round(float(ridge.coef_[idx]), 6)
        for idx, col in enumerate(REQUIRED_FEATURES)
    }
    intercept = round(float(ridge.intercept_), 6)

    trained_at = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    model_card: dict[str, Any] = {
        "modelName": "tr-cross-ridge-baseline",
        "modelVersion": model_version,
        "datasetVersion": loaded.dataset_version,
        "runId": loaded.run_id,
        "featureSchemaVersion": SUPPORTED_FEATURE_SCHEMA_VERSION,
        "trainedAt": trained_at,
        "status": "ready",
        "features": list(REQUIRED_FEATURES),
        "target": "tr",
        "metrics": ridge_metrics,
        "candidateComparison": {
            "tr-cross-ridge-baseline": ridge_metrics,
            "tr-cross-rf-v1": rf_metrics,
        },
        "dataSummary": {
            "totalRecordsInDataset": len(loaded.records),
            "recordsUsedForTraining": len(valid_rows),
            "recordsExcludedMissingRequired": excluded_missing,
            "zeroImputationApplied": False,
        },
        "evaluationStrategy": {
            "method": f"GroupKFold(n_splits={n_splits}) partitioned by playerId",
            "temporalValidationNote": (
                "Partición estricta por playerId y validación inter-temporada contra Season 1."
                if out_of_season_eval is not None
                else "Dataset transversal de una única ventana de extracción; partición estricta por playerId sin fuga de datos."
            ),
            "outOfSeasonEvaluation": out_of_season_eval,
        },
        "explanationSummary": {
            "method": "shap",
            "description": "Exact linear Shapley contributions phi_j(x) = w_j * (x_j - mu_j) / sigma_j relative to dataset mean.",
            "intercept": intercept,
            "featureMeans": feature_means,
            "featureStds": feature_stds,
            "linearWeights": linear_weights,
        },
    }

    output_dir.mkdir(parents=True, exist_ok=True)
    model_path = output_dir / "model.joblib"
    card_path = output_dir / "model_card.json"

    joblib.dump(ridge_pipeline, model_path)
    with card_path.open("w", encoding="utf-8") as fh:
        json.dump(model_card, fh, indent=2, ensure_ascii=False)

    return model_card


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Entrena y evalúa el modelo transversal de TR (tr-cross-v1)."
    )
    parser.add_argument(
        "--processed-dir",
        type=Path,
        default=Path("../data/processed"),
        help="Directorio raíz de datasets procesados.",
    )
    parser.add_argument(
        "--dataset-version",
        type=str,
        required=True,
        help="Versión del dataset procesado (subdirectorio con manifest.json).",
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=Path("artifacts"),
        help="Directorio de salida para model.joblib y model_card.json.",
    )
    parser.add_argument(
        "--season-test-dataset-version",
        type=str,
        default=None,
        help="Versión opcional de dataset histórico de temporada para validación inter-temporada.",
    )
    args = parser.parse_args()

    card = train_and_evaluate(
        processed_dir=args.processed_dir,
        dataset_version=args.dataset_version,
        output_dir=args.output_dir,
        season_test_dataset_version=args.season_test_dataset_version,
    )
    print(json.dumps(card, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
