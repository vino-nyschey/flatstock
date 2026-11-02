# FlatStock

Shared supplies for a flat share (WG): see what's running low, record who bought what, and split the cost.

- **Supplies list.** Every common item (dishwasher tabs, toilet paper, oil, salt, …) is marked **Full**, **Low** or **Out**, and shows how many **packs** are in stock (− when one is used up, + to add). Anyone in the WG can change it. Reaching 0 packs marks it **Out**.
- **Barcode scanner.** Scan a product in the shop to see how many packs are at home before you buy. The first time you scan a barcode, pick which supply it counts towards. After that, Ja! salt from Rewe and salt from Aldi both count as **Salt**.
- **Reminders.** You get a notification when a flatmate marks something low or out. You also get a summary of everything that's low, every day or on Saturdays at 9:00 (Android).
- **Purchases.** Tap **Bought** when you buy something. You can enter what it cost, choose who paid, and tick who shares it (everyone by default). The cost is split equally among them.
- **Settling up.** Balances are netted across all purchases, like Splitwise. **Money** shows who owes whom, and **Settle up** records a payment.
- **Joining.** Flatmates join by email invitation. The app is in English and German, and you can switch between them.

There is an Android app, and a web app for iPhone flatmates. Both use the same data.

---

## For users

### Android
1. Install the APK your flatmate sent you. Allow notifications when the app asks.
2. Tap **Create account** and enter your name, email and a password. Open the confirmation link in your email, then tap **I've confirmed my email**.
3. To start a WG, give it a name and tap **Create**. You get a starter list that you can edit. To join a WG, ask a flatmate to invite your email address, then tap **Join**.

### iPhone (or any browser)
1. Open the FlatStock web link in **Safari**. Tap **Share → Add to Home Screen**.
2. Open FlatStock from the home screen and sign up as above.
3. The web app shows flatmates' "low/out" marks as a banner while it is open. iPhones get no background reminders. If you want them, ask an Android flatmate to keep the Android app.

### Everyday use
| I want to… | Do this |
|---|---|
| Say something is running out | **Supplies**: tap **Low** or **Out** on the item |
| Record that I bought it | Tap **Bought** on the item, enter how many packs (e.g. 2 boxes on offer), optionally the cost and who shares it. The packs are added and the item goes back to **Full**. |
| Check stock in the shop | Tap **Scan** at the top and point the camera at the barcode |
| A pack is used up | Tap **−** on the item |
| Record something not on the list | **Money → Add a purchase** |
| Pay a flatmate back | **Money → Who owes whom → Settle up**. Adjust the amount if you paid only part of it. |
| Fix a mistake | **Money → History**: tap 🗑 on an entry you made yourself |
| Add, rename or remove an item | **Add item**, or tap an item's name |
| Invite someone | **WG → Invite a flatmate** |
| Change language or reminders | **WG** tab |

Leaving a WG keeps your past purchases and any open balance, so the others can still settle up with you.

---

## For testers (v0.x beta)

Please check:
- **Joining.** Have one person invite another, and have the invitee sign up and join. If you see "No access yet", sign out and back in, then report it.
- **Splitting.** Record a few purchases shared by different flatmates and check that **Money** adds up. The values in **Balances** always sum to 0.
- **Rounding.** €10.00 split 3 ways is €3.34 + €3.33 + €3.33. The extra cent goes to whoever sorts first by internal id, and it is the same on every phone.
- **Reminders (Android).** Mark something **Out** on one phone. The other phone notifies within about 30 minutes, or immediately if the app is open. Background checks need a network connection, and Android may delay them while saving battery.
- **Offline.** Marking levels works offline and syncs later. Purchases and payments need a connection.
- **German.** Switch the language under **WG → Language** and look for untranslated or clipped text.

Send bugs as GitHub issues with your phone model, the app version (shown at the bottom of the **WG** tab) and steps to reproduce.

---

## For developers

### Stack
- **Android.** Kotlin 2.2.20, Jetpack Compose Material 3, AppCompatActivity (per-app locales), Firebase Auth (email/password plus verification), Firestore with offline cache, WorkManager, and CameraX with ML Kit barcode scanning (on-device). Portrait only. Gradle 9.1.0, AGP 8.13.0, compile/target SDK 35, min SDK 26, JVM 17.
- **Web.** A no-build ES-module PWA in `web/`, using Preact + htm from a CDN, Firebase JS SDK 12 and ZXing for scanning.
- **Product names** for newly scanned barcodes come from Open Food Facts / Open Products Facts (free, no key). Hosted on Firebase Hosting.
- **No server code.** Security is enforced by `firestore.rules`. Reminders are computed on each phone, because Cloud Functions/FCM would need the paid Blaze plan.

