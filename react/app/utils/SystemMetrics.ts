import { NativeModules } from 'react-native';

const { SystemMetrics } = NativeModules;

interface SystemMetricsInterface {
    start(sessionId: string): void;
    stop(): void;
    setCurrentTest(testName: string, iteration: number): void;
}

export const SystemMetricsCollector = SystemMetrics as SystemMetricsInterface;
