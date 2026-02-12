import { Image } from 'expo-image';
import { useLocalSearchParams, useRouter } from 'expo-router';
import React from 'react';
import { ScrollView, View } from 'react-native';
import type { TestResult } from './types';
import { resolveResult } from './utils/navResult';

const IMAGE_URLS: ReadonlyArray<string> = [
  'https://fastly.picsum.photos/id/861/300/200.jpg?hmac=SePZxFhkEpm4mmZIJke4z7ghH-2l0PsNAtEm_2vq2W4',
  'https://fastly.picsum.photos/id/687/300/200.jpg?hmac=4cY--ZSfxEMRzYtVmyvUBPrHqzAqJ3JmMSEmdYqdfMM',
  'https://fastly.picsum.photos/id/408/300/200.jpg?hmac=WLBoOapFRUAh4eGfCSPD4htVThRV8LKEnheDBbmOYvY',
  'https://fastly.picsum.photos/id/297/300/200.jpg?hmac=FHS6m7Ec_3-9rDv45kvf5XCQz0tWD5sY9yZY7GwSC6c',
  'https://fastly.picsum.photos/id/723/300/200.jpg?hmac=r-Bu4Me1tZJW3ncPjx4Pj2nhJ2sV0XQhDEeM1kH9EyY',
  'https://fastly.picsum.photos/id/507/300/200.jpg?hmac=H7vqiU7dtXTNLQraEHG25D7naP8nQy-uGlbyUCvE6Mo',
  'https://fastly.picsum.photos/id/163/300/200.jpg?hmac=fHGMH6DT42ra3SOzs6JtojmYZ7jECNcq5xn1Ap9OPNA',
  'https://fastly.picsum.photos/id/54/300/200.jpg?hmac=7Cm5bybfBDMHwUF7AvEbAKWA7l5WnE9MZvcZhPpULTc',
  'https://fastly.picsum.photos/id/992/300/200.jpg?hmac=w137wSlXMe7QugWkdz2qvxFlif1dwEWqNnv4qFIyWps',
  'https://fastly.picsum.photos/id/764/300/200.jpg?hmac=1sBuxBDUdVzEEnIKB5S4cXJ_sQ5Tp3ZSnjrHOWF_E20',
];

/**
 * Image loading benchmark test.
 * Tests I/O performance with batch image loading, error tracking, and resource management.
 * Provides visual feedback via scrolling as images load.
 */
export default function ImageTest(): React.ReactElement {
  const router = useRouter();
  const params = useLocalSearchParams<{ key: string }>();
  const scrollRef = React.useRef<ScrollView>(null);
  const [loaded, setLoaded] = React.useState<number>(0);
  const [errors, setErrors] = React.useState<number>(0);
  const countRef = React.useRef<{ completed: number; succeeded: number }>(
    {
      completed: 0,
      succeeded: 0,
    }
  );
  const startRef = React.useRef<number>(Date.now());

  // Initialize timer on mount
  React.useEffect(() => {
    startRef.current = Date.now();
  }, []);

  const maybeFinish = React.useCallback((): void => {
    const { completed, succeeded } = countRef.current;
    if (completed >= IMAGE_URLS.length) {
      const elapsedMs = Date.now() - startRef.current;
      const failed = completed - succeeded;
      const res: TestResult = {
        testName: 'Image Loading Test',
        executionTimeMs: elapsedMs,
        details:
          failed > 0
            ? `Loaded ${succeeded}/${IMAGE_URLS.length} (${failed} failed)`
            : 'All images loaded',
        success: failed === 0,
      };
      resolveResult(params.key as string, res);
      router.back();
    }
  }, [params.key, router]);

  const handleImageLoad = React.useCallback((index: number): void => {
    countRef.current.succeeded += 1;
    countRef.current.completed += 1;
    setLoaded((prev) => prev + 1);

    // Scroll to loaded image for visual feedback
    setTimeout(() => {
      scrollRef.current?.scrollTo({ y: index * 200, animated: true });
    }, 0);

    maybeFinish();
  }, [maybeFinish]);

  const handleImageError = React.useCallback((): void => {
    countRef.current.completed += 1;
    setErrors((prev) => prev + 1);
    maybeFinish();
  }, [maybeFinish]);

  return (
    <ScrollView ref={scrollRef}>
      {IMAGE_URLS.map((url, i) => (
        <View
          key={i}
          style={{
            height: 200,
            justifyContent: 'center',
            alignItems: 'center',
          }}
        >
          <Image
            source={{ uri: url }}
            style={{ width: '100%', height: '100%' }}
            onLoad={() => handleImageLoad(i)}
            onError={() => handleImageError()}
            contentFit="cover"
          />
        </View>
      ))}
    </ScrollView>
  );
}
