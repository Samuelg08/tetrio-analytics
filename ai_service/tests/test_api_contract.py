import json
from pathlib import Path
import joblib
import numpy as np
import pytest
from fastapi.testclient import TestClient
from sklearn.linear_model import Ridge
from app.config import Settings
from app.main import create_app
from training.dataset_loader import load_and_verify_dataset
from training.evaluate_h2h_glicko2 import evaluate_glicko2_baseline, glicko2_expected_win_prob
from training.train_tr_cross import train_and_evaluate


def test_health_and_model_unavailable_when_no_artifacts_exist(tmp_path: Path) -> None:
    settings = Settings(
        ai_model_path=str(tmp_path / "missing_model.joblib"),
        ai_model_card_path=str(tmp_path / "missing_card.json"),
    )
    client = TestClient(create_app(settings=settings))

    health_res = client.get("/health", headers={"X-Request-Id": "req-health-1234"})
    assert health_res.status_code == 200
    assert health_res.headers["X-Request-Id"] == "req-health-1234"
    assert health_res.json() == {
        "status": "ok",
        "modelLoaded": False,
        "modelName": None,
        "modelVersion": None,
        "featureSchemaVersion": None,
    }

    card_res = client.get("/v1/model-card")
    assert card_res.status_code == 503
    assert card_res.headers["content-type"].startswith("application/problem+json")
    body = card_res.json()
    assert body["code"] == "MODEL_UNAVAILABLE"
    assert body["type"] == "/problems/model-unavailable"

    est_res = client.post(
        "/v1/tr-estimate",
        json={
            "requestId": "00000000-0000-0000-0000-000000000001",
            "featureSchemaVersion": "tr-cross-v1",
            "features": {"apm": 50.0, "pps": 1.5, "vs": 100.0, "gamesPlayed": 120},
        },
    )
    assert est_res.status_code == 503
    assert est_res.json()["code"] == "MODEL_UNAVAILABLE"


def test_tr_estimate_validates_schema_forbidden_fields_and_null_inputs(tmp_path: Path) -> None:
    settings = Settings(
        ai_model_path=str(tmp_path / "missing_model.joblib"),
        ai_model_card_path=str(tmp_path / "missing_card.json"),
    )
    client = TestClient(create_app(settings=settings))

    # 1. Wrong featureSchemaVersion -> 422 FEATURE_SCHEMA_MISMATCH
    mismatch_res = client.post(
        "/v1/tr-estimate",
        json={
            "requestId": "req-1",
            "featureSchemaVersion": "tr-cross-v999",
            "features": {"apm": 50.0, "pps": 1.5, "vs": 100.0, "gamesPlayed": 120},
        },
    )
    assert mismatch_res.status_code == 422
    assert mismatch_res.json()["code"] == "FEATURE_SCHEMA_MISMATCH"

    # 2. Forbidden leakage field (glicko / tr) -> 422 VALIDATION_FAILED
    leak_res = client.post(
        "/v1/tr-estimate",
        json={
            "requestId": "req-2",
            "featureSchemaVersion": "tr-cross-v1",
            "features": {
                "apm": 50.0,
                "pps": 1.5,
                "vs": 100.0,
                "gamesPlayed": 120,
                "glicko": 2000.0,
            },
        },
    )
    assert leak_res.status_code == 422
    leak_body = leak_res.json()
    assert leak_body["code"] == "VALIDATION_FAILED"
    assert any(err["field"] == "features.glicko" for err in leak_body["errors"])

    # 3. Null required variable -> 422 PREDICTION_NOT_APPLICABLE (never impute 0)
    null_res = client.post(
        "/v1/tr-estimate",
        json={
            "requestId": "req-3",
            "featureSchemaVersion": "tr-cross-v1",
            "features": {"apm": None, "pps": 1.5, "vs": 100.0, "gamesPlayed": 120},
        },
    )
    assert null_res.status_code == 422
    assert null_res.json()["code"] == "PREDICTION_NOT_APPLICABLE"


