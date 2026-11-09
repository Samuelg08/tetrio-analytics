import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import {
	apiBaseUrl,
	DEFAULT_API_BASE_URL,
	fetchPlayerProfile,
	fetchPlayerSnapshots,
	PlayerApiError
} from './player';
import { demoProfile, demoSnapshots } from './fixtures/player.fixture';

describe('apiBaseUrl', () => {
	it('devuelve DEFAULT_API_BASE_URL si el valor es nulo o indefinido', () => {
		expect(apiBaseUrl(undefined)).toBe(DEFAULT_API_BASE_URL);
		expect(apiBaseUrl(null)).toBe(DEFAULT_API_BASE_URL);
		expect(apiBaseUrl('')).toBe(DEFAULT_API_BASE_URL);
		expect(apiBaseUrl('   ')).toBe(DEFAULT_API_BASE_URL);
	});

	it('elimina barras diagonales al final de la URL', () => {
		expect(apiBaseUrl('http://localhost:8080/api/')).toBe('http://localhost:8080/api');
		expect(apiBaseUrl('http://localhost:8080/api///')).toBe('http://localhost:8080/api');
		expect(apiBaseUrl('http://example.com:8080')).toBe('http://example.com:8080');
	});
});

describe('fetchPlayerProfile', () => {
	const originalFetch = globalThis.fetch;

	beforeEach(() => {
		vi.restoreAllMocks();
	});

	afterEach(() => {
		globalThis.fetch = originalFetch;
	});

	it('lanza error de validación local si el username está vacío', async () => {
		await expect(fetchPlayerProfile('')).rejects.toThrow('Debes ingresar un nombre de usuario.');
		await expect(fetchPlayerProfile('   ')).rejects.toThrow(PlayerApiError);
	});

	it('consulta y mapea el perfil correctamente en respuesta 200 OK', async () => {
		const mockFetch = vi.fn().mockResolvedValue({
			ok: true,
			status: 200,
			json: async () => demoProfile
		});
		globalThis.fetch = mockFetch;

		const profile = await fetchPlayerProfile('  OSK  ', 'http://localhost:8080/api/');

		expect(mockFetch).toHaveBeenCalledWith(
			'http://localhost:8080/api/players/by-username/osk',
			{ headers: { Accept: 'application/json' } }
		);
		expect(profile.username).toBe(demoProfile.username);
		expect(profile.league?.tr).toBe(18452);
		expect(profile.dataSource).toBe('cache');
	});

	it('maneja ProblemDetail 404 PLAYER_NOT_FOUND', async () => {
		globalThis.fetch = vi.fn().mockResolvedValue({
			ok: false,
			status: 404,
			json: async () => ({
				title: 'Player not found',
				status: 404,
				code: 'PLAYER_NOT_FOUND',
				detail: 'El jugador no existe en TETRA CHANNEL.'
			})
		});

		try {
			await fetchPlayerProfile('nonexistent');
			expect.fail('Se esperaba que fetchPlayerProfile lanzara un error');
		} catch (error) {
			expect(error).toBeInstanceOf(PlayerApiError);
			const apiError = error as PlayerApiError;
			expect(apiError.code).toBe('PLAYER_NOT_FOUND');
			expect(apiError.status).toBe(404);
			expect(apiError.message).toBe('El jugador no existe en TETRA CHANNEL.');
		}
	});

	it('maneja ProblemDetail 400 VALIDATION_FAILED con lista de errores', async () => {
		globalThis.fetch = vi.fn().mockResolvedValue({
			ok: false,
			status: 400,
			json: async () => ({
				title: 'Validation failed',
				status: 400,
				code: 'VALIDATION_FAILED',
				detail: 'Uno o más parámetros no son válidos.',
				errors: [{ field: 'username', message: 'Formato de username no válido' }]
			})
		});

		try {
			await fetchPlayerProfile('ab');
			expect.fail('Se esperaba que fetchPlayerProfile lanzara un error');
		} catch (error) {
			expect(error).toBeInstanceOf(PlayerApiError);
			const apiError = error as PlayerApiError;
			expect(apiError.code).toBe('VALIDATION_FAILED');
			expect(apiError.status).toBe(400);
			expect(apiError.errors?.[0]?.message).toBe('Formato de username no válido');
		}
	});

	it('maneja respuestas HTTP de error sin cuerpo ProblemDetail válido', async () => {
		globalThis.fetch = vi.fn().mockResolvedValue({
			ok: false,
			status: 502,
			json: async () => {
				throw new Error('Not JSON');
			}
		});

		try {
			await fetchPlayerProfile('osk');
			expect.fail('Se esperaba que fetchPlayerProfile lanzara un error');
		} catch (error) {
			expect(error).toBeInstanceOf(PlayerApiError);
			const apiError = error as PlayerApiError;
			expect(apiError.status).toBe(502);
			expect(apiError.message).toBe('Ocurrió un error al consultar el jugador.');
		}
	});

	it('maneja errores de red cuando el backend no está disponible', async () => {
		globalThis.fetch = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'));

		try {
			await fetchPlayerProfile('osk');
			expect.fail('Se esperaba que fetchPlayerProfile lanzara un error');
		} catch (error) {
			expect(error).toBeInstanceOf(PlayerApiError);
			const apiError = error as PlayerApiError;
			expect(apiError.message).toContain('No se pudo conectar con el backend');
		}
	});
});

