import { requireOptionalNativeModule, requireNativeView, NativeModule } from 'expo';
import type { ViewProps } from 'react-native';

export interface RemoteState {
  host: string | null;
  name: string | null;
  paired: boolean;
  state: 'disconnected' | 'connecting' | 'pairing' | 'ready';
  detail: string | null;
  volume: number;
  muted: boolean;
  touchpad: boolean;
  naturalScrolling: boolean;
  waking: boolean;
  canWakeBluetooth: boolean;
}
export interface FoundTv { host: string; name: string; btMac: string | null }
export interface TvInput { id: string; label?: string; connected?: boolean }
export interface TvApp { id: string; title?: string; params?: Record<string, unknown> }
export interface TvResponse {
  devices?: TvInput[];
  launchPoints?: TvApp[];
  [key: string]: unknown;
}
type Events = { onChange: (state: RemoteState) => void };
declare class WaveRemoteNative extends NativeModule<Events> {
  snapshot(): Promise<RemoteState>;
  connect(host: string, name: string, btMac: string | null): Promise<void>;
  reconnect(): Promise<void>;
  disconnect(): Promise<void>;
  discover(): Promise<FoundTv[]>;
  forget(): Promise<void>;
  request(uri: string, payload: Record<string, unknown> | null): Promise<TvResponse>;
  button(key: string): Promise<void>;
  startHeld(key: string, pointer: boolean): Promise<void>;
  stopHeld(): Promise<void>;
  power(bluetooth: boolean): Promise<void>;
  preferences(touchpad: boolean, naturalScrolling: boolean): Promise<void>;
}
export const native = requireOptionalNativeModule<WaveRemoteNative>('WaveRemote');
export const NativeTouchpad = native
  ? requireNativeView<ViewProps & { naturalScrolling: boolean }>('WaveRemote')
  : null;
export const initialState: RemoteState = {
  host: null, name: null, paired: false, state: 'disconnected', detail: null,
  volume: -1, muted: false, touchpad: false, naturalScrolling: true,
  waking: false, canWakeBluetooth: false,
};
