// English and German texts, taken from the Android app's strings.xml so both apps say the same thing.
const en = {
  tagline: "Shared supplies for your flat: see what's running low, record who bought what and split the cost.",
  not_configured_title: "Firebase not set up",
  not_configured_body: "web/js/firebase-config.js still has placeholder values. Follow README > Developers to connect a Firebase project.",
  your_name: "Your name",
  your_name_hint: "Shown to your flatmates",
  email: "Email",
  password: "Password",
  password_hint: "At least 6 characters",
  sign_in: "Sign in",
  create_account: "Create account",
  have_account: "Already have an account? Sign in",
  no_account: "New here? Create an account",
  forgot_password: "Forgot password?",
  enter_email_first: "Enter your email address first.",
  reset_sent: "Password reset email sent.",
  privacy_body: "Your name, email address and your WG's supplies and purchases are stored in Google Firebase (EU/US) and shared only with the members of your WG.",
  verify_title: "Confirm your email",
  verify_body: "We sent a confirmation link to {0}. Open it, then come back and tap the button below.",
  verify_done: "I've confirmed my email",
  verify_resend: "Send the link again",
  verify_resent: "Link sent again.",
  verify_not_yet: "Not confirmed yet – check your inbox and spam folder.",
  sign_out: "Sign out",
  signed_in_as: "Signed in as {0}",
  error_email_not_verified: "Please confirm your email first: open the link we sent you, then sign out and sign in again.",
  error_permission_denied: "No access yet. Sign out, sign in again and try once more. If it still fails, ask your flatmate to send a new invitation.",
  install_title: "Add to Home Screen",
  install_body: "In Safari, tap Share and then \"Add to Home Screen\". FlatStock then opens like an app.",
  offline: "Offline – changes sync as soon as you're back online.",

  setup_title: "Join or start a WG",
  invites_for_you: "Invitations for you",
  invite_from: "Invited by {0}",
  join: "Join",
  decline: "Decline",
  create_wg_title: "Start a new WG",
  wg_name: "WG name, e.g. \"Kastanienallee 12\"",
  create_wg_hint: "We add a starter list (dishwasher tabs, toilet paper, oil, salt …). You can change it any time.",
  create: "Create",
  setup_hint: "Waiting for an invitation? Ask a flatmate to invite {0}.",

  tab_supplies: "Supplies",
  tab_money: "Money",
  tab_wg: "WG",

  level_FULL: "Full",
  level_LOW: "Low",
  level_OUT: "Out",
  section_OUT: "Out ({0})",
  section_LOW: "Running low ({0})",
  section_FULL: "In stock ({0})",
  supplies_empty: "No supplies yet. Add the first one with the button below.",
  add_supply: "Add item",
  edit_supply: "Edit item",
  marked_by: "Marked by {0} · {1}",
  bought: "Bought",
  name: "Name",
  save: "Save",
  cancel: "Cancel",
  close: "Close",
  delete: "Delete",

  bought_title: "Bought {0}",
  add_purchase: "Add a purchase",
  what_bought: "What did you buy?",
  cost_optional: "Cost (optional)",
  paid_by: "Paid by",
  shared_by: "Shared by",
  each_pays: "{0} each for {1} people",
  pick_someone: "Pick at least one flatmate.",
  no_cost_hint: "Without a cost the purchase is only noted; nobody owes anything.",
  purchase_saved: "Purchase saved",

  your_balance: "Your balance",
  you_are_owed: "You get {0} back",
  you_owe: "You owe {0}",
  all_settled: "All settled up",
  who_owes_whom: "Who owes whom",
  nobody_owes: "Nobody owes anything.",
  owes_line: "{0} → {1}: {2}",
  settle_up: "Settle up",
  settle_body: "{0} paid {1}:",
  record_payment: "Record payment",
  payment_saved: "Payment recorded",
  balances: "Balances",
  left_wg: "(left)",
  history: "History",
  history_empty: "No purchases yet.",
  history_purchase: "{0} bought {1} for {2}",
  history_purchase_no_cost: "{0} bought {1}",
  history_shared: "shared by {0}",
  history_payment: "{0} paid {1} {2}",
  delete_entry_title: "Delete this entry?",
  delete_entry_body: "Balances are recalculated for everyone.",

  members: "Members",
  you: "(you)",
  change_name: "Change your name",
  rename_wg: "Rename WG",
  invite_title: "Invite a flatmate",
  invite_body_hint: "They sign up with this email address and find the invitation in the app.",
  invite_send: "Invite",
  invite_saved: "Invitation saved. Send the email that just opened.",
  invites_pending: "Waiting for",
  invite_subject: "Join our WG \"{0}\" on FlatStock",
  invite_mail_body: "Hi!\n\n{1} invited you to the WG \"{0}\" on FlatStock, where we keep track of shared supplies and split the cost.\n\n1. Android: install FlatStock. iPhone: open {3} in Safari and choose Share > Add to Home Screen.\n2. Create an account with this email address: {2}\n3. Confirm your email, then tap Join.\n",
  reminders: "Reminders",
  web_reminders_hint: "While FlatStock is open you see when a flatmate marks something low or out. Background reminders need the Android app.",
  enable_notifications: "Allow notifications",
  notifications_on: "Notifications are on.",
  notifications_off: "Notifications are blocked or not supported on this device.",
  language: "Language",
  lang_system: "Device setting",
  leave_wg: "Leave WG",
  leave_wg_body: "You will no longer see \"{0}\". Open balances stay so your flatmates can still settle up with you.",
  notif_marked_out: "{0} marked {1} as out",
  notif_marked_low: "{0} marked {1} as low",
};

