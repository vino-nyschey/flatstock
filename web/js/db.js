// Single access point to Firebase, mirroring the Android app's Repository.kt.
//
// Firestore layout (shared with the Android app, keep it identical):
//   users/{uid}                   { email, name, groupIds[], activeGroupId }
//   invites/{wgId}_{email}        { wgId, wgName, email, invitedBy, status, createdAt }
//   wgs/{wid}                     { name, ownerUid, members[], memberEmails{uid: email}, memberNames{uid: name}, createdAt }
//   wgs/{wid}/supplies/{id}       { name, level: FULL|LOW|OUT, updatedBy, updatedAt }
//   wgs/{wid}/purchases/{id}      { supplyId, itemName, paidBy, amountCents, sharedBy[], createdBy, createdAt }
//   wgs/{wid}/payments/{id}       { fromUid, toUid, amountCents, createdBy, createdAt }
//   wgs/{wid}/products/{barcode}  { supplyId, name, brand }: which supply a scanned barcode counts towards
//
// Supply level changes are fire-and-forget like on Android; money entries are awaited.

import { initializeApp } from "https://www.gstatic.com/firebasejs/12.19.0/firebase-app.js";
import {
  getAuth, connectAuthEmulator, onAuthStateChanged, signInWithEmailAndPassword, createUserWithEmailAndPassword,
  sendEmailVerification, sendPasswordResetEmail, signOut as fbSignOut,
} from "https://www.gstatic.com/firebasejs/12.19.0/firebase-auth.js";
import {
  initializeFirestore, getFirestore, connectFirestoreEmulator, persistentLocalCache, persistentMultipleTabManager,
  collection, doc, onSnapshot, getDoc, setDoc, updateDoc, addDoc, deleteDoc, writeBatch,
  query, where, arrayUnion, arrayRemove, deleteField, increment,
} from "https://www.gstatic.com/firebasejs/12.19.0/firebase-firestore.js";
import { firebaseConfig, useEmulators, emulatorConfig } from "./firebase-config.js";
import { levelForCount } from "./ledger.js";

export const APP_VERSION = "web-0.2.0";

const app = initializeApp(useEmulators ? emulatorConfig : firebaseConfig);
export const auth = getAuth(app);
if (useEmulators) connectAuthEmulator(auth, "http://127.0.0.1:9099", { disableWarnings: true });

let db;
try {
  db = initializeFirestore(app, { localCache: persistentLocalCache({ tabManager: persistentMultipleTabManager() }) });
} catch (e) {
  // Private browsing or an old Safari without IndexedDB: work online only.
  console.warn("offline cache unavailable", e);
  db = getFirestore(app);
}
if (useEmulators) connectFirestoreEmulator(db, "127.0.0.1", 8085);

export const uid = () => auth.currentUser?.uid ?? "";
export const currentEmail = () => (auth.currentUser?.email ?? "").toLowerCase();

const users = () => collection(db, "users");
const invites = () => collection(db, "invites");
const wg = (wid) => doc(db, "wgs", wid);
const supplies = (wid) => collection(db, "wgs", wid, "supplies");
const purchases = (wid) => collection(db, "wgs", wid, "purchases");
const payments = (wid) => collection(db, "wgs", wid, "payments");
const products = (wid) => collection(db, "wgs", wid, "products");

const logFailure = (p) => p.catch((e) => console.warn("write failed", e));
const listen = (q, map, cb) =>
  onSnapshot(q, (s) => cb(s.docs.map(map)), (e) => console.warn("listen failed", e));
const withId = (d) => ({ id: d.id, ...d.data() });

/** Display name: the chosen name, else the part of the email before the @. */
export const nameOf = (group, userId) =>
  group?.memberNames?.[userId]?.trim() || group?.memberEmails?.[userId]?.split("@")[0] || "?";

// region Account

export const onAuth = (cb) => onAuthStateChanged(auth, cb);

export async function signIn(email, password) {
  await signInWithEmailAndPassword(auth, email.trim().toLowerCase(), password);
}

export async function register(name, email, password) {
  const { user } = await createUserWithEmailAndPassword(auth, email.trim().toLowerCase(), password);
  await setDoc(doc(users(), user.uid), { email: user.email?.toLowerCase(), name: name.trim() }, { merge: true });
  await sendEmailVerification(user);
}

