// Singleton capturing launch start/end timestamps.
// Earliest call to markAppStart (see _layout.tsx) sets the baseline.
let startTs: number = (globalThis as any).__APP_LAUNCH_TS__ || Date.now();
let endTs: number | null = null;

export function markAppStart(ts: number = Date.now()) {
  if (ts < startTs) startTs = ts; // keep earliest
  if (!(globalThis as any).__APP_LAUNCH_TS__) (globalThis as any).__APP_LAUNCH_TS__ = startTs;
}

export function markSuiteReady(ts: number = Date.now()) {
  if (endTs == null) endTs = ts;
}

export function getLaunchTime(): number | null { return endTs == null ? null : endTs - startTs; }
export function getLaunchStart(): number { return startTs; }
export function getLaunchEnd(): number | null { return endTs; }

export function __resetLaunchTimes() { startTs = Date.now(); endTs = null; }
