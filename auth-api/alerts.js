/**
 * Full Firebase Functions: Alerts & Notifications for GenAI Expense Tracker
 * Includes:
 * - Realtime: Overspending, New Category, Category Spike, Income Drop (updated)
 * - Scheduled: Budget Breach, Cleanup
 */

const { onDocumentCreated, onDocumentDeleted } = require("firebase-functions/v2/firestore");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { logger } = require("firebase-functions");
const admin = require("firebase-admin");
const crypto = require("crypto");

const db = admin.firestore();
const messaging = admin.messaging();

/** Utility: Generate SHA-256 hash for deduplication */
function generateHash(title, message, dateKey) {
  return crypto.createHash("sha256").update(title + message + dateKey).digest("hex").substring(0, 16);
}

/** Utility: Send FCM notification to user */
async function sendAlertFCM(uid, title, message, category) {
  try {
    const userDoc = await db.collection("users").doc(uid).get();
    const fcmToken = userDoc.get("fcmToken");
    if (fcmToken) {
      await messaging.send({
        token: fcmToken,
        notification: { title, body: message },
        data: { type: "Alert", title, message, category },
      });
    }
  } catch (e) {
    logger.error("Failed to send FCM", e);
  }
}

/** 1. Realtime: High Spending Alert */
exports.checkOverspending = onDocumentCreated("users/{uid}/expenses/{expenseId}", async (event) => {
  try {
    const snap = event.data;
    const context = event.params;
    const data = snap.data();

    if (!data?.amount || !data.category || !data.timestamp) {
      logger.info("Overspending: Missing required fields");
      return;
    }

    const amount = data.amount;
    const category = data.category;
    const timestamp = data.timestamp.toDate ? data.timestamp.toDate() : new Date(data.timestamp.seconds * 1000);

    const fourWeeksAgo = new Date(Date.now() - 28 * 86400000);
    const categorySnap = await db.collection("users").doc(context.uid).collection("expenses")
      .where("category", "==", category)
      .where("timestamp", ">=", fourWeeksAgo).get();

    if (categorySnap.size < 2) {
      logger.info(`Overspending: Not enough history for category ${category}`);
      return;
    }

    const avg = categorySnap.docs.reduce((sum, d) => sum + d.data().amount, 0) / categorySnap.size;
    if (amount < avg * 1.4) {
      logger.info("Overspending: Below threshold");
      return;
    }

    const title = `High Spending on ${category}`;
    const message = `You spent $${amount} on ${category}. Your 4-week avg is ~$${avg.toFixed(2)}.`;
    const hash = generateHash(title, message, timestamp.toISOString().slice(0, 10));

    const insightsRef = db.collection("users").doc(context.uid).collection("insights");
    const duplicate = await insightsRef.where("messageHash", "==", hash).limit(1).get();
    if (!duplicate.empty) return;

    await insightsRef.add({
      type: "Alert",
      title,
      message,
      category,
      reason: "Your recent expense is significantly above your average.",
      messageHash: hash,
      timestamp: admin.firestore.Timestamp.now(),
      read: false,
    });

    await sendAlertFCM(context.uid, title, message, category);
  } catch (e) {
    logger.error("Overspending: Error", e);
  }
});

/** 2. Realtime: Income Drop Alert */
exports.checkIncomeDropRealtime = onDocumentCreated("users/{uid}/income/{incomeId}", async (event) => {
  try {
    const snap = event.data;
    const context = event.params;
    const data = snap.data();

    if (!data?.amount || !data.timestamp) return;

    const newAmount = data.amount;
    const timestamp = data.timestamp.toDate ? data.timestamp.toDate() : new Date(data.timestamp.seconds * 1000);

    const recentSnap = await db.collection("users").doc(context.uid).collection("income")
      .orderBy("timestamp", "desc").limit(4).get();

    if (recentSnap.size < 2) return;

    const pastEntries = recentSnap.docs.filter(doc => doc.id !== event.params.incomeId);
    const avg = pastEntries.reduce((sum, d) => sum + d.data().amount, 0) / pastEntries.length;

    if (avg === 0 || newAmount >= avg * 0.5) return;

    const title = "Income Drop Alert";
    const message = `Your new income of $${newAmount.toFixed(2)} is less than half your recent average of ~$${avg.toFixed(2)}.`;
    const hash = generateHash(title, message, timestamp.toISOString().slice(0, 10));

    const insightsRef = db.collection("users").doc(context.uid).collection("insights");
    const duplicate = await insightsRef.where("messageHash", "==", hash).limit(1).get();
    if (!duplicate.empty) return;

    await insightsRef.add({
      type: "Alert",
      title,
      message,
      category: "Income",
      reason: "Latest income is significantly lower than your previous average.",
      messageHash: hash,
      timestamp: admin.firestore.Timestamp.now(),
      read: false,
    });

    await sendAlertFCM(context.uid, title, message, "Income");
  } catch (e) {
    logger.error("Income Drop Realtime: Error", e);
  }
});