export const resendVerification = () => auth.currentUser && sendEmailVerification(auth.currentUser);

/** Re-reads the user from Firebase; returns true once the email link was clicked. */
export async function reloadUser() {
  if (!auth.currentUser) return false;
  await auth.currentUser.reload();
  const verified = auth.currentUser.emailVerified;
  // Refresh the token so security rules see email_verified = true.
  if (verified) await auth.currentUser.getIdToken(true);
  return verified;
}

export const resetPassword = (email) => sendPasswordResetEmail(auth, email.trim().toLowerCase());
export const signOut = () => fbSignOut(auth);

// endregion
// region WG and invitations

function toUserGroups(snap) {
  const data = snap.data() ?? {};
  const ids = Array.isArray(data.groupIds) ? data.groupIds : [];
  const active = ids.includes(data.activeGroupId) ? data.activeGroupId : ids[0] ?? null;
  return { groupIds: ids, activeId: active, name: data.name ?? "" };
}

export function watchUserGroups(userId, cb) {
  return onSnapshot(doc(users(), userId), (snap) => {
    // An empty cache says nothing yet; wait for the server answer.
    if (snap.metadata.fromCache && !snap.exists()) return;
    cb(toUserGroups(snap));
  }, (e) => console.warn("user listen failed", e));
}

/** Calls back with null when the WG can't be read (deleted, or the user left it). */
export function watchWg(wid, cb) {
  return onSnapshot(wg(wid), (snap) => cb(snap.exists() ? { id: wid, ...snap.data() } : null), () => cb(null));
}

export function watchPendingInvites(email, cb) {
  const q = query(invites(), where("email", "==", email.toLowerCase()), where("status", "==", "pending"));
  return listen(q, withId, cb);
}

/** Invitations this WG sent that nobody accepted yet. */
export function watchSentInvites(wid, cb) {
  return listen(query(invites(), where("wgId", "==", wid), where("status", "==", "pending")), withId, cb);
}

async function myName() {
  try { return (await getDoc(doc(users(), uid()))).data()?.name ?? ""; } catch { return ""; }
}

const newSupply = (name) => ({ name: name.trim(), level: "FULL", count: 0, updatedBy: uid(), updatedAt: Date.now() });

/** Creates the WG with a starter list of supplies (in the user's language). */
export async function createWg(name, starterSupplies) {
  const ref = doc(collection(db, "wgs"));
  await setDoc(ref, {
    name: name.trim(),
    ownerUid: uid(),
    members: [uid()],
    memberEmails: { [uid()]: currentEmail() },
    memberNames: { [uid()]: await myName() },
    createdAt: Date.now(),
  });
  const batch = writeBatch(db);
  starterSupplies.forEach((s) => batch.set(doc(supplies(ref.id)), newSupply(s)));
  await batch.commit();
  await joinedWg(ref.id);
}

async function joinedWg(wid) {
  await setDoc(doc(users(), uid()),
    { email: currentEmail(), groupIds: arrayUnion(wid), activeGroupId: wid }, { merge: true });
}

export async function acceptInvite(invite) {
  // The join rule checks email_verified; a token issued before the email was confirmed still says false.
  await auth.currentUser.reload();
  const token = await auth.currentUser.getIdTokenResult(true);
  if (token.claims.email_verified !== true) throw Object.assign(new Error("email not verified"), { code: "email-not-verified" });
  await updateDoc(wg(invite.wgId), {
    members: arrayUnion(uid()),
    [`memberEmails.${uid()}`]: currentEmail(),
    [`memberNames.${uid()}`]: await myName(),
  });
  await joinedWg(invite.wgId);
  await updateDoc(doc(invites(), invite.id), { status: "accepted" });
}

export const declineInvite = (invite) => updateDoc(doc(invites(), invite.id), { status: "declined" });

export async function createInvite(group, inviteeEmail) {
  const email = inviteeEmail.trim().toLowerCase();
  await setDoc(doc(invites(), `${group.id}_${email}`), {
    wgId: group.id,
    wgName: group.name,
    email,
    invitedBy: currentEmail(),
    status: "pending",
    createdAt: Date.now(),
  });
}

