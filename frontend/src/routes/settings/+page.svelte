<script lang="ts">
  import { onMount } from 'svelte';
  import { api, type Settings } from '$lib/api';
  import { toast } from '$lib/stores/toast';

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
