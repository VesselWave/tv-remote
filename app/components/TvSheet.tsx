import { ScrollView, StyleSheet } from 'react-native';
import { List } from 'react-native-paper';
import { native, type RemoteState } from '../../modules/wave-remote';
import { palette } from '../theme';
import { Sheet } from './Sheet';

export function TvSheet({ state, open, onClose, onAdd, run }: {
  state: RemoteState; open: boolean; onClose: () => void; onAdd: () => void;
  run: (action: () => Promise<unknown>) => Promise<boolean>;
}) {
  return <Sheet open={open} onClose={onClose} title="Your TVs" subtitle="Pairings for every TV stay on this phone.">
    <ScrollView contentContainerStyle={styles.content}>
      {state.tvs.map((tv) => {
        const current = tv.host === state.host;
        return <List.Item key={tv.host} title={tv.name || 'LG TV'} description={tv.paired ? tv.host : `${tv.host} · Not paired yet`}
          titleNumberOfLines={2} accessibilityState={{ selected: current }}
          left={(props) => <List.Icon {...props} icon="television" color={current ? '#ADC6FF' : palette.muted} />}
          right={current ? (props) => <List.Icon {...props} icon="check" color="#ADC6FF" /> : undefined}
          onPress={() => {
            if (current) { onClose(); return; }
            void run(() => native!.connect(tv.host, tv.name || 'LG TV', null)).then((success) => { if (success) onClose(); });
          }} style={[styles.item, current && styles.current]} />;
      })}
      <List.Item title="Add a TV" description="Find another TV or enter its IP address" left={(props) => <List.Icon {...props} icon="plus" />} onPress={onAdd} style={styles.item} />
    </ScrollView>
  </Sheet>;
}
const styles = StyleSheet.create({
  content: { paddingHorizontal: 12, paddingBottom: 8, gap: 4 },
  item: { paddingHorizontal: 12, minHeight: 72, borderRadius: 16 }, current: { backgroundColor: palette.raised },
});
