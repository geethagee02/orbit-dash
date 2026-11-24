import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createApp } from './app.js';
import { openDatabase } from './db.js';

const port = Number(process.env.PORT ?? 8791);
if (!Number.isInteger(port) || port <= 0 || port > 65535) {
  console.error('PORT must be an integer from 1 to 65535.');
  process.exit(1);
}

const here = path.dirname(fileURLToPath(import.meta.url));
const databasePath = process.env.ORBIT_DB || path.join(here, '..', 'data', 'orbit-dash.sqlite');
const db = openDatabase(databasePath);
const app = createApp(db);

app.listen(port, '0.0.0.0', () => {
  console.log(`Orbit Dash leaderboard listening on http://127.0.0.1:${port}`);
});
