import { apiBaseUrl, PlayerApiError, type ApiProblem } from './player';

export interface TrFeatureContribution {
	feature: string;
	contribution: number;
}

export interface TrEstimateResponse {
	status: 'ok' | 'model_unavailable';
	playerId: string;
	estimatedTr: number | null;
	observedTr: number | null;
	modelName: string | null;
	modelVersion: string | null;
	featureSchemaVersion: string;
	featuresUsed: Record<string, number>;
	explanation: TrFeatureContribution[];
	explanationMethod: string | null;
	disclaimer: string;
	generatedAt: string;
}

export async function fetchTrEstimate(
	playerId: string,
	baseUrl?: string | null
): Promise<TrEstimateResponse> {
	let response: Response;
	try {
		response = await fetch(
			`${apiBaseUrl(baseUrl)}/players/${encodeURIComponent(playerId)}/tr-estimate`,
			{ headers: { Accept: 'application/json' } }
		);
	} catch {
		throw new PlayerApiError('No se pudo conectar con el backend para solicitar la estimación.');
	}

	if (!response.ok) {
		let problem: ApiProblem = {};
		try {
			problem = (await response.json()) as ApiProblem;
		} catch {
			// La respuesta podría no ser ProblemDetail.
		}
		throw new PlayerApiError(
			problem.detail ?? 'No se pudo obtener la estimación de TR.',
			problem.code,
			response.status,
			problem.errors
		);
	}

	const payload: unknown = await response.json();
	if (!isTrEstimateResponse(payload)) {
		throw new PlayerApiError('El backend devolvió una respuesta de estimación no válida.');
	}
	return payload;
}

function isTrEstimateResponse(value: unknown): value is TrEstimateResponse {
	if (!value || typeof value !== 'object') return false;
	const result = value as Partial<TrEstimateResponse>;
	const nullableFiniteNumber = (number: unknown) =>
		number === null || (typeof number === 'number' && Number.isFinite(number));
	return (
		(result.status === 'ok' || result.status === 'model_unavailable') &&
		typeof result.playerId === 'string' &&
		nullableFiniteNumber(result.estimatedTr) &&
		nullableFiniteNumber(result.observedTr) &&
		(result.modelName === null || typeof result.modelName === 'string') &&
		(result.modelVersion === null || typeof result.modelVersion === 'string') &&
		typeof result.featureSchemaVersion === 'string' &&
		!!result.featuresUsed &&
		typeof result.featuresUsed === 'object' &&
		Array.isArray(result.explanation) &&
		(result.explanation as unknown[]).every(
			(item) =>
				!!item &&
				typeof item === 'object' &&
				typeof (item as TrFeatureContribution).feature === 'string' &&
				typeof (item as TrFeatureContribution).contribution === 'number' &&
				Number.isFinite((item as TrFeatureContribution).contribution)
		) &&
		(result.explanationMethod === null || typeof result.explanationMethod === 'string') &&
		typeof result.disclaimer === 'string' &&
		typeof result.generatedAt === 'string' &&
		Number.isFinite(Date.parse(result.generatedAt))
	);
}
