import * as Location from 'expo-location';
import { useCallback, useEffect, useState } from 'react';

export type LocationPermissionState = 'unknown' | 'granted' | 'denied' | 'requesting';

// Hook to manage foreground location permission lifecycle.
export function useLocationPermission(autoRequestOnMount = true) {
  const [state, setState] = useState<LocationPermissionState>('unknown');
  const [error, setError] = useState<string | null>(null);

  const check = useCallback(async () => {
    try {
      const { status } = await Location.getForegroundPermissionsAsync();
      setState(status === 'granted' ? 'granted' : 'denied');
    } catch (e: any) {
      setError(String(e));
    }
  }, []);

  const request = useCallback(async () => {
    setState('requesting');
    try {
      const { status } = await Location.requestForegroundPermissionsAsync();
      setState(status === 'granted' ? 'granted' : 'denied');
    } catch (e: any) {
      setError(String(e));
      setState('denied');
    }
  }, []);

  useEffect(() => {
    if (autoRequestOnMount) {
      check().then(s => {
        if (s !== undefined && s !== 'granted') {
          // auto attempt request if not granted
          request();
        }
      });
    }
  }, [check, request, autoRequestOnMount]);

  return { state, error, request, check };
}
