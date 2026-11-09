import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { PlayerApiError } from './player';
import { fetchTrEstimate } from './tr-estimate';

describe('fetchTrEstimate', () => {
	const originalFetch = globalThis.fetch;
	const playerId = '69f0e049a9869cff4883fdb0';

	beforeEach(() => vi.restoreAllMocks());
	afterEach(() => {
		globalThis.fetch = originalFetch;
	});

	it('consulta el endpoint Spring Boot y devuelve la estimación y explicación', async () => {
		const responseBody = {
			status: 'ok',
			playerId,
			estimatedTr: 18320.5,
			observedTr: 18452,
			modelName: 'tr-cross-ridge-baseline',
			modelVersion: '0.2.0',
			featureSchemaVersion: 'tr-cross-v1',
			featuresUsed: { apm: 52.1, pps: 2.31, vs: 118.7, gamesPlayed: 842 },
			explanation: [{ feature: 'apm', contribution: 0.42 }],
			explanationMethod: 'shap',
			disclaimer: 'Estimación estadística transversal.',
			generatedAt: '2026-11-09T12:00:00Z'
		};
		const mockFetch = vi.fn().mockResolvedValue({ ok: true, status: 200, json: async () => responseBody });
		globalThis.fetch = mockFetch;

		const result = await fetchTrEstimate(playerId, 'http://localhost:8081/api/');

		expect(mockFetch).toHaveBeenCalledWith(
			`http://localhost:8081/api/players/${playerId}/tr-estimate`,
			{ headers: { Accept: 'application/json' } }
		);
		expect(result.estimatedTr).toBe(18320.5);
		expect(result.explanation[0].contribution).toBe(0.42);
	});

	it('acepta el estado model_unavailable sin inventar una estimación', async () => {
		const responseBody = {
			status: 'model_unavailable',
			playerId,
			estimatedTr: null,
			observedTr: 18452,
			modelName: null,
			modelVersion: null,
			featureSchemaVersion: 'tr-cross-v1',
			featuresUsed: { apm: 52.1, pps: 2.31, vs: 118.7, gamesPlayed: 842 },
			explanation: [],
			explanationMethod: null,
			disclaimer: 'Estimación estadística transversal.',
			generatedAt: '2026-11-09T12:00:00Z'
		};
		globalThis.fetch = vi.fn().mockResolvedValue({ ok: true, status: 200, json: async () => responseBody });

		const result = await fetchTrEstimate(playerId);

		expect(result.status).toBe('model_unavailable');
		expect(result.estimatedTr).toBeNull();
	});

	it('convierte ProblemDetail de variables ausentes a PlayerApiError', async () => {
		globalThis.fetch = vi.fn().mockResolvedValue({
			ok: false,
			status: 422,
			json: async () => ({ code: 'PREDICTION_NOT_APPLICABLE', detail: 'Faltan métricas.' })
		});

		await expect(fetchTrEstimate(playerId)).rejects.toMatchObject({
			name: 'PlayerApiError',
			code: 'PREDICTION_NOT_APPLICABLE',
			status: 422,
			message: 'Faltan métricas.'
		});
	});

	it('rechaza una respuesta exitosa malformada', async () => {
		globalThis.fetch = vi.fn().mockResolvedValue({ ok: true, status: 200, json: async () => ({ status: 'ok' }) });

		await expect(fetchTrEstimate(playerId)).rejects.toThrow(PlayerApiError);
	});
});
