import { Config } from '../consts/Config';

export interface SampleConfiguration {
    sampleCount: number;
    bufferSize: number;
    cpuIterations: number;
}

/**
 * Returns the sample configuration for the given config ID.
 * Currently only one configuration exists (medium), matching other platforms.
 */
export function getSampleConfig(_id: number = 0): SampleConfiguration {
    return {
        sampleCount: Config.sampleCount,
        bufferSize: Config.bufferSize,
        cpuIterations: Config.cpuIterations,
    };
}
