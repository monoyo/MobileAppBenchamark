import { useCallback, useEffect, useRef } from 'react';

/**
 * Hook to measure FPS (frames per second).
 * Tracks frame count using requestAnimationFrame and recalculates FPS every second.
 * Returns a stable callback function that retrieves the current FPS reading.
 *
 * @param enabled - Whether to actively measure FPS
 * @returns A function that returns the current FPS value
 */
export const useFpsCounter = (
  enabled: boolean
): (() => number) => {
  const fpsRef = useRef<number>(0);
  const frameCountRef = useRef<number>(0);
  const lastTimeRef = useRef<number>(0);
  const requestIdRef = useRef<number | undefined>();

  useEffect(() => {
    if (!enabled) return;

    lastTimeRef.current = Date.now();
    frameCountRef.current = 0;

    const loop = (): void => {
      const now = Date.now();
      frameCountRef.current += 1;

      const elapsedMs = now - lastTimeRef.current;
      if (elapsedMs >= 1_000) {
        // Calculate FPS: frames per second
        fpsRef.current = frameCountRef.current / (elapsedMs / 1_000);
        frameCountRef.current = 0;
        lastTimeRef.current = now;
      }

      requestIdRef.current = requestAnimationFrame(loop);
    };

    requestIdRef.current = requestAnimationFrame(loop);

    return () => {
      if (requestIdRef.current !== undefined) {
        cancelAnimationFrame(requestIdRef.current);
      }
    };
  }, [enabled]);

  // Return stable callback to get current FPS
  return useCallback(() => fpsRef.current, []);
};
