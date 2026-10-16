import type { RemoteState } from '../../modules/wave-remote';

/** `.N` is shorthand for host N on the same /24 as `near`, a TV address the app already knows. */
export function normalizeHost(raw: string, near?: string | null): string | null {
  const subnet = near?.match(/^(\d{1,3}\.){3}/)?.[0];
  const host = raw.trim().replace(/^\.(\d{1,3})$/, subnet ? `${subnet}$1` : '$&');
  return /^(\d{1,3}\.){3}\d{1,3}$/.test(host) && host.split('.').every((part) => Number(part) <= 255)
    ? host : null;
}
export function connectionLabel(state: RemoteState): string {
  if (state.waking) return state.detail ? `Waking TV · ${state.detail}` : 'Waking TV…';
  if (state.state === 'pairing') return 'Accept the connection request on your TV';
  if (state.state === 'connecting') return 'Connecting…';
  if (state.state === 'ready') return state.muted ? 'Connected · Muted'
    : state.volume >= 0 ? `Connected · Volume ${state.volume}` : 'Connected';
  return state.detail ? `${state.detail} · Tap to retry` : 'Disconnected · Tap to retry';
}
export function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : 'Something went wrong. Please try again.';
}
export const commands = {
  volumeUp: 'ssap://audio/volumeUp', volumeDown: 'ssap://audio/volumeDown',
  channelUp: 'ssap://tv/channelUp', channelDown: 'ssap://tv/channelDown',
  inputs: 'ssap://tv/getExternalInputList', apps: 'ssap://com.webos.applicationManager/listLaunchPoints',
};
