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

export interface ApiProblemFieldError {
	field?: string;
	message?: string;
}

export interface ApiProblem {
	title?: string;
	detail?: string;
	code?: string;
	status?: number;
	errors?: ApiProblemFieldError[];
}

export class PlayerApiError extends Error {
	readonly code?: string;
	readonly status?: number;
	readonly errors?: ApiProblemFieldError[];

	constructor(
		message: string,
		code?: string,
		status?: number,
		errors?: ApiProblemFieldError[]
	) {
		super(message);
		this.name = 'PlayerApiError';
		this.code = code;
		this.status = status;
		this.errors = errors;
	}
}

export const DEFAULT_API_BASE_URL = 'http://localhost:8080/api';

export const apiBaseUrl = (value?: string | null): string => {
	const raw = value && value.trim().length > 0 ? value.trim() : DEFAULT_API_BASE_URL;
	return raw.replace(/\/+$/, '');
};

export async function fetchPlayerProfile(
	username: string,
	baseUrl?: string | null
): Promise<PlayerProfile> {
	const cleanUsername = username.trim().toLowerCase();
	if (!cleanUsername) {
		throw new PlayerApiError('Debes ingresar un nombre de usuario.', 'EMPTY_USERNAME', 400);
	}

	const resolvedBase = apiBaseUrl(baseUrl);
	let response: Response;
	try {
		response = await fetch(
			`${resolvedBase}/players/by-username/${encodeURIComponent(cleanUsername)}`,
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
			// Respuestas del proxy o del servidor que no incluyan ProblemDetail RFC 9457
		}
		throw new PlayerApiError(
			problem.detail ?? 'Ocurrió un error al consultar el jugador.',
			problem.code,
			response.status,
			problem.errors
		);
	}

	return (await response.json()) as PlayerProfile;
}

export { demoProfile, unrankedDemoProfile } from './fixtures/player.fixture';
