import { html, render, useState, useEffect } from "https://cdn.jsdelivr.net/npm/htm@3.1.1/preact/standalone.module.js";
import { firebaseConfig, useEmulators } from "./firebase-config.js";
import { t, setLanguage, savedLanguage, formatCents, centsInput, formatDate, starterSupplies } from "./i18n.js";
import { balances, settle, parseCents, splitEqually, newAlerts } from "./ledger.js";

// Firebase is loaded only once firebase-config.js is filled in (see README > Developers).
const configured = useEmulators || !firebaseConfig.apiKey.startsWith("REPLACE");
const repo = configured ? await import("./db.js") : null;

const WEB_URL = location.origin + location.pathname;
const LEVELS = ["FULL", "LOW", "OUT"];
const LEVEL_TONE = { FULL: "ok", LOW: "soon", OUT: "bad" };
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

// region Small helpers

/** Subscribes while mounted; `subscribe(cb)` returns the unsubscribe function. */
function useLive(subscribe, deps, initial) {
  const [value, setValue] = useState(initial);
  useEffect(() => {
    setValue(initial);
    return subscribe(setValue);
  }, deps);
  return value;
}

/** Runs async actions from buttons, with a busy flag and the message to show. */
function useRunner() {
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState(null);
  const run = async (block) => {
    if (busy) return;
    setBusy(true);
    setMessage(null);
    try {
      await block();
    } catch (e) {
      setMessage(friendlyError(e));
    } finally {
      setBusy(false);
    }
  };
  return { busy, message, setMessage, run };
}

function friendlyError(e) {
  const code = e?.code ?? "";
  if (code === "email-not-verified") return t("error_email_not_verified");
  if (code === "permission-denied") return t("error_permission_denied");
  return (e?.message ?? String(e)).replace(/^Firebase: /, "");
}

const store = {
  get: (k) => { try { return localStorage.getItem(k); } catch { return null; } },
  set: (k, v) => { try { localStorage.setItem(k, v); } catch { /* private mode */ } },
  remove: (k) => { try { localStorage.removeItem(k); } catch { /* private mode */ } },
};

// endregion
// region Shared UI pieces

function Dialog({ title, onClose, children, actions }) {
  useEffect(() => {
    const onKey = (e) => e.key === "Escape" && onClose?.();
    addEventListener("keydown", onKey);
    return () => removeEventListener("keydown", onKey);
  }, [onClose]);
  return html`
    <div class="scrim" onClick=${(e) => e.target === e.currentTarget && onClose?.()}>
      <div class="dialog" role="dialog" aria-modal="true" aria-label=${title}>
        <h2>${title}</h2>
        <div class="dialog-body">${children}</div>
        ${actions && html`<div class="dialog-actions">${actions}</div>`}
      </div>
    </div>`;
}

function Snackbar({ snack, onDone }) {
  useEffect(() => {
    if (!snack) return;
    const timer = setTimeout(onDone, 4000);
    return () => clearTimeout(timer);
  }, [snack]);
  if (!snack) return null;
  return html`<div class="snackbar" role="status"><span>${snack}</span></div>`;
}

function Radio({ on, label, onClick }) {
  return html`
    <button type="button" class="radio-row" role="radio" aria-checked=${on} onClick=${onClick}>
      <span class=${"radio" + (on ? " on" : "")}></span><span>${label}</span>
    </button>`;
}

function LanguagePicker() {
  const [current, setCurrent] = useState(savedLanguage());
  const pick = (tag) => { setLanguage(tag); setCurrent(tag); location.reload(); };
  return html`<div role="radiogroup">
    ${[["", t("lang_system")], ["en", "English"], ["de", "Deutsch"]].map(([tag, label]) =>
      html`<${Radio} on=${current === tag} label=${label} onClick=${() => pick(tag)} />`)}
  </div>`;
}