def test_loaded_model_serves_model_card_and_tr_prediction(tmp_path: Path) -> None:
    model_path = tmp_path / "model.joblib"
    card_path = tmp_path / "model_card.json"

    X = np.array(
        [
            [40.0, 1.2, 80.0, 100.0],
            [80.0, 2.0, 160.0, 300.0],
            [120.0, 2.5, 250.0, 600.0],
            [160.0, 2.8, 330.0, 1000.0],
        ]
    )
    y = np.array([12000.0, 18000.0, 22500.0, 24500.0])
    estimator = Ridge(alpha=1.0).fit(X, y)
    joblib.dump(estimator, model_path)

    card_data = {
        "modelName": "tr-cross-ridge-baseline",
        "modelVersion": "0.1.0",
        "datasetVersion": "20261020-sample500",
        "featureSchemaVersion": "tr-cross-v1",
        "trainedAt": "2026-10-20T20:00:00Z",
        "metrics": {"mae": 410.5, "rmse": 520.2, "r2": 0.91},
        "status": "ready",
    }
    card_path.write_text(json.dumps(card_data), encoding="utf-8")

    settings = Settings(
        ai_model_path=str(model_path),
        ai_model_card_path=str(card_path),
    )
    client = TestClient(create_app(settings=settings))

    health_res = client.get("/health")
    assert health_res.status_code == 200
    assert health_res.json()["modelLoaded"] is True
    assert health_res.json()["modelName"] == "tr-cross-ridge-baseline"

    card_res = client.get("/v1/model-card")
    assert card_res.status_code == 200
    assert card_res.json()["datasetVersion"] == "20261020-sample500"

    est_res = client.post(
        "/v1/tr-estimate",
        json={
            "requestId": "req-ok-1",
            "featureSchemaVersion": "tr-cross-v1",
            "features": {"apm": 100.0, "pps": 2.2, "vs": 200.0, "gamesPlayed": 400},
        },
    )
    assert est_res.status_code == 200
    est_body = est_res.json()
    assert est_body["status"] == "ok"
    assert est_body["modelName"] == "tr-cross-ridge-baseline"
    assert isinstance(est_body["prediction"]["tr"], float)


def test_dataset_loader_validates_manifest_and_records(tmp_path: Path) -> None:
    dataset_version = "20261020-unit"
    ds_dir = tmp_path / dataset_version
    ds_dir.mkdir(parents=True)

    rec = {
        "playerId": "5e9d4eec530bc13f9fdcd2c0",
        "username": "5han",
        "country": "JP",
        "xp": 68967953.6,
        "gametime": None,
        "gamesPlayed": 1711,
        "gamesWon": 1471,
        "ranked": True,
        "tr": 24863.06,
        "apm": 170.6,
        "pps": 2.73,
        "vs": 353.52,
    }
    (ds_dir / "players_league.jsonl").write_text(json.dumps(rec) + "\n", encoding="utf-8")

    manifest = {
        "datasetVersion": dataset_version,
        "runId": "20261020T120000Z-league",
        "createdAt": "2026-10-20T12:01:00Z",
        "extractionWindow": {
            "startedAt": "2026-10-20T12:00:00Z",
            "finishedAt": "2026-10-20T12:01:00Z",
            "durationSeconds": 60,
        },
        "sources": [{"endpoint": "GET /users/by/league", "baseUrl": "https://ch.tetr.io/api", "limit": 100, "sessionIdUsed": True}],
        "featureSchemaVersion": "tr-cross-v1",
        "transformationsApplied": ["sentinel_-1_to_null"],
        "counts": {"uniquePlayersExported": 1},
        "fields": {"tr": {"type": "float", "nullable": True, "nullCount": 0, "nullRate": 0.0}},
    }
    (ds_dir / "manifest.json").write_text(json.dumps(manifest), encoding="utf-8")

    loaded = load_and_verify_dataset(tmp_path, dataset_version)
    assert loaded.dataset_version == dataset_version
    assert len(loaded.records) == 1
    assert loaded.records[0]["gametime"] is None

    with pytest.raises(FileNotFoundError):
        load_and_verify_dataset(tmp_path, "nonexistent-version")


def test_train_tr_cross_and_serve_shap_explanation(tmp_path: Path) -> None:
    dataset_version = "20261020-train-unit"
    ds_dir = tmp_path / "processed" / dataset_version
    ds_dir.mkdir(parents=True)
    out_dir = tmp_path / "artifacts"

    rows = []
    for i in range(15):
        player_id = f"{i:024x}"
        apm = 40.0 + i * 8.0
        pps = 1.2 + i * 0.1
        vs = 85.0 + i * 16.0
        games = 100 + i * 50
        tr = 10000.0 + i * 950.0
        rows.append(
            {
                "playerId": player_id,
                "username": f"user{i}",
                "country": "JP",
                "xp": 10000.0,
                "gametime": None,
                "gamesPlayed": games,
                "gamesWon": int(games * 0.55),
                "ranked": True,
                "tr": tr,
                "apm": apm,
                "pps": pps,
                "vs": vs,
            }
        )

    with (ds_dir / "players_league.jsonl").open("w", encoding="utf-8") as fh:
        for r in rows:
            fh.write(json.dumps(r) + "\n")

    manifest = {
        "datasetVersion": dataset_version,
        "runId": "20261020T120000Z-league",
        "createdAt": "2026-10-20T12:01:00Z",
        "extractionWindow": {
            "startedAt": "2026-10-20T12:00:00Z",
            "finishedAt": "2026-10-20T12:01:00Z",
            "durationSeconds": 60,
        },
        "sources": [{"endpoint": "GET /users/by/league", "baseUrl": "https://ch.tetr.io/api", "limit": 100, "sessionIdUsed": True}],
        "featureSchemaVersion": "tr-cross-v1",
        "transformationsApplied": ["sentinel_-1_to_null"],
        "counts": {"uniquePlayersExported": len(rows)},
        "fields": {"tr": {"type": "float", "nullable": True, "nullCount": 0, "nullRate": 0.0}},
    }
    (ds_dir / "manifest.json").write_text(json.dumps(manifest), encoding="utf-8")

    card = train_and_evaluate(
        processed_dir=tmp_path / "processed",
        dataset_version=dataset_version,
        output_dir=out_dir,
    )
    assert card["modelName"] == "tr-cross-ridge-baseline"
    assert card["dataSummary"]["zeroImputationApplied"] is False
    assert "mae" in card["metrics"] and "rmse" in card["metrics"] and "r2" in card["metrics"]

    settings = Settings(
        ai_model_path=str(out_dir / "model.joblib"),
        ai_model_card_path=str(out_dir / "model_card.json"),
    )
    client = TestClient(create_app(settings=settings))

    est_res = client.post(
        "/v1/tr-estimate",
        json={
            "requestId": "req-shap-1",
            "featureSchemaVersion": "tr-cross-v1",
            "features": {"apm": 110.0, "pps": 2.1, "vs": 230.0, "gamesPlayed": 500},
        },
    )
    assert est_res.status_code == 200
    body = est_res.json()
    assert body["explanationMethod"] == "shap"
    assert len(body["explanation"]) == 4
    features_explained = [item["feature"] for item in body["explanation"]]
    assert features_explained == ["apm", "pps", "vs", "gamesPlayed"]


