<script lang="ts">
	import { PUBLIC_API_BASE_URL } from '$app/env/public';
	import { fetchTrEstimate, type TrEstimateResponse } from './tr-estimate';
import { PlayerApiError, type LeagueSummary } from './player';

	let {
		playerId,
		league,
		isDemo = false
	}: {
		playerId: string;
		league: LeagueSummary | null;
		isDemo?: boolean;
	} = $props();

	let result = $state<TrEstimateResponse | null>(null);
	let isLoading = $state(false);
	let errorMessage = $state('');

	const requiredFeatures = [
		{ key: 'apm', label: 'ritmo de ataque por minuto' },
		{ key: 'pps', label: 'velocidad de piezas por segundo' },
		{ key: 'vs', label: 'puntaje de versus' },
		{ key: 'gamesPlayed', label: 'partidas de la liga' }
	] as const;

	const missingFeatures = $derived(
		requiredFeatures.filter(({ key }) => {
			const value = league?.[key];
			return typeof value !== 'number' || !Number.isFinite(value) || value < 0;
		})
	);

	const featureLabels: Record<string, string> = {
		apm: 'Ataque por minuto',
		pps: 'Piezas por segundo',
		vs: 'Puntaje de versus',
		gamesPlayed: 'Partidas jugadas'
	};

	async function requestEstimate() {
		if (isLoading || isDemo || missingFeatures.length > 0) return;
		isLoading = true;
		result = null;
		errorMessage = '';
		try {
			result = await fetchTrEstimate(playerId, PUBLIC_API_BASE_URL);
		} catch (error) {
			if (error instanceof PlayerApiError && error.code === 'PREDICTION_NOT_APPLICABLE') {
				errorMessage = 'El perfil no tiene todas las métricas necesarias para esta estimación.';
			} else if (error instanceof PlayerApiError && error.code === 'AI_SERVICE_UNAVAILABLE') {
				errorMessage = 'El servicio de inteligencia artificial no está disponible. Comprueba que esté activo e inténtalo de nuevo.';
			} else {
				errorMessage = error instanceof Error ? error.message : 'No se pudo obtener la estimación.';
			}
		} finally {
			isLoading = false;
		}
	}
</script>

<section class="ai-panel" aria-labelledby="ai-title">
	<div class="ai-heading">
		<div>
			<p class="eyebrow"><span>03</span> MODELO ESTADÍSTICO</p>
			<h3 id="ai-title">Estimación de TR</h3>
			<p class="ai-caption">Compara las métricas disponibles con un modelo entrenado.</p>
		</div>
	</div>

	{#if isDemo}
		<p class="ai-state" role="status">La estimación no está disponible en el perfil de demostración. Sus datos son sintéticos.</p>
	{:else if missingFeatures.length > 0}
		<div class="ai-state" role="status">
			<strong>No se puede estimar para este perfil todavía.</strong>
			<p>Faltan las métricas requeridas por el modelo:</p>
			<ul>{#each missingFeatures as feature (feature.key)}<li>{feature.label}</li>{/each}</ul>
		</div>
	{:else}
		<div class="ai-action-row">
			<p>Se usarán las cuatro métricas de rendimiento que aparecen en el perfil.</p>
			<button class="estimate-button" type="button" onclick={requestEstimate} disabled={isLoading}>
				{isLoading ? 'Calculando…' : result ? 'Volver a estimar' : 'Estimar TR'}
			</button>
		</div>
	{/if}

	{#if errorMessage}<p class="ai-error" role="alert">{errorMessage}</p>{/if}
	{#if result?.status === 'model_unavailable'}
		<p class="ai-state" role="status">El servicio está conectado, pero no hay un modelo entrenado cargado. Hay que preparar el conjunto de datos y entrenar el modelo antes de estimar.</p>
	{:else if result?.status === 'ok'}
		<div class="estimate-result" aria-live="polite">
			<div class="estimate-values">
				<div><span>TR ESTIMADO</span><strong>{result.estimatedTr === null ? 'Sin dato' : Math.round(result.estimatedTr).toLocaleString('es-CO')}</strong></div>
				<div><span>TR OBSERVADO</span><strong>{result.observedTr === null ? 'Sin dato' : Math.round(result.observedTr).toLocaleString('es-CO')}</strong></div>
			</div>
			<p class="model-meta">Modelo {result.modelName ?? 'sin nombre'} · versión {result.modelVersion ?? 'sin versión'}</p>
			{#if result.explanation.length > 0}
				<div class="explanation">
					<strong>Contribuciones del modelo</strong>
					<ul>{#each result.explanation as item (item.feature)}<li><span>{featureLabels[item.feature] ?? item.feature}</span><span>{item.contribution > 0 ? '+' : ''}{item.contribution.toFixed(2)}</span></li>{/each}</ul>
					<p>Estas contribuciones describen el cálculo del modelo; no demuestran causa y efecto.</p>
				</div>
			{/if}
			<p class="ai-disclaimer">{result.disclaimer}</p>
		</div>
	{/if}
</section>

<style>
	.ai-panel { margin-top: 38px; padding: 22px 0; border-top: 1px solid #ffffff18; border-bottom: 1px solid #ffffff18; }
	.ai-heading { display: flex; justify-content: space-between; align-items: flex-end; gap: 16px; }
	.ai-heading h3 { margin: 8px 0 5px; font-size: 20px; letter-spacing: -.04em; }
	.ai-caption, .ai-action-row p, .model-meta, .ai-disclaimer { margin: 0; color: #828c82; font-size: 10px; line-height: 1.6; }
	.ai-action-row { display: flex; justify-content: space-between; align-items: center; gap: 14px; margin-top: 15px; }
	.estimate-button { min-height: 38px; padding: 0 15px; border: 1px solid #b9f36b45; border-radius: 4px; color: #b9f36b; background: #b9f36b0d; font-size: 10px; font-weight: 700; cursor: pointer; }
	.estimate-button:hover:not(:disabled) { background: #b9f36b19; }
	.estimate-button:disabled { opacity: .5; cursor: progress; }
	.ai-state { margin: 15px 0 0; padding: 14px; border: 1px solid #ffffff15; border-radius: 5px; color: #aab2a9; background: #111617; font-size: 11px; line-height: 1.6; }
	.ai-state p { margin: 6px 0 0; }
	.ai-state ul { margin: 5px 0 0; padding-left: 20px; }
	.ai-error { margin: 12px 0 0; color: #f2a18c; font-size: 11px; }
	.estimate-result { margin-top: 15px; padding: 15px; border: 1px solid #b9f36b35; border-radius: 5px; background: #111617; }
	.estimate-values { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
	.estimate-values div { display: flex; flex-direction: column; gap: 8px; }
	.estimate-values span { color: #838d83; font-size: 8px; letter-spacing: .12em; }
	.estimate-values strong { color: #b9f36b; font-size: 23px; }
	.model-meta { margin-top: 13px; }
	.explanation { margin-top: 16px; padding-top: 13px; border-top: 1px solid #ffffff12; color: #cbd1c9; font-size: 10px; }
	.explanation ul { display: grid; gap: 8px; margin: 10px 0; padding: 0; list-style: none; }
	.explanation li { display: flex; justify-content: space-between; gap: 10px; }
	.explanation li span:last-child { color: #b9f36b; font-variant-numeric: tabular-nums; }
	.explanation p { margin: 0; color: #7f897f; line-height: 1.5; }
	.ai-disclaimer { margin-top: 12px; }
	@media (max-width: 600px) { .ai-action-row { align-items: stretch; flex-direction: column; }.estimate-button { align-self: flex-start; } }
</style>
