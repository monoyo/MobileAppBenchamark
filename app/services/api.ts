import type { Post } from '../types';

interface FetchOptions extends RequestInit {
  timeoutMs?: number;
}

/**
 * Fetches with timeout handling using AbortController.
 * Implements Java-style timeout mechanism.
 */
async function fetchWithTimeout(
  url: string,
  options: FetchOptions = {}
): Promise<Response> {
  const { timeoutMs = 5_000, ...fetchOpts } = options;
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), timeoutMs);

  try {
    const response = await fetch(url, {
      ...fetchOpts,
      signal: controller.signal,
    });
    return response;
  } finally {
    clearTimeout(timeoutId);
  }
}

/**
 * Fetches posts with exponential backoff retry logic.
 * Similar to Java's retry mechanism with configurable attempt count.
 * - Attempt 0: immediate
 * - Attempt 1: wait 300ms
 * - Attempt 2: wait 600ms
 */
export async function fetchPosts(maxRetries: number = 2): Promise<Post[]> {
  let lastError: Error | null = null;
  const url = 'https://jsonplaceholder.typicode.com/posts';

  for (let attempt = 0; attempt <= maxRetries; attempt++) {
    try {
      const response = await fetchWithTimeout(url, {
        headers: {
          Accept: 'application/json',
        },
        timeoutMs: 6_000,
      });

      if (!response.ok) {
        throw new Error(
          `API error: ${response.status} ${response.statusText}`
        );
      }

      const data: unknown = await response.json();
      if (!Array.isArray(data)) {
        throw new Error('Expected array response');
      }
      return data as Post[];
    } catch (error: unknown) {
      lastError =
        error instanceof Error
          ? error
          : new Error(String(error));

      // Only retry on network/abort/timeout errors
      if (attempt < maxRetries) {
        // Exponential backoff: 300ms, 600ms, 1200ms, ...
        const backoffMs = 300 * Math.pow(2, attempt);
        await new Promise((resolve) => setTimeout(resolve, backoffMs));
      }
    }
  }

  throw (
    lastError ??
    new Error('Failed to fetch posts after all retries')
  );
}