def test_out_of_season_evaluation_and_glicko2_h2h_baseline(tmp_path: Path) -> None:
    # 1. Test Glicko-2 expected win probability symmetry and monotonicity
    prob_equal = glicko2_expected_win_prob(2000.0, 65.0, 2000.0, 65.0)
    assert abs(prob_equal - 0.5) < 1e-9

    prob_higher = glicko2_expected_win_prob(4662.13, 99.99, 3301.88, 62.89)
    assert prob_higher > 0.99

    sample_entries = [
        {
            "_id": "6adc8fad910a597ea20c6f09",
            "extras": {
                "league": {
                    "5e9d4eec530bc13f9fdcd2c0": [
                        {"glicko": 4662.1338, "rd": 99.99, "tr": 24863.0561},
                        {"glicko": 4662.1602, "rd": 99.99, "tr": 24863.0620},
                    ],
                    "632d637cc9edd00721369108": [
                        {"glicko": 3301.8832, "rd": 62.89, "tr": 23906.2534},
                        {"glicko": 3301.8704, "rd": 63.72, "tr": 23904.9852},
                    ],
                },
                "result": "victory",
            },
        }
    ]
    h2h_eval = evaluate_glicko2_baseline(sample_entries, "5e9d4eec530bc13f9fdcd2c0")
    assert h2h_eval["matchesEvaluated"] == 1
    assert h2h_eval["accuracy"] == 1.0
    assert h2h_eval["brierScore"] < 0.001

    # 2. Test out-of-season dataset loading via season_league.jsonl
    season_ver = "20261028-season1-unit"
    s_dir = tmp_path / "processed" / season_ver
    s_dir.mkdir(parents=True)
    rec = {
        "playerId": "5e7cbb652932b46c9c671ce4",
        "username": "icly",
        "country": "VA",
        "xp": None,
        "gametime": None,
        "gamesPlayed": 1075,
        "gamesWon": 978,
        "ranked": True,
        "tr": 24999.69,
        "apm": 204.26,
        "pps": 3.52,
        "vs": 397.26,
    }
    (s_dir / "season_league.jsonl").write_text(json.dumps(rec) + "\n", encoding="utf-8")
    manifest = {
        "datasetVersion": season_ver,
        "runId": "20261028T190000Z-season-1",
        "createdAt": "2026-10-28T19:01:00Z",
        "extractionWindow": {
            "startedAt": "2026-10-28T19:00:00Z",
            "finishedAt": "2026-10-28T19:01:00Z",
            "durationSeconds": 60,
        },
        "sources": [{"endpoint": "GET /users/history/league/1", "baseUrl": "https://ch.tetr.io/api", "limit": 100, "sessionIdUsed": True}],
        "featureSchemaVersion": "tr-cross-v1",
        "transformationsApplied": ["sentinel_-1_to_null"],
        "counts": {"uniquePlayersExported": 1},
        "fields": {"tr": {"type": "float", "nullable": True, "nullCount": 0, "nullRate": 0.0}},
    }
    (s_dir / "manifest.json").write_text(json.dumps(manifest), encoding="utf-8")

    loaded_season = load_and_verify_dataset(tmp_path / "processed", season_ver)
    assert loaded_season.dataset_version == season_ver
    assert len(loaded_season.records) == 1


