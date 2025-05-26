const functions = require("firebase-functions");
const admin = require("firebase-admin");
const express = require("express");
const cors = require("cors");

admin.initializeApp();
const db = admin.firestore();
const messaging = admin.messaging();
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
 * GET /users-list
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
 * GET /user-by-email/:email/income
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
 * GET /user-by-email/:email/expenses
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
 * GET /user-by-email/:email/insights
 */
app.get("/user-by-email/:email/insights", async (req, res) => {
  const email = req.params.email;
  try {
    const snapshot = await db.collection("users").where("email", "==", email).limit(1).get();
    if (snapshot.empty) return res.status(404).json({ message: "User not found" });

    const uid = snapshot.docs[0].id;
    const insightsSnap = await db
      .collection("users")
      .doc(uid)
      .collection("insights")
      .orderBy("timestamp", "desc")
      .get();

    const data = insightsSnap.docs.map((doc) => ({ id: doc.id, ...doc.data() }));
    return res.status(200).json({ insights: data });
  } catch (error) {
    return res.status(500).json({ message: "Failed to fetch insights", error: error.message });
  }
});

/**
 * GET /latest-insights/:uid - Only 1 alert and 1 advice (used on Home screen)
 */
app.get("/latest-insights/:uid", async (req, res) => {
  const uid = req.params.uid;

  try {
    const insightsRef = db.collection("users").doc(uid).collection("insights");

    const [alertSnap, adviceSnap] = await Promise.all([
      insightsRef.where("type", "==", "Alert").orderBy("timestamp", "desc").limit(1).get(),
      insightsRef.where("type", "==", "Advice").orderBy("timestamp", "desc").limit(1).get()
    ]);

    const alert = alertSnap.empty ? null : { id: alertSnap.docs[0].id, ...alertSnap.docs[0].data() };
    const advice = adviceSnap.empty ? null : { id: adviceSnap.docs[0].id, ...adviceSnap.docs[0].data() };

    return res.status(200).json({ alert, advice });

  } catch (error) {
    return res.status(500).json({ message: "Failed to fetch latest insights", error: error.message });
  }
});

/**
 * POST /notify-insight - Deduplicates + notifies + auto-cleans insights
 */
app.post("/notify-insight", async (req, res) => {
  const { uid, title, message, type, category, messageHash } = req.body;

  if (!uid || !title || !message || !type) {
    return res.status(400).json({ message: "Missing uid, title, message, or type" });
  }

  try {
    const hashToUse = messageHash || require("crypto")
      .createHash("sha256")
      .update(title + message)
      .digest("hex")
      .substring(0, 16);

    const insightRef = db.collection("users").doc(uid).collection("insights");

    // Check for duplicate using messageHash
    const duplicateSnap = await insightRef.where("messageHash", "==", hashToUse).limit(1).get();
    if (!duplicateSnap.empty) {
      return res.status(200).json({ message: "Duplicate insight skipped." });
    }

    // Use precise timestamp (fixes Home screen issue)
    const timestampNow = admin.firestore.Timestamp.fromDate(new Date());

    // Save the new insight
    await insightRef.add({
      type,
      title,
      message,
      category: category || "General",
      messageHash: hashToUse,
      timestamp: timestampNow
    });

    // Send FCM notification if token exists
    const userDoc = await db.collection("users").doc(uid).get();
    const fcmToken = userDoc.get("fcmToken");
    if (fcmToken) {
      await messaging.send({
        token: fcmToken,
        notification: {
          title: type === "Alert" ? title : "Financial Advice",
          body: message
        },
        data: {
          type,
          title,
          message,
          category: category || "General"
        }
      });
    }

    // Auto-delete old insights older than 14 days
    const cutoff = admin.firestore.Timestamp.fromDate(
      new Date(Date.now() - 14 * 24 * 60 * 60 * 1000)
    );
    const oldInsights = await insightRef.where("timestamp", "<", cutoff).get();
    const deletions = oldInsights.docs.map(doc => doc.ref.delete());
    await Promise.all(deletions);

    return res.status(200).json({ message: "Insight saved and notified." });

  } catch (error) {
    console.error("Insight error:", error);
    return res.status(500).json({
      message: "Failed to save or notify insight",
      error: error.message
    });
  }
});


exports.api = functions.https.onRequest(app);
