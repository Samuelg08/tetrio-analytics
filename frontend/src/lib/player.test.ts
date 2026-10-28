import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import {
	apiBaseUrl,
	DEFAULT_API_BASE_URL,
	fetchPlayerProfile,
	PlayerApiError
} from './player';
import { demoProfile } from './fixtures/player.fixture';

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
