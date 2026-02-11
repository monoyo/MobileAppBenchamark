/**
 * Fixed sample configuration - 10000 samples as per requirement.
 */
export interface SampleConfiguration {
    sampleCount: number;
    bufferSize: number;
    cpuIterations: number;
}

export const SampleConfiguration = {
    // Low end device
    0: {
        sampleCount: 10000,
        cpuIterations: 1000,
        bufferSize: 100,
    },
};

// Legacy compatibility function
export const getSampleConfig = (_id: number): any => SampleConfiguration[0];
