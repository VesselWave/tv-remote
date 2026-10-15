import { useCallback, useEffect, useRef, useState } from 'react';
import { AppState, PermissionsAndroid, Platform } from 'react-native';
import { initialState, native } from '../../modules/tv-remote';
import { errorMessage } from '../lib/remote';

export function useRemote() {
  const [state, setState] = useState(initialState);
  const [loading, setLoading] = useState(!!native);
  const [message, setMessage] = useState('');
  const mounted = useRef(true);
  const run = useCallback(async (action: () => Promise<unknown>) => {
    try { await action(); return true; }
    catch (error) { if (mounted.current) setMessage(errorMessage(error)); return false; }
  }, []);

  useEffect(() => {
    mounted.current = true;
    const module = native;
    if (!module) return;
    let changed = false;
    const subscription = module.addListener('onChange', (next) => { changed = true; setState(next); });
    void module.snapshot().then((next) => {
      if (!mounted.current) return;
      if (!changed) setState(next);
      if (next.host) void run(() => module.reconnect());
    }).catch((error: unknown) => setMessage(errorMessage(error))).finally(() => setLoading(false));
    const lifecycle = AppState.addEventListener('change', (next) => {
      if (next !== 'active') void module.stopHeld();
    });
    return () => {
      mounted.current = false;
      subscription.remove(); lifecycle.remove();
      void module.stopHeld();
    };
  }, [run]);

  const power = () => run(async () => {
    if (!native) return;
    const current = await native.snapshot();
    let bluetooth = false;
    if (current.state !== 'ready' && current.canWakeBluetooth) {
      bluetooth = true;
      if (Platform.OS === 'android' && Number(Platform.Version) >= 31) {
        bluetooth = await PermissionsAndroid.request(PermissionsAndroid.PERMISSIONS.BLUETOOTH_ADVERTISE)
          === PermissionsAndroid.RESULTS.GRANTED;
        if (!bluetooth) setMessage('Bluetooth wake-up is unavailable. Trying Wake-on-LAN.');
      }
    }
    await native.power(bluetooth);
  });
  return { state, loading, message, setMessage, run, power };
}