function NameDialog({ title, initial, onSave, onClose, onDelete }) {
  const [name, setName] = useState(initial);
  return html`
    <${Dialog} title=${title} onClose=${onClose} actions=${html`
      ${onDelete && html`<button class="text danger" onClick=${onDelete}>${t("delete")}</button>`}
      <button class="text" onClick=${onClose}>${t("cancel")}</button>
      <button class="text" disabled=${!name.trim()} onClick=${() => onSave(name)}>${t("save")}</button>`}>
      <form onSubmit=${(e) => { e.preventDefault(); if (name.trim()) onSave(name); }}>
        <input value=${name} onInput=${(e) => setName(e.currentTarget.value)} placeholder=${t("name")} autofocus />
      </form>
    <//>`;
}

// endregion
// region Sign-in and setup

function NotConfiguredScreen() {
  return html`<div class="centered">
    <div class="big-icon">⚙️</div>
    <h1>${t("not_configured_title")}</h1>
    <p class="center">${t("not_configured_body")}</p>
  </div>`;
}

function AuthScreen() {
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [registering, setRegistering] = useState(false);
  const r = useRunner();
  const ok = email.trim() && password.length >= 6 && (!registering || name.trim());
  const submit = (e) => {
    e.preventDefault();
    if (ok) r.run(() => (registering ? repo.register(name, email, password) : repo.signIn(email, password)));
  };
  return html`<form class="centered" onSubmit=${submit}>
    <img class="logo" src="icons/icon-192.png" width="72" height="72" alt="" />
    <h1>FlatStock</h1>
    <p class="center muted">${t("tagline")}</p>
    ${registering && html`<label class="field">${t("your_name")}
      <input value=${name} onInput=${(e) => setName(e.currentTarget.value)} autocomplete="given-name" />
      <small>${t("your_name_hint")}</small></label>`}
    <label class="field">${t("email")}
      <input type="email" value=${email} onInput=${(e) => setEmail(e.currentTarget.value)} autocomplete="email" /></label>
    <label class="field">${t("password")}
      <input type="password" value=${password} onInput=${(e) => setPassword(e.currentTarget.value)}
        autocomplete=${registering ? "new-password" : "current-password"} />
      ${registering && html`<small>${t("password_hint")}</small>`}</label>
    ${r.message && html`<p class="error center">${r.message}</p>`}
    <button class="filled full" disabled=${!ok || r.busy}>${t(registering ? "create_account" : "sign_in")}</button>
    ${registering && html`<p class="small muted center">${t("privacy_body")}</p>`}
    <button type="button" class="text" onClick=${() => setRegistering(!registering)}>
      ${t(registering ? "have_account" : "no_account")}</button>
    ${!registering && html`<button type="button" class="text" onClick=${() =>
      email.trim() ? r.run(async () => { await repo.resetPassword(email); r.setMessage(t("reset_sent")); })
        : r.setMessage(t("enter_email_first"))}>${t("forgot_password")}</button>`}
    <${InstallHint} />
    <div class="card full"><${LanguagePicker} /></div>
  </form>`;
}

/** iPhones only get the full-screen app after "Add to Home Screen". */
function InstallHint() {
  const ios = /iPhone|iPad|iPod/.test(navigator.userAgent);
  const standalone = matchMedia("(display-mode: standalone)").matches || navigator.standalone;
  if (!ios || standalone) return null;
  return html`<div class="card hint"><h3>${t("install_title")}</h3><p class="small">${t("install_body")}</p></div>`;
}

function VerifyScreen({ email, onVerified }) {
  const r = useRunner();
  return html`<div class="centered">
    <div class="big-icon">✉️</div>
    <h1>${t("verify_title")}</h1>
    <p class="center">${t("verify_body", email)}</p>
    ${r.message && html`<p class="info center">${r.message}</p>`}
    <button class="filled full" disabled=${r.busy} onClick=${() =>
      r.run(async () => { if (await repo.reloadUser()) onVerified(); else r.setMessage(t("verify_not_yet")); })}>
      ${t("verify_done")}</button>
    <button class="outlined full" disabled=${r.busy} onClick=${() =>
      r.run(async () => { await repo.resendVerification(); r.setMessage(t("verify_resent")); })}>${t("verify_resend")}</button>
    <button class="text" onClick=${repo.signOut}>${t("sign_out")}</button>
  </div>`;
}

