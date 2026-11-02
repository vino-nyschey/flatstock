// Suggests a name for a newly scanned barcode. Open Food Facts covers food; its sister database
// Open Products Facts covers cleaning products and toiletries.
const FIELDS = "product_name,product_name_de,product_name_en,brands";
const HOSTS = ["world.openfoodfacts.org", "world.openproductsfacts.org"];

async function fetchFrom(host, barcode) {
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), 8000);
  try {
    const res = await fetch(`https://${host}/api/v2/product/${barcode}.json?fields=${FIELDS}`, { signal: ctrl.signal });
    if (!res.ok) return null;
    const json = await res.json();
    if (json.status !== 1) return null;
    const p = json.product ?? {};
    const name = ["product_name", "product_name_de", "product_name_en"].map((k) => (p[k] ?? "").trim()).find((s) => s);
    return name ? { name, brand: (p.brands ?? "").split(",")[0].trim() } : null;
  } catch {
    return null;
  } finally {
    clearTimeout(timer);
  }
}

export async function lookupProductName(barcode) {
  if (!/^\d+$/.test(barcode)) return null;
  for (const host of HOSTS) {
    const hit = await fetchFrom(host, barcode);
    if (hit) return hit;
  }
  return null;
}
