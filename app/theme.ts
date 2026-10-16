import { MD3DarkTheme } from 'react-native-paper';

// Preserve the existing remote's palette and geometry. Paper menus share the same color roles.
export const palette = {
  background: '#0F1115', surface: '#1E222A', raised: '#2A2F3A',
  text: '#E9ECF2', muted: '#A4ADBE', accent: '#3D7BFF', power: '#FF5A5F',
  // One look for every TV control while the TV can't take input, whatever its usual fill.
  disabled: '#171A20', onDisabled: '#4E5564',
};
export const theme = {
  ...MD3DarkTheme,
  roundness: 4,
  colors: {
    ...MD3DarkTheme.colors,
    primary: '#ADC6FF', onPrimary: '#002E69', primaryContainer: '#164788', onPrimaryContainer: '#D8E2FF',
    secondary: '#BDC7DC', secondaryContainer: palette.raised, onSecondaryContainer: palette.text,
    background: palette.background, onBackground: palette.text,
    surface: palette.surface, onSurface: palette.text, surfaceVariant: palette.raised, onSurfaceVariant: palette.muted,
    outline: '#7C8595', outlineVariant: '#424957', error: '#FFB4AB', onError: '#690005',
    errorContainer: '#51262B', onErrorContainer: '#FFDAD6',
    elevation: { level0: 'transparent', level1: '#20252E', level2: '#242A35', level3: '#28303D', level4: '#2B3340', level5: '#303948' },
  },
};
