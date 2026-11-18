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

function pastTimestamp() {
  return new Date(Date.now() - 5000);
}

function validUserProfile(uid = ALICE_UID, ts = pastTimestamp()) {
  return {
    userId: uid,
    displayName: "Ahmed Elbagorry",
    email: "ahmed@example.com",
    currencyCode: "EGP",
    themePreset: "EMERALD_OBSIDIAN",
    lastSyncedAtMillis: 1760000000000,
    createdAt: ts,
    updatedAt: ts,
  };
}

function validAccount(ts = pastTimestamp()) {
  return {
    localId: 1,
    name: "Bank Account",
    accountType: "BANK",
    openingBalancePiasters: 100000,
    currentBalancePiasters: 150000,
    currency: "EGP",
    colorHex: "#10B981",
    iconName: "bank",
    isArchived: false,
    createdAt: ts,
    updatedAt: ts,
  };
}

function validTransaction(ts = pastTimestamp()) {
  return {
    localId: 10,
    amountPiasters: 25000,
    transactionType: "EXPENSE",
    accountLocalId: 1,
    targetAccountLocalId: null,
    categoryLocalId: 3,
    description: "Coffee Shop",
    notes: "Morning espresso",
    source: "MANUAL_ENTRY",
    referenceNumber: null,
    timestampMillis: 1760000000000,
    createdAt: ts,
    updatedAt: ts,
  };
}

function validChatSession(chatId = "chat_1", ts = pastTimestamp()) {
  return {
    sessionId: chatId,
    title: "مراجعة مصروفات الشهر",
    lastMessagePreview: "تم تسجيل 50 جنيه أكل",
    messageCount: 2,
    createdAtMillis: 1760000000000,
    updatedAtMillis: 1760000005000,
    createdAt: ts,
    updatedAt: ts,
  };
}

function validChatMessage(chatId = "chat_1", messageId = "msg_1", isUser = true, ts = pastTimestamp()) {
  return {
    messageId,
    sessionId: chatId,
    isUser,
    text: isUser ? "صرفت 50 جنيه أكل" : "تم التسجيل محليًا بدون إنترنت.",
    isError: false,
    isUndone: false,
    engineSource: isUser ? "CLOUD_GEMINI" : "LOCAL_OFFLINE",
    executedActionsJson: "[]",
    timestampMillis: 1760000001000,
    createdAt: ts,
    updatedAt: ts,
  };
}

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

test("Unauthenticated user: cannot read or write any user profile or subcollection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).set(validUserProfile()));
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("accounts").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("transactions").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("budgets").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("goals").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("merchant_rules").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("ai_chats").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("ai_chats").doc("chat_1").collection("messages").get());
  await assertFails(unauthDb.collection("arbitrary").doc("x").get());
});

test("Cross-user isolation: Bob cannot read, write, update, or delete Alice's financial data", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(ALICE_UID).set(validUserProfile(ALICE_UID));
    await db.collection("users").doc(ALICE_UID).collection("accounts").doc("acc_1").set(validAccount());
    await db.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_10").set(validTransaction());
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("accounts").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("accounts").doc("acc_1").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_10").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_99").set(validTransaction()));
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("accounts").doc("acc_1").delete());
});

test("Shadow update & schema validation: rejects ghost fields, invalid currency, negative tx amount, future timestamp, and immutable field mutation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  // Ghost field in UserProfile
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      ...validUserProfile(),
      isAdmin: true,
    })
  );

  // Invalid currency enum
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      ...validUserProfile(),
      currencyCode: "XYZ",
    })
  );

  // Future timestamp rejected (ts > request.time)
  const futureTs = new Date(Date.now() + 60 * 60 * 1000);
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set(validUserProfile(ALICE_UID, futureTs))
  );

  // Negative transaction amount rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_bad").set({
      ...validTransaction(),
      amountPiasters: -100,
    })
  );

  // Create valid account, then attempt immutable createdAt or localId change
  const createdTs = pastTimestamp();
  const accRef = aliceDb.collection("users").doc(ALICE_UID).collection("accounts").doc("acc_1");
  await assertSucceeds(accRef.set(validAccount(createdTs)));

  await assertFails(
    accRef.update({
      localId: 999,
      updatedAt: pastTimestamp(),
    })
  );
  await assertFails(
    accRef.update({
      createdAt: new Date(Date.now() - 20000),
      updatedAt: pastTimestamp(),
    })
  );
});

