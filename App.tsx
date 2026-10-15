import { useState } from 'react';
import { StyleSheet, View } from 'react-native';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { PaperProvider, Snackbar, ActivityIndicator } from 'react-native-paper';
import { SafeAreaProvider, SafeAreaView } from 'react-native-safe-area-context';
import { StatusBar } from 'expo-status-bar';
import { theme, palette } from './app/theme';
import { useRemote } from './app/hooks/useRemote';
import { RemoteScreen, type Panel } from './app/components/RemoteScreen';
import { SetupScreen } from './app/components/SetupScreen';
import { SettingsSheet } from './app/components/SettingsSheet';
import { PickerSheet } from './app/components/PickerSheet';
import { FeedbackContext } from './app/components/Sheet';
import { KeyboardSheet } from './app/components/KeyboardSheet';

function RemoteApp() {
  const { state, loading, message, setMessage, run, power } = useRemote();
  const [panel, setPanel] = useState<Panel>(null);
  const showRemote = !!state.host && (state.paired || state.state === 'ready');
  const close = () => setPanel(null);
  return <FeedbackContext.Provider value={{ message, dismiss: () => setMessage('') }}><SafeAreaView style={styles.safe}>
    <StatusBar style="light" />
    {loading ? <View style={styles.loading}><ActivityIndicator size="large" accessibilityLabel="Loading your remote" /></View>
      : showRemote ? <RemoteScreen state={state} setPanel={setPanel} power={() => void power()} run={run} /> : <SetupScreen state={state} run={run} />}
    <SettingsSheet state={state} open={panel === 'settings'} onClose={close} run={run} />
    {panel === 'apps' || panel === 'inputs' ? <PickerSheet key={`${panel}-${state.host}-${state.state}`} kind={panel} onClose={close} report={setMessage} ready={state.state === 'ready'} /> : null}
    {panel === 'keyboard' ? <KeyboardSheet open ready={state.state === 'ready'} onClose={close} /> : null}
    <Snackbar visible={!!message} onDismiss={() => setMessage('')} duration={6500} action={{ label: 'Dismiss', onPress: () => setMessage('') }} style={styles.snackbar}>{message}</Snackbar>
  </SafeAreaView></FeedbackContext.Provider>;
}
export default function App() {
  return <SafeAreaProvider><PaperProvider theme={theme} settings={{ icon: (props) => <MaterialCommunityIcons {...props} name={props.name as keyof typeof MaterialCommunityIcons.glyphMap} /> }}><RemoteApp /></PaperProvider></SafeAreaProvider>;
}
const styles = StyleSheet.create({ safe: { flex: 1, backgroundColor: palette.background }, loading: { flex: 1, alignItems: 'center', justifyContent: 'center' }, snackbar: { marginBottom: 12 } });
