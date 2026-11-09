<script lang="ts">
	import { PUBLIC_API_BASE_URL } from '$app/env/public';
	import {
		demoProfile,
		fetchPlayerProfile,
		fetchPlayerSnapshots,
		PlayerApiError,
		type PlayerProfile,
		type SnapshotPage
	} from '../lib/player';
	import { demoSnapshots } from '../lib/fixtures/player.fixture';
	import { buildSnapshotChartPoints } from '../lib/snapshot-chart';
	import TrEstimatePanel from '../lib/TrEstimatePanel.svelte';

	let username = $state('');
	let profile = $state<PlayerProfile | null>(null);
	let isLoading = $state(false);
	let errorMessage = $state('');
	let isDemo = $state(false);
	let snapshots = $state<SnapshotPage | null>(null);
	let isLoadingSnapshots = $state(false);
	let snapshotError = $state('');
	let snapshotFilterError = $state('');
	let snapshotsPageNumber = $state(0);
	let historyFrom = $state('');
	let historyTo = $state('');

	const sourceLabels = {
		live: 'Actualizado en vivo',
		cache: 'Desde caché',
		stale: 'Copia local disponible'
	};

	const metrics = [
		{ label: 'TR', key: 'tr', format: (value: number) => Math.round(value).toLocaleString('es-CO'), featured: true },
		{ label: 'Rango actual', key: 'rank', format: (value: string) => value, featured: true },
		{ label: 'APM', key: 'apm', format: (value: number) => value.toFixed(2) },
		{ label: 'PPS', key: 'pps', format: (value: number) => value.toFixed(2) },
		{ label: 'VS', key: 'vs', format: (value: number) => value.toFixed(2) },
		{ label: 'Glicko', key: 'glicko', format: (value: number) => value.toFixed(2) },
		{ label: 'Récord de rango', key: 'bestRank', format: (value: string) => value },
		{ label: 'Posición global', key: 'standing', format: (value: number) => `#${value.toLocaleString('es-CO')}` }
	] as const;

	function displayMetric(metric: (typeof metrics)[number]): string {
		const value = profile?.league?.[metric.key];
		if (value === null || value === undefined || value === '') return 'Sin dato';
		if ('format' in metric && typeof metric.format === 'function') {
			return (metric.format as (v: any) => string)(value);
		}
		if (typeof value === 'number') {
			return value.toFixed(2);
		}
		return String(value);
	}

	function formatDate(date: string): string {
		const parsed = new Date(date);
		if (Number.isNaN(parsed.getTime())) return 'Fecha no disponible';
		return new Intl.DateTimeFormat('es-CO', {
			dateStyle: 'medium',
			timeStyle: 'short',
			timeZone: 'America/Bogota'
		}).format(parsed);
	}

	function formatDay(date: string): string {
		const parsed = new Date(date);
		if (Number.isNaN(parsed.getTime())) return 'Fecha inválida';
		return new Intl.DateTimeFormat('es-CO', { dateStyle: 'medium', timeZone: 'America/Bogota' }).format(parsed);
	}

	async function searchPlayer(event: SubmitEvent) {
		event.preventDefault();
		const query = username.trim();
		if (!query || isLoading) return;

		isLoading = true;
		errorMessage = '';
		snapshotError = '';
		snapshots = null;
		historyFrom = '';
		historyTo = '';
		isDemo = false;
		try {
			const result = await fetchPlayerProfile(query, PUBLIC_API_BASE_URL ?? undefined);
			profile = result;
			snapshotsPageNumber = 0;
			await loadSnapshots(result.playerId, 0);
		} catch (error) {
			profile = null;
			if (error instanceof PlayerApiError) {
				if (error.code === 'PLAYER_NOT_FOUND') {
					errorMessage = 'No encontramos ese jugador. Revisa el nombre e inténtalo de nuevo.';
				} else if (error.code === 'RATE_LIMITED' || error.code === 'UPSTREAM_UNAVAILABLE') {
					errorMessage = 'El servicio está temporalmente limitado. Espera un momento y vuelve a intentar.';
				} else if (error.code === 'VALIDATION_FAILED') {
					const fieldErrors = error.errors?.map((e) => e.message).filter(Boolean).join('. ');
					errorMessage = fieldErrors
						? `Parámetros inválidos: ${fieldErrors}`
						: (error.message || 'El username debe tener entre 3 y 16 caracteres alfanuméricos.');
				} else {
					errorMessage = error.message;
				}
			} else {
				errorMessage = 'No se pudo completar la búsqueda. Inténtalo de nuevo.';
			}
		} finally {
			isLoading = false;
		}
	}

	function loadDemo() {
		profile = demoProfile;
		username = demoProfile.username;
		errorMessage = '';
		isDemo = true;
		snapshotsPageNumber = 0;
		void loadSnapshots(demoProfile.playerId, 0);
	}

	function dateFilterBounds() {
		return {
			from: historyFrom ? new Date(`${historyFrom}T00:00:00`).toISOString() : undefined,
			to: historyTo ? new Date(`${historyTo}T23:59:59.999`).toISOString() : undefined
		};
	}

	async function loadSnapshots(playerId: string, page: number) {
		isLoadingSnapshots = true;
		snapshotError = '';
		snapshotFilterError = '';
		try {
			if (isDemo) {
				const { from, to } = dateFilterBounds();
				const fromTime = from ? Date.parse(from) : Number.NEGATIVE_INFINITY;
				const toTime = to ? Date.parse(to) : Number.POSITIVE_INFINITY;
				const filtered = demoSnapshots.filter((item) => {
					const capturedAt = Date.parse(item.capturedAt);
					return capturedAt >= fromTime && capturedAt <= toTime;
				});
				snapshots = { items: filtered, page: 0, size: 100, totalItems: filtered.length, totalPages: filtered.length ? 1 : 0 };
				snapshotsPageNumber = 0;
				return;
			}

			snapshots = await fetchPlayerSnapshots(
				playerId,
				{ ...dateFilterBounds(), page, size: 100 },
				PUBLIC_API_BASE_URL
			);
			snapshotsPageNumber = snapshots.page;
		} catch (error) {
			snapshots = null;
			snapshotError = error instanceof PlayerApiError && error.code === 'PLAYER_NOT_FOUND'
				? 'El perfil existe, pero todavía no hay snapshots guardados para mostrar.'
				: error instanceof PlayerApiError
					? error.message
					: 'No se pudo cargar el historial. Inténtalo de nuevo.';
		} finally {
			isLoadingSnapshots = false;
		}
	}

	function applySnapshotFilters(event: SubmitEvent) {
		event.preventDefault();
		if (!profile) return;
		if (historyFrom && historyTo && historyFrom > historyTo) {
			snapshotFilterError = 'La fecha inicial debe ser anterior o igual a la fecha final.';
			return;
		}
		void loadSnapshots(profile.playerId, 0);
	}

	function changeSnapshotPage(offset: number) {
		if (!profile || !snapshots) return;
		const nextPage = snapshotsPageNumber + offset;
		if (nextPage < 0 || nextPage >= snapshots.totalPages) return;
		void loadSnapshots(profile.playerId, nextPage);
	}

	const chartPoints = $derived(buildSnapshotChartPoints(snapshots?.items ?? []));
	const chartPolyline = $derived(chartPoints.map((point) => `${point.x},${point.y}`).join(' '));
