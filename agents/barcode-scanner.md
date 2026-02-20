# Agent: Barcode Scanner

## Responsibility
Implements barcode scanning in the browser using the smartphone camera. Decodes barcodes and extracts EAN codes to hand off to the EAN Lookup agent.

## Scope
- Camera access via the browser `getUserMedia` / `MediaDevices` API
- Barcode detection using a JS library (e.g. `@zxing/browser` or the native `BarcodeDetector` API where supported)
- Fallback handling when camera is unavailable or permission is denied
- Emitting the decoded EAN code to the calling UI component

## Key Decisions
- Prefer the native `BarcodeDetector` API (Chrome/Android); fall back to `@zxing/browser` for broader compatibility
- Scanning runs in a continuous loop until a valid EAN-8 or EAN-13 code is detected, then stops automatically
- Camera is released immediately after a successful scan

## Interfaces
- **Input**: User action (open scanner)
- **Output**: EAN string (13 or 8 digits) passed to the EAN Lookup flow
- **Error states**: permission denied, no camera, unsupported browser
