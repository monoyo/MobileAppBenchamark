import type { Post } from "../types";

export async function fetchPosts(): Promise<Post[]> {
  const resp = await fetch('https://jsonplaceholder.typicode.com/posts', {
    headers: { 'Accept': 'application/json' },
  });
  if (!resp.ok) throw new Error(`API error ${resp.status}`);
  return await resp.json();
}
