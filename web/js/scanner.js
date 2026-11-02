// Barcode scanning with the phone camera. iOS Safari has no BarcodeDetector, so ZXing decodes the
// video frames in JavaScript; it is loaded only when the scanner first opens.
let readerPromise;

function reader() {
  readerPromise ??= import("https://cdn.jsdelivr.net/npm/@zxing/browser@0.2.1/+esm")
    .then((m) => new m.BrowserMultiFormatReader(undefined, { delayBetweenScanAttempts: 150 }));
  return readerPromise;
}

/**
 * Starts the back camera in `video` and calls onCode(text) once for the first barcode found.
 * Resolves to a stop() function; rejects when the camera can't be opened.
 */
export async function startScanner(video, onCode) {
  // iOS only plays camera video inline when these are set before the stream starts.
  video.setAttribute("playsinline", "");
  video.setAttribute("autoplay", "");
  video.muted = true;
  const r = await reader();
  let done = false;
  let controls;
  controls = await r.decodeFromConstraints(
    { audio: false, video: { facingMode: { ideal: "environment" }, width: { ideal: 1280 }, height: { ideal: 720 } } },
    video,
    (result) => {
      if (!result || done) return;
      done = true;
      controls?.stop();
      navigator.vibrate?.(60);
      onCode(result.getText());
    },
  );
  if (done) controls.stop();
  return () => { done = true; controls.stop(); };
}