const de = {
  tagline: "Gemeinsame Vorräte für eure WG: sehen, was zur Neige geht, festhalten, wer was gekauft hat, und die Kosten teilen.",
  not_configured_title: "Firebase nicht eingerichtet",
  not_configured_body: "web/js/firebase-config.js enthält noch Platzhalter. Folge README > Developers, um ein Firebase-Projekt zu verbinden.",
  your_name: "Dein Name",
  your_name_hint: "Für deine Mitbewohner sichtbar",
  email: "E-Mail",
  password: "Passwort",
  password_hint: "Mindestens 6 Zeichen",
  sign_in: "Anmelden",
  create_account: "Konto erstellen",
  have_account: "Schon ein Konto? Anmelden",
  no_account: "Neu hier? Konto erstellen",
  forgot_password: "Passwort vergessen?",
  enter_email_first: "Gib zuerst deine E-Mail-Adresse ein.",
  reset_sent: "E-Mail zum Zurücksetzen gesendet.",
  privacy_body: "Dein Name, deine E-Mail-Adresse sowie die Vorräte und Einkäufe deiner WG werden in Google Firebase (EU/USA) gespeichert und nur mit den Mitgliedern deiner WG geteilt.",
  verify_title: "E-Mail bestätigen",
  verify_body: "Wir haben einen Bestätigungslink an {0} geschickt. Öffne ihn, komm zurück und tippe auf den Button unten.",
  verify_done: "Ich habe meine E-Mail bestätigt",
  verify_resend: "Link erneut senden",
  verify_resent: "Link erneut gesendet.",
  verify_not_yet: "Noch nicht bestätigt – schau in deinen Posteingang und Spam-Ordner.",
  sign_out: "Abmelden",
  signed_in_as: "Angemeldet als {0}",
  error_email_not_verified: "Bitte bestätige zuerst deine E-Mail: Öffne den Link, den wir dir geschickt haben, melde dich dann ab und wieder an.",
  error_permission_denied: "Noch kein Zugriff. Melde dich ab, wieder an und versuch es noch einmal. Klappt es weiterhin nicht, bitte deine Mitbewohnerin oder deinen Mitbewohner um eine neue Einladung.",
  install_title: "Zum Home-Bildschirm hinzufügen",
  install_body: "Tippe in Safari auf Teilen und dann auf „Zum Home-Bildschirm“. FlatStock öffnet sich dann wie eine App.",
  offline: "Offline – Änderungen werden synchronisiert, sobald du wieder online bist.",

  setup_title: "WG beitreten oder gründen",
  invites_for_you: "Einladungen für dich",
  invite_from: "Eingeladen von {0}",
  join: "Beitreten",
  decline: "Ablehnen",
  create_wg_title: "Neue WG anlegen",
  wg_name: "Name der WG, z. B. „Kastanienallee 12“",
  create_wg_hint: "Wir legen eine Startliste an (Spülmaschinentabs, Klopapier, Öl, Salz …). Du kannst sie jederzeit ändern.",
  create: "Anlegen",
  setup_hint: "Wartest du auf eine Einladung? Bitte jemanden aus der WG, {0} einzuladen.",

  tab_supplies: "Vorräte",
  tab_money: "Geld",
  tab_wg: "WG",

  level_FULL: "Voll",
  level_LOW: "Knapp",
  level_OUT: "Leer",
  section_OUT: "Leer ({0})",
  section_LOW: "Wird knapp ({0})",
  section_FULL: "Vorrätig ({0})",
  supplies_empty: "Noch keine Vorräte. Füge mit dem Button unten den ersten hinzu.",
  add_supply: "Hinzufügen",
  edit_supply: "Bearbeiten",
  marked_by: "Markiert von {0} · {1}",
  bought: "Gekauft",
  name: "Name",
  save: "Speichern",
  cancel: "Abbrechen",
  close: "Schließen",
  delete: "Löschen",

  bought_title: "{0} gekauft",
  add_purchase: "Einkauf eintragen",
  what_bought: "Was hast du gekauft?",
  cost_optional: "Kosten (optional)",
  paid_by: "Bezahlt von",
  shared_by: "Geteilt mit",
  each_pays: "{0} pro Person für {1} Personen",
  pick_someone: "Wähle mindestens eine Person.",
  no_cost_hint: "Ohne Kosten wird der Einkauf nur notiert; niemand schuldet etwas.",
  purchase_saved: "Einkauf gespeichert",

  your_balance: "Dein Saldo",
  you_are_owed: "Du bekommst {0} zurück",
  you_owe: "Du schuldest {0}",
  all_settled: "Alles ausgeglichen",
  who_owes_whom: "Wer schuldet wem",
  nobody_owes: "Niemand schuldet etwas.",
  owes_line: "{0} → {1}: {2}",
  settle_up: "Ausgleichen",
  settle_body: "{0} hat {1} bezahlt:",
  record_payment: "Zahlung eintragen",
  payment_saved: "Zahlung eingetragen",
  balances: "Salden",
  left_wg: "(ausgezogen)",
  history: "Verlauf",
  history_empty: "Noch keine Einkäufe.",
  history_purchase: "{0} hat {1} für {2} gekauft",
  history_purchase_no_cost: "{0} hat {1} gekauft",
  history_shared: "geteilt mit {0}",
  history_payment: "{0} hat {1} {2} gezahlt",
  delete_entry_title: "Eintrag löschen?",
  delete_entry_body: "Die Salden werden für alle neu berechnet.",

  members: "Mitglieder",
  you: "(du)",
  change_name: "Namen ändern",
  rename_wg: "WG umbenennen",
  invite_title: "Mitbewohner einladen",
  invite_body_hint: "Sie registrieren sich mit dieser E-Mail-Adresse und finden die Einladung in der App.",
  invite_send: "Einladen",
  invite_saved: "Einladung gespeichert. Schick die E-Mail ab, die sich gerade geöffnet hat.",
  invites_pending: "Ausstehend",
  invite_subject: "Tritt unserer WG „{0}“ bei FlatStock bei",
  invite_mail_body: "Hallo!\n\n{1} hat dich in die WG „{0}“ bei FlatStock eingeladen. Dort behalten wir den Überblick über gemeinsame Vorräte und teilen die Kosten.\n\n1. Android: FlatStock installieren. iPhone: {3} in Safari öffnen und Teilen > Zum Home-Bildschirm wählen.\n2. Ein Konto mit dieser E-Mail-Adresse erstellen: {2}\n3. E-Mail bestätigen, dann auf Beitreten tippen.\n",
  reminders: "Erinnerungen",
  web_reminders_hint: "Solange FlatStock offen ist, siehst du, wenn jemand etwas als knapp oder leer markiert. Erinnerungen im Hintergrund gibt es in der Android-App.",
  enable_notifications: "Benachrichtigungen erlauben",
  notifications_on: "Benachrichtigungen sind an.",
  notifications_off: "Benachrichtigungen sind blockiert oder auf diesem Gerät nicht möglich.",
  language: "Sprache",
  lang_system: "Wie am Gerät",
  leave_wg: "WG verlassen",
  leave_wg_body: "Du siehst „{0}“ dann nicht mehr. Offene Salden bleiben, damit deine Mitbewohner noch mit dir abrechnen können.",
  notif_marked_out: "{0} hat {1} als leer markiert",
  notif_marked_low: "{0} hat {1} als knapp markiert",
};

