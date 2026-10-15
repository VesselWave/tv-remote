import { request } from '../lib/transport';
import { useEffect, useRef, useState } from 'react';
import { ScrollView, StyleSheet, View } from 'react-native';
import { Button, HelperText, TextInput } from 'react-native-paper';
import { errorMessage } from '../lib/remote';
import { Sheet } from './Sheet';

export function KeyboardSheet({ open, ready, onClose }: { open: boolean; ready: boolean; onClose: () => void }) {
  const [text, setText] = useState('');
  const [sending, setSending] = useState(false);
  const [error, setError] = useState('');
  const pending = useRef(false);
  const mounted = useRef(true);
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
  const send = async (enter: boolean) => {
    if (pending.current || !ready) return;
    pending.current = true; setSending(true); setError('');
    let remaining = text;
    try {
      if (remaining) {
        await request('ssap://com.webos.service.ime/insertText', { text: remaining, replace: 0 });
        remaining = ''; if (mounted.current) setText('');
      }
      if (!mounted.current) return;
      if (enter) await request('ssap://com.webos.service.ime/sendEnterKey');
      if (mounted.current) onClose();
    } catch (issue) { if (mounted.current) setError(errorMessage(issue)); }
    finally { pending.current = false; if (mounted.current) setSending(false); }
  };
  return <Sheet open={open} onClose={onClose} title="Keyboard" subtitle="Type into the focused field on your TV.">
    <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
      <TextInput mode="outlined" label="Text for your TV" placeholder="Search for something to watch" value={text} onChangeText={setText} multiline numberOfLines={3} autoFocus={open} disabled={sending || !ready} />
      <HelperText type={error || !ready ? 'error' : 'info'}>{!ready ? 'Your TV disconnected. Reconnect to send text.' : error || 'Select a text field on the TV before sending.'}</HelperText>
      <View style={styles.actions}>
        <Button mode="outlined" icon="send" disabled={sending || !ready || !text} onPress={() => void send(false)} contentStyle={styles.button}>Send</Button>
        <Button mode="contained" icon="keyboard-return" loading={sending} disabled={sending || !ready} onPress={() => void send(true)} contentStyle={styles.button}>Send + Enter</Button>
      </View>
    </ScrollView>
  </Sheet>;
}
const styles = StyleSheet.create({ content: { paddingHorizontal: 24, gap: 12 }, actions: { flexDirection: 'row', gap: 12, justifyContent: 'flex-end', flexWrap: 'wrap' }, button: { minHeight: 48 } });
