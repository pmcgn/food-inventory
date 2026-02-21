<script lang="ts">
  import { onMount } from 'svelte';
  import { api, type InventoryEntry } from '$lib/api';
  import { toast } from '$lib/stores/toast';
  import BarcodeScanner from '$lib/components/BarcodeScanner.svelte';

  let items: InventoryEntry[] = [];
  let loading = true;

  // Scanner + add-product modal state
  let scanMode: 'add' | 'remove' | null = null;
  let showAddModal = false;
  let scannedEAN = '';
  let expiryDate = '';
  let adding = false;

  // Remove confirmation state
  let removingEAN: string | null = null;

  // Search
  let searchQuery = '';

  $: filteredItems = searchQuery.trim()
    ? items.filter(e => {
        const q = searchQuery.toLowerCase();
        return (
          e.product.ean.toLowerCase().includes(q) ||
          e.product.name.toLowerCase().includes(q) ||
          (e.product.category ?? '').toLowerCase().includes(q)
        );
      })
    : items;

  onMount(loadInventory);

  async function loadInventory() {
    loading = true;
    try {
      items = await api.inventory.list();
    } catch {
      toast.show('Failed to load inventory', 'error');
    } finally {
      loading = false;
    }
  }

  async function onScan(event: CustomEvent<string>) {
    const ean = event.detail;
    const mode = scanMode;
    scanMode = null;
    if (mode === 'remove') {
      await removeProduct(ean);
    } else {
      scannedEAN = ean;
      expiryDate = '';
      showAddModal = true;
    }
  }

  async function addProduct() {
    adding = true;
    try {
      await api.inventory.add(scannedEAN, expiryDate || undefined);
      showAddModal = false;
      toast.show('Product added to inventory');
      await loadInventory();
    } catch (e: unknown) {
      const err = e as { code?: string; message?: string };
      if (err.code === 'PRODUCT_NOT_FOUND') {
        toast.show('Product not found in the database', 'error');
        showAddModal = false;
      } else {
        toast.show(err.message ?? 'Failed to add product', 'error');
      }
    } finally {
      adding = false;
    }
  }

  async function removeProduct(ean: string) {
    removingEAN = ean;
    try {
      await api.inventory.remove(ean);
      toast.show('Quantity updated');
      await loadInventory();
    } catch (e: unknown) {
      const err = e as { message?: string };
      toast.show(err.message ?? 'Failed to remove product', 'error');
    } finally {
      removingEAN = null;
    }
  }

  function formatCategory(cat: string | null): string {
    if (!cat) return '';
    return cat.replace(/^en:/, '').replace(/-/g, ' ');
  }

  function isExpirySoon(entry: InventoryEntry): boolean {
    if (!entry.expiry_date) return false;
    const d = new Date(entry.expiry_date);
    const diff = (d.getTime() - Date.now()) / 86_400_000;
    return diff <= 7;
  }

  function isLowStock(entry: InventoryEntry): boolean {
    return entry.quantity <= entry.low_stock_threshold;
  }
</script>