</script>

<svelte:head>
	<title>TETR.IO Analytics — Perfil competitivo</title>
	<meta
		name="description"
		content="Consulta el perfil competitivo y las estadísticas de jugadores de TETR.IO."
	/>
</svelte:head>

<div class="app-shell">
	<header class="topbar">
		<a class="brand" href="/" aria-label="TETR.IO Analytics, inicio">
			<span class="brand-mark" aria-hidden="true"><span></span><span></span><span></span><span></span></span>
			<span>TETR.IO <b>ANALYTICS</b></span>
		</a>
		<div class="topbar-status"><span class="status-dot"></span> BETA ACADÉMICA</div>
	</header>

	<main>
		<section class="hero" aria-labelledby="page-title">
			<p class="eyebrow"><span>01</span> ANALÍTICA COMPETITIVA</p>
			<h1 id="page-title">Lee el juego.<br /><span>Sube de nivel.</span></h1>
			<p class="intro">Explora el rendimiento competitivo de la comunidad TETR.IO, jugador por jugador.</p>

			<form class="search-form" onsubmit={searchPlayer}>
				<label for="username">NOMBRE DE JUGADOR</label>
				<div class="search-row">
					<div class="input-wrap">
						<span class="at-sign" aria-hidden="true">@</span>
						<input
							id="username"
							name="username"
							bind:value={username}
							placeholder="ej. tetrio_player"
							autocomplete="off"
							maxlength="16"
							aria-describedby="search-hint"
							disabled={isLoading}
						/>
					</div>
					<button class="search-button" type="submit" disabled={isLoading || !username.trim()}>
						{isLoading ? 'Buscando…' : 'Buscar jugador'}
						<span aria-hidden="true">↗</span>
					</button>
				</div>
				<p class="hint" id="search-hint">Escribe un username de TETR.IO para consultar su perfil.</p>
			</form>
		</section>

		{#if errorMessage}
			<section class="notice error-notice" role="alert">
				<div><strong>No pudimos cargar el perfil</strong><p>{errorMessage}</p></div>
				<button type="button" class="text-button" onclick={loadDemo}>Ver perfil de demostración</button>
			</section>
		{/if}

		{#if profile}
			<section class="profile-section" aria-live="polite" aria-label="Perfil del jugador">
				{#if isDemo}
					<div class="notice demo-notice"><strong>Modo demostración</strong><span>Datos sintéticos para mostrar la interfaz; no corresponden a un jugador real.</span></div>
				{/if}
				<div class="profile-heading">
					<div class="avatar" aria-hidden="true">{profile.username.slice(0, 1).toUpperCase()}</div>
					<div class="identity">
						<p class="eyebrow">PERFIL DE JUGADOR</p>
						<h2>@{profile.username}</h2>
						<p class="player-id">ID <span>{profile.playerId}</span>{#if profile.country}<span class="country">{profile.country}</span>{/if}</p>
					</div>
					<div class="sync-info">
						<span class="source-badge" class:stale={profile.dataSource === 'stale'}>
							<span class="status-dot"></span>{isDemo ? 'Datos de demostración' : sourceLabels[profile.dataSource]}
						</span>
						<span class="sync-date">Sincronizado: {formatDate(profile.lastSyncedAt)}</span>
					</div>
				</div>

				{#if profile.league}
					<div class="league-summary">
						<div class="league-title"><span class="league-icon" aria-hidden="true">◆</span><div><h3>TETRA LEAGUE</h3><p>Resumen competitivo</p></div></div>
						<div class="league-meta">
							<span class:unranked={!profile.league.ranked} class="rank-pill">{profile.league.ranked ? profile.league.rank ?? 'Clasificado' : 'Sin clasificar'}</span>
							<span>{profile.league.gamesPlayed.toLocaleString('es-CO')} partidas</span>
						</div>
					</div>
					<div class="metric-grid">
						{#each metrics as metric (metric.key)}
			<article class="metric-card" class:featured={metric.key === 'tr' || metric.key === 'rank'}>
								<p>{metric.label}</p><strong>{displayMetric(metric)}</strong>
							</article>
						{/each}
					</div>
					<div class="record-strip">
						<div><span>PARTIDAS GANADAS</span><strong>{profile.league.gamesWon.toLocaleString('es-CO')}</strong></div>
						<div><span>PORCENTAJE DE VICTORIAS</span><strong>{profile.league.gamesPlayed > 0 ? `${((profile.league.gamesWon / profile.league.gamesPlayed) * 100).toFixed(1)}%` : 'Sin dato'}</strong></div>
						<div><span>ESTADO DE RATING</span><strong>{profile.league.decaying ? 'En decaimiento' : 'Activo'}</strong></div>
					</div>
				{:else}
					<div class="notice empty-league"><strong>Sin resumen de liga</strong><p>El backend encontró el perfil, pero TETRA LEAGUE aún no tiene estadísticas disponibles.</p></div>
				{/if}

				<TrEstimatePanel playerId={profile.playerId} league={profile.league} {isDemo} />

				<section class="history-panel" aria-labelledby="history-title">
					<div class="history-heading">
						<div>
							<p class="eyebrow"><span>02</span> EVOLUCIÓN</p>
							<h3 id="history-title">Historial de TR</h3>
							<p class="history-caption">Snapshots guardados por el backend en orden cronológico.</p>
						</div>
						{#if snapshots}<span class="history-count">{snapshots.totalItems.toLocaleString('es-CO')} registros</span>{/if}
					</div>

					<form class="history-filters" onsubmit={applySnapshotFilters}>
						<div class="date-field"><label for="history-from">DESDE</label><input id="history-from" type="date" bind:value={historyFrom} /></div>
						<div class="date-field"><label for="history-to">HASTA</label><input id="history-to" type="date" bind:value={historyTo} /></div>
						<button class="filter-button" type="submit" disabled={isLoadingSnapshots}>Aplicar fechas</button>
					</form>

					{#if snapshotFilterError}<p class="filter-error" role="alert">{snapshotFilterError}</p>{/if}
					{#if isLoadingSnapshots}
						<p class="history-state" role="status">Cargando snapshots…</p>
					{:else if snapshotError}
						<div class="notice history-error" role="alert"><div><strong>Historial no disponible</strong><p>{snapshotError}</p></div><button class="text-button" type="button" onclick={() => profile && loadSnapshots(profile.playerId, snapshotsPageNumber)}>Reintentar</button></div>
					{:else if snapshots && snapshots.items.length === 0}
						<p class="history-state">No hay snapshots para este jugador en el periodo seleccionado.</p>
					{:else if snapshots}
						{@const pointsWithTr = chartPoints}
						{#if pointsWithTr.length > 0}
							<div class="chart-shell">
								<svg class="history-chart" viewBox="0 0 720 250" role="img" aria-label="Evolución de TR en los snapshots cargados">
									<line class="chart-grid" x1="48" x2="672" y1="44" y2="44" />
									<line class="chart-grid" x1="48" x2="672" y1="124" y2="124" />
									<line class="chart-grid" x1="48" x2="672" y1="204" y2="204" />
									<text class="chart-label" x="7" y="48">TR alto</text>
									<text class="chart-label" x="7" y="208">TR bajo</text>
									{#if pointsWithTr.length > 1}<polyline class="chart-line" points={chartPolyline} />{/if}
									{#each pointsWithTr as point (point.item.capturedAt)}
										<circle class="chart-point" cx={point.x} cy={point.y} r="4"><title>{formatDay(point.item.capturedAt)} · TR {Math.round(point.tr).toLocaleString('es-CO')}</title></circle>
									{/each}
									<text class="chart-date" x="48" y="238">{formatDay(pointsWithTr[0].item.capturedAt)}</text>
									<text class="chart-date" x="672" y="238" text-anchor="end">{formatDay(pointsWithTr[pointsWithTr.length - 1].item.capturedAt)}</text>
								</svg>
								<p class="chart-note">Cada punto representa un snapshot persistido. La línea conecta observaciones disponibles; no es una predicción.</p>
							</div>
						{:else}
							<p class="history-state">Hay snapshots, pero no contienen valores de TR para graficar.</p>
						{/if}

						<div class="table-wrap">
							<table>
								<caption>Snapshots cargados, del más antiguo al más reciente</caption>
								<thead><tr><th scope="col">Fecha</th><th scope="col">TR</th><th scope="col">Rango</th><th scope="col">Partidas</th></tr></thead>
								<tbody>
									{#each snapshots.items as snapshot (snapshot.capturedAt)}
										<tr><td>{formatDay(snapshot.capturedAt)}</td><td>{snapshot.tr === null ? 'Sin dato' : Math.round(snapshot.tr).toLocaleString('es-CO')}</td><td>{snapshot.rank ?? 'Sin dato'}</td><td>{snapshot.gamesPlayed.toLocaleString('es-CO')}</td></tr>
									{/each}
								</tbody>
							</table>
						</div>
						{#if snapshots.totalPages > 1}
							<nav class="history-pagination" aria-label="Paginación del historial">
								<button type="button" class="filter-button secondary" onclick={() => changeSnapshotPage(-1)} disabled={snapshotsPageNumber === 0 || isLoadingSnapshots}>Anterior</button>
								<span>Página {snapshotsPageNumber + 1} de {snapshots.totalPages}</span>
								<button type="button" class="filter-button secondary" onclick={() => changeSnapshotPage(1)} disabled={snapshotsPageNumber + 1 >= snapshots.totalPages || isLoadingSnapshots}>Siguiente</button>
							</nav>
						{/if}
					{/if}
				</section>
			</section>
		{:else if !errorMessage}
			<section class="empty-state" aria-label="Inicia una búsqueda">
				<div class="empty-grid" aria-hidden="true"><span></span><span></span><span></span><span></span><span></span><span></span><span></span><span></span><span></span></div>
				<p>Tu próxima partida empieza con datos.</p>
				<button type="button" class="text-button" onclick={loadDemo}>Probar con perfil de demostración</button>
			</section>
		{/if}
	</main>

	<footer><span>TETR.IO ANALYTICS</span><span>Datos servidos por TETRA CHANNEL · Proyecto académico</span></footer>
</div>

<style>
	:global(*) { box-sizing: border-box; }
	:global(body) { margin: 0; background: #0b0e0f; color: #f2f3ed; font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; }
	:global(button), :global(input) { font: inherit; }
	:global(button:focus-visible), :global(input:focus-visible), :global(a:focus-visible) { outline: 2px solid #b9f36b; outline-offset: 3px; }
	.app-shell { min-height: 100vh; background-image: linear-gradient(90deg, #ffffff05 1px, transparent 1px), linear-gradient(#ffffff05 1px, transparent 1px); background-size: 56px 56px; }
	.topbar { height: 76px; padding: 0 clamp(22px, 7vw, 112px); border-bottom: 1px solid #ffffff12; display: flex; align-items: center; justify-content: space-between; background: #0b0e0fea; }
	.brand { color: #f2f3ed; text-decoration: none; display: flex; align-items: center; gap: 12px; font-size: 13px; font-weight: 750; letter-spacing: .1em; }
	.brand b { color: #aab0a8; font-weight: 500; }
	.brand-mark { width: 23px; height: 23px; display: grid; grid-template-columns: repeat(2, 9px); grid-template-rows: repeat(2, 9px); gap: 3px; transform: rotate(-8deg); }
	.brand-mark span { background: #b9f36b; border-radius: 2px; }
	.brand-mark span:nth-child(2) { opacity: .75; }.brand-mark span:nth-child(3) { opacity: .55; }.brand-mark span:nth-child(4) { opacity: .9; }
	.topbar-status { color: #969e95; font-size: 10px; letter-spacing: .13em; display: flex; align-items: center; gap: 9px; }
	.status-dot { width: 7px; height: 7px; display: inline-block; background: #b9f36b; border-radius: 50%; box-shadow: 0 0 12px #b9f36b88; }
	main { width: min(100% - 44px, 940px); margin: 0 auto; padding: 82px 0 64px; }
	.hero { max-width: 720px; margin: 0 auto; }
	.eyebrow { margin: 0; color: #a9b0a6; font-size: 10px; font-weight: 700; letter-spacing: .19em; }
	.eyebrow span { color: #b9f36b; margin-right: 8px; }
	h1 { margin: 22px 0 17px; font-size: clamp(42px, 7vw, 70px); line-height: .99; letter-spacing: -.065em; font-weight: 720; }
	h1 span { color: #b9f36b; }
	.intro { color: #a8afa7; font-size: 15px; line-height: 1.7; max-width: 470px; margin: 0 0 41px; }
	.search-form { padding: 19px; border: 1px solid #ffffff20; background: #111617; border-radius: 9px; box-shadow: 0 20px 70px #0004; }
	.search-form label { display: block; margin: 0 0 10px 2px; color: #929b91; font-size: 9px; letter-spacing: .16em; font-weight: 700; }
	.search-row { display: flex; gap: 10px; }
	.input-wrap { flex: 1; min-width: 0; display: flex; align-items: center; border: 1px solid #ffffff24; background: #0b0e0f; border-radius: 5px; padding: 0 13px; }
	.at-sign { color: #899188; font-size: 16px; }
	input { flex: 1; min-width: 0; height: 48px; padding: 0 9px; border: 0; outline: 0; color: #f2f3ed; background: transparent; font-size: 14px; }
	input::placeholder { color: #606961; }
	.search-button { height: 48px; padding: 0 17px; border: 0; border-radius: 5px; background: #b9f36b; color: #10150c; font-weight: 750; font-size: 12px; cursor: pointer; transition: background .18s, transform .18s; }
	.search-button:hover:not(:disabled) { background: #c9ff7e; transform: translateY(-1px); }
	.search-button:disabled { opacity: .55; cursor: not-allowed; }
	.search-button span { margin-left: 11px; font-size: 16px; }
	.hint { color: #737d74; margin: 11px 2px 0; font-size: 10px; }
	.empty-state { min-height: 230px; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 21px; color: #757e75; font-size: 12px; }
	.empty-state p { margin: 0; }
	.empty-grid { display: grid; grid-template-columns: repeat(3, 14px); gap: 4px; transform: rotate(-8deg); }
	.empty-grid span { width: 14px; height: 14px; border: 1px solid #b9f36b42; border-radius: 2px; }
	.empty-grid span:nth-child(2), .empty-grid span:nth-child(4), .empty-grid span:nth-child(6), .empty-grid span:nth-child(8) { background: #b9f36b18; }
	.profile-section { margin: 51px auto 0; max-width: 820px; }
	.profile-heading { display: flex; align-items: center; gap: 17px; padding: 0 0 23px; border-bottom: 1px solid #ffffff18; }
	.avatar { width: 54px; height: 54px; flex: 0 0 54px; display: grid; place-items: center; border: 1px solid #b9f36b66; border-radius: 7px; color: #b9f36b; background: #b9f36b0d; font-size: 22px; font-weight: 700; }
	.identity { min-width: 0; }.identity h2 { margin: 5px 0 5px; font-size: 22px; letter-spacing: -.04em; }
	.player-id { margin: 0; color: #7f8980; font-size: 9px; letter-spacing: .08em; }.player-id span { font-family: ui-monospace, monospace; letter-spacing: 0; }.country { margin-left: 10px; color: #bbc2b9; font-family: inherit !important; }
	.sync-info { margin-left: auto; display: flex; flex-direction: column; align-items: flex-end; gap: 8px; }
	.source-badge { display: flex; align-items: center; gap: 7px; color: #b9f36b; font-size: 10px; }.source-badge .status-dot { width: 6px; height: 6px; }.source-badge.stale { color: #f2bd75; }.source-badge.stale .status-dot { background: #f2bd75; box-shadow: 0 0 12px #f2bd7588; }
	.sync-date { color: #777f77; font-size: 9px; }
	.league-summary { padding: 21px 0 13px; display: flex; justify-content: space-between; align-items: center; }
	.league-title { display: flex; align-items: center; gap: 11px; }.league-icon { color: #b9f36b; font-size: 20px; }.league-title h3 { margin: 0; font-size: 12px; letter-spacing: .12em; }.league-title p { margin: 4px 0 0; color: #747e75; font-size: 10px; }
	.league-meta { display: flex; align-items: center; gap: 13px; color: #8a938a; font-size: 10px; }.rank-pill { padding: 6px 10px; color: #b9f36b; background: #b9f36b14; border: 1px solid #b9f36b35; border-radius: 4px; font-weight: 700; }.rank-pill.unranked { color: #a2aaa1; background: #ffffff09; border-color: #ffffff1a; }
	.metric-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 9px; }
	.metric-card { min-height: 86px; padding: 15px; border: 1px solid #ffffff14; border-radius: 5px; background: #111617; }.metric-card p { margin: 0 0 11px; color: #838d83; font-size: 9px; letter-spacing: .12em; text-transform: uppercase; }.metric-card strong { color: #e8ebe3; font-size: 19px; letter-spacing: -.04em; }.metric-card.featured { border-color: #b9f36b38; background: linear-gradient(135deg, #b9f36b0d, #111617 70%); }.metric-card.featured strong { color: #b9f36b; }
	.record-strip { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; margin-top: 10px; padding: 16px; border: 1px solid #ffffff14; border-radius: 5px; background: #111617; }.record-strip div { display: flex; flex-direction: column; gap: 8px; }.record-strip span { color: #838d83; font-size: 8px; letter-spacing: .1em; }.record-strip strong { font-size: 13px; font-weight: 600; }
	.history-panel { margin-top: 45px; padding-top: 24px; border-top: 1px solid #ffffff18; }
	.history-heading { display: flex; justify-content: space-between; align-items: flex-end; gap: 16px; }
	.history-heading h3 { margin: 8px 0 5px; font-size: 20px; letter-spacing: -.04em; }
	.history-caption, .history-count { margin: 0; color: #828c82; font-size: 10px; }
	.history-count { padding-bottom: 4px; white-space: nowrap; }
	.history-filters { display: flex; align-items: flex-end; gap: 10px; margin: 18px 0 12px; }
	.date-field { display: flex; flex-direction: column; gap: 7px; }
	.date-field label { color: #899289; font-size: 8px; font-weight: 700; letter-spacing: .14em; }
	.date-field input { height: 37px; width: 150px; padding: 0 9px; border: 1px solid #ffffff20; border-radius: 4px; color: #e6eae2; background: #111617; color-scheme: dark; font-size: 11px; }
	.filter-button { min-height: 37px; padding: 0 13px; border: 1px solid #b9f36b45; border-radius: 4px; color: #b9f36b; background: #b9f36b0d; font-size: 10px; font-weight: 650; cursor: pointer; }
	.filter-button:hover:not(:disabled) { background: #b9f36b19; }
	.filter-button:disabled { opacity: .45; cursor: not-allowed; }
	.filter-button.secondary { border-color: #ffffff25; color: #c6ccc4; background: #ffffff08; }
	.filter-error { margin: 8px 0; color: #f2a18c; font-size: 11px; }
	.history-state { margin: 0; padding: 20px 14px; border: 1px solid #ffffff15; border-radius: 5px; color: #9ca49c; background: #111617; font-size: 11px; }
	.chart-shell { margin: 12px 0; padding: 12px 14px 9px; border: 1px solid #ffffff15; border-radius: 5px; background: #111617; }
	.history-chart { display: block; width: 100%; max-height: 290px; overflow: visible; }
	.chart-grid { stroke: #ffffff16; stroke-width: 1; stroke-dasharray: 3 6; }
	.chart-label, .chart-date { fill: #788278; font-family: inherit; font-size: 10px; }
	.chart-line { fill: none; stroke: #b9f36b; stroke-width: 2.5; stroke-linecap: round; stroke-linejoin: round; }
	.chart-point { fill: #0b0e0f; stroke: #c9ff7e; stroke-width: 2.5; }
	.chart-note { margin: 0; color: #747e75; font-size: 9px; line-height: 1.5; }
	.table-wrap { overflow-x: auto; border: 1px solid #ffffff15; border-radius: 5px; }
	table { width: 100%; border-collapse: collapse; background: #111617; font-size: 10px; text-align: left; }
	caption { padding: 11px 12px; color: #828c82; text-align: left; }
	th, td { padding: 10px 12px; border-top: 1px solid #ffffff10; white-space: nowrap; }
	th { color: #9ca69b; font-size: 8px; letter-spacing: .12em; text-transform: uppercase; }
	td { color: #d8ddd5; }
	.history-pagination { display: flex; align-items: center; justify-content: center; gap: 15px; margin-top: 12px; color: #8c968c; font-size: 10px; }
	.notice { display: flex; align-items: center; justify-content: space-between; gap: 15px; margin: 20px auto 0; max-width: 820px; padding: 14px 16px; border: 1px solid #ffffff1a; border-radius: 5px; background: #111617; }.notice strong { display: block; font-size: 12px; }.notice p { margin: 5px 0 0; color: #9aa39a; font-size: 11px; line-height: 1.5; }.error-notice { border-color: #ed8b7144; }.error-notice strong { color: #f2a18c; }.demo-notice { justify-content: flex-start; border-color: #f2bd7540; color: #f2bd75; }.demo-notice span { color: #a8afa7; font-size: 11px; }.text-button { flex: 0 0 auto; border: 0; padding: 8px; color: #b9f36b; background: transparent; font-size: 10px; cursor: pointer; text-decoration: underline; text-underline-offset: 3px; }.empty-league { margin-top: 10px; display: block; }
	footer { min-height: 64px; display: flex; justify-content: space-between; align-items: center; padding: 0 clamp(22px, 7vw, 112px); border-top: 1px solid #ffffff12; color: #727b72; font-size: 9px; letter-spacing: .06em; }footer span:first-child { color: #a3aca2; font-weight: 700; letter-spacing: .13em; }
	@media (max-width: 600px) { main { padding-top: 57px; }.topbar { height: 65px; }.brand { font-size: 11px; }.topbar-status { font-size: 8px; }.search-form { padding: 14px; }.search-row { flex-direction: column; }.search-button { width: 100%; }.profile-heading { align-items: flex-start; flex-wrap: wrap; }.sync-info { width: 100%; margin-left: 71px; align-items: flex-start; }.metric-grid { grid-template-columns: repeat(2, 1fr); }.record-strip { grid-template-columns: 1fr; }.league-summary { gap: 12px; align-items: flex-start; flex-direction: column; }.history-filters { align-items: stretch; flex-wrap: wrap; }.date-field { flex: 1; min-width: 130px; }.date-field input { width: 100%; }.filter-button { align-self: flex-end; }.history-heading { align-items: flex-start; }.history-count { padding-top: 24px; }.chart-shell { padding: 8px; }.chart-label { font-size: 8px; }.chart-date { font-size: 8px; }.history-pagination { gap: 8px; }.notice { align-items: flex-start; flex-direction: column; }.demo-notice span { line-height: 1.5; }footer { gap: 15px; align-items: flex-start; flex-direction: column; justify-content: center; padding-top: 15px; padding-bottom: 15px; } }
</style>
