<script lang="ts">
  import { onMount } from 'svelte';
  import { api, type Settings } from '$lib/api';
  import { toast } from '$lib/stores/toast';
  import { theme, themes } from '$lib/stores/theme';

  let settings: Settings = { expiry_warning_days: 7 };
  let loading = true;
  let saving = false;

  onMount(load);

  async function load() {
    loading = true;
    try {
      settings = await api.settings.get();
    } catch {
      toast.show('Failed to load settings', 'error');
    } finally {
      loading = false;
    }
  }

  async function save() {
    if (settings.expiry_warning_days < 1) {
      toast.show('Expiry warning must be at least 1 day', 'warning');
      return;
    }
    saving = true;
    try {
      settings = await api.settings.update(settings);
      toast.show('Settings saved');
    } catch {
      toast.show('Failed to save settings', 'error');
    } finally {
      saving = false;
    }
  }
</script>

<div class="page-header">
  <h1>Settings</h1>
</div>

{#if loading}
  <div class="spinner" />
{:else}
  <div class="settings-group card">
    <div class="setting-row">
      <span class="setting-title">Appearance</span>
    </div>
    <div class="theme-grid">
      {#each themes as t}
        <button
          class="theme-card"
          class:selected={$theme === t.id}
          on:click={() => theme.set(t.id)}
          aria-label="Select {t.name} theme"
          aria-pressed={$theme === t.id}
        >
          <div class="theme-swatches">
            {#each t.swatches as color}
              <span class="swatch" style="background:{color}"></span>
            {/each}
          </div>
          <span class="theme-name">{t.name}</span>
          <span class="theme-desc">{t.desc}</span>
        </button>
      {/each}
    </div>
  </div>

  <div class="settings-group card">
    <div class="setting-row">
      <div class="setting-label">
        <span class="setting-title">Expiry Warning</span>
        <span class="setting-desc">
          Warn when a product expires within this many days
        </span>
      </div>
      <div class="setting-control">
        <button
          class="stepper-btn"
          on:click={() => { if (settings.expiry_warning_days > 1) settings.expiry_warning_days--; }}
          aria-label="Decrease"
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none"
               stroke="currentColor" stroke-width="2.5" stroke-linecap="round">
            <path d="M5 12h14"/>
          </svg>
        </button>
        <span class="stepper-value">{settings.expiry_warning_days}d</span>
        <button
          class="stepper-btn"
          on:click={() => settings.expiry_warning_days++}
          aria-label="Increase"
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none"
               stroke="currentColor" stroke-width="2.5" stroke-linecap="round">
            <path d="M12 5v14M5 12h14"/>
          </svg>
        </button>
      </div>
    </div>
  </div>

  <div style="margin-top: 20px">
    <button class="btn btn-primary btn-full" on:click={save} disabled={saving}>
      {saving ? 'Saving…' : 'Save Settings'}
    </button>
  </div>

  <div class="about card">
    <p class="about-title">Food Inventory</p>
    <p>Scan barcodes with your camera to manage your home food stock.</p>
    <p style="margin-top:6px; font-size:.8rem">
      Product data provided by
      <a href="https://world.openfoodfacts.org" target="_blank" rel="noopener">
        Open Food Facts
      </a>
    </p>
  </div>
{/if}

<style>
  .settings-group {
    overflow: hidden;
    margin-bottom: 12px;
  }

  .theme-grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 8px;
    padding: 0 12px 12px;
  }

  .theme-card {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 6px;
    padding: 10px 10px 12px;
    border-radius: var(--radius-sm);
    border: 1.5px solid var(--c-border);
    background: var(--c-bg);
    cursor: pointer;
    transition: border-color .15s, box-shadow .15s;
    text-align: left;
  }
  .theme-card:hover {
    border-color: var(--c-primary);
  }
  .theme-card.selected {
    border-color: var(--c-primary);
    box-shadow: 0 0 0 2px var(--c-primary);
  }

  .theme-swatches {
    display: flex;
    gap: 4px;
    width: 100%;
  }

  .swatch {
    flex: 1;
    height: 20px;
    border-radius: 4px;
  }

  .theme-name {
    font-size: .82rem;
    font-weight: 700;
    color: var(--c-text);
    line-height: 1.2;
  }

  .theme-desc {
    font-size: .72rem;
    color: var(--c-muted);
    line-height: 1.3;
  }

  .setting-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    padding: 16px;
  }

  .setting-label { flex: 1; }
  .setting-title {
    font-weight: 600;
    font-size: .95rem;
    display: block;
  }
  .setting-desc {
    font-size: .8rem;
    color: var(--c-muted);
    margin-top: 2px;
    display: block;
  }

  .setting-control {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-shrink: 0;
  }

  .stepper-btn {
    width: 34px;
    height: 34px;
    border-radius: 50%;
    background: var(--c-bg);
    border: 1.5px solid var(--c-border);
    display: flex;
    align-items: center;
    justify-content: center;
    color: var(--c-text);
    transition: background .15s;
  }
  .stepper-btn:hover { background: var(--c-primary-bg); border-color: var(--c-primary); color: var(--c-primary); }

  .stepper-value {
    font-size: 1.1rem;
    font-weight: 700;
    min-width: 36px;
    text-align: center;
  }

  .about {
    margin-top: 32px;
    padding: 16px;
    color: var(--c-muted);
    font-size: .82rem;
    line-height: 1.5;
  }
  .about-title {
    font-weight: 700;
    color: var(--c-text);
    font-size: .95rem;
    margin-bottom: 4px;
  }
  .about a { color: var(--c-primary); }
</style>