/** 3. Realtime: Category Spike Alert */
exports.checkCategorySpike = onDocumentCreated("users/{uid}/expenses/{expenseId}", async (event) => {
  const snap = event.data;
  const context = event.params;
  const data = snap.data();
  if (!data?.amount || !data.category || !data.timestamp) return;

  const { category, amount } = data;
  const sevenDaysAgo = new Date(Date.now() - 7 * 86400000);

  const snapCat = await db.collection("users").doc(context.uid).collection("expenses")
    .where("category", "==", category)
    .where("timestamp", ">=", sevenDaysAgo).get();

  if (snapCat.size < 2) return;

  const avg = snapCat.docs.reduce((sum, d) => sum + d.data().amount, 0) / snapCat.size;
  if (amount < avg * 2) return;

  const title = `Category Spike: ${category}`;
  const message = `You spent $${amount} on ${category}. 7-day avg: ~$${avg.toFixed(2)}.`;
  const hash = generateHash(title, message, data.timestamp.toDate().toISOString().slice(0, 10));

  const insightsRef = db.collection("users").doc(context.uid).collection("insights");
  const duplicate = await insightsRef.where("messageHash", "==", hash).limit(1).get();
  if (!duplicate.empty) return;

  await insightsRef.add({
    type: "Alert",
    title,
    message,
    category,
    reason: "This transaction is far above normal activity for this category.",
    messageHash: hash,
    timestamp: admin.firestore.Timestamp.now(),
    read: false
  });

  await sendAlertFCM(context.uid, title, message, category);
});

/** 4. Realtime: First-Time Category Expense */
exports.checkUnusualExpense = onDocumentCreated("users/{uid}/expenses/{expenseId}", async (event) => {
  const snap = event.data;
  const context = event.params;
  const data = snap.data();
  if (!data?.amount || !data.category || !data.timestamp) return;

  const { category, amount } = data;

  const existing = await db.collection("users").doc(context.uid).collection("expenses")
    .where("category", "==", category).limit(2).get();

  if (existing.size > 1) return;

  const title = `New Category: ${category}`;
  const message = `You spent $${amount} on \"${category}\" for the first time.`;
  const hash = generateHash(title, message, data.timestamp.toDate().toISOString().slice(0, 10));

  const insightsRef = db.collection("users").doc(context.uid).collection("insights");
  const duplicate = await insightsRef.where("messageHash", "==", hash).limit(1).get();
  if (!duplicate.empty) return;

  await insightsRef.add({
    type: "Alert",
    title,
    message,
    category,
    reason: "You've never spent in this category before.",
    messageHash: hash,
    timestamp: admin.firestore.Timestamp.now(),
    read: false
  });

  await sendAlertFCM(context.uid, title, message, category);
});

