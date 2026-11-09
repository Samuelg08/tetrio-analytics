import type { PlayerProfile, PlayerSnapshot } from '../player';

/**
 * Fixture sintético para desarrollo de UI y demostración cuando el backend no esté disponible.
 * ADVERTENCIA: Datos sintéticos para pruebas de renderizado; no corresponden a un jugador real.
 */
export const demoProfile: PlayerProfile = {
	playerId: '000000000000000000000000',
	username: 'tetrio_demo',
	country: 'CO',
	lastSyncedAt: '2026-10-22T12:00:00Z',
	dataSource: 'cache',
	league: {
		gamesPlayed: 842,
		gamesWon: 476,
		ranked: true,
		tr: 18452,
		glicko: 1968.42,
		rd: 62.5,
		gxe: 73.4,
		rank: 'S+',
		bestRank: 'S+',
		apm: 52.1,
		pps: 2.31,
		vs: 118.7,
		standing: 1248,
		percentile: 0.08,
		decaying: false
	}
};

/** Snapshots sintéticos para mostrar la evolución en la interfaz. */
export const demoSnapshots: PlayerSnapshot[] = [
	{ capturedAt: '2026-10-14T12:00:00Z', tr: 17680, glicko: 1935.4, rd: 68.1, gxe: 71.2, rank: 'S', apm: 50.8, pps: 2.24, vs: 113.1, gamesPlayed: 798, gamesWon: 443, standing: 1450 },
	{ capturedAt: '2026-10-16T12:00:00Z', tr: 17895, glicko: 1944.2, rd: 66.9, gxe: 71.8, rank: 'S', apm: 51.2, pps: 2.26, vs: 114.6, gamesPlayed: 809, gamesWon: 449, standing: 1392 },
	{ capturedAt: '2026-10-18T12:00:00Z', tr: 18142, glicko: 1952.8, rd: 65.2, gxe: 72.4, rank: 'S+', apm: 51.7, pps: 2.28, vs: 116.5, gamesPlayed: 821, gamesWon: 458, standing: 1336 },
	{ capturedAt: '2026-10-20T12:00:00Z', tr: 18220, glicko: 1958.1, rd: 64.1, gxe: 72.8, rank: 'S+', apm: 51.9, pps: 2.29, vs: 117.1, gamesPlayed: 829, gamesWon: 465, standing: 1305 },
	{ capturedAt: '2026-10-22T12:00:00Z', tr: 18452, glicko: 1968.42, rd: 62.5, gxe: 73.4, rank: 'S+', apm: 52.1, pps: 2.31, vs: 118.7, gamesPlayed: 842, gamesWon: 476, standing: 1248 }
];

/**
 * Fixture sintético de jugador no clasificado (sin partidas de TETRA LEAGUE).
 */
export const unrankedDemoProfile: PlayerProfile = {
	playerId: '000000000000000000000001',
	username: 'unranked_demo',
	country: null,
	lastSyncedAt: '2026-10-22T12:00:00Z',
	dataSource: 'live',
	league: {
		gamesPlayed: 0,
		gamesWon: 0,
		ranked: false,
		tr: null,
		glicko: null,
		rd: null,
		gxe: null,
		rank: null,
		bestRank: null,
		apm: null,
		pps: null,
		vs: null,
		standing: null,
		percentile: null,
		decaying: false
	}
};
