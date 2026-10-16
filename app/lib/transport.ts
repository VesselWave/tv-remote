import { native } from '../../modules/wave-remote';

export function request(uri: string, payload: Record<string, unknown> | null = null) {
  if (!native) return Promise.reject(new Error('Install the Android development build to connect to a TV.'));
  return native.request(uri, payload);
}