/** 5. Monthly: Budget Breach Detection */
exports.checkBudgetBreach = onSchedule({ schedule: "0 9 1 * *", timeZone: "Australia/Sydney" }, async () => {
  const usersSnap = await db.collection("users").get();
  for (const user of usersSnap.docs) {
    const uid = user.id;
    const now = new Date();
    const monthStart = new Date(now.getFullYear(), now.getMonth() - 1, 1);
    const monthEnd = new Date(now.getFullYear(), now.getMonth(), 0, 23, 59, 59);

    const incomeSnap = await db.collection("users").doc(uid).collection("income")
      .where("timestamp", ">=", monthStart).where("timestamp", "<=", monthEnd).get();
    const expenseSnap = await db.collection("users").doc(uid).collection("expenses")
      .where("timestamp", ">=", monthStart).where("timestamp", "<=", monthEnd).get();

    const totalIncome = incomeSnap.docs.reduce((sum, d) => sum + d.data().amount, 0);
    const totalExpense = expenseSnap.docs.reduce((sum, d) => sum + d.data().amount, 0);

    if (totalIncome === 0 || totalExpense <= totalIncome) continue;

    const title = "Budget Breach Alert";
    const message = `You spent $${totalExpense.toFixed(2)} but earned only $${totalIncome.toFixed(2)} last month.`;
    const hash = generateHash(title, message, monthStart.toISOString().slice(0, 10));

    const insightsRef = db.collection("users").doc(uid).collection("insights");
    const duplicate = await insightsRef.where("messageHash", "==", hash).limit(1).get();
    if (!duplicate.empty) continue;

    await insightsRef.add({
      type: "Alert",
      title,
      message,
      category: "Budget",
      reason: "Your total expenses exceeded your income last month.",
      messageHash: hash,
      timestamp: admin.firestore.Timestamp.now(),
      read: false
    });

    await sendAlertFCM(uid, title, message, "Budget");
  }
});

/** 6. Daily: Clean alerts older than 14 days */
exports.cleanOldAlerts = onSchedule({ schedule: "0 2 * * *", timeZone: "Australia/Sydney" }, async () => {
  const usersSnap = await db.collection("users").get();
  const cutoff = admin.firestore.Timestamp.fromDate(new Date(Date.now() - 14 * 86400000));

  for (const user of usersSnap.docs) {
    const uid = user.id;
    const insightsRef = db.collection("users").doc(uid).collection("insights");

    const oldAlerts = await insightsRef
      .where("type", "==", "Alert")
      .where("timestamp", "<", cutoff)
      .get();

    const deletions = oldAlerts.docs.map(doc => doc.ref.delete());
    if (deletions.length > 0) {
      logger.log(`Cleaning ${deletions.length} old alerts for ${uid}`);
      await Promise.all(deletions);
    }
  }

  logger.log("Old alert cleanup complete.");
  return null;
});

/**
 * Realtime Alert: Negative Balance
 * Triggered on each new expense. Compares monthly income vs expense.
 * Fires alert instantly if expense causes total expenses > income for the month.
 */
exports.checkNegativeBalance = onDocumentCreated("users/{uid}/expenses/{expenseId}", async (event) => {
  const snap = event.data;
  const context = event.params;
  const data = snap.data();

  if (!data?.amount || !data.timestamp) return;

  const now = new Date();
  const monthStart = new Date(now.getFullYear(), now.getMonth(), 1);
  const monthEnd = new Date(now.getFullYear(), now.getMonth() + 1, 0, 23, 59, 59);

  const incomeSnap = await db.collection("users").doc(context.uid).collection("income")
    .where("timestamp", ">=", monthStart).where("timestamp", "<=", monthEnd).get();

  const expenseSnap = await db.collection("users").doc(context.uid).collection("expenses")
    .where("timestamp", ">=", monthStart).where("timestamp", "<=", monthEnd).get();

  const totalIncome = incomeSnap.docs.reduce((sum, d) => sum + d.data().amount, 0);
  const totalExpense = expenseSnap.docs.reduce((sum, d) => sum + d.data().amount, 0);

  if (totalIncome === 0 || totalExpense <= totalIncome) return;

  const title = "Negative Balance Alert";
  const message = `Your spending has exceeded income this month. Spent: $${totalExpense.toFixed(2)}, Earned: $${totalIncome.toFixed(2)}.`;
  const dateKey = monthStart.toISOString().slice(0, 10);
  const hash = generateHash(title, message, dateKey);

  const insightsRef = db.collection("users").doc(context.uid).collection("insights");
  const duplicate = await insightsRef.where("messageHash", "==", hash).limit(1).get();
  if (!duplicate.empty) return;

  await insightsRef.add({
    type: "Alert",
    title,
    message,
    category: "Budget",
    reason: "Your expenses this month have exceeded your income.",
    messageHash: hash,
    timestamp: admin.firestore.Timestamp.now(),
    read: false
  });

  await sendAlertFCM(context.uid, title, message, "Budget");
});

