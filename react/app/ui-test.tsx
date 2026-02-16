import { useLocalSearchParams, useRouter } from 'expo-router';
import React from 'react';
import { Dimensions, PixelRatio, Text, View } from 'react-native';
import type { TestResult } from './types';
import { resolveResult } from './utils/navResult';
import { getSampleConfig } from './constants/SampleConfiguration';

// Match Java/Kotlin constants (Physical Pixels)
const INITIAL_OBJECT_COUNT = 250;
const OBJECT_INCREMENT_PER_SECOND = 250;
const NATIVE_OBJECT_SIZE = 50; // 50 physical px
const NATIVE_MAX_VELOCITY = 10; // 10 physical px/frame

const scale = PixelRatio.get();
const OBJECT_SIZE = NATIVE_OBJECT_SIZE / scale;
const MAX_VELOCITY = NATIVE_MAX_VELOCITY / scale;

interface Square {
  id: number;
  x: number;
  y: number;
  vx: number;
  vy: number;
  color: string;
}

/**
 * UI rendering performance benchmark.
 * Matches Java/Kotlin GPUTestActivity algorithm:
 * - 50px squares
 * - Physics-based movement with wall bouncing
 * - +250 objects per second
 * - Frame-count based completion
 */
import { Config } from './consts/Config';
import { BufferedCsvWriter } from './utils/BufferedCsvWriter';

// ... (imports remain)

