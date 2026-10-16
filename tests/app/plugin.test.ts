import { describe, expect, test } from 'bun:test';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
// Exercise config mods without generating or building an Android project.
import { createRequire } from 'node:module';
const plugin = createRequire(import.meta.url)('../../plugins/with-wave-remote.cjs');
const projectRoot = path.resolve(import.meta.dir, '../..');
function config() { return plugin({ name: 'Wave Remote', slug: 'wave-remote', mods: {} }); }
function request(extra = {}) { return { projectRoot, introspect: false, ...extra }; }

describe('Android integration plugin', () => {
  test('retains LAN access, private pairing, and permission boundaries', async () => {
    const manifest = { application: [{ $: {} }], 'uses-permission': [
      { $: { 'android:name': 'android.permission.BLUETOOTH' } },
      { $: { 'android:name': 'android.permission.BLUETOOTH_ADMIN' } },
      { $: { 'android:name': 'android.permission.BLUETOOTH_ADVERTISE' } },
    ] };
    const result = await config().mods.android.manifest({ modResults: { manifest }, modRequest: request() });
    expect(result.modResults.manifest.application[0].$['android:usesCleartextTraffic']).toBe('true');
    expect(result.modResults.manifest.application[0].$['android:allowBackup']).toBe('false');
    expect(result.modResults.manifest['uses-permission'][0].$['android:maxSdkVersion']).toBe('30');
    expect(result.modResults.manifest['uses-permission'][2].$['android:maxSdkVersion']).toBeUndefined();
  });
  test('volume-key handler is inserted once and template mismatch is explicit', async () => {
    const mod = config().mods.android.mainActivity;
    const source = { language: 'kt', contents: 'package me.vwave.remote\nclass MainActivity : ReactActivity() {\n}\n' };
    const first = await mod({ modResults: source, modRequest: request() });
    expect(first.modResults.contents).toContain('WaveRemoteModule.handleVolumeKey(event)');
    const second = await mod({ modResults: first.modResults, modRequest: request() });
    expect(second.modResults.contents.match(/override fun dispatchKeyEvent/g)).toHaveLength(1);
    await expect(mod({ modResults: { language: 'kt', contents: 'unknown template' }, modRequest: request() })).rejects.toThrow('Could not install');
  });
  test('preserves the original icon without overwriting Expo color resources', async () => {
    const root = await fs.mkdtemp(path.join(os.tmpdir(), 'waveremote-plugin-'));
    try {
      const values = path.join(root, 'app/src/main/res/values');
      await fs.mkdir(values, { recursive: true });
      await fs.writeFile(path.join(values, 'colors.xml'), '<resources/>');
      await config().mods.android.finalized({ modResults: {}, modRequest: request({ platformProjectRoot: root }) });
      expect(await fs.readFile(path.join(values, 'colors.xml'), 'utf8')).toBe('<resources/>');
      expect(await fs.readFile(path.join(root, 'app/src/main/res/drawable/splashscreen_logo.xml'), 'utf8')).toContain('<vector');
      expect(await fs.readFile(path.join(values, 'tv_remote_icon.xml'), 'utf8')).toContain('icon_bg');
      expect(await fs.readFile(path.join(root, 'app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml'), 'utf8')).toContain('@drawable/ic_launcher_fg');
    } finally { await fs.rm(root, { recursive: true, force: true }); }
  });
});
