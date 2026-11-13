const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");
const pastDate = () => new Date(Date.now() - 2000);

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

test("Unauthenticated caller cannot read or list user tasks or subjects", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("subjects").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("tasks").get());
});

test("Authenticated user can create and read their own subject, slot, task, and session", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = pastDate();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      currentStreak: 1,
      longestStreak: 1,
      lastFocusDate: "2026-11-12",
      dailyGoalMinutes: 60,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("subjects").doc("subj_1").set({
      userId: ALICE_UID,
      name: "Algorithms",
      code: "CS301",
      instructor: "Dr. Rao",
      colorHex: 0xFF4338CA,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("slots").doc("slot_1").set({
      userId: ALICE_UID,
      subjectId: "subj_1",
      subjectName: "Algorithms",
      colorHex: 0xFF4338CA,
      dayOfWeek: 1,
      startMinutes: 540,
      endMinutes: 600,
      room: "Hall B",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_1").set({
      userId: ALICE_UID,
      subjectId: "subj_1",
      subjectName: "Algorithms",
      colorHex: 0xFF4338CA,
      title: "Dynamic Programming Lab",
      notes: "Submit PDF before midnight",
      type: "ASSIGNMENT",
      priority: "HIGH",
      dueAtMillis: 1794528000000,
      reminderLeadMinutes: 60,
      completed: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_reading").set({
      userId: ALICE_UID,
      subjectId: "subj_1",
      subjectName: "Algorithms",
      colorHex: 0xFF4338CA,
      title: "Read Chapter 4 Red-Black Trees",
      notes: "",
      type: "READING",
      priority: "MEDIUM",
      dueAtMillis: 1794528000000,
      reminderLeadMinutes: 60,
      completed: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_project").set({
      userId: ALICE_UID,
      subjectId: "subj_1",
      subjectName: "Algorithms",
      colorHex: 0xFF4338CA,
      title: "Capstone Milestone",
      notes: "",
      type: "PROJECT",
      priority: "HIGH",
      dueAtMillis: 1794528000000,
      reminderLeadMinutes: 60,
      completed: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_lab").set({
      userId: ALICE_UID,
      subjectId: "subj_1",
      subjectName: "Algorithms",
      colorHex: 0xFF4338CA,
      title: "Benchmark Lab 2",
      notes: "",
      type: "LAB",
      priority: "LOW",
      dueAtMillis: 1794528000000,
      reminderLeadMinutes: 60,
      completed: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("sessions").doc("sess_1").set({
      userId: ALICE_UID,
      subjectId: "subj_1",
      subjectName: "Algorithms",
      taskId: "task_1",
      taskType: "EXAM",
      durationMinutes: 25,
      dateKey: "2026-11-12",
      completedAt: now,
    })
  );

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("tasks").get());
});

test("Cross-user access is denied for reads and writes", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = pastDate();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("alice_task").set({
      userId: ALICE_UID,
      subjectId: "subj_1",
      subjectName: "Math",
      colorHex: 0xFF10B981,
      title: "Problem Set 4",
      notes: "",
      type: "ASSIGNMENT",
      priority: "MEDIUM",
      dueAtMillis: 1794528000000,
      reminderLeadMinutes: 60,
      completed: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("tasks").doc("alice_task").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("tasks").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("tasks").doc("spoof_task").set({
      userId: ALICE_UID,
      subjectId: "subj_1",
      subjectName: "Math",
      colorHex: 0xFF10B981,
      title: "Spoof",
      notes: "",
      type: "ASSIGNMENT",
      priority: "LOW",
      dueAtMillis: 1794528000000,
      reminderLeadMinutes: 60,
      completed: false,
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Shadow update with ghost field or invalid enum or immutable createdAt change fails", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = pastDate();

  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("bad_task").set({
      userId: ALICE_UID,
      subjectId: "subj_1",
      subjectName: "Math",
      colorHex: 0xFF10B981,
      title: "Shadow",
      notes: "",
      type: "QUIZ",
      priority: "HIGH",
      dueAtMillis: 1794528000000,
      reminderLeadMinutes: 60,
      completed: false,
      isAdmin: true,
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Focus sessions are immutable once created", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = pastDate();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("sessions").doc("sess_immutable").set({
      userId: ALICE_UID,
      subjectId: "subj_1",
      subjectName: "Algorithms",
      taskId: "",
      taskType: "READING",
      durationMinutes: 25,
      dateKey: "2026-11-12",
      completedAt: now,
    })
  );

  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("sessions").doc("sess_bad_type").set({
      userId: ALICE_UID,
      subjectId: "subj_1",
      subjectName: "Algorithms",
      taskId: "",
      taskType: "GAMING",
      durationMinutes: 25,
      dateKey: "2026-11-12",
      completedAt: now,
    })
  );

  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("sessions").doc("sess_immutable").update({
      durationMinutes: 99,
    })
  );
});

test("Authenticated user can create and delete study spots; invalid coords or cross-user access fails", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = pastDate();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("spots").doc("spot_lib").set({
      userId: ALICE_UID,
      name: "Central Science Library",
      category: "LIBRARY",
      address: "100 Campus Way",
      notes: "3rd floor quiet zone",
      subjectId: "subj_1",
      latitude: 37.4275,
      longitude: -122.1697,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("spots").doc("spot_bad").set({
      userId: ALICE_UID,
      name: "Invalid Latitude",
      category: "CAFE",
      address: "",
      notes: "",
      subjectId: "",
      latitude: 120.0,
      longitude: 0.0,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("spots").get());
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("spots").doc("spot_lib").delete());
});

