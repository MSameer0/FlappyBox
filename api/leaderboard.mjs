const DEFAULT_LIMIT = 10;
const MAX_LIMIT = 100;
const MAX_SCORE = 999999;
const MAX_STORED_ENTRIES = 100;
const LEADERBOARD_KEY = process.env.LEADERBOARD_KEY || "flappybox:leaderboard";

export default {
  async fetch(request) {
    try {
      if (request.method === "GET") {
        return await getLeaderboard(request);
      }

      if (request.method === "POST") {
        return await submitScore(request);
      }

      return json({ error: "Method not allowed" }, 405);
    } catch (error) {
      console.error(error);
      return json({ error: "Leaderboard unavailable" }, 500);
    }
  },
};

async function getLeaderboard(request) {
  const limit = clampNumber(
    Number(new URL(request.url).searchParams.get("limit") || DEFAULT_LIMIT),
    1,
    MAX_LIMIT,
  );

  const response = await redis(["ZREVRANGE", LEADERBOARD_KEY, 0, limit - 1, "WITHSCORES"]);
  const entries = [];

  for (let index = 0; index < response.result.length; index += 2) {
    const member = safeParseEntry(response.result[index]);
    entries.push({
      name: member.name,
      score: Number(response.result[index + 1] || 0),
    });
  }

  return json({ entries });
}

async function submitScore(request) {
  const body = await request.json().catch(() => null);
  const name = sanitizeName(body?.name);
  const score = Math.floor(Number(body?.score));

  if (!name || !Number.isFinite(score) || score < 0 || score > MAX_SCORE) {
    return json({ error: "Invalid score submission" }, 400);
  }

  const member = JSON.stringify({
    name,
    submittedAt: Date.now(),
    id: crypto.randomUUID(),
  });

  await redis(["ZADD", LEADERBOARD_KEY, score, member]);
  await redis(["ZREMRANGEBYRANK", LEADERBOARD_KEY, 0, -(MAX_STORED_ENTRIES + 1)]);

  return json({ ok: true });
}

async function redis(command) {
  const url = process.env.UPSTASH_REDIS_REST_URL;
  const token = process.env.UPSTASH_REDIS_REST_TOKEN;

  if (!url || !token) {
    throw new Error("Missing Upstash Redis environment variables");
  }

  const response = await fetch(url, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(command),
  });

  const body = await response.json();
  if (!response.ok || body.error) {
    throw new Error(body.error || `Redis request failed: ${response.status}`);
  }

  return body;
}

function sanitizeName(value) {
  if (typeof value !== "string") return "";

  return value
    .trim()
    .replace(/\s+/g, " ")
    .replace(/[^\w .-]/g, "")
    .slice(0, 16);
}

function safeParseEntry(value) {
  try {
    const entry = JSON.parse(value);
    return {
      name: sanitizeName(entry.name) || "Player",
    };
  } catch {
    return { name: "Player" };
  }
}

function clampNumber(value, min, max) {
  if (!Number.isFinite(value)) return min;
  return Math.max(min, Math.min(max, Math.floor(value)));
}

function json(body, status = 200) {
  return Response.json(body, {
    status,
    headers: {
      "Cache-Control": "no-store",
    },
  });
}
