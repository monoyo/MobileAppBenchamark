import { Image } from 'expo-image';
import { useLocalSearchParams, useRouter } from 'expo-router';
import React from 'react';
import { FlatList, View, ListRenderItem } from 'react-native';
import { Config } from './consts/Config';
import { BufferedCsvWriter } from './utils/BufferedCsvWriter';
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

interface ImageItem {
  index: number;
  url: string;
}

export default function ImageTest(): React.ReactElement {
  const router = useRouter();
  const params = useLocalSearchParams<{ key: string; csvPath?: string; batchSize?: string }>();
  const flatListRef = React.useRef<FlatList>(null);

  // Determine target count
  const targetSamples = params.batchSize
    ? parseInt(params.batchSize, 10)
    : Config.sampleCount;

  // Sequential State
  // We start with 1 image. When it finishes, we add another.
  const [items, setItems] = React.useState<ImageItem[]>([
    { index: 0, url: IMAGE_URLS[0] }
  ]);

  const countRef = React.useRef<{ completed: number; succeeded: number }>(
    {
      completed: 0,
      succeeded: 0,
    }
  );

  const startRef = React.useRef<number>(Date.now());
  const writerRef = React.useRef<BufferedCsvWriter | null>(null);
  const currentItemStartTimeRef = React.useRef<number>(Date.now());

  // Initialize timer and writer on mount
  React.useEffect(() => {
    startRef.current = Date.now();
    currentItemStartTimeRef.current = Date.now();

    if (params.csvPath) {
      writerRef.current = new BufferedCsvWriter(params.csvPath, Config.bufferSize);
    }

    return () => {
      // cleanup
    };
  }, [params.csvPath]);

  const finish = React.useCallback(async (): Promise<void> => {
    if (writerRef.current) {
      await writerRef.current.flush();
    }

    const elapsedMs = Date.now() - startRef.current;
    const { completed, succeeded } = countRef.current;
    const failed = completed - succeeded;
    const res: TestResult = {
      testName: 'Image Loading Test',
      executionTimeMs: elapsedMs,
      details:
        failed > 0
          ? `Loaded ${succeeded}/${targetSamples} (${failed} failed)`
          : 'All images loaded',
      success: failed === 0,
    };
    resolveResult(params.key as string, res);
    router.back();
  }, [params.key, router, targetSamples]);

  const loadNext = React.useCallback((currentIndex: number) => {
    if (currentIndex + 1 < targetSamples) {
      const nextIndex = currentIndex + 1;
      const nextUrl = IMAGE_URLS[nextIndex % IMAGE_URLS.length];

      // Update start time for next item
      currentItemStartTimeRef.current = Date.now();

      setItems(prev => [...prev, { index: nextIndex, url: nextUrl }]);
    } else {
      finish();
    }
  }, [targetSamples, finish]);

  const handleResult = React.useCallback((index: number, success: boolean) => {
    const now = Date.now();
    countRef.current.completed += 1;
    if (success) countRef.current.succeeded += 1;

    // Write CSV
    if (writerRef.current) {
      // Start time for this specific item was set before render
      const startTime = currentItemStartTimeRef.current;

      const duration = now - startTime;
      writerRef.current.write([
        index + 1,
        Math.max(0, duration),
        success ? 'Success' : 'Error',
        startTime,
        Math.max(0, duration),
        now - startRef.current
      ]);
    }

    // Trigger next load
    // We use setTimeout to allow UI to update and not block main thread
    setTimeout(() => {
      loadNext(index);
    }, 0);
  }, [loadNext]);

  const renderItem: ListRenderItem<ImageItem> = React.useCallback(({ item }) => {
    return (
      <View
        style={{
          height: 200,
          justifyContent: 'center',
          alignItems: 'center',
        }}
      >
        <Image
          source={{ uri: item.url }}
          style={{ width: '100%', height: '100%' }}
          onLoad={() => {
            if (item.index === countRef.current.completed) {
              handleResult(item.index, true);
            }
          }}
          onError={() => {
            if (item.index === countRef.current.completed) {
              handleResult(item.index, false);
            }
          }}
          contentFit="cover"
          cachePolicy="none"
        />
      </View>
    );
  }, [handleResult]);

  return (
    <FlatList<ImageItem>
      ref={flatListRef}
      data={items}
      renderItem={renderItem}
      keyExtractor={(item) => item.index.toString()}
      onContentSizeChange={() => {
        // Auto-scroll to bottom
        flatListRef.current?.scrollToEnd({ animated: true });
      }}
      getItemLayout={(data, index) => (
        { length: 200, offset: 200 * index, index }
      )}
    />
  );
}