const STARTER = {
  en: ["Dishwasher tabs", "Toilet paper", "Kitchen roll", "Washing-up liquid", "All-purpose cleaner", "Bin bags", "Hand soap", "Sponges", "Cooking oil", "Salt"],
  de: ["Spülmaschinentabs", "Klopapier", "Küchenrolle", "Spülmittel", "Allzweckreiniger", "Müllbeutel", "Handseife", "Schwämme", "Speiseöl", "Salz"],
};

const LANG_KEY = "flatstock.lang";

export function savedLanguage() {
  try { return localStorage.getItem(LANG_KEY) ?? ""; } catch { return ""; }
}

export function setLanguage(tag) {
  try {
    if (tag) localStorage.setItem(LANG_KEY, tag); else localStorage.removeItem(LANG_KEY);
  } catch { /* private mode: language resets on reload */ }
  lang = resolve();
  document.documentElement.lang = lang;
}

const resolve = () => savedLanguage() || ((navigator.language ?? "en").toLowerCase().startsWith("de") ? "de" : "en");

export let lang = resolve();
document.documentElement.lang = lang;

/** t("section_LOW", 3) → "Running low (3)"; {0}, {1}… are replaced by the arguments. */
export function t(key, ...args) {
  const s = (lang === "de" ? de[key] : undefined) ?? en[key] ?? key;
  return s.replace(/\{(\d)\}/g, (_, i) => String(args[+i] ?? ""));
}

export const starterSupplies = () => STARTER[lang];

const locale = () => (lang === "de" ? "de-DE" : "en-GB");

export const formatCents = (cents) =>
  new Intl.NumberFormat(locale(), { style: "currency", currency: "EUR" }).format(cents / 100);

/** Cents as the user would type them, e.g. "3,49" in German. */
export const centsInput = (cents) =>
  new Intl.NumberFormat(locale(), { minimumFractionDigits: 2, maximumFractionDigits: 2, useGrouping: false }).format(cents / 100);

export const formatDate = (millis) =>
  new Intl.DateTimeFormat(locale(), { day: "numeric", month: "short", year: "numeric" }).format(new Date(millis));
