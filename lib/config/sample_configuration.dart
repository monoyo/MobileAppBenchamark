enum SampleConfig {
  small(0, 100, 50, 500000, 'SMALL (100 samples)'),
  medium(1, 1000, 200, 1000000, 'MEDIUM (1K samples)'),
  large(2, 10000, 1000, 2000000, 'LARGE (10K samples)'),
  veryLarge(3, 100000, 5000, 5000000, 'VERY LARGE (100K samples)'),
  extreme(4, 1000000, 10000, 10000000, 'EXTREME (1M samples)');

  final int id;
  final int sampleCount;
  final int bufferSize;
  final int cpuIterations;
  final String displayName;

  const SampleConfig(this.id, this.sampleCount, this.bufferSize, this.cpuIterations, this.displayName);

  static SampleConfig fromId(int id) => SampleConfig.values.firstWhere((e) => e.id == id, orElse: () => SampleConfig.small);
}
