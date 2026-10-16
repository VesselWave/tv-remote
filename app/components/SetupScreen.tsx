import { useEffect, useRef, useState } from 'react';
import { ScrollView, StyleSheet, View } from 'react-native';
import { ActivityIndicator, Button, HelperText, Icon, List, Surface, Text, TextInput } from 'react-native-paper';
import { native, type FoundTv, type RemoteState } from '../../modules/wave-remote';
import { errorMessage, normalizeHost } from '../lib/remote';
import { palette } from '../theme';

export function SetupScreen({ state, run }: { state: RemoteState; run: (action: () => Promise<unknown>) => Promise<boolean> }) {
  const [host, setHost] = useState(state.host || '');
  const [invalid, setInvalid] = useState(false);
  const [searching, setSearching] = useState(!!native);
  const [searched, setSearched] = useState(false);
  const [devices, setDevices] = useState<FoundTv[]>([]);
  const [searchError, setSearchError] = useState('');
  const mounted = useRef(true);
  const searchingRef = useRef(false);
  const busy = state.state === 'connecting' || state.state === 'pairing';
  const discover = async () => {
    if (!native) return;
    try { const found = await native.discover(); if (mounted.current) setDevices(found); }
    catch (error) { if (mounted.current) setSearchError(errorMessage(error)); }
    finally { searchingRef.current = false; if (mounted.current) { setSearching(false); setSearched(true); } }
  };
  const search = () => {
    if (!native || searchingRef.current) return;
    searchingRef.current = true; setSearching(true); setSearchError('');
    void discover();
  };
  useEffect(() => {
    mounted.current = true; searchingRef.current = true;
    if (native) {
      void native.discover().then((found) => { if (mounted.current) setDevices(found); })
        .catch((error: unknown) => { if (mounted.current) setSearchError(errorMessage(error)); })
        .finally(() => { searchingRef.current = false; if (mounted.current) { setSearching(false); setSearched(true); } });
    }
    return () => { mounted.current = false; };
  }, []);
  const connect = (device?: FoundTv) => {
    const address = normalizeHost(device?.host || host);
    if (!address) { setInvalid(true); return; }
    setInvalid(false);
    void run(() => native!.connect(address, device?.name || 'LG TV', device?.btMac || null));
  };
  return <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
    <View style={styles.logo}><Icon source="television" size={36} color="#ADC6FF" /></View>
    <Text variant="headlineLarge" accessibilityRole="header">Pick your TV</Text>
    <Text variant="bodyLarge" style={styles.intro}>Your phone and TV need to be on the same Wi-Fi network.</Text>
    {!native ? <Surface elevation={1} style={styles.notice}>
      <Text variant="titleMedium">Android development build required</Text>
      <Text variant="bodyMedium" style={styles.secondary}>TV control uses a custom native module. Open the installed Wave Remote app; Expo Go cannot connect to your TV.</Text>
    </Surface> : null}
    {busy ? <Surface elevation={1} style={styles.notice}>
      <View style={styles.busyHeading}><ActivityIndicator size={22} /><Text variant="titleMedium">{state.state === 'pairing' ? 'Approve on your TV' : 'Connecting to your TV'}</Text></View>
      <Text variant="bodyMedium" style={styles.secondary}>{state.state === 'pairing' ? 'Accept the connection request on the TV using its own remote. Keep this screen open.' : `Connecting to ${state.host}…`}</Text>
      <Button onPress={() => { void run(() => native!.disconnect()); }}>Cancel</Button>
    </Surface> : state.detail ? <Surface elevation={1} style={styles.notice}>
      <Text variant="titleMedium">Couldn’t connect</Text><Text style={styles.secondary}>{state.detail}</Text>
      <Button icon="refresh" onPress={() => { void run(() => native!.reconnect()); }}>Try again</Button>
    </Surface> : null}
    <View style={styles.sectionHeading}><Text variant="titleMedium" accessibilityRole="header">Nearby TVs</Text>
      {searching ? <ActivityIndicator size={20} /> : <Button icon="refresh" onPress={() => void search()} disabled={!native || busy}>Search again</Button>}
    </View>
    <View style={styles.devices}>
      {devices.map((device) => <List.Item key={device.host} title={device.name} description={device.host} titleNumberOfLines={2} left={(props) => <List.Icon {...props} icon="television" />} right={(props) => <List.Icon {...props} icon="chevron-right" />} onPress={() => connect(device)} disabled={busy} style={styles.device} />)}
      {searching ? <Text variant="bodyMedium" style={styles.empty}>Looking for LG webOS TVs…</Text> : null}
      {!searching && !devices.length && searched ? <Text variant="bodyMedium" style={styles.empty}>{searchError || 'No TVs found. Turn your TV on, check Wi-Fi, or enter its IP address below.'}</Text> : null}
    </View>
    <Text variant="titleMedium" style={styles.manualHeading} accessibilityRole="header">Connect by IP address</Text>
    <TextInput label="TV IP address" placeholder="192.168.86.112" mode="outlined" value={host} onChangeText={(value) => { setHost(value); setInvalid(false); }} keyboardType="decimal-pad" autoCorrect={false} autoCapitalize="none" returnKeyType="go" onSubmitEditing={() => connect()} error={invalid} disabled={!native || busy} />
    <HelperText type={invalid ? 'error' : 'info'}>{invalid ? 'Enter a valid IP address, such as 192.168.86.112.' : 'Find it in your TV’s Settings → Network.'}</HelperText>
    <Button mode="contained" icon="lan-connect" onPress={() => connect()} disabled={!native || busy || !host.trim()} contentStyle={styles.connect}>Connect</Button>
    <View style={styles.privacy}><Icon source="shield-check-outline" size={20} color={palette.muted} /><Text variant="bodySmall" style={styles.secondary}>Local network only. No account required.</Text></View>
  </ScrollView>;
}
const styles = StyleSheet.create({
  content: { padding: 24, paddingTop: 32, flexGrow: 1, maxWidth: 600, width: '100%', alignSelf: 'center' },
  logo: { width: 72, height: 72, borderRadius: 20, backgroundColor: '#164788', alignItems: 'center', justifyContent: 'center', marginBottom: 24 },
  intro: { color: palette.muted, marginTop: 12, marginBottom: 24 }, notice: { borderRadius: 16, padding: 20, marginBottom: 20, gap: 12 },
  secondary: { color: palette.muted }, busyHeading: { flexDirection: 'row', alignItems: 'center', gap: 12, flexWrap: 'wrap' },
  sectionHeading: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', minHeight: 48 },
  devices: { gap: 8 }, device: { backgroundColor: palette.surface, borderRadius: 16, minHeight: 80 }, empty: { color: palette.muted, paddingVertical: 20, lineHeight: 22 },
  manualHeading: { marginTop: 32, marginBottom: 16 }, connect: { minHeight: 52 }, privacy: { flexDirection: 'row', justifyContent: 'center', alignItems: 'center', gap: 8, marginTop: 32 },
});
