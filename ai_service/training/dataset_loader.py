import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any
from app.schemas import SUPPORTED_FEATURE_SCHEMA_VERSION

REQUIRED_MANIFEST_KEYS: tuple[str, ...] = (
    "datasetVersion",
    "runId",
    "createdAt",
    "extractionWindow",
    "sources",
    "featureSchemaVersion",
    "transformationsApplied",
    "counts",
    "fields",
)


@dataclass(frozen=True)
class LoadedDataset:
    dataset_version: str
    run_id: str
    feature_schema_version: str
    manifest: dict[str, Any]
    records: list[dict[str, Any]]


def load_and_verify_dataset(processed_dir: Path, dataset_version: str) -> LoadedDataset:
    """Loads and validates a processed dataset directory before any model training.

    Never replaces null values with 0 and requires a valid manifest.json.
    """
    dataset_dir = (processed_dir / dataset_version).resolve()
    manifest_path = dataset_dir / "manifest.json"
    jsonl_path = dataset_dir / "players_league.jsonl"
    if not jsonl_path.is_file():
        season_jsonl_path = dataset_dir / "season_league.jsonl"
        if season_jsonl_path.is_file():
            jsonl_path = season_jsonl_path

    if not manifest_path.is_file():
        raise FileNotFoundError(
            f"No existe manifest.json en '{dataset_dir}'. Ningún modelo puede entrenarse sin manifiesto."
        )
    if not jsonl_path.is_file():
        raise FileNotFoundError(
            f"No existe players_league.jsonl ni season_league.jsonl en '{dataset_dir}'."
        )

    with manifest_path.open("r", encoding="utf-8") as fh:
        manifest: dict[str, Any] = json.load(fh)

    missing_keys = [k for k in REQUIRED_MANIFEST_KEYS if k not in manifest]
    if missing_keys:
        raise ValueError(f"manifest.json incompleto; faltan claves obligatorias: {missing_keys}")

    if manifest["datasetVersion"] != dataset_version:
        raise ValueError(
            f"datasetVersion en manifest.json ('{manifest['datasetVersion']}') no coincide con '{dataset_version}'."
        )

    if manifest["featureSchemaVersion"] != SUPPORTED_FEATURE_SCHEMA_VERSION:
        raise ValueError(
            f"featureSchemaVersion no soportado: '{manifest['featureSchemaVersion']}'."
        )

    records: list[dict[str, Any]] = []
    seen_ids: set[str] = set()
    with jsonl_path.open("r", encoding="utf-8") as fh:
        for line_num, raw_line in enumerate(fh, start=1):
            stripped = raw_line.strip()
            if not stripped:
                continue
            row = json.loads(stripped)
            player_id = row.get("playerId")
            if not isinstance(player_id, str) or len(player_id) != 24:
                raise ValueError(f"Fila {line_num} con playerId inválido: {player_id!r}")
            if player_id in seen_ids:
                raise ValueError(f"playerId duplicado detectado en fila {line_num}: {player_id}")
            seen_ids.add(player_id)
            records.append(row)

    expected_count = int(manifest["counts"]["uniquePlayersExported"])
    if len(records) != expected_count:
        raise ValueError(
            f"El número de filas en players_league.jsonl ({len(records)}) no coincide con "
            f"counts.uniquePlayersExported ({expected_count}) del manifest.json."
        )

    return LoadedDataset(
        dataset_version=manifest["datasetVersion"],
        run_id=manifest["runId"],
        feature_schema_version=manifest["featureSchemaVersion"],
        manifest=manifest,
        records=records,
    )
