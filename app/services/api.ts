import type { Post } from "../types";

// Basic retry + timeout wrapper to reduce flakiness (no external deps)
async function fetchWithTimeout(url: string, opts: RequestInit & { timeoutMs?: number } = {}): Promise<Response> {
  const { timeoutMs = 5000, ...rest } = opts;
  const controller = new AbortController();
  const id = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const resp = await fetch(url, { ...rest, signal: controller.signal });
    return resp;
  } finally {
    clearTimeout(id);
  }
}

export async function fetchPosts(retries = 2): Promise<Post[]> {
  let attempt = 0;
  let lastErr: any = null;
  const url = 'https://jsonplaceholder.typicode.com/posts';
  while (attempt <= retries) {
    try {
      const resp = await fetchWithTimeout(url, { headers: { 'Accept': 'application/json' }, timeoutMs: 6000 });
      if (!resp.ok) throw new Error(`API error ${resp.status}`);
      return await resp.json();
    } catch (e: any) {
      lastErr = e;
      // only retry on network/abort/timeouts
      if (attempt === retries) break;
      const backoff = 300 * Math.pow(2, attempt); // 300, 600, 1200...
      await new Promise(r => setTimeout(r, backoff));
    }
    attempt++;
  }
  throw lastErr ?? new Error('Unknown API failure');
}

