<script lang="ts">
  import { toasts } from '$lib/stores/toast';
</script>

<div class="toast-stack" aria-live="polite" aria-atomic="false">
  {#each $toasts as t (t.id)}
    <div class="toast toast-{t.type}" role="alert">
      {#if t.type === 'success'}
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none"
             stroke="currentColor" stroke-width="2.5" stroke-linecap="round">
          <path d="M20 6 9 17l-5-5"/>
        </svg>
      {:else if t.type === 'error'}
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none"
             stroke="currentColor" stroke-width="2.5" stroke-linecap="round">
          <circle cx="12" cy="12" r="10"/><path d="M12 8v4m0 4h.01"/>
        </svg>
      {:else}
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none"
             stroke="currentColor" stroke-width="2.5" stroke-linecap="round">
          <path d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0zM12 9v4m0 4h.01"/>
        </svg>
      {/if}
      <span>{t.message}</span>
    </div>
  {/each}
</div>

<style>
  .toast-stack {
    position: fixed;
    top: 16px;
    left: 50%;
    transform: translateX(-50%);
    z-index: 200;
    display: flex;
    flex-direction: column;
    gap: 8px;
    width: min(360px, calc(100vw - 32px));
    pointer-events: none;
  }

  .toast {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 12px 16px;
    border-radius: 12px;
    font-size: .9rem;
    font-weight: 500;
    box-shadow: 0 4px 12px rgba(0,0,0,.15);
    animation: slideDown .25s ease;
  }

  .toast-success  { background: #16a34a; color: #fff; }
  .toast-error    { background: #dc2626; color: #fff; }
  .toast-warning  { background: #d97706; color: #fff; }

  @keyframes slideDown {
    from { opacity: 0; transform: translateY(-12px); }
    to   { opacity: 1; transform: translateY(0); }
  }
</style>
