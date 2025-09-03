import { TestResult } from './testTypes';

const escapeCSV = (value: string): string => {
  const needsQuotes = /[",\n]/.test(value);
  const escaped = value.replace(/"/g, '""');
  return needsQuotes ? `"${escaped}"` : escaped;
};

export const buildCsv = (testName: string, results: TestResult[]): string => {
  const header = 'iteration,executionTimeMs,details,success';
  const rows = results.map(r => [r.iteration, r.executionTimeMs, escapeCSV(r.details), r.success].join(','));
  return [`# ${testName}`, header, ...rows].join('\n');
};
