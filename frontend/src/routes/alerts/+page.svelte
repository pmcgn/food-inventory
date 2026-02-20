<script lang="ts">
  import { onMount } from 'svelte';
  import { api, type Alert } from '$lib/api';
  import { toast } from '$lib/stores/toast';

  let alerts: Alert[] = [];
  let loading = true;

  onMount(load);

  async function load() {
    loading = true;
    try {
      alerts = await api.alerts.list();
    } catch {
      toast.show('Failed to load alerts', 'error');
    } finally {
      loading = false;
    }
  }
</script>

<div class="page-header">
  <h1>Alerts</h1>
  {#if !loading && alerts.length > 0}
    <span class="count-badge">{alerts.length}</span>
  {/if}
</div>

{#if loading}
  <div class="spinner" />
{:else if alerts.length === 0}
  <div class="empty">
    <svg width="56" height="56" viewBox="0 0 24 24" fill="none"
         stroke="currentColor" stroke-width="1.2">
      <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/>
      <path d="M13.73 21a2 2 0 0 1-3.46 0"/>
    </svg>
    <p>All good — no active alerts.</p>
  </div>
{:else}
  <ul class="alert-list">
    {#each alerts as a (a.ean + a.type)}
      <li class="card alert-card alert-{a.type}">
        <div class="alert-icon">
          {#if a.type === 'low_stock'}
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none"
                 stroke="currentColor" stroke-width="2" stroke-linecap="round">
              <path d="M3 3h18M3 9h18M3 15h18"/>
            </svg>
          {:else}
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none"
                 stroke="currentColor" stroke-width="2" stroke-linecap="round">
              <circle cx="12" cy="12" r="10"/>
              <path d="M12 8v4m0 4h.01"/>
            </svg>
          {/if}
        </div>
        <div class="alert-body">
          <div class="alert-product">{a.product_name}</div>
          <div class="alert-detail">{a.detail}</div>
        </div>
        <span class="alert-type-badge">
          {a.type === 'low_stock' ? 'Low Stock' : 'Expiry Soon'}
        </span>
      </li>
    {/each}
  </ul>
{/if}

<style>
  .count-badge {
    background: var(--c-danger);
    color: #fff;
    font-size: .75rem;
    font-weight: 700;
    border-radius: 99px;
    padding: 2px 8px;
    min-width: 24px;
    text-align: center;
  }

  .alert-list {
    list-style: none;
    display: flex;
    flex-direction: column;
    gap: 10px;
  }

  .alert-card {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 14px 14px 14px 12px;
    border-left: 4px solid transparent;
  }
  .alert-low_stock  { border-left-color: var(--c-danger); }
  .alert-expiry_soon { border-left-color: var(--c-warning); }

  .alert-icon {
    width: 38px;
    height: 38px;
    border-radius: 10px;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
  }
  .alert-low_stock .alert-icon {
    background: var(--c-danger-bg);
    color: var(--c-danger);
  }
  .alert-expiry_soon .alert-icon {
    background: var(--c-warning-bg);
    color: var(--c-warning);
  }

  .alert-body { flex: 1; min-width: 0; }
  .alert-product {
    font-weight: 600;
    font-size: .95rem;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  .alert-detail {
    font-size: .8rem;
    color: var(--c-muted);
    margin-top: 2px;
  }

  .alert-type-badge {
    font-size: .68rem;
    font-weight: 700;
    padding: 3px 8px;
    border-radius: 99px;
    white-space: nowrap;
    flex-shrink: 0;
    text-transform: uppercase;
    letter-spacing: .04em;
  }
  .alert-low_stock .alert-type-badge {
    background: var(--c-danger-bg);
    color: var(--c-danger);
  }
  .alert-expiry_soon .alert-type-badge {
    background: var(--c-warning-bg);
    color: var(--c-warning);
  }
</style>
