/// Encapsulates CPU benchmark execution result.
/// Contains metrics for cross-platform comparison.
class CpuResult {
  final int threads;
  final int durationMs;
  final int iterations;
  final double checksum;

  const CpuResult({
    required this.threads,
    required this.durationMs,
    required this.iterations,
    required this.checksum,
  });
}