describe('fetchPlayerSnapshots', () => {
	const originalFetch = globalThis.fetch;

	beforeEach(() => {
		vi.restoreAllMocks();
	});

	afterEach(() => {
		globalThis.fetch = originalFetch;
	});

	it('consulta snapshots con rango ISO y paginación', async () => {
		const expectedPage = { items: demoSnapshots, page: 1, size: 100, totalItems: 105, totalPages: 2 };
		const mockFetch = vi.fn().mockResolvedValue({ ok: true, status: 200, json: async () => expectedPage });
		globalThis.fetch = mockFetch;

		const result = await fetchPlayerSnapshots(
			demoProfile.playerId,
			{ from: '2026-10-01T00:00:00Z', to: '2026-10-31T23:59:59.999Z', page: 1, size: 100 },
			'http://localhost:8080/api/'
		);

		expect(mockFetch).toHaveBeenCalledWith(
			'http://localhost:8080/api/players/000000000000000000000000/snapshots?from=2026-10-01T00%3A00%3A00Z&to=2026-10-31T23%3A59%3A59.999Z&page=1&size=100',
			{ headers: { Accept: 'application/json' } }
		);
		expect(result.items).toHaveLength(5);
		expect(result.totalPages).toBe(2);
	});

	it('usa paginación predeterminada y acepta una página vacía', async () => {
		const expectedPage = { items: [], page: 0, size: 100, totalItems: 0, totalPages: 0 };
		const mockFetch = vi.fn().mockResolvedValue({ ok: true, status: 200, json: async () => expectedPage });
		globalThis.fetch = mockFetch;

		const result = await fetchPlayerSnapshots(demoProfile.playerId);

		expect(mockFetch).toHaveBeenCalledWith(
			`http://localhost:8080/api/players/${demoProfile.playerId}/snapshots?page=0&size=100`,
			{ headers: { Accept: 'application/json' } }
		);
		expect(result.items).toEqual([]);
	});

	it('preserva el ProblemDetail de errores HTTP', async () => {
		globalThis.fetch = vi.fn().mockResolvedValue({
			ok: false,
			status: 404,
			json: async () => ({ code: 'PLAYER_NOT_FOUND', detail: 'Jugador no almacenado.' })
		});

		await expect(fetchPlayerSnapshots(demoProfile.playerId)).rejects.toMatchObject({
			name: 'PlayerApiError',
			code: 'PLAYER_NOT_FOUND',
			status: 404,
			message: 'Jugador no almacenado.'
		});
	});

	it('rechaza una respuesta 200 con envoltorio de página malformado', async () => {
		globalThis.fetch = vi.fn().mockResolvedValue({
			ok: true,
			status: 200,
			json: async () => ({ items: 'no-es-una-lista' })
		});

		await expect(fetchPlayerSnapshots(demoProfile.playerId)).rejects.toThrow(
			'El backend devolvió un formato de historial no válido.'
		);
	});
});