export const cancelInvite = (invite) => deleteDoc(doc(invites(), invite.id));

export async function setMyName(wid, name) {
  await updateDoc(wg(wid), { [`memberNames.${uid()}`]: name.trim() });
  await setDoc(doc(users(), uid()), { name: name.trim() }, { merge: true });
}

export const renameWg = (wid, name) => updateDoc(wg(wid), { name: name.trim() });

/** Leaves wid; name and email stay so past purchases still show who paid. */
export async function leaveWg(wid, allIds) {
  await updateDoc(wg(wid), { members: arrayRemove(uid()) });
  const next = allIds.find((id) => id !== wid);
  await updateDoc(doc(users(), uid()), { groupIds: arrayRemove(wid), activeGroupId: next ?? deleteField() });
}

// endregion
// region Supplies

export function watchSupplies(wid, cb) {
  return listen(supplies(wid), withId, (list) =>
    cb(list.sort((a, b) => a.name.toLowerCase().localeCompare(b.name.toLowerCase()))));
}

/** Returns the new supply's id at once; the write itself syncs in the background. */
export function addSupply(wid, name) {
  const ref = doc(supplies(wid));
  logFailure(setDoc(ref, newSupply(name)));
  return ref.id;
}

/** Adds or removes packs (never below 0); the level follows, see levelForCount. */
export function changeCount(wid, supply, delta) {
  const count = Math.max(0, (supply.count ?? 0) + delta);
  logFailure(updateDoc(doc(supplies(wid), supply.id),
    { count, level: levelForCount(count, supply.level ?? "FULL"), updatedBy: uid(), updatedAt: Date.now() }));
}

const productDoc = (wid, barcode) => doc(products(wid), barcode.replaceAll("/", "_"));

export async function productOnce(wid, barcode) {
  try {
    const snap = await getDoc(productDoc(wid, barcode));
    return snap.exists() ? { barcode, ...snap.data() } : null;
  } catch { return null; }
}

/** Remembers that barcode counts towards supplyId. */
export const linkProduct = (wid, barcode, supplyId, name, brand) =>
  logFailure(setDoc(productDoc(wid, barcode), { supplyId, name: name.trim(), brand: brand.trim() }));
export const renameSupply = (wid, id, name) => logFailure(updateDoc(doc(supplies(wid), id), { name: name.trim() }));
export const deleteSupply = (wid, id) => logFailure(deleteDoc(doc(supplies(wid), id)));
export const setLevel = (wid, id, level) =>
  logFailure(updateDoc(doc(supplies(wid), id), { level, updatedBy: uid(), updatedAt: Date.now() }));

// endregion
// region Purchases and settling up

const newestFirst = (list) => list.sort((a, b) => b.createdAt - a.createdAt);
export const watchPurchases = (wid, cb) => listen(purchases(wid), withId, (l) => cb(newestFirst(l)));
export const watchPayments = (wid, cb) => listen(payments(wid), withId, (l) => cb(newestFirst(l)));

/** Records the purchase and marks the supply as full again, in one write. */
export async function recordPurchase(wid, supply, itemName, packs, amountCents, paidBy, sharedBy) {
  const now = Date.now();
  const batch = writeBatch(db);
  batch.set(doc(purchases(wid)), {
    supplyId: supply?.id ?? "",
    itemName: itemName.trim(),
    packs,
    paidBy,
    amountCents,
    sharedBy: [...new Set(sharedBy)],
    createdBy: uid(),
    createdAt: now,
  });
  if (supply) batch.update(doc(supplies(wid), supply.id), { level: "FULL", count: increment(packs), updatedBy: uid(), updatedAt: now });
  await batch.commit();
}

export async function recordPayment(wid, fromUid, toUid, amountCents) {
  await addDoc(payments(wid), { fromUid, toUid, amountCents, createdBy: uid(), createdAt: Date.now() });
}

/** Only the person who entered a purchase or payment may delete it (see firestore.rules). */
export const deletePurchase = (wid, id) => deleteDoc(doc(purchases(wid), id));
export const deletePayment = (wid, id) => deleteDoc(doc(payments(wid), id));

// endregion