/**
 * Realtime Advice: Positive Balance Recovery
 * Triggered when new income is added that brings the monthly balance back to positive.
 */
exports.checkPositiveBalanceRecovery = onDocumentCreated("users/{uid}/income/{incomeId}", async (event) => {
  const snap = event.data;
  const context = event.params;
  const data = snap.data();

  if (!data?.amount || !data.timestamp) return;

  const now = new Date();
  const monthStart = new Date(now.getFullYear(), now.getMonth(), 1);
  const monthEnd = new Date(now.getFullYear(), now.getMonth() + 1, 0, 23, 59, 59);

  const incomeSnap = await db.collection("users").doc(context.uid).collection("income")
    .where("timestamp", ">=", monthStart).where("timestamp", "<=", monthEnd).get();

  const expenseSnap = await db.collection("users").doc(context.uid).collection("expenses")
    .where("timestamp", ">=", monthStart).where("timestamp", "<=", monthEnd).get();

  const totalIncome = incomeSnap.docs.reduce((sum, d) => sum + d.data().amount, 0);
  const totalExpense = expenseSnap.docs.reduce((sum, d) => sum + d.data().amount, 0);

  if (totalIncome <= totalExpense) return; // Still negative or break-even

  // Income growth check: compare with recent average (excluding current)
  const recentIncomeSnap = await db.collection("users").doc(context.uid).collection("income")
    .orderBy("timestamp", "desc").limit(4).get();

  const past = recentIncomeSnap.docs.filter(d => d.id !== snap.id);
  const avgIncome = past.reduce((sum, d) => sum + d.data().amount, 0) / Math.max(past.length, 1);

  // Dynamic message generation
  const title = totalIncome > avgIncome * 1.3
    ? "Strong Financial Recovery!"
    : "Balance Back to Positive!";

  const message = totalIncome > avgIncome * 1.3
    ? `Awesome! Your income ($${totalIncome.toFixed(2)}) is up significantly and exceeds your expenses ($${totalExpense.toFixed(2)}). Keep it up!`
    : `Great job! Your income ($${totalIncome.toFixed(2)}) has exceeded expenses ($${totalExpense.toFixed(2)}) for this month.`;

  const dateKey = monthStart.toISOString().slice(0, 10);
  const hash = generateHash(title, message, dateKey);

  const insightsRef = db.collection("users").doc(context.uid).collection("insights");

  // Only trigger if a negative balance alert previously existed
  const negativeAlertExists = await insightsRef
    .where("title", "==", "Negative Balance Alert")
    .where("timestamp", ">=", monthStart)
    .get();

  if (negativeAlertExists.empty) return;

  // Prevent duplicate advice entries
  const duplicate = await insightsRef.where("messageHash", "==", hash).limit(1).get();
  if (!duplicate.empty) return;

  // Add advice insight
  await insightsRef.add({
    type: "Advice",
    title,
    message,
    category: "Budget",
    reason: "Your income now exceeds your expenses this month.",
    messageHash: hash,
    timestamp: admin.firestore.Timestamp.now(),
    read: false
  });

  // Send FCM notification with Advice type
  await sendAlertFCM(context.uid, title, message, "Budget", "Advice");
});

/**
 * Re-evaluate insights when an expense is deleted:
 * - If new totalExpense < totalIncome → remove Negative Balance Alert
 * - If positive balance → optionally show Advice
 */
