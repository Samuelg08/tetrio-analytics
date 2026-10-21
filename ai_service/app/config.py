from pathlib import Path
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    ai_host: str = "127.0.0.1"
    ai_port: int = 8000
    ai_model_path: str = "artifacts/model.joblib"
    ai_model_card_path: str = "artifacts/model_card.json"
    ai_processed_data_dir: str = "../data/processed"

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    def resolve_model_path(self, base_dir: Path | None = None) -> Path:
        root = base_dir or Path(__file__).resolve().parent.parent
        p = Path(self.ai_model_path)
        return p if p.is_absolute() else (root / p).resolve()

    def resolve_model_card_path(self, base_dir: Path | None = None) -> Path:
        root = base_dir or Path(__file__).resolve().parent.parent
        p = Path(self.ai_model_card_path)
        return p if p.is_absolute() else (root / p).resolve()

    def resolve_processed_data_dir(self, base_dir: Path | None = None) -> Path:
        root = base_dir or Path(__file__).resolve().parent.parent
        p = Path(self.ai_processed_data_dir)
        return p if p.is_absolute() else (root / p).resolve()
