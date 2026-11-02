// Security rules tests against the Firestore emulator. Run from this folder: npm test
import { test, before, beforeEach, after } from "node:test";
import { readFileSync } from "node:fs";
import { initializeTestEnvironment, assertSucceeds, assertFails } from "@firebase/rules-unit-testing";
import {
  doc, getDoc, setDoc, updateDoc, deleteDoc, addDoc, collection, getDocs, query, where, arrayUnion, arrayRemove,
} from "firebase/firestore";

let env;
const WG = "wg1";

before(async () => {
  env = await initializeTestEnvironment({
    projectId: "demo-flatstock",
    firestore: { rules: readFileSync(new URL("../firestore.rules", import.meta.url), "utf8"), host: "127.0.0.1", port: 8085 },
  });
});
after(() => env.cleanup());

// anna and ben live in wg1; cara was invited; eve is a stranger.
beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, "wgs", WG), {
      name: "Flat", ownerUid: "anna", members: ["anna", "ben"],
      memberEmails: { anna: "anna@x.de", ben: "ben@x.de" }, memberNames: { anna: "Anna", ben: "Ben" }, createdAt: 1,
    });
    await setDoc(doc(db, "wgs", WG, "supplies", "s1"), { name: "Salt", level: "FULL", updatedBy: "anna", updatedAt: 1 });
    await setDoc(doc(db, "wgs", WG, "purchases", "p1"),
      { supplyId: "s1", itemName: "Salt", paidBy: "anna", amountCents: 100, sharedBy: ["anna", "ben"], createdBy: "anna", createdAt: 1 });
    await setDoc(doc(db, "invites", `${WG}_cara@x.de`),
      { wgId: WG, wgName: "Flat", email: "cara@x.de", invitedBy: "anna@x.de", status: "pending", createdAt: 1 });
  });
});

const as = (uid, email, verified = true) =>
  env.authenticatedContext(uid, { email, email_verified: verified }).firestore();
const anna = () => as("anna", "anna@x.de");
const ben = () => as("ben", "ben@x.de");
const cara = (verified = true) => as("cara", "cara@x.de", verified);
const eve = () => as("eve", "eve@x.de");

const purchase = (over = {}) => ({
  supplyId: "", itemName: "Oil", paidBy: "ben", amountCents: 450, sharedBy: ["anna", "ben"], createdBy: "ben", createdAt: 2, ...over,
});

test("members read and write their WG's data", async () => {
  await assertSucceeds(getDoc(doc(ben(), "wgs", WG)));
  await assertSucceeds(getDocs(collection(ben(), "wgs", WG, "supplies")));
  await assertSucceeds(updateDoc(doc(ben(), "wgs", WG, "supplies", "s1"), { level: "OUT", updatedBy: "ben", updatedAt: 2 }));
  await assertSucceeds(addDoc(collection(ben(), "wgs", WG, "purchases"), purchase()));
  await assertSucceeds(addDoc(collection(ben(), "wgs", WG, "payments"),
    { fromUid: "ben", toUid: "anna", amountCents: 50, createdBy: "ben", createdAt: 3 }));
});

test("strangers can't read or write anything in the WG", async () => {
  await assertFails(getDoc(doc(eve(), "wgs", WG)));
  await assertFails(getDocs(collection(eve(), "wgs", WG, "supplies")));
  await assertFails(getDocs(collection(eve(), "wgs", WG, "purchases")));
  await assertFails(getDocs(collection(eve(), "wgs", WG, "payments")));
  await assertFails(updateDoc(doc(eve(), "wgs", WG, "supplies", "s1"), { level: "OUT" }));
  await assertFails(addDoc(collection(eve(), "wgs", WG, "purchases"), purchase({ createdBy: "eve", paidBy: "eve" })));
  await assertFails(updateDoc(doc(eve(), "wgs", WG), { members: arrayUnion("eve") }));
  await assertFails(getDocs(collection(env.unauthenticatedContext().firestore(), "wgs", WG, "supplies")));
});

test("invited people with a verified email can join, others can't", async () => {
  const join = (db, uid, email) => updateDoc(doc(db, "wgs", WG), {
    members: arrayUnion(uid), [`memberEmails.${uid}`]: email, [`memberNames.${uid}`]: "C",
  });
  await assertFails(join(cara(false), "cara", "cara@x.de"));
  await assertFails(join(eve(), "eve", "eve@x.de"));
  // cara can't sneak in someone else, or rename the WG while joining.
  await assertFails(updateDoc(doc(cara(), "wgs", WG), { members: arrayUnion("cara", "eve"), "memberEmails.cara": "cara@x.de" }));
  await assertFails(updateDoc(doc(cara(), "wgs", WG), { members: arrayUnion("cara"), "memberEmails.cara": "cara@x.de", name: "Mine" }));
  await assertSucceeds(join(cara(), "cara", "cara@x.de"));
  await assertSucceeds(getDocs(collection(cara(), "wgs", WG, "supplies")));
});

