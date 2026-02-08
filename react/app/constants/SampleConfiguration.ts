/**
 * Fixed sample configuration - 10000 samples as per requirement.
 */
export interface SampleConfiguration {
    sampleCount: number;
    bufferSize: number;
    cpuIterations: number;
}

export const CONFIG: SampleConfiguration = {
    sampleCount: 10_000,
    bufferSize: 1_000,
    cpuIterations: 2_000_000,
};

// Legacy compatibility function
export const getSampleConfig = (_id: number): SampleConfiguration => CONFIG;
