import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import { AccessibilityInfo, KeyboardAvoidingView, Modal, Platform, Pressable, StyleSheet, View } from 'react-native';
import { IconButton, Snackbar, Text } from 'react-native-paper';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { palette } from '../theme';

export const FeedbackContext = createContext({ message: '', dismiss: () => {} });

export function Sheet({ title, subtitle, open, onClose, children }: {
  title: string; subtitle?: string; open: boolean; onClose: () => void; children: ReactNode;
}) {
  const insets = useSafeAreaInsets();
  const feedback = useContext(FeedbackContext);
  const [reducedMotion, setReducedMotion] = useState(true);
  useEffect(() => {
    void AccessibilityInfo.isReduceMotionEnabled().then(setReducedMotion);
    const listener = AccessibilityInfo.addEventListener('reduceMotionChanged', setReducedMotion);
    return () => listener.remove();
  }, []);
  return <Modal visible={open} transparent animationType={reducedMotion ? 'none' : 'fade'} onRequestClose={onClose} statusBarTranslucent>
    <KeyboardAvoidingView style={styles.overlay} behavior={Platform.OS === 'ios' ? 'padding' : 'height'}>
      <Pressable style={StyleSheet.absoluteFill} onPress={onClose} accessibilityLabel="Close menu" accessibilityRole="button" />
      <View style={[styles.sheet, { paddingBottom: Math.max(24, insets.bottom) }]} accessibilityViewIsModal>
        <View style={styles.handle} />
        <View style={styles.heading}>
          <View style={styles.title}><Text variant="headlineSmall" accessibilityRole="header">{title}</Text>
            {subtitle ? <Text variant="bodyMedium" style={styles.subtitle}>{subtitle}</Text> : null}</View>
          <IconButton icon="close" accessibilityLabel={`Close ${title}`} onPress={onClose} size={24} style={styles.close} />
        </View>
        {children}
      </View>
      <Snackbar visible={!!feedback.message} onDismiss={feedback.dismiss} duration={6500} action={{ label: 'Dismiss', onPress: feedback.dismiss }}>{feedback.message}</Snackbar>
    </KeyboardAvoidingView>
  </Modal>;
}
const styles = StyleSheet.create({
  overlay: { flex: 1, justifyContent: 'flex-end', backgroundColor: '#00000099' },
  sheet: { backgroundColor: palette.surface, maxHeight: '90%', borderTopLeftRadius: 28, borderTopRightRadius: 28, paddingBottom: 28, width: '100%', maxWidth: 600, alignSelf: 'center' },
  handle: { width: 32, height: 4, borderRadius: 2, backgroundColor: '#7C8595', alignSelf: 'center', marginTop: 12, marginBottom: 8 },
  heading: { flexDirection: 'row', paddingLeft: 24, paddingRight: 8, alignItems: 'center', paddingBottom: 16 },
  title: { flex: 1 }, subtitle: { color: palette.muted, marginTop: 4 }, close: { width: 48, height: 48 },
});
