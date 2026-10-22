export type DataSource = 'live' | 'cache' | 'stale';

export interface LeagueSummary {
	gamesPlayed: number;
	gamesWon: number;
	ranked: boolean;
	tr: number | null;
	glicko: number | null;
	rd: number | null;
	gxe: number | null;
	rank: string | null;
	bestRank: string | null;
	apm: number | null;
	pps: number | null;
	vs: number | null;
	standing: number | null;
	percentile: number | null;
	decaying: boolean;
}

export interface PlayerProfile {
	playerId: string;
	username: string;
	country: string | null;
	lastSyncedAt: string;
	dataSource: DataSource;
	league: LeagueSummary | null;
}

export interface ApiProblem {
	title?: string;
	detail?: string;
	code?: string;
	status?: number;
}

export class PlayerApiError extends Error {
	constructor(
		message: string,
		readonly code?: string,
		readonly status?: number
	) {
		super(message);
		this.name = 'PlayerApiError';
	}
}

const apiBaseUrl = (value: string) => value.replace(/\/+$/, '');

export async function fetchPlayerProfile(username: string, baseUrl: string): Promise<PlayerProfile> {
	let response: Response;
	try {
		response = await fetch(
			`${apiBaseUrl(baseUrl)}/players/by-username/${encodeURIComponent(username.trim())}`,
			{ headers: { Accept: 'application/json' } }
		);
	} catch {
		throw new PlayerApiError('No se pudo conectar con el backend. Revisa que Spring Boot esté activo.');
	}

	if (!response.ok) {
		let problem: ApiProblem = {};
		try {
			problem = (await response.json()) as ApiProblem;
		} catch {
			// Algunas respuestas del proxy o del proveedor pueden no incluir ProblemDetail.
		}
		throw new PlayerApiError(
			problem.detail ?? 'Ocurrió un error al consultar el jugador.',
			problem.code,
			response.status
		);
	}

	return (await response.json()) as PlayerProfile;
}

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
