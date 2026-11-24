// Name rule shared with OrbitRules.canonicalName on the phone.
export function canonicalName(raw) {
  if (typeof raw !== 'string') return null;
  const trimmed = raw.trim().replace(/\s+/g, ' ');
  if (trimmed.length < 1 || trimmed.length > 16) return null;
  let hasAlnum = false;
  for (const ch of trimmed) {
    if (isAlnum(ch)) {
      hasAlnum = true;
      continue;
    }
    if (ch !== ' ' && ch !== '-' && ch !== '.' && ch !== "'") return null;
  }
  return hasAlnum ? trimmed : null;
}

export function parseRun(body) {
  if (body == null || typeof body !== 'object' || Array.isArray(body)) {
    return { error: 'Send a JSON object with name, score, and dodges.' };
  }
  const name = canonicalName(body.name);
  if (!name) {
    return {
      error: 'Name must be 1–16 letters, numbers, spaces, hyphens, dots, or apostrophes.',
    };
  }
  if (!Number.isInteger(body.score) || body.score < 0 || body.score > 1_000_000) {
    return { error: 'Score must be an integer from 0 to 1000000.' };
  }
  if (!Number.isInteger(body.dodges) || body.dodges < 0 || body.dodges > 100_000) {
    return { error: 'Dodges must be an integer from 0 to 100000.' };
  }
  return { run: { name, score: body.score, dodges: body.dodges } };
}

export function normalizeLimit(raw) {
  if (raw == null || raw === '') return { limit: 20 };
  if (typeof raw !== 'string' || !/^\d+$/.test(raw)) {
    return { error: 'Limit must be an integer from 1 to 50.' };
  }
  const limit = Number(raw);
  if (limit < 1 || limit > 50) return { error: 'Limit must be an integer from 1 to 50.' };
  return { limit };
}

function isAlnum(ch) {
  return /^[\p{L}\p{N}]$/u.test(ch);
}
