
export enum SampleConfig {
    SMALL = 0,
    MEDIUM = 1,
    LARGE = 2,
    VERY_LARGE = 3,
    EXTREME = 4
}

export const SampleConfiguration = [
    {
        id: SampleConfig.SMALL,
        sampleCount: 100,
        bufferSize: 50,
        cpuIterations: 500_000,
        displayName: 'SMALL (100 samples)'
    },
    {
        id: SampleConfig.MEDIUM,
        sampleCount: 1_000,
        bufferSize: 200,
        cpuIterations: 1_000_000,
        displayName: 'MEDIUM (1K samples)'
    },
    {
        id: SampleConfig.LARGE,
        sampleCount: 10_000,
        bufferSize: 1_000,
        cpuIterations: 2_000_000,
        displayName: 'LARGE (10K samples)'
    },
    {
        id: SampleConfig.VERY_LARGE,
        sampleCount: 100_000,
        bufferSize: 5_000,
        cpuIterations: 5_000_000,
        displayName: 'VERY LARGE (100K samples)'
    },
    {
        id: SampleConfig.EXTREME,
        sampleCount: 1_000_000,
        bufferSize: 10_000,
        cpuIterations: 10_000_000,
        displayName: 'EXTREME (1M samples)'
    }
];

export const getSampleConfig = (id: number) => SampleConfiguration[id] || SampleConfiguration[0];