function SetupScreen({ email }) {
  const invites = useLive((cb) => repo.watchPendingInvites(email, cb), [email], []);
  const [name, setName] = useState("");
  const r = useRunner();
  return html`<div class="page stack">
    <h1>${t("setup_title")}</h1>
    ${invites.length > 0 && html`
      <h2>${t("invites_for_you")}</h2>
      ${invites.map((inv) => html`<div class="card row">
        <div class="grow"><div class="title">${inv.wgName}</div><small class="muted">${t("invite_from", inv.invitedBy)}</small></div>
        <button class="text" disabled=${r.busy} onClick=${() => r.run(() => repo.declineInvite(inv))}>${t("decline")}</button>
        <button class="filled" disabled=${r.busy} onClick=${() => r.run(() => repo.acceptInvite(inv))}>${t("join")}</button>
      </div>`)}
      <hr />`}
    <h2>${t("create_wg_title")}</h2>
    <input value=${name} onInput=${(e) => setName(e.currentTarget.value)} placeholder=${t("wg_name")} />
    <p class="small muted">${t("create_wg_hint")}</p>
    <button class="filled" disabled=${!name.trim() || r.busy} onClick=${() => r.run(() => repo.createWg(name, starterSupplies()))}>
      ${t("create")}</button>
    ${r.message && html`<p class="error">${r.message}</p>`}
    <p class="small muted">${t("setup_hint", email)}</p>
    <button class="text" onClick=${repo.signOut}>${t("sign_out")}</button>
  </div>`;
}

// endregion
// region Supplies

function SuppliesScreen({ wg, supplies, me, showSnack }) {
  const [adding, setAdding] = useState(false);
  const [editing, setEditing] = useState(null);
  const [buying, setBuying] = useState(null);
  const sections = ["OUT", "LOW", "FULL"].map((level) => [level, supplies.filter((s) => (s.level ?? "FULL") === level)]);

  return html`<div class="content">
    ${supplies.length === 0 && html`<p class="empty">${t("supplies_empty")}</p>`}
    ${sections.map(([level, list]) => list.length > 0 && html`
      <h3 class=${"section " + LEVEL_TONE[level]}>${t("section_" + level, list.length)}</h3>
      <ul class="list">${list.map((s) => html`
        <li key=${s.id} class=${"supply " + LEVEL_TONE[s.level ?? "FULL"]}>
          <div class="row nowrap">
            <button class="item-main grow" onClick=${() => setEditing(s)}>
              <span class="title">${s.name}</span>
              ${s.level !== "FULL" && s.updatedBy && html`<small class="muted">
                ${t("marked_by", repo.nameOf(wg, s.updatedBy), formatDate(s.updatedAt))}</small>`}
            </button>
            <button class="tonal" onClick=${() => setBuying(s)}>🛒 ${t("bought")}</button>
          </div>
          <div class="segmented" role="radiogroup">
            ${LEVELS.map((l) => html`<button role="radio" aria-checked=${s.level === l}
              class=${(s.level ?? "FULL") === l ? "on " + LEVEL_TONE[l] : ""}
              onClick=${() => s.level !== l && repo.setLevel(wg.id, s.id, l)}>${t("level_" + l)}</button>`)}
          </div>
        </li>`)}
      </ul>`)}
    <button class="fab" onClick=${() => setAdding(true)}>＋ ${t("add_supply")}</button>
    ${adding && html`<${NameDialog} title=${t("add_supply")} initial=""
      onSave=${(n) => { repo.addSupply(wg.id, n); setAdding(false); }} onClose=${() => setAdding(false)} />`}
    ${editing && html`<${NameDialog} title=${t("edit_supply")} initial=${editing.name}
      onSave=${(n) => { repo.renameSupply(wg.id, editing.id, n); setEditing(null); }}
      onDelete=${() => { repo.deleteSupply(wg.id, editing.id); setEditing(null); }}
      onClose=${() => setEditing(null)} />`}
    ${buying && html`<${PurchaseDialog} wg=${wg} supply=${buying} me=${me}
      onClose=${() => setBuying(null)} onSaved=${() => { setBuying(null); showSnack(t("purchase_saved")); }} />`}
  </div>`;
}

