import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import test from 'node:test';
import { createApp } from '../src/app.js';
import { openDatabase } from '../src/db.js';
import { canonicalName, normalizeLimit, parseRun } from '../src/rules.js';

test('names match the phone rule', () => {
  assert.equal(canonicalName('  Geetha   M '), 'Geetha M');
  assert.equal(canonicalName("O'Neil"), "O'Neil");
  assert.equal(canonicalName('A'), 'A');
  assert.equal(canonicalName('   '), null);
  assert.equal(canonicalName('...'), null);
  assert.equal(canonicalName('Geetha@M'), null);
  assert.equal(canonicalName('12345678901234567'), null);
  assert.equal(canonicalName('1234567890123456'), '1234567890123456');
  assert.equal(canonicalName("'); DROP TABLE scores;--"), null);
});

test('a run payload has to be a shaped object', () => {
  assert.equal(parseRun(null).error.includes('JSON object'), true);
  assert.equal(parseRun({ name: 'Geetha', score: 10, dodges: 1 }).run.name, 'Geetha');
  assert.equal(parseRun({ name: 'Geetha', score: 10.5, dodges: 1 }).error.includes('Score'), true);
  assert.equal(parseRun({ name: 'Geetha', score: -1, dodges: 1 }).error.includes('Score'), true);
  assert.equal(parseRun({ name: 'Geetha', score: 10, dodges: 1.2 }).error.includes('Dodges'), true);
  assert.equal(parseRun({ name: '   ', score: 10, dodges: 1 }).error.includes('Name'), true);
});

test('limit defaults and stays inside 1 to 50', () => {
  assert.equal(normalizeLimit(undefined).limit, 20);
  assert.equal(normalizeLimit('1').limit, 1);
  assert.equal(normalizeLimit('0').error.includes('Limit'), true);
  assert.equal(normalizeLimit('51').error.includes('Limit'), true);
  assert.equal(normalizeLimit('nope').error.includes('Limit'), true);
});

test('posts land on the board in score order, ties keep the earlier id', async () => {
  const db = openDatabase(':memory:');
  await withServer(createApp(db), async (base) => {
    const health = await fetch(`${base}/health`);
    assert.equal((await health.json()).ok, true);

    const first = await post(base, { name: 'Geetha', score: 20, dodges: 2 });
    assert.equal(first.status, 201);
    assert.equal(first.body.score.rank, 1);

    const higher = await post(base, { name: '  Geetha   M ', score: 50, dodges: 4 });
    assert.equal(higher.body.score.name, 'Geetha M');
    assert.equal(higher.body.score.rank, 1);

    const tied = await post(base, { name: "O'Neil", score: 20, dodges: 2 });
    assert.equal(tied.body.score.rank, 3);

    const board = await fetch(`${base}/v1/scores?limit=2`);
    const body = await board.json();
    assert.deepEqual(
      body.scores.map((row) => [row.rank, row.name, row.score]),
      [
        [1, 'Geetha M', 50],
        [2, 'Geetha', 20],
      ],
    );

    const refused = await post(base, { name: 'Geetha', score: 10, dodges: -1 });
    assert.equal(refused.status, 400);

    const broken = await fetch(`${base}/v1/scores`, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: '{',
    });
    assert.equal(broken.status, 400);

    const missing = await fetch(`${base}/nope`);
    assert.equal(missing.status, 404);
  });
  db.close();
});

async function post(base, payload) {
  const response = await fetch(`${base}/v1/scores`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(payload),
  });
  const body = await response.json();
  return { status: response.status, body };
}

function withServer(app, run) {
  return new Promise((resolve, reject) => {
    const server = createServer(app);
    server.listen(0, '127.0.0.1', async () => {
      const { port } = server.address();
      try {
        await run(`http://127.0.0.1:${port}`);
        resolve();
      } catch (error) {
        reject(error);
      } finally {
        server.close();
      }
    });
  });
}