<div class="page-header">
  <div class="page-title">
    <img src="/logo.svg" alt="" class="logo" aria-hidden="true" />
    <h1>Inventory</h1>
  </div>
  <span class="item-count">
    {#if searchQuery.trim() && filteredItems.length !== items.length}
      {filteredItems.length} of {items.length}
    {:else}
      {items.length}
    {/if}
    item{items.length !== 1 ? 's' : ''}
  </span>
</div>

<div class="search-wrap">
  <svg class="search-icon" width="16" height="16" viewBox="0 0 24 24" fill="none"
       stroke="currentColor" stroke-width="2.2" stroke-linecap="round">
    <circle cx="11" cy="11" r="8"/><path d="m21 21-4.35-4.35"/>
  </svg>
  <input
    class="search-input"
    type="search"
    placeholder="Search by name, EAN or category…"
    bind:value={searchQuery}
    aria-label="Search inventory"
  />
</div>

{#if loading}
  <div class="spinner" />
{:else if items.length === 0}
  <div class="empty">
    <svg width="56" height="56" viewBox="0 0 24 24" fill="none"
         stroke="currentColor" stroke-width="1.2">
      <path d="M6 2 3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4z"/>
      <line x1="3" y1="6" x2="21" y2="6"/>
      <path d="M16 10a4 4 0 0 1-8 0"/>
    </svg>
    <p>Your inventory is empty.<br/>Tap the button below to scan a product.</p>
  </div>
{:else if filteredItems.length === 0}
  <div class="empty">
    <p>No items match "<strong>{searchQuery}</strong>".</p>
  </div>
{:else}
  <ul class="item-list">
    {#each filteredItems as entry (entry.id)}
      {@const lowStock = isLowStock(entry)}
      {@const expirySoon = isExpirySoon(entry)}
      <li class="card item-card" class:warn-low={lowStock} class:warn-expiry={expirySoon}>
        <!-- Product image -->
        <div class="item-img">
          {#if entry.product.image_url}
            <img src={entry.product.image_url} alt={entry.product.name} loading="lazy" />
          {:else}
            <div class="img-placeholder">
              <svg width="28" height="28" viewBox="0 0 24 24" fill="none"
                   stroke="currentColor" stroke-width="1.5">
                <rect x="3" y="3" width="18" height="18" rx="2"/>
                <path d="M3 9h18M9 21V9"/>
              </svg>
            </div>
          {/if}
        </div>

        <!-- Product info -->
        <div class="item-info">
          <div class="item-name">{entry.product.name}</div>
          {#if entry.product.category}
            <div class="item-category">{formatCategory(entry.product.category)}</div>
          {/if}
          <div class="item-meta">
            {#if entry.expiry_date}
              <span class="badge" class:badge-warning={expirySoon}>
                Exp {entry.expiry_date}
              </span>
            {/if}
            {#if lowStock}
              <span class="badge badge-danger">Low stock</span>
            {/if}
          </div>
        </div>

        <!-- Quantity + remove -->
        <div class="item-actions">
          <div class="qty-display">{entry.quantity}</div>
          <button
            class="remove-btn"
            on:click={() => removeProduct(entry.product.ean)}
            disabled={removingEAN === entry.product.ean}
            aria-label="Remove one {entry.product.name}"
          >
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none"
                 stroke="currentColor" stroke-width="2.5" stroke-linecap="round">
              <path d="M5 12h14"/>
            </svg>
          </button>
        </div>
      </li>
    {/each}
  </ul>
{/if}

<!-- Scan action buttons -->
<div class="scan-actions">
  <button class="scan-btn scan-btn--add" on:click={() => (scanMode = 'add')} aria-label="Scan barcode to add product">
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none"
         stroke="currentColor" stroke-width="2.5" stroke-linecap="round">
      <path d="M12 5v14M5 12h14"/>
    </svg>
  </button>
  <button class="scan-btn scan-btn--remove" on:click={() => (scanMode = 'remove')} aria-label="Scan barcode to remove product">
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none"
         stroke="currentColor" stroke-width="2.5" stroke-linecap="round">
      <path d="M5 12h14"/>
    </svg>
  </button>
</div>

<!-- Barcode scanner overlay -->
{#if scanMode !== null}
  <BarcodeScanner on:scan={onScan} on:cancel={() => (scanMode = null)} />
{/if}

<!-- Add-product bottom sheet -->
{#if showAddModal}
  <!-- svelte-ignore a11y-click-events-have-key-events -->
  <!-- svelte-ignore a11y-no-static-element-interactions -->
  <div class="overlay" on:click|self={() => (showAddModal = false)}>
    <div class="sheet">
      <h2>Add to Inventory</h2>

      <div class="form-group">
        <label for="ean">EAN code</label>
        <input id="ean" type="text" value={scannedEAN} readonly />
      </div>

      <div class="form-group">
        <label for="expiry">Expiry date <span style="font-weight:400">(optional)</span></label>
        <input id="expiry" type="date" bind:value={expiryDate} />
      </div>

      <div style="display:flex; gap:10px; margin-top:8px">
        <button class="btn btn-ghost" style="flex:1"
                on:click={() => (showAddModal = false)}>
          Cancel
        </button>
        <button class="btn btn-primary" style="flex:2"
                on:click={addProduct}
                disabled={adding}>
          {adding ? 'Adding…' : 'Add Product'}
        </button>
      </div>
    </div>
  </div>
{/if}

<style>
  .page-title {
    display: flex;
    align-items: center;
    gap: 8px;
  }
  .logo {
    width: 32px;
    height: 32px;
    border-radius: 8px;
    flex-shrink: 0;
  }

  .item-count {
    font-size: .85rem;
    color: var(--c-muted);
    font-weight: 500;
  }

  .search-wrap {
    position: relative;
    margin-bottom: 12px;
  }
  .search-icon {
    position: absolute;
    left: 12px;
    top: 50%;
    transform: translateY(-50%);
    color: var(--c-muted);
    pointer-events: none;
  }
  .search-input {
    width: 100%;
    padding: 10px 12px 10px 36px;
    border-radius: 10px;
    border: 1px solid var(--c-border, #e0e0e0);
    background: var(--c-surface, #fff);
    font-size: .95rem;
    box-sizing: border-box;
  }
  .search-input:focus {
    outline: none;
    border-color: var(--c-primary, #4caf50);
  }

  .item-list {
    list-style: none;
    display: flex;
    flex-direction: column;
    gap: 10px;
    margin-bottom: 80px;
  }

  .item-card {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 12px;
    border-left: 3px solid transparent;
    transition: border-color .2s;
  }
  .warn-low    { border-left-color: var(--c-danger); }
  .warn-expiry { border-left-color: var(--c-warning); }

  .item-img {
    width: 56px;
    height: 56px;
    border-radius: 10px;
    overflow: hidden;
    flex-shrink: 0;
    background: var(--c-bg);
  }
  .item-img img { width: 100%; height: 100%; object-fit: cover; }
  .img-placeholder {
    width: 100%;
    height: 100%;
    display: flex;
    align-items: center;
    justify-content: center;
    color: var(--c-muted);
  }

  .item-info {
    flex: 1;
    min-width: 0;
  }
  .item-name {
    font-weight: 600;
    font-size: .95rem;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  .item-category {
    font-size: .78rem;
    color: var(--c-muted);
    text-transform: capitalize;
    margin-top: 1px;
  }
  .item-meta {
    display: flex;
    flex-wrap: wrap;
    gap: 4px;
    margin-top: 5px;
  }

  .badge {
    font-size: .68rem;
    font-weight: 600;
    padding: 2px 7px;
    border-radius: 99px;
    background: var(--c-bg);
    color: var(--c-muted);
    text-transform: uppercase;
    letter-spacing: .04em;
  }
  .badge-warning { background: var(--c-warning-bg); color: var(--c-warning); }
  .badge-danger  { background: var(--c-danger-bg);  color: var(--c-danger); }

  .item-actions {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 6px;
    flex-shrink: 0;
  }
  .qty-display {
    font-size: 1.15rem;
    font-weight: 700;
    min-width: 28px;
    text-align: center;
  }
  .remove-btn {
    width: 32px;
    height: 32px;
    border-radius: 50%;
    background: var(--c-danger-bg);
    color: var(--c-danger);
    display: flex;
    align-items: center;
    justify-content: center;
    transition: background .15s;
  }
  .remove-btn:hover   { background: var(--c-danger); color: #fff; }
  .remove-btn:disabled { opacity: .4; pointer-events: none; }

  .scan-actions {
    position: fixed;
    bottom: 5rem;
    left: 50%;
    transform: translateX(-50%);
    display: flex;
    flex-direction: row;
    gap: 1.25rem;
  }

  .scan-btn {
    width: 56px;
    height: 56px;
    border-radius: 50%;
    border: none;
    color: #fff;
    cursor: pointer;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
    display: flex;
    align-items: center;
    justify-content: center;
    transition: filter .15s;
  }
  .scan-btn:hover { filter: brightness(1.1); }

  .scan-btn--add    { background: #4caf50; }
  .scan-btn--remove { background: #f44336; }
</style>
