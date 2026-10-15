import { useEffect, useRef, useState, type ComponentProps } from 'react';
import { AppState, Pressable, StyleSheet, View, type StyleProp, type ViewStyle } from 'react-native';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { Text } from 'react-native-paper';
import * as Haptics from 'expo-haptics';
import { native } from '../../modules/tv-remote';
import { palette } from '../theme';

type Props = {
  label: string;
  icon?: ComponentProps<typeof MaterialCommunityIcons>['name'];
  /** Short visible text under the icon; `label` remains the accessible name. */
  caption?: string;
  onPress?: () => void;
  repeat?: { key: string; pointer?: boolean };
  disabled?: boolean;
  accent?: boolean;
  color?: string;
  style?: StyleProp<ViewStyle>;
};
export function RemoteButton({ label, icon, caption, onPress, repeat, disabled, accent, color = palette.text, style }: Props) {
  const [pressed, setPressed] = useState(false);
  const held = useRef(false);
  const stop = () => {
    setPressed(false);
    if (held.current) { held.current = false; void native?.stopHeld(); }
  };
  useEffect(() => {
    const subscription = AppState.addEventListener('change', (next) => {
      if (next !== 'active' && held.current) { held.current = false; setPressed(false); void native?.stopHeld(); }
    });
    return () => { subscription.remove(); if (held.current) void native?.stopHeld(); };
  }, []);
  useEffect(() => { if (disabled) { held.current = false; void native?.stopHeld(); } }, [disabled]);
  return (
    <Pressable
      accessibilityRole="button" accessibilityLabel={label} accessibilityState={{ disabled: !!disabled }}
      accessibilityHint={repeat ? 'Touch and hold to repeat' : undefined}
      disabled={disabled}
      android_ripple={{ color: '#FFFFFF22', foreground: true }}
      onPressIn={() => {
        setPressed(true);
        void Haptics.selectionAsync().catch(() => {});
        if (repeat) { held.current = true; void native?.startHeld(repeat.key, !!repeat.pointer); }
      }}
      onPressOut={stop}
      onPress={onPress}
      accessibilityActions={repeat ? [{ name: 'activate', label }] : undefined}
      onAccessibilityAction={() => {
        if (repeat?.pointer) void native?.button(repeat.key);
        else if (repeat) void native?.request(repeat.key, null).catch(() => {});
      }}
      style={[styles.button, { backgroundColor: accent ? palette.accent : palette.surface }, style,
        pressed && !disabled && styles.pressed, disabled && styles.disabled]}
    >
      {icon ? <>
        <MaterialCommunityIcons name={icon} color={color} size={26} />
        {caption ? <Text style={[styles.caption, { color }]} numberOfLines={1}>{caption}</Text> : null}
      </> : <Text style={[styles.label, { color, fontSize: label === 'OK' ? 20 : 15, fontWeight: label === 'OK' ? '700' : '400' }]}>{label}</Text>}
    </Pressable>
  );
}
export function Dpad({ disabled }: { disabled: boolean }) {
  return <View style={styles.dpad} accessibilityLabel="Directional controls">
    <View style={styles.dpadRow}><View style={styles.spacer} /><RemoteButton label="Up" icon="chevron-up" repeat={{ key: 'UP', pointer: true }} disabled={disabled} style={styles.round} /><View style={styles.spacer} /></View>
    <View style={styles.dpadRow}>
      <RemoteButton label="Left" icon="chevron-left" repeat={{ key: 'LEFT', pointer: true }} disabled={disabled} style={styles.round} />
      <RemoteButton label="OK" onPress={() => void native?.button('ENTER')} disabled={disabled} accent style={styles.round} />
      <RemoteButton label="Right" icon="chevron-right" repeat={{ key: 'RIGHT', pointer: true }} disabled={disabled} style={styles.round} />
    </View>
    <View style={styles.dpadRow}><View style={styles.spacer} /><RemoteButton label="Down" icon="chevron-down" repeat={{ key: 'DOWN', pointer: true }} disabled={disabled} style={styles.round} /><View style={styles.spacer} /></View>
  </View>;
}
export function Rocker({ label, up, down, disabled }: { label: string; up: string; down: string; disabled: boolean }) {
  return <View style={styles.rocker}>
    <RemoteButton label={`${label} up`} icon={label === 'Volume' ? 'plus' : 'chevron-up'} repeat={{ key: up }} disabled={disabled} style={styles.rockerButton} />
    <Text style={styles.rockerLabel}>{label === 'Volume' ? 'VOL' : 'CH'}</Text>
    <RemoteButton label={`${label} down`} icon={label === 'Volume' ? 'minus' : 'chevron-down'} repeat={{ key: down }} disabled={disabled} style={styles.rockerButton} />
  </View>;
}
const styles = StyleSheet.create({
  button: { minHeight: 48, borderRadius: 18, alignItems: 'center', justifyContent: 'center', overflow: 'hidden', paddingHorizontal: 4 },
  label: { textAlign: 'center' }, caption: { fontSize: 12, marginTop: 2, textAlign: 'center' }, pressed: { backgroundColor: palette.raised }, disabled: { opacity: 0.44 },
  dpad: { alignSelf: 'center', gap: 8 }, dpadRow: { flexDirection: 'row', gap: 8 },
  round: { width: 84, height: 84, borderRadius: 42 }, spacer: { width: 84, height: 84 },
  rocker: { width: 64, height: 144, borderRadius: 42, backgroundColor: palette.surface, overflow: 'hidden' },
  rockerButton: { flex: 1, borderRadius: 42 }, rockerLabel: { color: palette.muted, textAlign: 'center', fontSize: 12 },
});
