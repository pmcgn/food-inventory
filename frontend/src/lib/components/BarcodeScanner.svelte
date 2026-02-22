<script lang="ts">
  import { createEventDispatcher, onMount, onDestroy } from 'svelte';

  const dispatch = createEventDispatcher<{ scan: string; cancel: void }>();

  let video: HTMLVideoElement;
  let stream: MediaStream | null = null;
  let rafId = 0;
  let zxingReader: { reset: () => void } | null = null;

  let error = '';
  let status = 'Starting camera…';

  onMount(start);
  onDestroy(stop);

  async function start() {
    try {
      stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: 'environment', width: { ideal: 1920 } }
      });
      video.srcObject = stream;
      await video.play();
      status = 'Point camera at a barcode';

      if ('BarcodeDetector' in window) {
        startNative();
      } else {
        startZXing();
      }
    } catch (e: unknown) {
      const err = e as { name?: string };
      if (err.name === 'NotAllowedError') {
        error = 'Camera permission denied. Please allow camera access and try again.';
      } else if (err.name === 'NotFoundError') {
        error = 'No camera found on this device.';
      } else {
        error = 'Could not access the camera.';
      }
    }
  }

  function startNative() {
    // BarcodeDetector is not yet in lib.dom.d.ts
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const detector = new (window as any).BarcodeDetector({
      formats: ['ean_8', 'ean_13']
    });

    const tick = async () => {
      if (video && video.readyState >= 2) {
        try {
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          const codes: any[] = await detector.detect(video);
          if (codes.length > 0) {
            emit(codes[0].rawValue as string);
            return;
          }
        } catch (_) {
          // frame not ready — retry
        }
      }
      rafId = requestAnimationFrame(tick);
    };
    rafId = requestAnimationFrame(tick);
  }

  async function startZXing() {
    const { BrowserMultiFormatReader } = await import('@zxing/browser');
    const reader = new BrowserMultiFormatReader();
    zxingReader = reader;

    reader.decodeFromStream(
      stream!,
      video,
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      (result: any, _err: unknown, controls: any) => {
        if (result) {
          controls.stop();
          emit(result.getText() as string);
        }
      }
    );
  }

  function emit(ean: string) {
    stop();
    dispatch('scan', ean);
  }

  function stop() {
    cancelAnimationFrame(rafId);
    zxingReader?.reset();
    stream?.getTracks().forEach(t => t.stop());
    stream = null;
  }

  function cancel() {
    stop();
    dispatch('cancel');
  }
</script>

<!-- Full-screen scanner overlay -->
<div class="scanner" role="dialog" aria-modal="true" aria-label="Barcode scanner">
  <header class="scanner-header">
    <button class="close-btn" on:click={cancel} aria-label="Close scanner">
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none"
           stroke="currentColor" stroke-width="2.5" stroke-linecap="round">
        <path d="M18 6 6 18M6 6l12 12"/>
      </svg>
    </button>
    <span>Scan Barcode</span>
    <div style="width:40px" />
  </header>

  <div class="viewfinder-wrap">
    <!-- svelte-ignore a11y-media-has-caption -->
    <video bind:this={video} playsinline muted class="camera-feed" />

    {#if error}
      <div class="error-card">
        <svg width="36" height="36" viewBox="0 0 24 24" fill="none"
             stroke="currentColor" stroke-width="1.5">
          <circle cx="12" cy="12" r="10"/>
          <path d="M12 8v4m0 4h.01"/>
        </svg>
        <p>{error}</p>
        <button class="btn btn-ghost" on:click={cancel}>Close</button>
      </div>
    {:else}
      <!-- Viewfinder frame with corner brackets -->
      <div class="frame">
        <div class="corner tl" /><div class="corner tr" />
        <div class="corner bl" /><div class="corner br" />
        <div class="scan-line" />
      </div>
    {/if}
  </div>

  {#if !error}
    <p class="hint">{status}</p>
  {/if}
</div>

<style>
  .scanner {
    position: fixed;
    inset: 0;
    background: #000;
    z-index: 100;
    display: flex;
    flex-direction: column;
  }

  .scanner-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 16px;
    padding-top: calc(16px + env(safe-area-inset-top, 0px));
    color: #fff;
    font-weight: 600;
    font-size: 1rem;
  }

  .close-btn {
    width: 40px;
    height: 40px;
    border-radius: 50%;
    background: rgba(255,255,255,.15);
    color: #fff;
    display: flex;
    align-items: center;
    justify-content: center;
    touch-action: manipulation;
  }

  .viewfinder-wrap {
    flex: 1;
    position: relative;
    overflow: hidden;
  }

  .camera-feed {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }

  /* ── Corner-bracket viewfinder ── */
  .frame {
    position: absolute;
    top: 50%;
    left: 50%;
    transform: translate(-50%, -50%);
    width: min(280px, 80vw);
    height: 180px;
  }

  .corner {
    position: absolute;
    width: 22px;
    height: 22px;
    border-color: #16a34a;
    border-style: solid;
  }
  .tl { top: 0; left: 0;  border-width: 3px 0 0 3px; border-radius: 4px 0 0 0; }
  .tr { top: 0; right: 0; border-width: 3px 3px 0 0; border-radius: 0 4px 0 0; }
  .bl { bottom: 0; left: 0;  border-width: 0 0 3px 3px; border-radius: 0 0 0 4px; }
  .br { bottom: 0; right: 0; border-width: 0 3px 3px 0; border-radius: 0 0 4px 0; }

  /* ── Animated scanning line ── */
  .scan-line {
    position: absolute;
    left: 0; right: 0;
    height: 2px;
    background: linear-gradient(90deg, transparent 0%, #16a34a 50%, transparent 100%);
    animation: scan 2s ease-in-out infinite;
  }
  @keyframes scan {
    0%   { top: 0; }
    50%  { top: calc(100% - 2px); }
    100% { top: 0; }
  }

  .error-card {
    position: absolute;
    inset: 0;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 12px;
    color: #fff;
    text-align: center;
    padding: 32px;
  }
  .error-card p { font-size: .95rem; opacity: .85; }

  .hint {
    color: rgba(255,255,255,.75);
    font-size: .9rem;
    text-align: center;
    padding: 20px 16px calc(20px + env(safe-area-inset-bottom, 0px));
  }
</style>