/**
 * "I bought it": optional cost, who paid (default me) and who shares it (default everyone).
 * With a supply it is also marked full again; without one the user types what was bought.
 */
function PurchaseDialog({ wg, supply, me, onClose, onSaved }) {
  const [itemName, setItemName] = useState(supply?.name ?? "");
  const [cost, setCost] = useState("");
  const [paidBy, setPaidBy] = useState(wg.members.includes(me) ? me : wg.members[0]);
  const [sharedBy, setSharedBy] = useState(wg.members);
  const r = useRunner();
  const cents = parseCents(cost);
  const costValid = !cost.trim() || cents != null;
  const shares = cents != null ? splitEqually(cents, sharedBy) : new Map();
  const ok = costValid && itemName.trim() && (cents == null || sharedBy.length > 0);
  const toggle = (u) => setSharedBy(sharedBy.includes(u) ? sharedBy.filter((x) => x !== u) : [...sharedBy, u]);
  const save = () => r.run(async () => {
    await repo.recordPurchase(wg.id, supply, itemName, cents ?? 0,
      cents != null ? paidBy : me, cents != null ? sharedBy : wg.members);
    onSaved();
  });

  return html`<${Dialog} title=${supply ? t("bought_title", supply.name) : t("add_purchase")} onClose=${onClose} actions=${html`
      <button class="text" onClick=${onClose}>${t("cancel")}</button>
      <button class="text" disabled=${!ok || r.busy} onClick=${save}>${t("save")}</button>`}>
    ${!supply && html`<label class="field">${t("what_bought")}
      <input value=${itemName} onInput=${(e) => setItemName(e.currentTarget.value)} /></label>`}
    <label class="field">${t("cost_optional")}
      <input inputmode="decimal" placeholder="€ 0,00" value=${cost} class=${costValid ? "" : "invalid"}
        onInput=${(e) => setCost(e.currentTarget.value)} /></label>
    ${cents != null ? html`
      <div class="field">${t("paid_by")}
        <div class="chips wrap">${wg.members.map((u) => html`<button type="button"
          class=${"chip" + (paidBy === u ? " on" : "")} onClick=${() => setPaidBy(u)}>${repo.nameOf(wg, u)}</button>`)}</div></div>
      <div class="field">${t("shared_by")}
        <div class="chips wrap">${wg.members.map((u) => html`<button type="button"
          class=${"chip" + (sharedBy.includes(u) ? " on" : "")} onClick=${() => toggle(u)}>${repo.nameOf(wg, u)}</button>`)}</div></div>
      <p class=${"small " + (shares.size ? "muted" : "error")}>
        ${shares.size ? t("each_pays", formatCents(Math.min(...shares.values())), sharedBy.length) : t("pick_someone")}</p>`
    : html`<p class="small muted">${t("no_cost_hint")}</p>`}
    ${r.message && html`<p class="error">${r.message}</p>`}
  <//>`;
}

// endregion
// region Money

