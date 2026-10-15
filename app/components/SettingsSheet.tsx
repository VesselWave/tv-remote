import { useState } from 'react';
import { ScrollView, StyleSheet, View } from 'react-native';
import { Button, Divider, Icon, List, Text, TouchableRipple } from 'react-native-paper';
import { native, type RemoteState } from '../../modules/tv-remote';
import { connectionLabel } from '../lib/remote';
import { palette } from '../theme';
import { Sheet } from './Sheet';

export function SettingsSheet({ state, open, onClose, run }: {
  state: RemoteState; open: boolean; onClose: () => void;
  run: (action: () => Promise<unknown>) => Promise<boolean>;
}) {
  const [confirmForget, setConfirmForget] = useState(false);
  const close = () => { setConfirmForget(false); onClose(); };
  return <Sheet open={open} onClose={close} title={confirmForget ? 'Forget this TV?' : 'Remote settings'} subtitle={confirmForget ? undefined : 'Make this remote feel right for you.'}>
    {confirmForget ? <View style={styles.confirm}>
      <Text variant="bodyLarge">This removes the saved pairing for {state.name || 'your TV'}. You’ll need to approve a new connection on the TV to use it again.</Text>
      <View style={styles.actions}><Button onPress={() => setConfirmForget(false)}>Keep TV</Button>
        <Button mode="contained" icon="link-off" buttonColor="#FFB4AB" textColor="#690005" onPress={() => { void run(() => native!.forget()).then((success) => { if (success) close(); }); }}>Forget TV</Button></View>
    </View> : <ScrollView contentContainerStyle={styles.content}>
      <View style={styles.device}><Icon source="television" size={28} color="#ADC6FF" /><View style={styles.deviceInfo}>
        <Text variant="titleMedium">{state.name || 'LG TV'}</Text><Text variant="bodyMedium" style={styles.secondary}>{state.host}</Text>
        <Text variant="bodySmall" style={styles.secondary}>{connectionLabel(state)}</Text>
      </View></View>
      <Text variant="titleSmall" style={styles.sectionTitle} accessibilityRole="header">Touchpad scrolling</Text>
      <Text variant="bodyMedium" style={styles.explanation}>Applies to the right-edge strip and two-finger gestures.</Text>
      {[{ natural: true, title: 'Natural', description: 'Content follows your fingers' }, { natural: false, title: 'Traditional', description: 'Content moves opposite to your fingers' }].map(({ natural, title, description }) =>
        <TouchableRipple key={title} onPress={() => { void run(() => native!.preferences(state.touchpad, natural)); }} accessibilityRole="radio" accessibilityState={{ checked: state.naturalScrolling === natural }} style={styles.radio}>
          <View style={styles.radioContent}><Icon source={state.naturalScrolling === natural ? 'radiobox-marked' : 'radiobox-blank'} size={24} color={state.naturalScrolling === natural ? '#ADC6FF' : palette.muted} /><View style={styles.deviceInfo}><Text variant="bodyLarge">{title}</Text><Text variant="bodyMedium" style={styles.secondary}>{description}</Text></View></View>
        </TouchableRipple>)}
      <Divider style={styles.divider} />
      <List.Item title="Reconnect" description="Start a fresh connection to this TV" left={(props) => <List.Icon {...props} icon="refresh" />} onPress={() => { void run(() => native!.reconnect()).then((success) => { if (success) close(); }); }} style={styles.action} />
      <List.Item title="Forget this TV" titleStyle={{ color: '#FFB4AB' }} description="Remove its saved pairing" left={(props) => <List.Icon {...props} color="#FFB4AB" icon="link-off" />} onPress={() => setConfirmForget(true)} style={styles.action} />
      <View style={styles.local}><Icon source="shield-check-outline" color={palette.muted} size={18} /><Text variant="bodySmall" style={styles.secondary}>Your pairing stays on this phone.</Text></View>
    </ScrollView>}
  </Sheet>;
}
const styles = StyleSheet.create({
  content: { paddingBottom: 8 }, device: { flexDirection: 'row', gap: 16, padding: 20, marginHorizontal: 24, borderRadius: 16, backgroundColor: palette.raised },
  deviceInfo: { flex: 1, gap: 4 }, secondary: { color: palette.muted }, sectionTitle: { marginTop: 28, marginBottom: 8, marginHorizontal: 24, color: '#ADC6FF' },
  explanation: { marginHorizontal: 24, color: palette.muted, marginBottom: 12 }, radio: { marginHorizontal: 12, borderRadius: 12 }, radioContent: { flexDirection: 'row', alignItems: 'center', padding: 16, gap: 16, minHeight: 80 },
  divider: { marginHorizontal: 24, marginVertical: 20 }, action: { paddingHorizontal: 12, minHeight: 80 },
  local: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8, padding: 24 }, confirm: { paddingHorizontal: 24, gap: 24 }, actions: { flexDirection: 'row', justifyContent: 'flex-end', gap: 12, flexWrap: 'wrap' },
});
