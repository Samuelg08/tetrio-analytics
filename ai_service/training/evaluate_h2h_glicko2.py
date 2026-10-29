import math
from typing import Any

GLICKO_Q: float = math.log(10.0) / 400.0

FORBIDDEN_IN_MATCH_STATS: frozenset[str] = frozenset(
    {
        "apm",
        "pps",
        "vsscore",
        "garbagesent",
        "garbagereceived",
        "kills",
        "wins",
        "lifetime",
    }
)


def glicko2_g(rd: float) -> float:
    """Computes Glicko-2 g(RD) attenuation factor."""
    if rd < 0.0 or not math.isfinite(rd):
        raise ValueError(f"RD debe ser finito y >= 0, recibido: {rd}")
    return 1.0 / math.sqrt(1.0 + (3.0 * (GLICKO_Q**2) * (rd**2)) / (math.pi**2))


def glicko2_expected_win_prob(
    glicko_a: float,
    rd_a: float,
    glicko_b: float,
    rd_b: float,
) -> float:
    """Computes pre-match expected win probability for player A vs player B using Glicko-2."""
    for name, val in (("glicko_a", glicko_a), ("rd_a", rd_a), ("glicko_b", glicko_b), ("rd_b", rd_b)):
        if val is None or isinstance(val, bool) or not math.isfinite(float(val)) or float(val) < 0.0:
            raise ValueError(f"Parámetro '{name}' inválido para Glicko-2: {val!r}")

    combined_rd = math.sqrt(float(rd_a) ** 2 + float(rd_b) ** 2)
    g_factor = glicko2_g(combined_rd)
    exponent = -g_factor * (float(glicko_a) - float(glicko_b)) / 400.0
    # Clamp exponent to avoid overflow
    exponent = max(-60.0, min(60.0, exponent))
    return 1.0 / (1.0 + (10.0**exponent))


def extract_pre_match_h2h_record(raw_entry: dict[str, Any], focal_player_id: str) -> dict[str, Any] | None:
    """Extracts strictly pre-match Glicko-2 ratings (extras.league[id][0]) and binary outcome.

    Never reads post-match ratings (extras.league[id][1]) or in-match stats (results.leaderboard[].stats),
    which are post-outcome and would cause data leakage (docs/API_CONTRACTS.md §5.5).
    """
    extras = raw_entry.get("extras")
    if not isinstance(extras, dict):
        return None
    league_map = extras.get("league")
    if not isinstance(league_map, dict) or len(league_map) != 2:
        return None

    focal_id = focal_player_id.strip().lower()
    if focal_id not in league_map:
        return None

    opponent_ids = [pid for pid in league_map.keys() if pid != focal_id]
    if len(opponent_ids) != 1:
        return None
    opp_id = opponent_ids[0]

    focal_snapshots = league_map.get(focal_id)
    opp_snapshots = league_map.get(opp_id)
    if (
        not isinstance(focal_snapshots, list)
        or len(focal_snapshots) < 1
        or not isinstance(opp_snapshots, list)
        or len(opp_snapshots) < 1
    ):
        return None

    pre_focal = focal_snapshots[0]
    pre_opp = opp_snapshots[0]
    if not isinstance(pre_focal, dict) or not isinstance(pre_opp, dict):
        return None

    glicko_a = pre_focal.get("glicko")
    rd_a = pre_focal.get("rd")
    glicko_b = pre_opp.get("glicko")
    rd_b = pre_opp.get("rd")

    for val in (glicko_a, rd_a, glicko_b, rd_b):
        if val is None or isinstance(val, bool) or not isinstance(val, (int, float)):
            return None
        if not math.isfinite(float(val)) or float(val) < 0.0:
            return None

    result_str = str(extras.get("result", "")).strip().lower()
    if result_str == "victory":
        label = 1
    elif result_str == "defeat":
        label = 0
    else:
        return None

    win_prob = glicko2_expected_win_prob(float(glicko_a), float(rd_a), float(glicko_b), float(rd_b))
    return {
        "matchId": raw_entry.get("_id"),
        "playerAId": focal_id,
        "playerBId": opp_id,
        "preMatchGlickoA": float(glicko_a),
        "preMatchRdA": float(rd_a),
        "preMatchGlickoB": float(glicko_b),
        "preMatchRdB": float(rd_b),
        "expectedWinProbA": round(win_prob, 6),
        "actualWinA": label,
    }


def evaluate_glicko2_baseline(entries: list[dict[str, Any]], focal_player_id: str) -> dict[str, Any]:
    """Evaluates the Glicko-2 pre-match baseline on a list of TETRA CHANNEL league match entries."""
    parsed = [
        rec
        for entry in entries
        if (rec := extract_pre_match_h2h_record(entry, focal_player_id)) is not None
    ]
    if not parsed:
        return {
            "matchesEvaluated": 0,
            "accuracy": None,
            "brierScore": None,
            "logLoss": None,
            "leakageNote": (
                "Solo están disponibles glicko/rd previos en extras.league[id][0]. "
                "Las métricas results.leaderboard[].stats (apm, pps, vsscore) pertenecen a la propia partida "
                "y están prohibidas por fuga de datos (docs/API_CONTRACTS.md §5.5)."
            ),
        }

    n = len(parsed)
    correct = 0
    brier_sum = 0.0
    logloss_sum = 0.0
    eps = 1e-12

    for item in parsed:
        p = max(eps, min(1.0 - eps, float(item["expectedWinProbA"])))
        y = int(item["actualWinA"])
        pred_label = 1 if p >= 0.5 else 0
        if pred_label == y:
            correct += 1
        brier_sum += (p - y) ** 2
        logloss_sum += -(y * math.log(p) + (1 - y) * math.log(1.0 - p))

    return {
        "matchesEvaluated": n,
        "accuracy": round(correct / n, 4),
        "brierScore": round(brier_sum / n, 4),
        "logLoss": round(logloss_sum / n, 4),
        "leakageNote": (
            "Evaluado exclusivamente con glicko y rd previos a la partida (extras.league[id][0]). "
            "No existen históricos pre-partida de APM/PPS/VS por partida en TETRA CHANNEL; "
            "results.leaderboard[].stats son post-partida y están excluidos para evitar data leakage."
        ),
    }