test("members can't add or remove others, only leave themselves", async () => {
  await assertFails(updateDoc(doc(ben(), "wgs", WG), { members: arrayUnion("eve") }));
  await assertFails(updateDoc(doc(ben(), "wgs", WG), { members: arrayRemove("anna") }));
  await assertFails(updateDoc(doc(ben(), "wgs", WG), { ownerUid: "ben" }));
  await assertSucceeds(updateDoc(doc(ben(), "wgs", WG), { "memberNames.ben": "Benny", name: "Our flat" }));
  await assertSucceeds(updateDoc(doc(ben(), "wgs", WG), { members: arrayRemove("ben") }));
  await assertFails(getDocs(collection(ben(), "wgs", WG, "supplies")));
});

test("purchases must be split among members and are append-only", async () => {
  const purchases = (db) => collection(db, "wgs", WG, "purchases");
  await assertFails(addDoc(purchases(ben()), purchase({ sharedBy: ["anna", "eve"] })));
  await assertFails(addDoc(purchases(ben()), purchase({ sharedBy: [] })));
  await assertFails(addDoc(purchases(ben()), purchase({ createdBy: "anna" })));
  await assertFails(addDoc(purchases(ben()), purchase({ amountCents: -5 })));
  await assertFails(addDoc(purchases(ben()), purchase({ amountCents: 4.5 })));
  await assertFails(addDoc(purchases(ben()), purchase({ paidBy: "eve" })));
  await assertFails(updateDoc(doc(ben(), "wgs", WG, "purchases", "p1"), { amountCents: 1 }));
  await assertFails(deleteDoc(doc(ben(), "wgs", WG, "purchases", "p1")));
  await assertSucceeds(deleteDoc(doc(anna(), "wgs", WG, "purchases", "p1")));
});

test("payments need two different people known to the WG", async () => {
  const payments = (db) => collection(db, "wgs", WG, "payments");
  const pay = (over) => ({ fromUid: "ben", toUid: "anna", amountCents: 50, createdBy: "ben", createdAt: 3, ...over });
  await assertFails(addDoc(payments(ben()), pay({ toUid: "ben" })));
  await assertFails(addDoc(payments(ben()), pay({ toUid: "eve" })));
  await assertFails(addDoc(payments(ben()), pay({ amountCents: 0 })));
  await assertSucceeds(addDoc(payments(ben()), pay({})));
});

test("invitations: members invite, invitees see and answer their own", async () => {
  await assertSucceeds(setDoc(doc(ben(), "invites", `${WG}_dan@x.de`),
    { wgId: WG, wgName: "Flat", email: "dan@x.de", invitedBy: "ben@x.de", status: "pending", createdAt: 2 }));
  await assertFails(setDoc(doc(eve(), "invites", `${WG}_eve@x.de`),
    { wgId: WG, wgName: "Flat", email: "eve@x.de", invitedBy: "eve@x.de", status: "pending", createdAt: 2 }));
  await assertSucceeds(getDocs(query(collection(cara(), "invites"), where("email", "==", "cara@x.de"), where("status", "==", "pending"))));
  await assertSucceeds(getDocs(query(collection(ben(), "invites"), where("wgId", "==", WG), where("status", "==", "pending"))));
  await assertFails(getDocs(query(collection(eve(), "invites"), where("wgId", "==", WG))));
  await assertFails(getDoc(doc(eve(), "invites", `${WG}_cara@x.de`)));
  await assertSucceeds(updateDoc(doc(cara(), "invites", `${WG}_cara@x.de`), { status: "declined" }));
  await assertFails(updateDoc(doc(cara(), "invites", `${WG}_cara@x.de`), { wgId: "other" }));
});

test("a declined invitation no longer lets you join", async () => {
  await assertSucceeds(updateDoc(doc(cara(), "invites", `${WG}_cara@x.de`), { status: "declined" }));
  await assertFails(updateDoc(doc(cara(), "wgs", WG), { members: arrayUnion("cara"), "memberEmails.cara": "cara@x.de" }));
});

test("pack counts and scanned products", async () => {
  const s1 = (db) => doc(db, "wgs", WG, "supplies", "s1");
  await assertSucceeds(updateDoc(s1(ben()), { count: 12, level: "FULL" }));
  await assertFails(updateDoc(s1(ben()), { count: -1 }));
  await assertFails(updateDoc(s1(ben()), { count: 1.5 }));
  const p = (db) => doc(db, "wgs", WG, "products", "4001234567890");
  await assertSucceeds(setDoc(p(ben()), { supplyId: "s1", name: "Ja! Salz", brand: "Ja!" }));
  await assertSucceeds(getDoc(p(anna())));
  await assertFails(getDoc(p(eve())));
  await assertFails(setDoc(p(eve()), { supplyId: "s1", name: "x", brand: "" }));
  await assertFails(setDoc(p(ben()), { name: "no supply" }));
});

test("user profiles are private", async () => {
  await assertSucceeds(setDoc(doc(ben(), "users", "ben"), { email: "ben@x.de", groupIds: [WG], activeGroupId: WG }));
  await assertFails(getDoc(doc(anna(), "users", "ben")));
});
