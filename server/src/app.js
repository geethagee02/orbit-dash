import express from 'express';
import { addScore, listScores } from './db.js';
import { parseRun } from './rules.js';

export function createApp(db) {
  const app = express();
  app.disable('x-powered-by');
  app.use((_req, res, next) => {
    res.set('Cache-Control', 'no-store');
    next();
  });
  app.use(express.json({ limit: '32kb' }));

  app.get('/', (_req, res) => {
    res
      .type('text/plain')
      .send('Orbit Dash leaderboard. GET /health, GET /v1/scores, POST /v1/scores.\n');
  });

  app.get('/health', (_req, res) => {
    res.json({ ok: true, service: 'orbit-dash' });
  });

  app.get('/v1/scores', (req, res) => {
    const listed = listScores(db, req.query.limit);
    if (listed.error) {
      res.status(400).json({ error: listed.error });
      return;
    }
    res.json({ scores: listed.scores });
  });

  app.post('/v1/scores', (req, res) => {
    const parsed = parseRun(req.body);
    if (parsed.error) {
      res.status(400).json({ error: parsed.error });
      return;
    }
    res.status(201).json({ score: addScore(db, parsed.run) });
  });

  app.use((_req, res) => {
    res.status(404).json({ error: 'Not found.' });
  });

  app.use((error, _req, res, _next) => {
    if (error.type === 'entity.too.large') {
      res.status(413).json({ error: 'Payload too large.' });
      return;
    }
    if (error.type === 'entity.parse.failed' || error instanceof SyntaxError) {
      res.status(400).json({ error: 'Malformed JSON.' });
      return;
    }
    console.error('request failed');
    res.status(500).json({ error: 'Unexpected server error.' });
  });

  return app;
}