### Layout
```
app/src/main/java/app/flatstock/
  domain/   Ledger.kt (split, balances, settle-up, money parsing), Alerts.kt  ← pure Kotlin, unit tested
  data/     Models.kt, Repository.kt (all Firestore access)
  ui/       Compose screens: Auth, Supplies, Money, WG
  work/     ReminderWorker (every 30 min), Reminders (alert/summary logic), Notifications
web/js/     app.js (UI), db.js (mirror of Repository.kt), ledger.js (mirror of domain/), i18n.js
firestore.rules, rules-test/   security rules and their emulator tests
```
`domain/Ledger.kt` and `web/js/ledger.js` must give identical results. Both test suites cover the same cases.

### Firestore data
```
users/{uid}                 { email, name, groupIds[], activeGroupId }
invites/{wgId}_{email}      { wgId, wgName, email, invitedBy, status: pending|accepted|declined }
wgs/{wid}                   { name, ownerUid, members[], memberEmails{uid:email}, memberNames{uid:name} }
wgs/{wid}/supplies/{id}     { name, level: FULL|LOW|OUT, count (packs), updatedBy, updatedAt }
wgs/{wid}/purchases/{id}    { itemName, supplyId, packs, paidBy, amountCents, sharedBy[], createdBy, createdAt }
wgs/{wid}/payments/{id}     { fromUid, toUid, amountCents, createdBy, createdAt }
wgs/{wid}/products/{barcode} { supplyId, name, brand }   scanned barcode -> supply it counts towards
```
Money is stored in whole cents. Balances are never stored; every device recomputes them from purchases and payments.

### Security rules (summary)
- Only members (`uid in wgs/{wid}.members`) can read or write a WG and its subcollections.
- A non-member can add only **themselves**, only with a **verified** email, and only while a **pending** invite for that email exists.
- Members can rename the WG, change their own name, or leave. They cannot add or remove anyone else.
- Purchases and payments are append-only, with validated amounts and members. Only the author can delete an entry.

### Set up Firebase (once)
1. Create a Firebase project (suggested ID: `flatstock-wg`) at https://console.firebase.google.com.
2. In **Authentication**, enable **Email/Password**.
3. Create a **Firestore** database (region `eur3` or `europe-west3`).
4. Add an **Android app** with package `app.flatstock`, download `google-services.json` and put it in `app/`. It is git-ignored.
5. Add a **Web app**. Copy `web/js/firebase-config.example.js` to `web/js/firebase-config.js` (git-ignored) and paste its config there. In Google Cloud > Credentials, restrict that browser key to your hosting domains and to the Identity Toolkit, Token Service and Firestore APIs.
6. Add `.firebaserc` with `{"projects":{"default":"<project-id>"}}`. Update `web_url` in `app/src/main/res/values/strings.xml` to the hosting URL.
7. Deploy: `firebase deploy --only firestore:rules,hosting`.

Without `google-services.json` the Android app still builds, and shows a "Firebase not set up" screen.

### Build and test
```bash
# Windows: JAVA_HOME = C:\Program Files\Android\Android Studio\jbr
./gradlew testDebugUnitTest assembleDebug          # Kotlin unit tests + APK
adb install -r app/build/outputs/apk/debug/app-debug.apk
node --test "web/test/*.test.mjs"                  # web ledger tests
cd rules-test && npm install && npm test           # security rules on the Firestore emulator (needs Java on PATH)
```

### Run the web app locally without a Firebase project
```bash
firebase emulators:start --project demo-flatstock --only auth,firestore,hosting
```
Open http://localhost:5000/?emulator. The `?emulator` switch works only on localhost. Verification emails are not sent. Mark addresses as verified via `http://127.0.0.1:9099/emulator/v1/projects/demo-flatstock/oobCodes`: open each `oobLink`. To test as two flatmates, use a second origin such as http://127.0.0.1:5000/?emulator.

### Releases
Each phone build is tagged `v0.x.0`. Bump `versionCode`/`versionName` in `app/build.gradle.kts`, `APP_VERSION` in `web/js/db.js` and `CACHE` in `web/sw.js`.

Never commit `google-services.json`, `.firebase/` or `*-debug.log`.
