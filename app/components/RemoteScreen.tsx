import { Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { ActivityIndicator, Text, TouchableRipple } from 'react-native-paper';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { native, NativeTouchpad, type RemoteState } from '../../modules/wave-remote';
import { commands, connectionLabel } from '../lib/remote';
import { palette } from '../theme';
import { Dpad, RemoteButton, Rocker } from './RemoteButton';

export type Panel = 'settings' | 'tvs' | 'inputs' | 'apps' | 'keyboard' | null;
export function RemoteScreen({ state, setPanel, power, run }: {
  state: RemoteState; setPanel: (panel: Panel) => void; power: () => void;
  run: (action: () => Promise<unknown>) => Promise<boolean>;
}) {
  const ready = state.state === 'ready';
  const busy = state.waking || state.state === 'connecting' || state.state === 'pairing';
  const key = (name: string) => () => { void run(() => native!.button(name)); };
  return <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
    <View style={styles.header}>
      <Pressable accessibilityRole="button" accessibilityLabel={`${state.name || 'LG TV'}. ${connectionLabel(state)}`} accessibilityHint="Reconnect to your TV" style={styles.titles} onPress={() => { if (!ready && !busy) void run(() => native!.reconnect()); }}>
        <Text style={styles.name} numberOfLines={1}>{state.name || 'LG TV'}</Text>
        <Text style={[styles.status, !ready && !busy && { color: palette.power }]} numberOfLines={2} accessibilityLiveRegion="polite">{connectionLabel(state)}</Text>
      </Pressable>
      {busy ? <ActivityIndicator size={18} style={styles.activity} /> : null}
      <RemoteButton label={state.tvs.length > 1 ? 'Switch TV' : 'Add or switch TV'} icon="swap-horizontal" onPress={() => setPanel('tvs')} style={styles.more} />
      <RemoteButton label="Remote settings" icon="dots-horizontal" onPress={() => setPanel('settings')} style={styles.more} />
    </View>
    <View style={styles.row}>
      <RemoteButton label={ready ? 'Power off' : 'Wake TV'} icon="power" color={palette.power} disabled={busy} onPress={power} style={styles.weight} />
      <RemoteButton label="Inputs" icon="application-import" caption="Inputs" disabled={!ready} onPress={() => setPanel('inputs')} style={styles.weight} />
      <RemoteButton label={state.muted ? 'Unmute' : 'Mute'} icon={state.muted ? 'volume-off' : 'volume-high'} color={state.muted ? palette.power : palette.text} disabled={!ready} onPress={() => { void run(() => native!.request('ssap://audio/setMute', { mute: !state.muted })); }} style={styles.weight} />
    </View>
    <View style={styles.controls}>
      <Rocker label="Volume" up={commands.volumeUp} down={commands.volumeDown} disabled={!ready} />
      <View style={styles.shortcuts}>
        <RemoteButton label="Apps" icon="view-grid-outline" caption="Apps" disabled={!ready} onPress={() => setPanel('apps')} style={styles.shortcut} />
        <RemoteButton label="Keyboard" icon="keyboard-outline" caption="Keyboard" disabled={!ready} onPress={() => setPanel('keyboard')} style={styles.shortcut} />
      </View>
      <Rocker label="Channel" up={commands.channelUp} down={commands.channelDown} disabled={!ready} />
    </View>
    <View style={styles.row}>
      {([['rewind', 'rewind', 'Rewind'], ['play', 'play', 'Play'], ['pause', 'pause', 'Pause'], ['fastForward', 'fast-forward', 'Fast forward']] as const).map(([command, icon, label]) =>
        <RemoteButton key={command} label={label} icon={icon} disabled={!ready} onPress={() => { void run(() => native!.request(`ssap://media.controls/${command}`, null)); }} style={styles.weight} />)}
    </View>
    <View style={styles.modes} accessibilityRole="tablist">
      {([['Buttons', 'gamepad-round-outline'], ['Touchpad', 'gesture-tap']] as const).map(([label, icon], index) => {
        const selected = state.touchpad === (index === 1);
        return <TouchableRipple key={label} onPress={() => { void run(() => native!.preferences(index === 1, state.naturalScrolling)); }} accessibilityRole="tab" accessibilityState={{ selected }} style={[styles.mode, selected && styles.selectedMode]}>
          <View style={styles.modeContent}>
            <MaterialCommunityIcons name={icon} size={18} color={selected ? palette.text : palette.muted} />
            <Text style={{ color: selected ? palette.text : palette.muted, fontSize: 14 }}>{label}</Text>
          </View>
        </TouchableRipple>;
      })}
    </View>
    <View style={styles.pad}>
      {state.touchpad && NativeTouchpad ? <View style={[styles.touchpad, !ready && styles.inactivePad]} pointerEvents={ready ? 'auto' : 'none'}>
        <NativeTouchpad style={styles.nativePad} naturalScrolling={state.naturalScrolling} />
        {!ready ? <View style={styles.offlinePad}><MaterialCommunityIcons name="television-off" size={28} color={palette.muted} /><Text variant="bodyMedium" style={styles.offlineText}>Connect to use the touchpad</Text></View> : null}
      </View> : <Dpad disabled={!ready} />}
    </View>
    <View style={styles.landingZone} />
    <View style={[styles.row, styles.navigation]}>
      <RemoteButton label="Back" icon="arrow-u-left-top" disabled={!ready} onPress={key('BACK')} style={[styles.weight, styles.navButton]} />
      <RemoteButton label="Home" icon="home-outline" disabled={!ready} onPress={key('HOME')} style={[styles.weight, styles.navButton]} />
      <RemoteButton label="TV settings" icon="cog-outline" disabled={!ready} onPress={key('MENU')} style={[styles.weight, styles.navButton]} />
    </View>
  </ScrollView>;
}
const styles = StyleSheet.create({
  content: { flexGrow: 1, paddingHorizontal: 16, paddingTop: 8, paddingBottom: 12, maxWidth: 600, width: '100%', alignSelf: 'center' },
  header: { minHeight: 48, flexDirection: 'row', alignItems: 'center', marginBottom: 12, gap: 8 },
  titles: { flex: 1, minHeight: 48, justifyContent: 'center' }, name: { fontSize: 19, fontWeight: '500', color: palette.text },
  status: { fontSize: 12, color: palette.muted, marginTop: 2 }, activity: { marginHorizontal: 4 }, more: { width: 48 },
  row: { flexDirection: 'row', gap: 10, marginHorizontal: 5, marginBottom: 12 }, weight: { flex: 1 },
  controls: { flexDirection: 'row', marginBottom: 12 }, shortcuts: { flex: 1, marginHorizontal: 12, gap: 12 }, shortcut: { minHeight: 66, flex: 1 },
  modes: { flexDirection: 'row', alignSelf: 'center', minWidth: 240, borderRadius: 28, backgroundColor: palette.surface, padding: 4, marginBottom: 12 },
  mode: { minHeight: 48, paddingHorizontal: 16, alignItems: 'center', justifyContent: 'center', borderRadius: 24, flex: 1 }, selectedMode: { backgroundColor: palette.raised },
  modeContent: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  pad: { flex: 1, minHeight: 268, justifyContent: 'center' },
  touchpad: { flex: 1, minHeight: 268, borderRadius: 28, overflow: 'hidden' }, nativePad: { flex: 1, minHeight: 268 },
  inactivePad: { opacity: 0.6 }, offlinePad: { position: 'absolute', top: 0, right: 0, bottom: 0, left: 0, backgroundColor: palette.surface, alignItems: 'center', justifyContent: 'center', gap: 12 },
  offlineText: { color: palette.muted }, landingZone: { height: 72 }, navigation: { marginBottom: 0 }, navButton: { minHeight: 52, backgroundColor: palette.raised },
});
