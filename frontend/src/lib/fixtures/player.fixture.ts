import type { PlayerProfile } from '../player';

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