function MoneyScreen({ wg, purchases, payments, me, showSnack }) {
  const net = balances(
    purchases.map((p) => ({ payer: p.paidBy, amountCents: p.amountCents ?? 0, sharers: p.sharedBy ?? [] })),
    payments.map((p) => ({ from: p.fromUid, to: p.toUid, amountCents: p.amountCents ?? 0 })),
  );
  const transfers = settle(net);
  // Current members always show; people who left only while they still owe or are owed.
  const people = [...new Set([...wg.members, ...[...net].filter(([, v]) => v !== 0).map(([k]) => k)])];
  const mine = net.get(me) ?? 0;
  const [adding, setAdding] = useState(false);
  const [settling, setSettling] = useState(null);
  const [deleting, setDeleting] = useState(null);
  const r = useRunner();
  const history = [
    ...purchases.map((p) => ({ ...p, kind: "purchase" })),
    ...payments.map((p) => ({ ...p, kind: "payment" })),
  ].sort((a, b) => b.createdAt - a.createdAt).slice(0, 50);

  return html`<div class="page stack">
    <div class="card primary">
      <small>${t("your_balance")}</small>
      <div class="balance">${mine > 0 ? t("you_are_owed", formatCents(mine)) : mine < 0 ? t("you_owe", formatCents(-mine)) : t("all_settled")}</div>
    </div>
    <button class="outlined" onClick=${() => setAdding(true)}>＋ ${t("add_purchase")}</button>
    <div class="card stack-s">
      <h2>${t("who_owes_whom")}</h2>
      ${transfers.length === 0 && html`<p class="muted">${t("nobody_owes")}</p>`}
      ${transfers.map((tr) => html`<div class="row nowrap">
        <span class=${"grow" + (tr.from === me || tr.to === me ? " title strong" : "")}>
          ${t("owes_line", repo.nameOf(wg, tr.from), repo.nameOf(wg, tr.to), formatCents(tr.amountCents))}</span>
        <button class="filled" onClick=${() => setSettling(tr)}>${t("settle_up")}</button>
      </div>`)}
    </div>
    <div class="card stack-s">
      <h2>${t("balances")}</h2>
      ${people.map((u) => {
        const v = net.get(u) ?? 0;
        return html`<div class="row nowrap">
          <span class="grow">${repo.nameOf(wg, u)}${!wg.members.includes(u) ? " " + t("left_wg") : ""}</span>
          <span class=${"strong " + (v > 0 ? "pos" : v < 0 ? "neg" : "muted")}>${(v > 0 ? "+" : "") + formatCents(v)}</span>
        </div>`;
      })}
    </div>
    <h2>${t("history")}</h2>
    ${history.length === 0 && html`<p class="muted">${t("history_empty")}</p>`}
    <ul class="list">${history.map((h) => html`<li key=${h.kind + h.id} class="row nowrap history">
      <div class="grow">
        <div>${h.kind === "payment"
          ? t("history_payment", repo.nameOf(wg, h.fromUid), repo.nameOf(wg, h.toUid), formatCents(h.amountCents))
          : h.amountCents > 0
            ? t("history_purchase", repo.nameOf(wg, h.paidBy), h.itemName, formatCents(h.amountCents))
            : t("history_purchase_no_cost", repo.nameOf(wg, h.paidBy), h.itemName)}</div>
        <small class="muted">${formatDate(h.createdAt)}${h.kind === "purchase" && h.amountCents > 0
          ? " · " + t("history_shared", (h.sharedBy ?? []).map((u) => repo.nameOf(wg, u)).join(", ")) : ""}</small>
      </div>
      ${h.createdBy === me && html`<button class="icon" aria-label=${t("delete")} onClick=${() => setDeleting(h)}>🗑</button>`}
    </li>`)}</ul>

    ${adding && html`<${PurchaseDialog} wg=${wg} supply=${null} me=${me}
      onClose=${() => setAdding(false)} onSaved=${() => { setAdding(false); showSnack(t("purchase_saved")); }} />`}
    ${settling && html`<${SettleDialog} wg=${wg} transfer=${settling}
      onClose=${() => setSettling(null)} onSaved=${() => { setSettling(null); showSnack(t("payment_saved")); }} />`}
    ${deleting && html`<${Dialog} title=${t("delete_entry_title")} onClose=${() => setDeleting(null)} actions=${html`
        <button class="text" onClick=${() => setDeleting(null)}>${t("cancel")}</button>
        <button class="text danger" disabled=${r.busy} onClick=${() => r.run(async () => {
          await (deleting.kind === "purchase" ? repo.deletePurchase(wg.id, deleting.id) : repo.deletePayment(wg.id, deleting.id));
          setDeleting(null);
        })}>${t("delete")}</button>`}>
      <p>${r.message ?? t("delete_entry_body")}</p>
    <//>`}
  </div>`;
}

function SettleDialog({ wg, transfer, onClose, onSaved }) {
  const [amount, setAmount] = useState(centsInput(transfer.amountCents));
  const cents = parseCents(amount);
  const r = useRunner();
  return html`<${Dialog} title=${t("settle_up")} onClose=${onClose} actions=${html`
      <button class="text" onClick=${onClose}>${t("cancel")}</button>
      <button class="text" disabled=${cents == null || r.busy} onClick=${() => r.run(async () => {
        await repo.recordPayment(wg.id, transfer.from, transfer.to, cents);
        onSaved();
      })}>${t("record_payment")}</button>`}>
    <p>${t("settle_body", repo.nameOf(wg, transfer.from), repo.nameOf(wg, transfer.to))}</p>
    <input inputmode="decimal" value=${amount} class=${cents == null ? "invalid" : ""} onInput=${(e) => setAmount(e.currentTarget.value)} />
    ${r.message && html`<p class="error">${r.message}</p>`}
  <//>`;
}