export default function UITest(): React.ReactElement {
  const { width, height } = Dimensions.get('window');
  const router = useRouter();
  const params = useLocalSearchParams<{ key: string; csvPath?: string }>();
  const config = getSampleConfig(0);

  const [squares, setSquares] = React.useState<Square[]>([]);
  const [frameCount, setFrameCount] = React.useState<number>(0);
  const [currentFps, setCurrentFps] = React.useState<number>(0);
  const [currentObjectCount, setCurrentObjectCount] = React.useState<number>(0);

  const startTimeRef = React.useRef<number>(performance.now());
  const lastFpsUpdateRef = React.useRef<number>(performance.now());
  const framesSinceFpsUpdateRef = React.useRef<number>(0);
  const animFrameRef = React.useRef<number | null>(null);
  const squaresRef = React.useRef<Square[]>([]);
  const writerRef = React.useRef<BufferedCsvWriter | null>(null);

  // Generate deterministic color
  const generateColor = React.useCallback((index: number): string => {
    const hue = (index % 12) * 30;
    // Convert HSV to RGB (simplified for bright colors)
    const h = hue / 60;
    const x = 1 - Math.abs((h % 2) - 1);
    let r = 0, g = 0, b = 0;
    if (h < 1) { r = 1; g = x; }
    else if (h < 2) { r = x; g = 1; }
    else if (h < 3) { g = 1; b = x; }
    else if (h < 4) { g = x; b = 1; }
    else if (h < 5) { r = x; b = 1; }
    else { r = 1; b = x; }
    return `rgb(${Math.round(r * 230)},${Math.round(g * 230)},${Math.round(b * 230)})`;
  }, []);

  // Add objects to the scene
  const addObjects = React.useCallback((count: number, startIndex: number): Square[] => {
    const newSquares: Square[] = [];
    const maxW = width > 0 ? width : 1000;
    const maxH = height > 0 ? height : 2000;

    for (let i = 0; i < count; i++) {
      newSquares.push({
        id: startIndex + i,
        x: Math.random() * (maxW - OBJECT_SIZE),
        y: Math.random() * (maxH - OBJECT_SIZE),
        vx: (Math.random() - 0.5) * MAX_VELOCITY * 2,
        vy: (Math.random() - 0.5) * MAX_VELOCITY * 2,
        color: generateColor(startIndex + i),
      });
    }
    return newSquares;
  }, [width, height, generateColor]);

  // Update positions with bouncing physics
  const updatePositions = React.useCallback((sqs: Square[]): Square[] => {
    return sqs.map(sq => {
      let { x, y, vx, vy } = sq;

      // Move
      x += vx;
      y += vy;

      // Bounce off walls
      if (x < 0 || x + OBJECT_SIZE > width) {
        vx = -vx;
        x += vx * 2;
      }
      if (y < 0 || y + OBJECT_SIZE > height) {
        vy = -vy;
        y += vy * 2;
      }

      return { ...sq, x, y, vx, vy };
    });
  }, [width, height]);

  const currentFpsRef = React.useRef<number>(0);

  // Initialize and run animation loop
  React.useEffect(() => {
    const startTime = performance.now();
    startTimeRef.current = startTime;
    lastFpsUpdateRef.current = startTime;

    if (params.csvPath) {
      writerRef.current = new BufferedCsvWriter(params.csvPath, Config.bufferSize);
      writerRef.current.initialize('Frame,ObjectCount,FrameTimeMs,FPS,ElapsedMs');
    }

    // Initialize with initial objects
    const initial = addObjects(INITIAL_OBJECT_COUNT, 0);
    squaresRef.current = initial;
    setSquares(initial);
    setCurrentObjectCount(INITIAL_OBJECT_COUNT);

    let frame = 0;
    let objCount = INITIAL_OBJECT_COUNT;
    let cancelled = false;
    let lastFrameTime = performance.now();

    const loop = async () => {
      if (cancelled) return;

      const now = performance.now();
      const elapsedMs = now - startTime;

      // Calculate instantaneous frame time (delta)
      const frameDelta = now - lastFrameTime;
      lastFrameTime = now;

      // instantaneous FPS = 1000 / delta
      // If delta is 0 (shouldn't happen often in RAFL), guard it.
      const instantFps = frameDelta > 0 ? 1000 / frameDelta : 0;

      currentFpsRef.current = instantFps;

      // Update UI only every 500ms to avoid bridge overload, BUT CSV gets every frame
      if (now - lastFpsUpdateRef.current >= 500) {
        setCurrentFps(instantFps); // Show instant FPS on UI too, but sampled
        lastFpsUpdateRef.current = now;
      }

      // Check min FPS (using instant or maybe a small moving average for stability? 
      // User wants raw 8ms/120fps, so we use instant for logging. 
      // For termination, maybe we should be lenient or use a short average, 
      // but let's stick to the instant check for now as requested or check if it drops consistently.
      // Actually existing check was: if (currentFpsRef.current <= 10)

      if (frame > 30 && frameDelta > 100) { // > 100ms frame time = < 10 FPS
        // Log warning or stop? Standard logic is stopping.
        // Let's stick to the previous logic but using frameDelta
      }

      if (frame > 30 && instantFps <= 10) {
        const duration = performance.now() - startTime;

        if (writerRef.current) {
          await writerRef.current.flush();
        }

        const res: TestResult = {
          testName: 'UI Test',
          executionTimeMs: duration,
          details: `Max Objects: ${objCount}, Samples: ${frame} (Low FPS: ${instantFps.toFixed(1)})`,
          success: true,
        };
        resolveResult(params.key as string, res);
        router.back();
        return;
      }

      // Manage object count (+250 per second)
      const secondsElapsed = Math.floor(elapsedMs / 1000);
      const desiredObjects = INITIAL_OBJECT_COUNT + (secondsElapsed * OBJECT_INCREMENT_PER_SECOND);
      if (desiredObjects > objCount) {
        const toAdd = desiredObjects - objCount;
        const newOnes = addObjects(toAdd, objCount);
        squaresRef.current = [...squaresRef.current, ...newOnes];
        objCount = desiredObjects;
        setCurrentObjectCount(objCount);
      }

      // Update positions
      squaresRef.current = updatePositions(squaresRef.current);
      setSquares([...squaresRef.current]);

      // Write sample (frame)
      if (writerRef.current) {
        writerRef.current.write([
          frame + 1,
          objCount,
          frameDelta.toFixed(2), // Instantaneous FrameTimeMs
          instantFps.toFixed(1), // Instantaneous FPS
          elapsedMs.toFixed(2)
        ]);
      }

      frame++;
      setFrameCount(frame);

      // Check completion
      if (frame >= config.sampleCount) {
        const duration = performance.now() - startTime;

        if (writerRef.current) {
          await writerRef.current.flush();
        }

        const res: TestResult = {
          testName: 'UI Test',
          executionTimeMs: duration,
          details: `Max Objects: ${objCount}, Samples: ${frame}`,
          success: true,
        };
        resolveResult(params.key as string, res);
        router.back();
      } else {
        animFrameRef.current = requestAnimationFrame(loop);
      }
    };

    animFrameRef.current = requestAnimationFrame(loop);

    return () => {
      cancelled = true;
      if (animFrameRef.current !== null) {
        cancelAnimationFrame(animFrameRef.current);
      }
    };
  }, [addObjects, updatePositions, config.sampleCount, params.key, router]);

  return (
    <View style={{ flex: 1, backgroundColor: '#ffffff' }}>
      {squares.map(sq => (
        <View
          key={sq.id}
          style={{
            position: 'absolute',
            left: sq.x,
            top: sq.y,
            width: OBJECT_SIZE,
            height: OBJECT_SIZE,
            backgroundColor: sq.color,
          }}
        />
      ))}
      <View style={{
        position: 'absolute',
        left: 20,
        top: 40,
        backgroundColor: 'rgba(255,255,255,0.8)',
        padding: 8,
      }}>
        <Text style={{ fontSize: 14, color: '#000' }}>
          Samples: {frameCount} / {config.sampleCount}{'\n'}
          Objects: {currentObjectCount}{'\n'}
          FPS: {currentFps.toFixed(1)}
        </Text>
      </View>
    </View>
  );
}

