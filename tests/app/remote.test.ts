import { describe, expect, test } from 'bun:test';
import { connectionLabel, normalizeHost } from '../../app/lib/remote';
import type { RemoteState } from '../../modules/wave-remote';

const disconnected: RemoteState = {
  host: '192.168.0.1', name: 'LG TV', paired: true, state: 'disconnected', detail: null,
  volume: -1, muted: false, touchpad: false, naturalScrolling: true, waking: false, canWakeBluetooth: false, tvs: [],
};
describe('manual connection addresses', () => {
  test('accepts IPv4 addresses and the .N shorthand on a known TV\'s subnet', () => {
    expect(normalizeHost(' 192.168.0.1 ')).toBe('192.168.0.1');
    expect(normalizeHost('.20', '192.168.0.1')).toBe('192.168.0.20');
    expect(normalizeHost('.20', '10.1.2.3')).toBe('10.1.2.20');
    expect(normalizeHost('.20')).toBeNull();
  });
  test('rejects malformed, out-of-range and non-address input before native calls', () => {
    for (const host of ['', '.256', '.-1', '256.1.2.3', '192.168.1', '1.2.3.4:3001', 'http://1.2.3.4', '1.2.3.4/path', '1.2.3.-1', '1.2.3.4\nextra']) {
      expect(normalizeHost(host)).toBeNull();
    }
  });
});
describe('connection feedback', () => {
  test('shows real pairing guidance and reconnect recovery', () => {
    expect(connectionLabel({ ...disconnected, state: 'pairing' })).toContain('on your TV');
    expect(connectionLabel(disconnected)).toContain('Tap to retry');
    expect(connectionLabel({ ...disconnected, detail: 'TV is off' })).toContain('TV is off');
  });
  test('prioritizes wake state and mute over a stale volume value', () => {
    expect(connectionLabel({ ...disconnected, waking: true, detail: 'Bluetooth is off' })).toBe('Waking TV · Bluetooth is off');
    expect(connectionLabel({ ...disconnected, state: 'ready', muted: true, volume: 20 })).toBe('Connected · Muted');
    expect(connectionLabel({ ...disconnected, state: 'ready', volume: 0 })).toBe('Connected · Volume 0');
    expect(connectionLabel({ ...disconnected, state: 'ready' })).toBe('Connected');
  });
});
