import { request } from '../lib/transport';
import { useEffect, useRef, useState } from 'react';
import { FlatList, StyleSheet, View } from 'react-native';
import { ActivityIndicator, Button, Icon, List, Searchbar, Text } from 'react-native-paper';
import { native, type TvApp, type TvInput } from '../../modules/tv-remote';
import { commands, errorMessage } from '../lib/remote';
import { palette } from '../theme';
import { Sheet } from './Sheet';

type Choice = { id: string; title: string; description?: string; params?: Record<string, unknown> };
export function PickerSheet({ kind, onClose, report, ready }: {
  kind: 'apps' | 'inputs'; onClose: () => void; report: (message: string) => void; ready: boolean;
}) {
  const [items, setItems] = useState<Choice[]>([]);
  const [query, setQuery] = useState('');
  const [loading, setLoading] = useState(ready);
  const [error, setError] = useState('');
  const [acting, setActing] = useState<string | null>(null);
  const [retry, setRetry] = useState(0);
  const generation = useRef(0);
  const activeAction = useRef(false);
  useEffect(() => {
    const token = ++generation.current;
    if (!ready) return;
    void request(kind === 'apps' ? commands.apps : commands.inputs).then((response) => {
      if (generation.current !== token) return;
      const choices: Choice[] = kind === 'apps'
        ? (response.launchPoints || []).filter((app: TvApp) => !!app.id).map((app) => ({ id: app.id, title: app.title || app.id, params: app.params }))
        : (response.devices || []).filter((input: TvInput) => !!input.id).map((input) => ({ id: input.id, title: input.label || input.id, description: input.connected === false ? 'Nothing connected' : input.id }));
      setItems(choices.sort((a, b) => a.title.localeCompare(b.title)));
    }).catch((issue: unknown) => { if (generation.current === token) setError(errorMessage(issue)); })
      .finally(() => { if (generation.current === token) setLoading(false); });
    return () => { generation.current = token + 1; };
  }, [kind, retry, ready]);
  const choose = async (item: Choice) => {
    if (activeAction.current || !ready) return;
    activeAction.current = true;
    const token = generation.current;
    setActing(item.id);
    try {
      await native!.request(kind === 'apps' ? 'ssap://system.launcher/launch' : 'ssap://tv/switchInput',
        kind === 'apps' ? { id: item.id, ...(item.params ? { params: item.params } : {}) } : { inputId: item.id });
      if (token === generation.current) onClose();
    } catch (issue) { if (token === generation.current) report(errorMessage(issue)); }
    finally { if (token === generation.current) { setActing(null); activeAction.current = false; } }
  };
  const filtered = items.filter((item) => item.title.toLowerCase().includes(query.toLowerCase()));
  return <Sheet open={!!kind} onClose={onClose} title={kind === 'apps' ? 'Apps' : 'Inputs'} subtitle={kind === 'apps' ? 'Open an app on your TV.' : 'Choose what’s on your screen.'}>
    {kind === 'apps' && !loading && !error && items.length > 0 ? <Searchbar placeholder="Search apps" accessibilityLabel="Search TV apps" value={query} onChangeText={setQuery} style={styles.search} /> : null}
    {!ready ? <View style={styles.empty}><Text>Your TV disconnected. Close this menu and reconnect.</Text></View> : loading ? <View style={styles.empty}><ActivityIndicator /><Text style={styles.secondary}>Loading from your TV…</Text></View> : error ? <View style={styles.empty}><Icon source="wifi-alert" size={36} color={palette.muted} /><Text variant="bodyLarge" style={styles.center}>{error}</Text><Button icon="refresh" onPress={() => { setError(''); setLoading(true); setRetry((value) => value + 1); }} disabled={!ready}>Try again</Button></View> :
      <FlatList data={filtered} keyExtractor={(item) => item.id} keyboardShouldPersistTaps="handled" contentContainerStyle={styles.list} renderItem={({ item }) =>
        <List.Item title={item.title} titleNumberOfLines={2} description={item.description} left={(props) => <List.Icon {...props} icon={kind === 'apps' ? 'view-grid-outline' : 'application-import'} />} right={(props) => acting === item.id ? <ActivityIndicator size={20} style={styles.progress} /> : <List.Icon {...props} icon="chevron-right" />} onPress={() => void choose(item)} disabled={!!acting || !ready} style={styles.item} />
      } ListEmptyComponent={<View style={styles.empty}><Icon source={kind === 'apps' ? 'view-grid-outline' : 'application-import'} size={40} color={palette.muted} /><Text variant="titleMedium">{query ? 'No matching apps' : kind === 'apps' ? 'No apps reported' : 'No inputs reported'}</Text><Text style={styles.secondary}>{query ? 'Try a different app name.' : 'Your TV returned an empty list.'}</Text></View>} />}
  </Sheet>;
}
const styles = StyleSheet.create({
  search: { marginHorizontal: 24, marginBottom: 12, backgroundColor: palette.raised }, list: { paddingHorizontal: 12, paddingBottom: 16 },
  item: { minHeight: 72, borderRadius: 12 }, empty: { alignItems: 'center', padding: 32, gap: 16 }, secondary: { color: palette.muted, textAlign: 'center' }, center: { textAlign: 'center' }, progress: { margin: 16 },
});
