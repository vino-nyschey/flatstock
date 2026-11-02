// Firebase console > Project settings > Your apps > Web app > SDK setup and configuration ("Config").
// These values identify the project; they are not secrets. Access is controlled by firestore.rules.
// Until they are filled in, the app shows a "not set up" page.
export const firebaseConfig = {
  apiKey: "REPLACE_ME",
  authDomain: "",
  projectId: "",
  storageBucket: "",
  messagingSenderId: "",
  appId: "",
};

// For local testing: http://localhost:5000/?emulator talks to `firebase emulators:start` instead
// (see README > Developers). Never active on a real domain.
export const useEmulators =
  ["localhost", "127.0.0.1"].includes(location.hostname) && new URLSearchParams(location.search).has("emulator");
export const emulatorConfig = { apiKey: "demo-key", authDomain: "localhost", projectId: "demo-flatstock" };