test("Owner CRUD: Alice can create, read, list, update, and delete her own profile, accounts, categories, transactions, budgets, goals, and merchant rules", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = pastTimestamp();
  const userRef = aliceDb.collection("users").doc(ALICE_UID);

  // Profile create & update
  await assertSucceeds(userRef.set(validUserProfile(ALICE_UID, ts)));
  await assertSucceeds(userRef.get());
  await assertSucceeds(userRef.update({ currencyCode: "USD", updatedAt: pastTimestamp() }));

  // Account create, list, update
  const accRef = userRef.collection("accounts").doc("acc_1");
  await assertSucceeds(accRef.set(validAccount(ts)));
  await assertSucceeds(userRef.collection("accounts").get());
  await assertSucceeds(accRef.update({ currentBalancePiasters: 200000, updatedAt: pastTimestamp() }));

  // Category create
  await assertSucceeds(
    userRef.collection("categories").doc("cat_1").set({
      localId: 1,
      nameAr: "طعام",
      nameEn: "Food",
      transactionType: "EXPENSE",
      iconName: "restaurant",
      colorHex: "#F59E0B",
      isDefault: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Transaction create, update, list
  const txRef = userRef.collection("transactions").doc("tx_10");
  await assertSucceeds(txRef.set(validTransaction(ts)));
  await assertSucceeds(userRef.collection("transactions").get());
  await assertSucceeds(txRef.update({ description: "Updated Coffee", updatedAt: pastTimestamp() }));

  // Budget create
  await assertSucceeds(
    userRef.collection("budgets").doc("bud_1").set({
      localId: 1,
      name: "Monthly Budget",
      categoryLocalId: null,
      limitPiasters: 500000,
      yearMonth: "2026-11",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Goal create
  await assertSucceeds(
    userRef.collection("goals").doc("goal_1").set({
      localId: 1,
      name: "Emergency Fund",
      presetType: "EMERGENCY",
      targetAmountPiasters: 1000000,
      savedAmountPiasters: 250000,
      status: "ACTIVE",
      colorHex: "#10B981",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Merchant Rule create & delete
  const ruleRef = userRef.collection("merchant_rules").doc("rule_1");
  await assertSucceeds(
    ruleRef.set({
      localId: 1,
      keyword: "Uber",
      normalizedKeyword: "uber",
      categoryLocalId: 2,
      accountLocalId: 1,
      preferredType: "EXPENSE",
      hitCount: 3,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(ruleRef.delete());
});

test("AI Chats & Messages: enforces owner isolation, parent existence, valid engineSource, immutable metadata, and allows atomic batch CRUD", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastTimestamp();

  const chatRef = aliceDb.collection("users").doc(ALICE_UID).collection("ai_chats").doc("chat_1");
  const msg1Ref = chatRef.collection("messages").doc("msg_1");
  const msg2Ref = chatRef.collection("messages").doc("msg_2");

  // Orphan message without existing parent session fails
  await assertFails(msg1Ref.set(validChatMessage("chat_1", "msg_1", true, ts)));

  // Atomic batch write creating session + messages succeeds via existsAfter()
  const batch = aliceDb.batch();
  batch.set(chatRef, validChatSession("chat_1", ts));
  batch.set(msg1Ref, validChatMessage("chat_1", "msg_1", true, ts));
  batch.set(msg2Ref, validChatMessage("chat_1", "msg_2", false, ts));
  await assertSucceeds(batch.commit());

  // Invalid engineSource or extra ghost field fails
  await assertFails(
    chatRef.collection("messages").doc("msg_bad").set({
      ...validChatMessage("chat_1", "msg_bad", false, ts),
      engineSource: "UNKNOWN_MODEL",
    })
  );
  await assertFails(
    chatRef.collection("messages").doc("msg_ghost").set({
      ...validChatMessage("chat_1", "msg_ghost", false, ts),
      extraAdmin: true,
    })
  );

  // Future timestamp rejected
  const futureTs = new Date(Date.now() + 60 * 60 * 1000);
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("ai_chats").doc("chat_future").set(validChatSession("chat_future", futureTs))
  );

  // Immutable sessionId or isUser change rejected on update
  await assertFails(msg2Ref.update({ isUser: true, updatedAt: pastTimestamp() }));
  await assertFails(chatRef.update({ createdAtMillis: 999, updatedAt: pastTimestamp() }));

  // Valid owner update (e.g., marking actions undone or updating session title)
  await assertSucceeds(msg2Ref.update({ isUndone: true, updatedAt: pastTimestamp() }));
  await assertSucceeds(chatRef.update({ title: "تحليل مصاريف الطعام", updatedAt: pastTimestamp() }));

  // Cross-user isolation: Bob cannot read or write Alice's AI chats/messages
  const bobChatRef = bobDb.collection("users").doc(ALICE_UID).collection("ai_chats").doc("chat_1");
  await assertFails(bobChatRef.get());
  await assertFails(bobChatRef.collection("messages").get());
  await assertFails(bobChatRef.collection("messages").doc("msg_3").set(validChatMessage("chat_1", "msg_3", true, ts)));

  // Owner delete
  await assertSucceeds(msg1Ref.delete());
  await assertSucceeds(msg2Ref.delete());
  await assertSucceeds(chatRef.delete());
});

test("Extended Sync Collections (installments, recurring_rules, debts, bnpl_programs, financial_sources, savings_certificates): enforces owner CRUD, cross-user denial, and strict schema validation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const ts = pastTimestamp();
  const aliceUserRef = aliceDb.collection("users").doc(ALICE_UID);

  // Unauthenticated read denied
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("installments").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("savings_certificates").get());

  // 1. Installments
  const instRef = aliceUserRef.collection("installments").doc("inst_1");
  await assertSucceeds(instRef.set({
    localId: 1,
    name: "iPhone Installment",
    creditor: "ValU",
    accountLocalId: 1,
    totalAmountPiasters: 3000000,
    installmentAmountPiasters: 250000,
    totalCount: 12,
    paidCount: 2,
    nextDueDateMillis: 1765000000000,
    frequency: "MONTHLY",
    status: "ACTIVE",
    bnplProgramId: null,
    principalAmountPiasters: 2500000,
    createdAt: ts,
    updatedAt: ts,
  }));
  await assertSucceeds(instRef.update({ paidCount: 3, updatedAt: pastTimestamp() }));
  await assertFails(instRef.update({ localId: 99, updatedAt: pastTimestamp() }));

  // 2. Recurring rules
  const recRef = aliceUserRef.collection("recurring_rules").doc("rec_1");
  await assertSucceeds(recRef.set({
    localId: 1,
    title: "Monthly Salary",
    transactionType: "INCOME",
    incomeSourceType: "MONTHLY_SALARY",
    amountPiasters: 1500000,
    accountLocalId: 1,
    categoryLocalId: 1,
    frequency: "MONTHLY",
    nextRunDateMillis: 1765000000000,
    notifyDaysBefore: 2,
    isActive: true,
    createdAt: ts,
    updatedAt: ts,
  }));

  // 3. Debts
  const debtRef = aliceUserRef.collection("debts").doc("debt_1");
  await assertSucceeds(debtRef.set({
    localId: 1,
    personName: "Karim",
    direction: "OWED_TO_ME",
    totalAmountPiasters: 50000,
    paidAmountPiasters: 10000,
    dueDateMillis: null,
    status: "PARTIAL",
    notes: "Loan",
    createdAt: ts,
    updatedAt: ts,
  }));

  // 4. BNPL Programs
  const bnplRef = aliceUserRef.collection("bnpl_programs").doc("bnpl_1");
  await assertSucceeds(bnplRef.set({
    localId: 1,
    providerCode: "VALU",
    nameAr: "فاليو",
    nameEn: "ValU",
    creditLimitPiasters: 5000000,
    manualUsedPiasters: 0,
    defaultAnnualInterestPercent: 18.5,
    colorHex: "#38BDF8",
    isEnabled: true,
    notes: "",
    createdAt: ts,
    updatedAt: ts,
  }));

  // 5. Financial Sources
  const srcRef = aliceUserRef.collection("financial_sources").doc("src_1");
  await assertSucceeds(srcRef.set({
    localId: 1,
    code: "NBE",
    displayNameAr: "البنك الأهلي المصري",
    displayNameEn: "NBE",
    senderKeywords: "NBE,الأهلي",
    packageNames: "com.ahly.app",
    defaultAccountLocalId: 1,
    isEnabled: true,
    isIgnored: false,
    createdAt: ts,
    updatedAt: ts,
  }));

  // 6. Savings Certificates & Gold Reserves (including float grams + karat check)
  const certRef = aliceUserRef.collection("savings_certificates").doc("cert_1");
  await assertSucceeds(certRef.set({
    localId: 1,
    title: "شهادة بلاتينية 27%",
    assetKind: "BANK_CERTIFICATE",
    institutionName: "البنك الأهلي",
    principalPiasters: 10000000,
    annualYieldPercent: 27.0,
    payoutCycle: "MONTHLY",
    linkedAccountLocalId: 1,
    nextPayoutMillis: 1765000000000,
    maturityMillis: null,
    autoCreditInterest: true,
    grams: 0.0,
    karat: 21,
    pricePerGramPiasters: 0,
    notes: "",
    isActive: true,
    createdAt: ts,
    updatedAt: ts,
  }));

  // Reject invalid assetKind or extra ghost field
  await assertFails(aliceUserRef.collection("savings_certificates").doc("cert_bad").set({
    localId: 2,
    title: "Crypto",
    assetKind: "CRYPTO",
    institutionName: "",
    principalPiasters: 1000,
    annualYieldPercent: 0,
    payoutCycle: "MONTHLY",
    nextPayoutMillis: 1765000000000,
    autoCreditInterest: false,
    grams: 0,
    karat: 21,
    pricePerGramPiasters: 0,
    notes: "",
    isActive: true,
    createdAt: ts,
    updatedAt: ts,
  }));

  // Cross-user isolation: Bob denied on Alice's new collections
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("savings_certificates").doc("cert_1").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("debts").doc("debt_1").delete());

  // Clean up by owner succeeds
  await assertSucceeds(certRef.delete());
  await assertSucceeds(instRef.delete());
});

