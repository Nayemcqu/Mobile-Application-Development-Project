const functions = require("firebase-functions");
const admin = require("firebase-admin");
const express = require("express");
const cors = require("cors");

admin.initializeApp();
const db = admin.firestore();
const app = express();
app.use(cors({ origin: true }));
app.use(express.json());

/**
 * POST /register
 * Register a new user (Firebase Auth + Firestore)
 */
app.post("/register", async (req, res) => {
  const { name, email, password } = req.body;
  if (!email || !password || !name) {
    return res.status(400).json({ message: "Missing name, email or password" });
  }

  try {
    const userRecord = await admin.auth().createUser({ email, password, displayName: name });
    await db.collection("users").doc(userRecord.uid).set({ uid: userRecord.uid, name, email });
    return res.status(201).json({ message: "User registered successfully", uid: userRecord.uid });
  } catch (error) {
    return res.status(500).json({ message: "Registration failed", error: error.message });
  }
});

/**
 * GET /user/:uid
 * Fetch a single user by UID
 */
app.get("/user/:uid", async (req, res) => {
  const uid = req.params.uid;
  try {
    const doc = await db.collection("users").doc(uid).get();
    if (!doc.exists) return res.status(404).json({ message: "User not found" });
    return res.status(200).json({ user: doc.data() });
  } catch (error) {
    return res.status(500).json({ message: "Failed to fetch user", error: error.message });
  }
});

/**
 * GET /users-list
 * List all registered users
 */
app.get("/users-list", async (req, res) => {
  try {
    const snapshot = await db.collection("users").get();
    const users = snapshot.docs.map(doc => doc.data());
    return res.status(200).json({ users });
  } catch (error) {
    return res.status(500).json({ message: "Failed to fetch users", error: error.message });
  }
});

/**
 * POST /income-add
 */
app.post("/income-add", async (req, res) => {
  const { uid, amount, source } = req.body;
  if (!uid || !amount || !source) return res.status(400).json({ message: "Missing fields" });

  try {
    const ref = await db.collection("income").add({ uid, amount, source, createdAt: new Date() });
    res.status(201).json({ message: "Income added", id: ref.id });
  } catch (error) {
    res.status(500).json({ message: "Failed to add income", error: error.message });
  }
});

/**
 * GET /income-list
 */
app.get("/income-list", async (req, res) => {
  try {
    const snapshot = await db.collection("income").get();
    const data = snapshot.docs.map(doc => {
      const d = doc.data();
      return {
        id: doc.id,
        ...d,
        createdAt: d.createdAt?.toDate().toISOString() || null
      };
    });
    res.status(200).json(data);
  } catch (error) {
    res.status(500).json({ message: "Failed to list income", error: error.message });
  }
});

/**
 * POST /expense-add
 */
app.post("/expense-add", async (req, res) => {
  const { uid, amount, category, description } = req.body;
  if (!uid || !amount || !category || !description) return res.status(400).json({ message: "Missing fields" });

  try {
    const ref = await db.collection("expenses").add({ uid, amount, category, description, createdAt: new Date() });
    res.status(201).json({ message: "Expense added", id: ref.id });
  } catch (error) {
    res.status(500).json({ message: "Failed to add expense", error: error.message });
  }
});

/**
 * GET /expense-list
 */
app.get("/expense-list", async (req, res) => {
  try {
    const snapshot = await db.collection("expenses").get();
    const data = snapshot.docs.map(doc => {
      const d = doc.data();
      return {
        id: doc.id,
        ...d,
        createdAt: d.createdAt?.toDate().toISOString() || null
      };
    });
    res.status(200).json(data);
  } catch (error) {
    res.status(500).json({ message: "Failed to list expenses", error: error.message });
  }
});

/**
 * POST /budget-set
 */
app.post("/budget-set", async (req, res) => {
  const { uid, month, totalAmount } = req.body;
  if (!uid || !month || !totalAmount) return res.status(400).json({ message: "Missing fields" });

  try {
    await db.collection("budget").doc(`${uid}_${month}`).set({ uid, month, totalAmount, createdAt: new Date() });
    res.status(201).json({ message: "Budget set" });
  } catch (error) {
    res.status(500).json({ message: "Failed to set budget", error: error.message });
  }
});

/**
 * GET /budget-list
 */
app.get("/budget-list", async (req, res) => {
  try {
    const snapshot = await db.collection("budget").get();
    const data = snapshot.docs.map(doc => {
      const d = doc.data();
      return {
        ...d,
        createdAt: d.createdAt?.toDate().toISOString() || null
      };
    });
    res.status(200).json(data);
  } catch (error) {
    res.status(500).json({ message: "Failed to list budgets", error: error.message });
  }
});

/**
 * GET /insights
 */
app.get("/insights", (req, res) => {
  res.json({
    insights: [
      "Try reducing entertainment expenses by 15%.",
      "Your savings increased by 12% compared to last month.",
      "You exceeded your grocery budget by $50."
    ]
  });
});

/**
 * POST /report-upload
 */
app.post("/report-upload", async (req, res) => {
  const { uid, reportName } = req.body;
  if (!uid || !reportName) return res.status(400).json({ message: "Missing fields" });

  try {
    const ref = await db.collection("report").add({ uid, reportName, uploadedAt: new Date() });
    res.status(201).json({ message: "Report uploaded", id: ref.id });
  } catch (error) {
    res.status(500).json({ message: "Failed to upload report", error: error.message });
  }
});

/**
 * GET /report-list
 */
app.get("/report-list", async (req, res) => {
  try {
    const snapshot = await db.collection("report").get();
    const data = snapshot.docs.map(doc => {
      const d = doc.data();
      return {
        id: doc.id,
        ...d,
        uploadedAt: d.uploadedAt?.toDate().toISOString() || null
      };
    });
    res.status(200).json(data);
  } catch (error) {
    res.status(500).json({ message: "Failed to list reports", error: error.message });
  }
});

// Export the app
exports.api = functions.https.onRequest(app);
