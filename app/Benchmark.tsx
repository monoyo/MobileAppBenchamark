import React, { useRef, useState } from 'react';
import { ScrollView, Text, TouchableOpacity } from 'react-native';

const Benchmark = () => {
  const [runs, setRuns] = useState('50');
  const [status, setStatus] = useState('Benchmark Status: Not Started');
  const [results, setResults] = useState<any>(null);
  const [launchTime, setLaunchTime] = useState<number | null>(null);
  const appStartTime = useRef(Date.now());

  React.useEffect(() => {
    setLaunchTime(Date.now() - appStartTime.current);
  }, []);

  // CPU: count primes up to 100_000
  const countPrimes = (limit: number) => {
    let count = 0;
    for (let i = 2; i <= limit; i++) {
      let isPrime = true;
      for (let j = 2; j * j <= i; j++) {
        if (i % j === 0) {
          isPrime = false;
          break;
        }
      }
      if (isPrime) count++;
    }
    return count;
  };

  // RAM: allocate big array and measure memory usage (approximate)
  const getUsedMemoryMB = () => {
    if (global && (global as any).gc) (global as any).gc();
    // JS doesn't provide real memory info, so this is a placeholder
    return 0;
  };

  // UI: render 200 Text components and measure time
  const measureUiLatency = async () => {
    const start = Date.now();
    // Simulate UI rendering
    await new Promise(resolve => setTimeout(resolve, 0));
    const end = Date.now();
    return end - start;
  };

  const runBenchmarks = async () => {
    setStatus('Benchmark Status: Running...');
    setResults(null);
    const cpuTimes: number[] = [];
    const ramTimes: number[] = [];
    const uiLatencies: number[] = [];
    const cpuResults: number[] = [];
    const ramUsages: [number, number][] = [];
    let nRuns = parseInt(runs) || 50;
    if (nRuns > 80) nRuns = 70;
    if (nRuns < 1) nRuns = 1;
    for (let i = 0; i < nRuns; i++) {
      // CPU
      const cpuStart = Date.now();
      const cpuResult = countPrimes(100000);
      const cpuTime = Date.now() - cpuStart;
      cpuTimes.push(cpuTime);
      cpuResults.push(cpuResult);
      // RAM
      const ramStart = Date.now();
      // JS: cannot measure memory, so just allocate
      const bigArray = new Array(500000).fill(0).map((_, idx) => idx);
      const ramUsageBefore = getUsedMemoryMB();
      const ramUsageAfter = getUsedMemoryMB();
      const ramTime = Date.now() - ramStart;
      ramTimes.push(ramTime);
      ramUsages.push([ramUsageBefore, ramUsageAfter]);
      // UI
      const uiLatency = await measureUiLatency();
      uiLatencies.push(uiLatency);
    }
    // Averages
    const avg = (arr: number[]) => arr.reduce((a, b) => a + b, 0) / arr.length;
    setResults({
      cpuAvg: avg(cpuTimes),
      ramAvg: avg(ramTimes),
      uiAvg: avg(uiLatencies),
      ramBeforeAvg: avg(ramUsages.map(x => x[0])),
      ramAfterAvg: avg(ramUsages.map(x => x[1])),
      runs: nRuns,
    });
    setStatus('Benchmark Status: Done');
  };

  return (
      <ScrollView contentContainerStyle={{ padding: 20 , backgroundColor: '#fff'}}>
        <Text style={{ fontWeight: 'bold', fontSize: 28, marginBottom: 20 }}>Welcome to the Mobile Benchmark App</Text>
        <Text style={{ fontSize: 20, marginBottom: 20, textAlign: 'center', color: 'gray',}}>App Launch Time: {launchTime} ms</Text>
        <Text style={{ fontSize: 18, color: 'gray', marginBottom: 10 }}>Next steps: Click the button below to start the benchmark</Text>
        <Text style={{ fontSize: 20, marginBottom: 20, textAlign: 'center', color: 'gray', }}>{status}</Text>
        {/* Zamiana domyślnego Button na stylowany przycisk z zaokrąglonymi rogami */}
        <TouchableOpacity
          style={{
            backgroundColor: '#007AFF',
            borderRadius: 32, // bardziej zaokrąglone rogi
            paddingVertical: 12,
            paddingHorizontal: 32,
            marginTop: 10,
            marginHorizontal: 10,
            alignItems: 'center',
            shadowColor: '#000',
            shadowOffset: { width: 0, height: 2 },
            shadowOpacity: 0.2,
            shadowRadius: 4,
            elevation: 3,
          }}
          onPress={runBenchmarks}
        >
          <Text style={{ color: '#fff', fontSize: 16, fontWeight: 'bold', letterSpacing: 1 }}>Start Benchmark</Text>
        </TouchableOpacity>
      </ScrollView>
  );
};

export default Benchmark;
