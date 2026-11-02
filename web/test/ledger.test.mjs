// Same cases as the Android LedgerTest/AlertsTest. Run: node --test "web/test/*.test.mjs"
import { test } from "node:test";
import assert from "node:assert/strict";
import { splitEqually, balances, settle, parseCents, newAlerts, lowSummary, levelForCount } from "../js/ledger.js";

const obj = (map) => Object.fromEntries(map);

test("split evenly", () => {
  assert.deepEqual(obj(splitEqually(900, ["a", "b", "c"])), { a: 300, b: 300, c: 300 });
});

test("leftover cents go to first ids and add up", () => {
  const shares = splitEqually(1000, ["c", "a", "b"]);
  assert.deepEqual(obj(shares), { a: 334, b: 333, c: 333 });
  assert.equal([...shares.values()].reduce((x, y) => x + y), 1000);
});

test("split ignores duplicates and handles empty", () => {
  assert.deepEqual(obj(splitEqually(500, ["a", "b", "a"])), { a: 250, b: 250 });
  assert.equal(splitEqually(500, []).size, 0);
  assert.throws(() => splitEqually(-1, ["a"]));
});

test("payer not sharing is owed everything", () => {
  assert.deepEqual(obj(balances([{ payer: "anna", amountCents: 600, sharers: ["ben", "cem"] }], [])),
    { anna: 600, ben: -300, cem: -300 });
});

test("balances net across purchases and payments", () => {
  const b = balances([
    { payer: "anna", amountCents: 900, sharers: ["anna", "ben", "cem"] },
    { payer: "ben", amountCents: 600, sharers: ["anna", "ben", "cem"] },
    { payer: "cem", amountCents: 300, sharers: ["anna", "ben"] },
  ], []);
  assert.deepEqual(obj(b), { anna: 250, ben: -50, cem: -200 });
  const paid = balances([{ payer: "anna", amountCents: 900, sharers: ["anna", "ben", "cem"] }],
    [{ from: "ben", to: "anna", amountCents: 300 }]);
  assert.deepEqual(obj(paid), { anna: 300, ben: 0, cem: -300 });
});

test("settle suggests the same transfers as Android", () => {
  assert.deepEqual(settle(new Map(Object.entries({ anna: 250, ben: -50, cem: -200 }))), [
    { from: "cem", to: "anna", amountCents: 200 },
    { from: "ben", to: "anna", amountCents: 50 },
  ]);
});

test("recording suggested transfers settles up", () => {
  const expenses = [
    { payer: "anna", amountCents: 1299, sharers: ["anna", "ben", "cem", "dana"] },
    { payer: "dana", amountCents: 455, sharers: ["ben", "dana"] },
  ];
  const payments = settle(balances(expenses, []));
  assert.ok([...balances(expenses, payments).values()].every((v) => v === 0));
});

test("parseCents", () => {
  assert.equal(parseCents("3,49"), 349);
  assert.equal(parseCents("€ 3.49"), 349);
  assert.equal(parseCents(",50"), 50);
  assert.equal(parseCents("19.99"), 1999);
  for (const bad of ["", "abc", "0", "-2", "1.234", "1.2.3"]) assert.equal(parseCents(bad), null, bad);
});

test("alerts and summary", () => {
  const s = [
    { name: "Salt", level: "LOW", updatedBy: "ben", updatedAt: 200 },
    { name: "Toilet paper", level: "OUT", updatedBy: "ben", updatedAt: 300 },
    { name: "Oil", level: "OUT", updatedBy: "me", updatedAt: 300 },
    { name: "Tabs", level: "FULL", updatedBy: "ben", updatedAt: 400 },
    { name: "Bin bags", level: "OUT", updatedBy: "ben", updatedAt: 50 },
  ];
  assert.deepEqual(newAlerts(s, 100, "me").map((x) => x.name), ["Toilet paper", "Salt"]);
  assert.deepEqual(lowSummary(s).map((x) => x.name), ["Bin bags", "Oil", "Toilet paper", "Salt"]);
});

test("level follows pack count", () => {
  assert.equal(levelForCount(0, "FULL"), "OUT");
  assert.equal(levelForCount(12, "OUT"), "FULL");
  assert.equal(levelForCount(1, "LOW"), "LOW");
});
