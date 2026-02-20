<script lang="ts">
  import { onMount } from 'svelte';
  import { api, type InventoryEntry } from '$lib/api';
  import { toast } from '$lib/stores/toast';
  import BarcodeScanner from '$lib/components/BarcodeScanner.svelte';

  let items: InventoryEntry[] = [];
  let loading = true;

  // Scanner + add-product modal state
  let showScanner = false;
  let showAddModal = false;
  let scannedEAN = '';
  let expiryDate = '';
  let adding = false;

  // Remove confirmation state
  let removingEAN: string | null = null;

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

  function onScan(event: CustomEvent<string>) {
    scannedEAN = event.detail;
    expiryDate = '';
    showScanner = false;
    showAddModal = true;
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
  <h1>Inventory</h1>
  <span class="item-count">{items.length} item{items.length !== 1 ? 's' : ''}</span>
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
{:else}
  <ul class="item-list">
    {#each items as entry (entry.id)}
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

<!-- FAB: open scanner -->
<button class="fab" on:click={() => (showScanner = true)} aria-label="Scan barcode to add product">
  <svg width="26" height="26" viewBox="0 0 24 24" fill="none"
       stroke="currentColor" stroke-width="2" stroke-linecap="round">
    <path d="M3 7V5a2 2 0 0 1 2-2h2M17 3h2a2 2 0 0 1 2 2v2M21 17v2a2 2 0 0 1-2 2h-2M7 21H5a2 2 0 0 1-2-2v-2"/>
    <rect x="7" y="7" width="10" height="10" rx="1"/>
  </svg>
</button>

<!-- Barcode scanner overlay -->
{#if showScanner}
  <BarcodeScanner on:scan={onScan} on:cancel={() => (showScanner = false)} />
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
  .item-count {
    font-size: .85rem;
    color: var(--c-muted);
    font-weight: 500;
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
</style>
