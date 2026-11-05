import type { PlayerSnapshot } from './player';

export interface SnapshotChartPoint {
	item: PlayerSnapshot;
	time: number;
	tr: number;
	x: number;
	y: number;
}

/** Maps valid TR observations to a fixed responsive SVG viewBox. */
export function buildSnapshotChartPoints(items: PlayerSnapshot[]): SnapshotChartPoint[] {
	const valid = items
		.filter((item) => item.tr !== null && Number.isFinite(item.tr) && Number.isFinite(Date.parse(item.capturedAt)))
		.map((item) => ({ item, time: Date.parse(item.capturedAt), tr: item.tr as number }));
	if (valid.length === 0) return [];

	const minTime = Math.min(...valid.map((point) => point.time));
	const maxTime = Math.max(...valid.map((point) => point.time));
	const minTr = Math.min(...valid.map((point) => point.tr));
	const maxTr = Math.max(...valid.map((point) => point.tr));

	return valid.map((point) => ({
		...point,
		x: valid.length === 1 ? 360 : 48 + ((point.time - minTime) / (maxTime - minTime)) * 624,
		y: maxTr === minTr ? 112 : 204 - ((point.tr - minTr) / (maxTr - minTr)) * 160
	}));
}
