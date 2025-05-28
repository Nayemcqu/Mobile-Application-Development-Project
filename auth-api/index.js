/**
 * GenAI Expense Tracker - Firebase Functions Entry Point
 * Combines Express HTTP routes and smart financial alert triggers
 * All insights now triggered ONLY by Firebase automated logic (no manual /notify-insight)
 */

const functions = require("firebase-functions");
const admin = require("firebase-admin");
const express = require("express");
const cors = require("cors");

admin.initializeApp();

const db = admin.firestore();
const messaging = admin.messaging();
const alerts = require("./alerts");

const app = express();
app.use(cors({ origin: true }));
app.use(express.json());

/**
 * POST /register - Register a new user in Firebase Auth + Firestore
 */
app.post("/register", async (req, res) => {
  const { name, email, password } = req.body;
  if (!name || !email || !password) {
    return res.status(400).json({ message: "Missing name, email or password" });
  }

  try {
    const userRecord = await admin.auth().createUser({
      email,
      password,
      displayName: name,
    });

    await db.collection("users").doc(userRecord.uid).set({
      uid: userRecord.uid,
      name,
      email,
      profileImage: "",
    });

    return res.status(201).json({ message: "User registered successfully", uid: userRecord.uid });
  } catch (error) {
    return res.status(500).json({ message: "Registration failed", error: error.message });
  }
});

/**
 * GET /users-list - Fetch all user profiles
 */
app.get("/users-list", async (req, res) => {
  try {
    const snapshot = await db.collection("users").get();
    const users = snapshot.docs.map((doc) => doc.data());
    return res.status(200).json({ users });
  } catch (error) {
    return res.status(500).json({ message: "Failed to fetch users", error: error.message });
  }
});

/**
 * GET /user-by-email/:email/income - Fetch income by user email
 */
app.get("/user-by-email/:email/income", async (req, res) => {
  const email = req.params.email;
  try {
    const snapshot = await db.collection("users").where("email", "==", email).limit(1).get();
    if (snapshot.empty) return res.status(404).json({ message: "User not found" });

    const uid = snapshot.docs[0].id;
    const incomeSnap = await db.collection("users").doc(uid).collection("income").get();
    const data = incomeSnap.docs.map((doc) => ({ id: doc.id, ...doc.data() }));

    return res.status(200).json({ income: data });
  } catch (error) {
    return res.status(500).json({ message: "Failed to fetch income", error: error.message });
  }
});

/**
 * GET /user-by-email/:email/expenses - Fetch expenses by user email
 */
app.get("/user-by-email/:email/expenses", async (req, res) => {
  const email = req.params.email;
  try {
    const snapshot = await db.collection("users").where("email", "==", email).limit(1).get();
    if (snapshot.empty) return res.status(404).json({ message: "User not found" });

    const uid = snapshot.docs[0].id;
    const expenseSnap = await db.collection("users").doc(uid).collection("expenses").get();
    const data = expenseSnap.docs.map((doc) => ({ id: doc.id, ...doc.data() }));

    return res.status(200).json({ expenses: data });
  } catch (error) {
    return res.status(500).json({ message: "Failed to fetch expenses", error: error.message });
  }
});

/**
 * GET /user-by-email/:email/insights - Fetch all Alert-type insights for user
 */
app.get("/user-by-email/:email/insights", async (req, res) => {
  const email = req.params.email;
  try {
    const snapshot = await db.collection("users").where("email", "==", email).limit(1).get();
    if (snapshot.empty) return res.status(404).json({ message: "User not found" });

    const uid = snapshot.docs[0].id;
    const insightsSnap = await db.collection("users").doc(uid).collection("insights")
      .where("type", "==", "Alert")
      .orderBy("timestamp", "desc").get();

    const data = insightsSnap.docs.map((doc) => ({ id: doc.id, ...doc.data() }));
    return res.status(200).json({ insights: data });
  } catch (error) {
    return res.status(500).json({ message: "Failed to fetch insights", error: error.message });
  }
});

/**
 * GET /latest-insights/:email - Fetch most recent Alert-type insight only
 */
app.get("/latest-insights/:email", async (req, res) => {
  const email = req.params.email;
  try {
    const snapshot = await db.collection("users").where("email", "==", email).limit(1).get();
    if (snapshot.empty) return res.status(404).json({ message: "User not found" });

    const uid = snapshot.docs[0].id;

    const alertSnap = await db.collection("users").doc(uid).collection("insights")
      .where("type", "==", "Alert")
      .orderBy("timestamp", "desc")
      .limit(1)
      .get();

    const alert = alertSnap.empty ? null : { id: alertSnap.docs[0].id, ...alertSnap.docs[0].data() };
    return res.status(200).json({ alert });

  } catch (error) {
    return res.status(500).json({ message: "Failed to fetch latest alert", error: error.message });
  }
});

// Expose Express API
exports.api = functions.https.onRequest(app);

// === REGISTER SMART ALERT CLOUD FUNCTIONS ===

/**
 * Triggered on new expense creation.
 * Checks if expense is 40% above 4-week average in the same category.
 */
exports.checkOverspending = alerts.checkOverspending;


/**
 * Triggered on new expense creation.
 * Checks if the amount is double the 7-day category average.
 */
exports.checkCategorySpike = alerts.checkCategorySpike;

/**
 * Triggered on new expense creation.
 * Sends alert if user has never used this category before.
 */
exports.checkUnusualExpense = alerts.checkUnusualExpense;

/**
 * Scheduled on 1st of every month at 9:00 AM.
 * Compares last month's total expense vs income and alerts if breached.
 */
exports.checkBudgetBreach = alerts.checkBudgetBreach;

/**
 * Scheduled daily at 2:00 AM to clean up alerts older than 14 days.
 * Keeps the insights collection optimized and avoids clutter.
 */
exports.cleanOldAlerts = alerts.cleanOldAlerts;

/**
 * Triggered on new income document creation.
 * Sends alert if income is significantly lower than recent average.
 */
exports.checkIncomeDropRealtime = alerts.checkIncomeDropRealtime;

/**
 * Triggered on new expense creation.
 * Sends alert if total monthly spending exceeds income (Negative Balance).
 */
exports.checkNegativeBalance = alerts.checkNegativeBalance;

/**
 * Triggered on new income creation.
 * Sends advice if user's monthly balance turns positive after being negative.
 */
exports.checkPositiveBalanceRecovery = alerts.checkPositiveBalanceRecovery;

/**
 * Triggered on income deletion.
 * Re-evaluates monthly balance to determine if alert/advice needs to change.
 */
exports.recheckAfterIncomeDelete = alerts.recheckAfterIncomeDelete;

/**
 * Triggered on expense deletion.
 * Re-evaluates monthly balance to determine if alert/advice needs to change.
 */
exports.recheckAfterExpenseDelete = alerts.recheckAfterExpenseDelete;
