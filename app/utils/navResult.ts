type Resolver = (value: any) => void;

const resolvers = new Map<string, Resolver>();

export function createResultKey(): string {
  return Math.random().toString(36).slice(2);
}

export function waitForResult<T = any>(key: string): Promise<T> {
  return new Promise<T>((resolve) => {
    resolvers.set(key, resolve as Resolver);
  });
}

export function resolveResult<T = any>(key: string, value: T) {
  const r = resolvers.get(key);
  if (r) {
    r(value);
    resolvers.delete(key);
  }
}
