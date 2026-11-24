import fs from 'node:fs';
import path from 'node:path';
import { DatabaseSync } from 'node:sqlite';
import { normalizeLimit } from './rules.js';

export function openDatabase(filePath) {
  if (filePath !== ':memory:') {
    fs.mkdirSync(path.dirname(path.resolve(filePath)), { recursive: true });
  }
  const db = new DatabaseSync(filePath);
  db.exec(`
    CREATE TABLE IF NOT EXISTS scores (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      name TEXT NOT NULL,
      score INTEGER NOT NULL,
      dodges INTEGER NOT NULL,
      created_at TEXT NOT NULL
    )
  `);
  db.exec('CREATE INDEX IF NOT EXISTS scores_board ON scores (score DESC, id ASC)');
  return db;
}

export function addScore(db, run) {
  const createdAt = new Date().toISOString();
  const result = db
    .prepare('INSERT INTO scores (name, score, dodges, created_at) VALUES (?, ?, ?, ?)')
    .run(run.name, run.score, run.dodges, createdAt);
  const id = Number(result.lastInsertRowid);
  return toScore(
    { id, name: run.name, score: run.score, dodges: run.dodges, created_at: createdAt },
    rankOf(db, id, run.score),
  );
}

export function listScores(db, rawLimit) {
  const parsed = normalizeLimit(rawLimit);
  if (parsed.error) return parsed;
  const rows = db
    .prepare(
      `SELECT id, name, score, dodges, created_at
       FROM scores
       ORDER BY score DESC, id ASC
       LIMIT ?`,
    )
    .all(parsed.limit);
  return { scores: rows.map((row, index) => toScore(row, index + 1)) };
}

function rankOf(db, id, score) {
  const row = db
    .prepare(
      `SELECT COUNT(*) AS ahead
       FROM scores
       WHERE score > ? OR (score = ? AND id < ?)`,
    )
    .get(score, score, id);
  return Number(row.ahead) + 1;
}

function toScore(row, rank) {
  return {
    id: Number(row.id),
    name: row.name,
    score: row.score,
    dodges: row.dodges,
    createdAt: row.created_at,
    rank,
  };
}
