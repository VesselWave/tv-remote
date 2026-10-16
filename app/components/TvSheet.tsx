import { useState } from 'react';
import { ScrollView, StyleSheet, View } from 'react-native';
import { Button, HelperText, IconButton, List, TextInput } from 'react-native-paper';
import { native, type RemoteState, type SavedTv } from '../../modules/wave-remote';
import { palette } from '../theme';
import { Sheet } from './Sheet';

export function TvSheet({ state, open, onClose, onAdd, run }: {
  state: RemoteState; open: boolean; onClose: () => void; onAdd: () => void;
  run: (action: () => Promise<unknown>) => Promise<boolean>;
}) {
  const [editing, setEditing] = useState<SavedTv | null>(null);
  const [label, setLabel] = useState('');
  const close = () => { setEditing(null); onClose(); };
  const edit = (tv: SavedTv) => { setLabel(tv.label || ''); setEditing(tv); };
  const save = (host: string, value: string) => { void run(() => native!.rename(host, value)).then((success) => { if (success) setEditing(null); }); };
  if (editing) {
    const own = editing.tvName || 'LG TV';
    return <Sheet open={open} onClose={close} title="Rename TV" subtitle={editing.host}>
      <View style={styles.form}>
        <TextInput label="Name on this phone" placeholder={own} mode="outlined" value={label} onChangeText={setLabel} maxLength={40} autoFocus returnKeyType="done" onSubmitEditing={() => save(editing.host, label)} />
        <HelperText type="info">Leave empty to use the TV’s own name, {own}.</HelperText>
        <View style={styles.actions}>
          <Button onPress={() => setEditing(null)}>Cancel</Button>
          <Button mode="contained" onPress={() => save(editing.host, label)}>Save</Button>
        </View>
      </View>
    </Sheet>;
  }
  return <Sheet open={open} onClose={close} title="Your TVs" subtitle="Pairings for every TV stay on this phone.">
    <ScrollView contentContainerStyle={styles.content}>
      {state.tvs.map((tv) => {
        const current = tv.host === state.host;
        return <List.Item key={tv.host} title={tv.name || 'LG TV'} description={tv.paired ? tv.host : `${tv.host} · Not paired yet`}
          titleNumberOfLines={2} accessibilityState={{ selected: current }}
          accessibilityLabel={`${tv.name || 'LG TV'}, ${tv.host}${tv.paired ? '' : ', not paired yet'}${current ? ', current TV' : ''}`}
          left={(props) => <List.Icon {...props} icon="television" color={current ? '#ADC6FF' : palette.muted} />}
          right={() => <View style={styles.trailing}>
            {current ? <List.Icon icon="check" color="#ADC6FF" /> : null}
            <IconButton icon="pencil-outline" accessibilityLabel={`Rename ${tv.name || 'LG TV'}`} onPress={() => edit(tv)} style={styles.rename} />
          </View>}
          onPress={() => {
            if (current) { close(); return; }
            void run(() => native!.connect(tv.host, tv.name || 'LG TV', null)).then((success) => { if (success) close(); });
          }} style={[styles.item, current && styles.current]} />;
      })}
      <List.Item title="Add a TV" accessibilityLabel="Add a TV" description="Find another TV or enter its IP address" left={(props) => <List.Icon {...props} icon="plus" />} onPress={onAdd} style={styles.item} />
    </ScrollView>
  </Sheet>;
}
const styles = StyleSheet.create({
  content: { paddingHorizontal: 12, paddingBottom: 8, gap: 4 },
  item: { paddingHorizontal: 12, minHeight: 72, borderRadius: 16 }, current: { backgroundColor: palette.raised },
  trailing: { flexDirection: 'row', alignItems: 'center', gap: 4 }, rename: { width: 48, height: 48, margin: 0 },
  form: { paddingHorizontal: 24, gap: 4 }, actions: { flexDirection: 'row', justifyContent: 'flex-end', gap: 12, marginTop: 12 },
});
