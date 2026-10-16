const fs = require('node:fs/promises');
const path = require('node:path');
const { withAndroidManifest, withMainActivity, withFinalizedMod } = require('expo/config-plugins');

module.exports = function withWaveRemote(config) {
  config = withAndroidManifest(config, (mod) => {
    const app = mod.modResults.manifest.application[0];
    app.$['android:usesCleartextTraffic'] = 'true';
    app.$['android:allowBackup'] = 'false';
    for (const permission of mod.modResults.manifest['uses-permission'] ?? []) {
      if (['android.permission.BLUETOOTH', 'android.permission.BLUETOOTH_ADMIN'].includes(permission.$['android:name'])) {
        permission.$['android:maxSdkVersion'] = '30';
      }
    }
    return mod;
  });
  config = withFinalizedMod(config, ['android', async (mod) => {
    const source = path.join(mod.modRequest.projectRoot, 'res');
    const destination = path.join(mod.modRequest.platformProjectRoot, 'app/src/main/res');
    const copies = [
      ['drawable/ic_launcher_fg.xml', 'drawable/ic_launcher_fg.xml'],
      ['drawable/ic_launcher_fg.xml', 'drawable/splashscreen_logo.xml'],
      ['mipmap-anydpi-v26/ic_launcher.xml', 'mipmap-anydpi-v26/ic_launcher.xml'],
      ['mipmap-anydpi-v26/ic_launcher.xml', 'mipmap-anydpi-v26/ic_launcher_round.xml'],
      ['values/colors.xml', 'values/tv_remote_icon.xml'],
    ];
    for (const [from, to] of copies) {
      const target = path.join(destination, to);
      await fs.mkdir(path.dirname(target), { recursive: true });
      await fs.copyFile(path.join(source, from), target);
    }
    return mod;
  }]);
  return withMainActivity(config, (mod) => {
    if (mod.modResults.language !== 'kt') throw new Error('Wave Remote requires the Expo Kotlin MainActivity template.');
    if (!mod.modResults.contents.includes('WaveRemoteModule.handleVolumeKey')) {
      mod.modResults.contents = mod.modResults.contents.replace(
        /class MainActivity : ReactActivity\(\) \{/,
        `class MainActivity : ReactActivity() {
  override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
    if (me.vwave.remote.WaveRemoteModule.handleVolumeKey(event)) return true
    return super.dispatchKeyEvent(event)
  }
`
      );
      if (!mod.modResults.contents.includes('WaveRemoteModule.handleVolumeKey')) {
        throw new Error('Could not install the TV volume key handler in MainActivity.');
      }
    }
    return mod;
  });
};
