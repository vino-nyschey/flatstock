// Splitwise-style bookkeeping: a line-by-line copy of the Android app's domain/Ledger.kt and
// domain/Alerts.kt, so both apps compute the same balances. Keep them in step.
// All amounts are whole euro cents, so shares always add up exactly.

/**
 * Splits amountCents equally. The leftover cents (at most one each) go to the first sharers in
 * id order, so the result always adds up to the full amount and is the same on every device.
 */
export function splitEqually(amountCents, sharers) {
  if (amountCents < 0) throw new RangeError("negative amount");
  const ids = [...new Set(sharers)].sort(byKey);
  const shares = new Map();
  if (ids.length === 0) return shares;
  const base = Math.floor(amountCents / ids.length);
  const rest = amountCents % ids.length;
  ids.forEach((id, i) => shares.set(id, base + (i < rest ? 1 : 0)));
  return shares;
}

/**
 * Net balance per person across all purchases ({ payer, amountCents, sharers }) and payments
 * ({ from, to, amountCents }): positive means the others owe them, negative means they owe.
 */
export function balances(expenses, payments) {
  const net = new Map();
  const add = (id, cents) => net.set(id, (net.get(id) ?? 0) + cents);
  for (const e of expenses) {
    if (!(e.amountCents > 0) || e.sharers.length === 0) continue;
    add(e.payer, e.amountCents);
    for (const [id, share] of splitEqually(e.amountCents, e.sharers)) add(id, -share);
  }
  for (const p of payments) {
    if (!(p.amountCents > 0) || p.from === p.to) continue;
    // Paying back lowers what you owe; receiving lowers what you are owed.
    add(p.from, p.amountCents);
    add(p.to, -p.amountCents);
  }
  return net;
}

// Kotlin sorts strings by UTF-16 code units; so does this, unlike localeCompare.
function byKey(a, b) { return a < b ? -1 : a > b ? 1 : 0; }
const biggestFirst = ([ka, va], [kb, vb]) => vb - va || byKey(ka, kb);

/**
 * Who pays whom to settle everything: the biggest debtor pays the biggest creditor, repeated.
 * Needs at most (people - 1) transfers. Ties are broken by id so every device suggests the same.
 */
export function settle(balances) {
  const debtors = new Map([...balances].filter(([, v]) => v < 0).map(([k, v]) => [k, -v]));
  const creditors = new Map([...balances].filter(([, v]) => v > 0));
  const transfers = [];
  while (debtors.size > 0 && creditors.size > 0) {
    const [from, owes] = [...debtors].sort(biggestFirst)[0];
    const [to, owed] = [...creditors].sort(biggestFirst)[0];
    const amount = Math.min(owes, owed);
    transfers.push({ from, to, amountCents: amount });
    if (owes === amount) debtors.delete(from); else debtors.set(from, owes - amount);
    if (owed === amount) creditors.delete(to); else creditors.set(to, owed - amount);
  }
  return transfers;
}

const MONEY = /^(\d+([.]\d{0,2})?|[.]\d{1,2})$/;

/** Accepts "3,49", "3.49", "€ 3.49" or "3"; null when empty, not a number or not positive. */
export function parseCents(text) {
  const clean = text.replaceAll("€", "").replaceAll(" ", "").replaceAll(",", ".").trim();
  if (!MONEY.test(clean)) return null;
  const cents = Math.round(Number(clean) * 100);
  return cents > 0 ? cents : null;
}

const outFirst = (a, b) => (b.level === "OUT") - (a.level === "OUT") || byKey(a.name.toLowerCase(), b.name.toLowerCase());

/** Supplies that someone else marked low or out after `since`. */
export const newAlerts = (supplies, since, me) =>
  supplies.filter((s) => s.level !== "FULL" && s.updatedAt > since && s.updatedBy !== me).sort(outFirst);

/** Level after the pack count changed; see levelForCount in Alerts.kt. */
export const levelForCount = (count, current) => (count <= 0 ? "OUT" : current === "OUT" ? "FULL" : current);

/** Everything currently low or out, out first. */
export const lowSummary = (supplies) => supplies.filter((s) => s.level !== "FULL").sort(outFirst);
