export type TestResult = {
  testName: string;
  executionTimeMs: number;
  details: string;
  success: boolean;
};

export type User = {
  name: string;
  surname: string;
  age: number;
  active: boolean;
};

export type Post = {
  userId: number;
  id: number;
  title: string;
  body: string;
};

export function formatResult(r: TestResult): string {
  return `${r.testName}: ${r.executionTimeMs}ms (${r.details})`;
}
