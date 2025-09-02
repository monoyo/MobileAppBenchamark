import React from 'react';
import { ScrollView, View, Text } from 'react-native';
import { Image } from 'expo-image';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { resolveResult } from './utils/navResult';
import type { TestResult } from './types';

const urls = [
  "https://fastly.picsum.photos/id/861/300/200.jpg?hmac=SePZxFhkEpm4mmZIJke4z7ghH-2l0PsNAtEm_2vq2W4",
  "https://fastly.picsum.photos/id/687/300/200.jpg?hmac=4cY--ZSfxEMRzYtVmyvUBPrHqzAqJ3JmMSEmdYqdfMM",
  "https://fastly.picsum.photos/id/408/300/200.jpg?hmac=WLBoOapFRUAh4eGfCSPD4htVThRV8LKEnheDBbmOYvY",
  "https://fastly.picsum.photos/id/297/300/200.jpg?hmac=FHS6m7Ec_3-9rDv45kvf5XCQz0tWD5sY9yZY7GwSC6c",
  "https://fastly.picsum.photos/id/723/300/200.jpg?hmac=r-Bu4Me1tZJW3ncPjx4Pj2nhJ2sV0XQhDEeM1kH9EyY",
  "https://fastly.picsum.photos/id/507/300/200.jpg?hmac=H7vqiU7dtXTNLQraEHG25D7naP8nQy-uGlbyUCvE6Mo",
  "https://fastly.picsum.photos/id/163/300/200.jpg?hmac=fHGMH6DT42ra3SOzs6JtojmYZ7jECNcq5xn1Ap9OPNA",
  "https://fastly.picsum.photos/id/54/300/200.jpg?hmac=7Cm5bybfBDMHwUF7AvEbAKWA7l5WnE9MZvcZhPpULTc",
  "https://fastly.picsum.photos/id/992/300/200.jpg?hmac=w137wSlXMe7QugWkdz2qvxFlif1dwEWqNnv4qFIyWps",
  "https://fastly.picsum.photos/id/764/300/200.jpg?hmac=1sBuxBDUdVzEEnIKB5S4cXJ_sQ5Tp3ZSnjrHOWF_E20"
];

export default function ImageTest() {
  const router = useRouter();
  const params = useLocalSearchParams<{ key: string }>();
  const scrollRef = React.useRef<ScrollView>(null);
  const [loaded, setLoaded] = React.useState(0);
  const [errors, setErrors] = React.useState(0);
  const doneCountRef = React.useRef(0);
  const successCountRef = React.useRef(0);
  const startRef = React.useRef(Date.now());

  React.useEffect(() => { startRef.current = Date.now(); }, []);

  const maybeFinish = () => {
    if (doneCountRef.current >= urls.length) {
      const elapsed = Date.now() - startRef.current;
      const ok = successCountRef.current;
      const failed = doneCountRef.current - ok;
      const res: TestResult = {
        testName: 'Image Loading Test',
        executionTimeMs: elapsed,
        details: failed > 0 ? `Loaded ${ok}/${urls.length} (failed ${failed})` : 'All images loaded',
        success: failed === 0,
      };
      resolveResult(params.key as string, res);
      router.back();
    }
  };

  const onLoad = (idx: number) => {
    successCountRef.current += 1;
    doneCountRef.current += 1;
    setLoaded(successCountRef.current);
    // Scroll to the item that just finished loading (like Flutter)
    setTimeout(() => {
      scrollRef.current?.scrollTo({ y: idx * 200, animated: true });
    }, 0);
    maybeFinish();
  };

  const onError = (_idx: number) => {
    doneCountRef.current += 1;
    setErrors(prev => prev + 1);
    maybeFinish();
  };

  return (
    <ScrollView ref={scrollRef}>
      {urls.map((u, i) => (
        <View key={i} style={{ height: 200, justifyContent: 'center', alignItems: 'center' }}>
          <Image
            source={{ uri: u }}
            style={{ width: '100%', height: '100%' }}
            onLoad={() => onLoad(i)}
            onError={() => onError(i)}
            contentFit="cover"
          />
        </View>
      ))}
    </ScrollView>
  );
}
