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

export interface PlayerSnapshot {
	capturedAt: string;
	tr: number | null;
	glicko: number | null;
	rd: number | null;
	gxe: number | null;
	rank: string | null;
	apm: number | null;
	pps: number | null;
	vs: number | null;
	gamesPlayed: number;
	gamesWon: number;
	standing: number | null;
}

export interface SnapshotPage {
	items: PlayerSnapshot[];
	page: number;
	size: number;
	totalItems: number;
	totalPages: number;
}

export interface SnapshotFilters {
	from?: string;
	to?: string;
	page?: number;
	size?: number;
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

export async function fetchPlayerSnapshots(
	playerId: string,
	filters: SnapshotFilters = {},
	baseUrl?: string | null
): Promise<SnapshotPage> {
	const query = new URLSearchParams();
	if (filters.from) query.set('from', filters.from);
	if (filters.to) query.set('to', filters.to);
	query.set('page', String(filters.page ?? 0));
	query.set('size', String(filters.size ?? 100));

	let response: Response;
	try {
		response = await fetch(
			`${apiBaseUrl(baseUrl)}/players/${encodeURIComponent(playerId)}/snapshots?${query.toString()}`,
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
			problem.detail ?? 'Ocurrió un error al consultar el historial.',
			problem.code,
			response.status,
			problem.errors
		);
	}

	const payload: unknown = await response.json();
	if (!isSnapshotPage(payload)) {
		throw new PlayerApiError('El backend devolvió un formato de historial no válido.');
	}
	return payload;
}

function isSnapshotPage(value: unknown): value is SnapshotPage {
	if (!value || typeof value !== 'object') return false;
	const page = value as Partial<SnapshotPage>;
	if (
		!Array.isArray(page.items) ||
		!Number.isInteger(page.page) ||
		!Number.isInteger(page.size) ||
		!Number.isInteger(page.totalItems) ||
		!Number.isInteger(page.totalPages)
	) {
		return false;
	}

	return page.items.every((item) => {
		if (!item || typeof item !== 'object') return false;
		const snapshot = item as Partial<PlayerSnapshot>;
		const nullableNumber = (field: unknown) => field === null || (typeof field === 'number' && Number.isFinite(field));
		return (
			typeof snapshot.capturedAt === 'string' &&
			Number.isFinite(Date.parse(snapshot.capturedAt)) &&
			Number.isInteger(snapshot.gamesPlayed) &&
			Number.isInteger(snapshot.gamesWon) &&
			(snapshot.rank === null || typeof snapshot.rank === 'string') &&
			nullableNumber(snapshot.tr) &&
			nullableNumber(snapshot.glicko) &&
			nullableNumber(snapshot.rd) &&
			nullableNumber(snapshot.gxe) &&
			nullableNumber(snapshot.apm) &&
			nullableNumber(snapshot.pps) &&
			nullableNumber(snapshot.vs) &&
			nullableNumber(snapshot.standing)
		);
	});
}

export { demoProfile, unrankedDemoProfile } from './fixtures/player.fixture';
