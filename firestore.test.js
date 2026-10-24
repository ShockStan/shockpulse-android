const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function validTransactionPayload(userId, nowTimestamp) {
  return {
    userId,
    title: "Whole Foods Market",
    amountCents: 8450,
    type: "EXPENSE",
    status: "PENDING",
    category: "Groceries",
    paymentMethodId: "pm_1",
    paymentMethodLabel: "Chase Sapphire ••4821",
    sourceType: "SMS",
    linkedMessageId: "msg_1",
    linkedMessageSnippet: "Chase Alert: $84.50 charged at WHOLE FOODS on card ending 4821",
    linkedSender: "CHASE",
    itemsBoughtSummary: "Organic Apples; Almond Milk; Salmon Fillet",
    billPaid: false,
    billReference: "",
    transactionDateMillis: 1761280000000,
    monthKey: "2026-10",
    notes: "Weekly grocery run",
    createdAt: nowTimestamp,
    updatedAt: nowTimestamp,
  };
}

test("Unauthenticated user cannot read or list transactions", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("transactions").get());
});

test("Authenticated user can create and read their own valid transaction", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const txRef = aliceDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_valid_1");
  await assertSucceeds(txRef.set(validTransactionPayload(ALICE_UID, new Date(Date.now() - 1000))));
  await assertSucceeds(txRef.get());
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("transactions").get());
});

test("Cross-user access: Bob cannot read or modify Alice's transactions", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context
      .firestore()
      .collection("users")
      .doc(ALICE_UID)
      .collection("transactions")
      .doc("tx_alice")
      .set(validTransactionPayload(ALICE_UID, new Date(Date.now() - 1000)));
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_alice").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("transactions").get()
  );
});

test("Shadow field injection on Transaction is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const txRef = aliceDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_shadow");
  const badPayload = {
    ...validTransactionPayload(ALICE_UID, new Date(Date.now() - 1000)),
    isAdminBypass: true,
  };
  await assertFails(txRef.set(badPayload));
});

test("Negative amountCents on Transaction is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const txRef = aliceDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_neg");
  const badPayload = {
    ...validTransactionPayload(ALICE_UID, new Date(Date.now() - 1000)),
    amountCents: -500,
  };
  await assertFails(txRef.set(badPayload));
});

test("Authenticated user can create and list PaymentMethods, Budgets, and ParsedMessages", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = new Date(Date.now() - 1000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("payment_methods").doc("pm_1").set({
      userId: ALICE_UID,
      name: "Sapphire Reserve",
      institution: "Chase",
      methodType: "CREDIT_CARD",
      lastFourDigits: "4821",
      networkOrRouting: "Visa",
      limitOrBalanceCents: 1500000,
      colorHex: "#0F4C81",
      defaultMethod: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("budgets").doc("b_1").set({
      userId: ALICE_UID,
      category: "Groceries",
      monthlyLimitCents: 60000,
      alertThresholdPercent: 80,
      monthKey: "2026-10",
      alertsEnabled: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("parsed_messages").doc("msg_1").set({
      userId: ALICE_UID,
      sourceType: "EMAIL",
      sender: "receipts@apple.com",
      subject: "Your receipt from Apple",
      body: "Billed $9.99 to Visa ••4821 for iCloud+ 200GB Storage Plan.",
      detectedAmountCents: 999,
      detectedMerchant: "Apple iCloud+",
      detectedCategory: "Subscriptions",
      detectedLastFour: "4821",
      detectedType: "BILL_PAID",
      detectedItems: "iCloud+ 200GB Monthly Plan",
      linkedTransactionId: "",
      approvalStatus: "PENDING",
      receivedAtMillis: 1761280000000,
      createdAt: ts,
      updatedAt: ts,
    })
  );
});
