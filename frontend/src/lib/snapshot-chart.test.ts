import { describe, expect, it } from 'vitest';
import { demoSnapshots } from './fixtures/player.fixture';
import { buildSnapshotChartPoints } from './snapshot-chart';

describe('buildSnapshotChartPoints', () => {
	it('maps chronological TR observations into the SVG chart bounds', () => {
		const points = buildSnapshotChartPoints(demoSnapshots);

		expect(points).toHaveLength(demoSnapshots.length);
		expect(points[0]?.x).toBe(48);
		expect(points.at(-1)?.x).toBe(672);
		expect(points[0]?.y).toBeGreaterThan(points.at(-1)?.y ?? 0);
		expect(points.every((point) => point.x >= 48 && point.x <= 672 && point.y >= 44 && point.y <= 204)).toBe(true);
	});

	it('ignores snapshots with missing TR or invalid timestamps', () => {
		const [valid] = demoSnapshots;
		const points = buildSnapshotChartPoints([
			valid,
			{ ...valid, capturedAt: 'not-a-date' },
			{ ...valid, capturedAt: '2026-10-23T12:00:00Z', tr: null }
		]);

		expect(points).toHaveLength(1);
		expect(points[0]?.x).toBe(360);
		expect(points[0]?.y).toBe(112);
	});

	it('centers snapshots with identical TR values without dividing by zero', () => {
		const [first, second] = demoSnapshots;
		const points = buildSnapshotChartPoints([
			{ ...first!, tr: 18000 },
			{ ...second!, tr: 18000 }
		]);

		expect(points.map((point) => point.y)).toEqual([112, 112]);
	});
});