exports.recheckAfterExpenseDelete = onDocumentDeleted("users/{uid}/expenses/{expenseId}", async (event) => {
  const context = event.params;
  const uid = context.uid;

  const now = new Date();
  const monthStart = new Date(now.getFullYear(), now.getMonth(), 1);
  const monthEnd = new Date(now.getFullYear(), now.getMonth() + 1, 0, 23, 59, 59);

  const incomeSnap = await db.collection("users").doc(uid).collection("income")
    .where("timestamp", ">=", monthStart).where("timestamp", "<=", monthEnd).get();
  const expenseSnap = await db.collection("users").doc(uid).collection("expenses")
    .where("timestamp", ">=", monthStart).where("timestamp", "<=", monthEnd).get();

  const totalIncome = incomeSnap.docs.reduce((sum, d) => sum + d.data().amount, 0);
  const totalExpense = expenseSnap.docs.reduce((sum, d) => sum + d.data().amount, 0);

  const insightsRef = db.collection("users").doc(uid).collection("insights");

  // Delete Negative Balance Alert if not valid anymore
  if (totalExpense <= totalIncome) {
    const negAlerts = await insightsRef
      .where("title", "==", "Negative Balance Alert")
      .where("timestamp", ">=", monthStart)
      .get();
    for (const doc of negAlerts.docs) {
      await doc.ref.delete();
    }

    // Check if advice already exists
    const title = "Balance Back to Positive!";
    const message = `Your income ($${totalIncome.toFixed(2)}) has exceeded expenses ($${totalExpense.toFixed(2)}) for this month.`;
    const hash = generateHash(title, message, monthStart.toISOString().slice(0, 10));
    const duplicate = await insightsRef.where("messageHash", "==", hash).limit(1).get();

    if (duplicate.empty) {
      await insightsRef.add({
        type: "Advice",
        title,
        message,
        category: "Budget",
        reason: "Your income now exceeds your expenses this month.",
        messageHash: hash,
        timestamp: admin.firestore.Timestamp.now(),
        read: false
      });
      await sendAlertFCM(uid, title, message, "Budget");
    }
  }
});

/**
 * Re-evaluate insights when income is deleted:
 * - If new totalExpense > totalIncome → show Negative Balance Alert
 * - Remove Positive Balance Advice if not valid anymore
 */
exports.recheckAfterIncomeDelete = onDocumentDeleted("users/{uid}/income/{incomeId}", async (event) => {
  const context = event.params;
  const uid = context.uid;

  const now = new Date();
  const monthStart = new Date(now.getFullYear(), now.getMonth(), 1);
  const monthEnd = new Date(now.getFullYear(), now.getMonth() + 1, 0, 23, 59, 59);

  const incomeSnap = await db.collection("users").doc(uid).collection("income")
    .where("timestamp", ">=", monthStart).where("timestamp", "<=", monthEnd).get();
  const expenseSnap = await db.collection("users").doc(uid).collection("expenses")
    .where("timestamp", ">=", monthStart).where("timestamp", "<=", monthEnd).get();

  const totalIncome = incomeSnap.docs.reduce((sum, d) => sum + d.data().amount, 0);
  const totalExpense = expenseSnap.docs.reduce((sum, d) => sum + d.data().amount, 0);

  const insightsRef = db.collection("users").doc(uid).collection("insights");

  // Remove any existing Advice if now invalid
  if (totalIncome <= totalExpense) {
    const advice = await insightsRef
      .where("type", "==", "Advice")
      .where("title", "in", ["Balance Back to Positive!", "Strong Financial Recovery!"])
      .where("timestamp", ">=", monthStart)
      .get();
    for (const doc of advice.docs) {
      await doc.ref.delete();
    }

    // Show new Alert if not already shown
    const title = "Negative Balance Alert";
    const message = `Your spending has exceeded income this month. Spent: $${totalExpense.toFixed(2)}, Earned: $${totalIncome.toFixed(2)}.`;
    const hash = generateHash(title, message, monthStart.toISOString().slice(0, 10));
    const duplicate = await insightsRef.where("messageHash", "==", hash).limit(1).get();

    if (duplicate.empty) {
      await insightsRef.add({
        type: "Alert",
        title,
        message,
        category: "Budget",
        reason: "Your expenses this month have exceeded your income.",
        messageHash: hash,
        timestamp: admin.firestore.Timestamp.now(),
        read: false
      });
      await sendAlertFCM(uid, title, message, "Budget");
    }
  }
});

