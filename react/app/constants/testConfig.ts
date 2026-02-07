// Central test configuration constants.
// Adjust here to change global test iteration counts or timing thresholds.
// UI test: number of full A->B->A ping-pong cycles (reduced to 3 per requirement)
export const UI_TEST_ITERATIONS = 3;
export const SUITE_GROUPS = 6; // expected number of logical groups
export const DEFAULT_ITERATIONS_PER_SUITE = 6; // fallback if needed elsewhere

// TODO: Potentially load these from remote config or user settings screen.

export const EXPORT_FILE_PREFIX = 'benchmark_results';