// endregion
// region WG settings

function WgScreen({ wg, me, groupIds }) {
  const sent = useLive((cb) => repo.watchSentInvites(wg.id, cb), [wg.id], []);
  const [email, setEmail] = useState("");
  const [renamingMe, setRenamingMe] = useState(false);
  const [renamingWg, setRenamingWg] = useState(false);
  const [confirmLeave, setConfirmLeave] = useState(false);
  const [notify, setNotify] = useState(typeof Notification !== "undefined" ? Notification.permission : "unsupported");
  const r = useRunner();

  const invite = () => r.run(async () => {
    const to = email.trim().toLowerCase();
    await repo.createInvite(wg, to);
    setEmail("");
    const subject = t("invite_subject", wg.name);
    const body = t("invite_mail_body", wg.name, repo.nameOf(wg, me), to, WEB_URL);
    location.href = `mailto:${encodeURIComponent(to)}?subject=${encodeURIComponent(subject)}&body=${encodeURIComponent(body)}`;
    r.setMessage(t("invite_saved"));
  });

  return html`<div class="page stack">
    <div class="card stack-s">
      <h2>${t("members")}</h2>
      ${wg.members.map((u) => html`<div class="row nowrap">
        <div class="grow"><div>${repo.nameOf(wg, u)}${u === me ? " " + t("you") : ""}</div>
          <small class="muted">${wg.memberEmails?.[u] ?? ""}</small></div>
        ${u === me && html`<button class="icon" aria-label=${t("change_name")} onClick=${() => setRenamingMe(true)}>✏️</button>`}
      </div>`)}
      <button class="text compact start" onClick=${() => setRenamingWg(true)}>${t("rename_wg")}</button>
    </div>

    <div class="card stack-s">
      <h2>${t("invite_title")}</h2>
      <p class="small">${t("invite_body_hint")}</p>
      <form class="row nowrap" onSubmit=${(e) => { e.preventDefault(); if (EMAIL_RE.test(email.trim())) invite(); }}>
        <input type="email" value=${email} placeholder=${t("email")} onInput=${(e) => setEmail(e.currentTarget.value)} />
        <button class="filled" disabled=${!EMAIL_RE.test(email.trim()) || r.busy}>${t("invite_send")}</button>
      </form>
      ${r.message && html`<p class="info small">${r.message}</p>`}
      ${sent.length > 0 && html`<small class="muted">${t("invites_pending")}</small>
        ${sent.map((inv) => html`<div class="row nowrap"><span class="grow">${inv.email}</span>
          <button class="icon" aria-label=${t("cancel")} onClick=${() => r.run(() => repo.cancelInvite(inv))}>✕</button></div>`)}`}
    </div>

    <div class="card stack-s">
      <h2>${t("reminders")}</h2>
      <p class="small">${t("web_reminders_hint")}</p>
      ${notify === "default" && html`<button class="outlined" onClick=${async () =>
        setNotify(await Notification.requestPermission())}>${t("enable_notifications")}</button>`}
      ${notify === "granted" && html`<p class="small ok-text">${t("notifications_on")}</p>`}
      ${(notify === "denied" || notify === "unsupported") && html`<p class="small muted">${t("notifications_off")}</p>`}
    </div>

    <div class="card"><h2>${t("language")}</h2><${LanguagePicker} /></div>

    <button class="outlined danger" onClick=${() => setConfirmLeave(true)}>${t("leave_wg")}</button>
    <button class="outlined" onClick=${() => { store.remove(ALERT_KEY); repo.signOut(); }}>${t("sign_out")}</button>
    <p class="small muted">${t("signed_in_as", repo.currentEmail())} · ${repo.APP_VERSION}</p>

    ${renamingMe && html`<${NameDialog} title=${t("change_name")} initial=${wg.memberNames?.[me] ?? ""}
      onSave=${(n) => { r.run(() => repo.setMyName(wg.id, n)); setRenamingMe(false); }} onClose=${() => setRenamingMe(false)} />`}
    ${renamingWg && html`<${NameDialog} title=${t("rename_wg")} initial=${wg.name}
      onSave=${(n) => { r.run(() => repo.renameWg(wg.id, n)); setRenamingWg(false); }} onClose=${() => setRenamingWg(false)} />`}
    ${confirmLeave && html`<${Dialog} title=${t("leave_wg")} onClose=${() => setConfirmLeave(false)} actions=${html`
        <button class="text" onClick=${() => setConfirmLeave(false)}>${t("cancel")}</button>
        <button class="text danger" onClick=${() => { setConfirmLeave(false); store.remove(ALERT_KEY); r.run(() => repo.leaveWg(wg.id, groupIds)); }}>
          ${t("leave_wg")}</button>`}>
      <p>${t("leave_wg_body", wg.name)}</p>
    <//>`}
  </div>`;
}

// endregion
// region Main and session

const ALERT_KEY = "flatstock.lastAlertCheck";

/**
 * Flatmates' "low"/"out" marks since the last look: shown as a banner, and as a notification when
 * allowed. There is no push server, so this only runs while the web app is open.
 */
function useAlerts(wg, supplies, me) {
  const [alerts, setAlerts] = useState([]);
  useEffect(() => {
    if (!wg || supplies.length === 0) return;
    const since = Number(store.get(ALERT_KEY) ?? 0);
    store.set(ALERT_KEY, String(Date.now()));
    if (!since) return;
    const fresh = newAlerts(supplies, since, me);
    if (fresh.length === 0) return;
    // Keep earlier alerts that are still news; a newer mark of the same supply replaces its old one.
    setAlerts((shown) => [...shown.filter((a) => !fresh.some((f) => f.id === a.id)), ...fresh]);
    const text = fresh.map((s) => t(s.level === "OUT" ? "notif_marked_out" : "notif_marked_low", repo.nameOf(wg, s.updatedBy), s.name)).join("\n");
    if (typeof Notification !== "undefined" && Notification.permission === "granted" && document.hidden) {
      navigator.serviceWorker?.ready.then((reg) => reg.showNotification("FlatStock", { body: text, icon: "icons/icon-192.png", tag: "alerts" }));
    }
  }, [wg?.id, supplies]);
  return [alerts, () => setAlerts([])];
}

const TABS = [["supplies", "🧻"], ["money", "💶"], ["wg", "🏠"]];

function MainScreen({ wid, me, groupIds }) {
  const [tab, setTab] = useState("supplies");
  const [snack, setSnack] = useState(null);
  const wg = useLive((cb) => repo.watchWg(wid, cb), [wid], undefined);
  const supplies = useLive((cb) => repo.watchSupplies(wid, cb), [wid], []);
  const purchases = useLive((cb) => repo.watchPurchases(wid, cb), [wid], []);
  const payments = useLive((cb) => repo.watchPayments(wid, cb), [wid], []);
  const [alerts, clearAlerts] = useAlerts(wg, supplies, me);
  const online = useOnline();

  if (!wg) return html`<div class="loading full-height"><div class="spinner"></div></div>`;
  return html`<div class="main">
    <header class="topbar"><h1 class="ellipsis">${wg.name}</h1></header>
    ${!online && html`<div class="offline">${t("offline")}</div>`}
    ${alerts.length > 0 && html`<div class="card warn row nowrap" role="alert">
      <span class="grow">${alerts.map((s) => t(s.level === "OUT" ? "notif_marked_out" : "notif_marked_low", repo.nameOf(wg, s.updatedBy), s.name)).join(" · ")}</span>
      <button class="icon" aria-label=${t("close")} onClick=${clearAlerts}>✕</button></div>`}
    ${tab === "supplies" && html`<${SuppliesScreen} wg=${wg} supplies=${supplies} me=${me} showSnack=${setSnack} />`}
    ${tab === "money" && html`<${MoneyScreen} wg=${wg} purchases=${purchases} payments=${payments} me=${me} showSnack=${setSnack} />`}
    ${tab === "wg" && html`<${WgScreen} wg=${wg} me=${me} groupIds=${groupIds} />`}
    <${Snackbar} snack=${snack} onDone=${() => setSnack(null)} />
    <nav class="tabs">
      ${TABS.map(([id, icon]) => html`
        <button class=${tab === id ? "on" : ""} aria-current=${tab === id ? "page" : undefined} onClick=${() => setTab(id)}>
          <span class="tab-icon">${icon}</span><span>${t("tab_" + id)}</span>
        </button>`)}
    </nav>
  </div>`;
}

function useOnline() {
  const [online, setOnline] = useState(navigator.onLine);
  useEffect(() => {
    const on = () => setOnline(true), off = () => setOnline(false);
    addEventListener("online", on);
    addEventListener("offline", off);
    return () => { removeEventListener("online", on); removeEventListener("offline", off); };
  }, []);
  return online;
}

function App() {
  const [user, setUser] = useState(undefined);
  const [refresh, setRefresh] = useState(0);
  const [groups, setGroups] = useState(null);

  useEffect(() => repo.onAuth((u) => { setUser(u); setGroups(null); }), []);
  const verified = !!user?.emailVerified;
  useEffect(() => {
    if (!user || !verified) return;
    return repo.watchUserGroups(user.uid, setGroups);
  }, [user?.uid, verified, refresh]);

  if (user === undefined) return html`<div class="loading full-height"><div class="spinner"></div></div>`;
  if (user === null) return html`<${AuthScreen} />`;
  if (!verified) return html`<${VerifyScreen} email=${user.email ?? ""} onVerified=${() => setRefresh(refresh + 1)} />`;
  if (groups == null) return html`<div class="loading full-height"><div class="spinner"></div></div>`;
  if (!groups.activeId) return html`<${SetupScreen} email=${(user.email ?? "").toLowerCase()} />`;
  return html`<${MainScreen} wid=${groups.activeId} me=${user.uid} groupIds=${groups.groupIds} />`;
}

render(configured ? html`<${App} />` : html`<${NotConfiguredScreen} />`, document.getElementById("app"));

if ("serviceWorker" in navigator) {
  addEventListener("load", () => navigator.serviceWorker.register("sw.js").catch((e) => console.warn("sw failed", e)));
}

// endregion
