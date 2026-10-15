---
name: TV Remote
description: A familiar dark Android remote with Material setup and menus.
colors:
  background: "#0F1115"
  surface: "#1E222A"
  raised: "#2A2F3A"
  text: "#E9ECF2"
  muted: "#A4ADBE"
  accent: "#3D7BFF"
  power: "#FF5A5F"
  primary: "#ADC6FF"
  on-primary: "#002E69"
  primary-container: "#164788"
  on-primary-container: "#D8E2FF"
  secondary: "#BDC7DC"
  outline: "#7C8595"
  outline-variant: "#424957"
  error: "#FFB4AB"
  on-error: "#690005"
  error-container: "#51262B"
  on-error-container: "#FFDAD6"
typography:
  headline-large: {fontFamily: sans-serif, fontSize: "32px", fontWeight: 400, lineHeight: "40px", letterSpacing: "0px"}
  headline-small: {fontFamily: sans-serif, fontSize: "24px", fontWeight: 400, lineHeight: "32px", letterSpacing: "0px"}
  title-medium: {fontFamily: sans-serif-medium, fontSize: "16px", fontWeight: 500, lineHeight: "24px", letterSpacing: "0.15px"}
  title-small: {fontFamily: sans-serif-medium, fontSize: "14px", fontWeight: 500, lineHeight: "20px", letterSpacing: "0.1px"}
  body-large: {fontFamily: sans-serif, fontSize: "16px", fontWeight: 400, lineHeight: "24px", letterSpacing: "0.15px"}
  body-medium: {fontFamily: sans-serif, fontSize: "14px", fontWeight: 400, lineHeight: "20px", letterSpacing: "0.25px"}
  body-small: {fontFamily: sans-serif, fontSize: "12px", fontWeight: 400, lineHeight: "16px", letterSpacing: "0.4px"}
  label-large: {fontFamily: sans-serif-medium, fontSize: "14px", fontWeight: 500, lineHeight: "20px", letterSpacing: "0.1px"}
  remote-title: {fontSize: "19px", fontWeight: 500}
  remote-label: {fontSize: "15px", fontWeight: 400}
  remote-ok: {fontSize: "20px", fontWeight: 700}
rounded:
  row: "12px"
  card: "16px"
  remote-button: "18px"
  material-button: "20px"
  mode: "24px"
  sheet: "28px"
  round-control: "42px"
spacing:
  compact: "4px"
  small: "8px"
  control-gap: "10px"
  medium: "12px"
  inset: "16px"
  card: "20px"
  page: "24px"
  section: "32px"
components:
  remote-button:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.text}"
    rounded: "{rounded.remote-button}"
    typography: "{typography.remote-label}"
    padding: "0px 4px"
  remote-button-pressed:
    backgroundColor: "{colors.raised}"
  remote-ok:
    backgroundColor: "{colors.accent}"
    textColor: "{colors.text}"
    rounded: "{rounded.round-control}"
    typography: "{typography.remote-ok}"
    size: "84px"
  remote-navigation:
    backgroundColor: "{colors.raised}"
    textColor: "{colors.text}"
    rounded: "{rounded.remote-button}"
  button-contained:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    typography: "{typography.label-large}"
    rounded: "{rounded.material-button}"
  button-destructive:
    backgroundColor: "{colors.error}"
    textColor: "{colors.on-error}"
    rounded: "{rounded.material-button}"
  device-card:
    backgroundColor: "{colors.raised}"
    rounded: "{rounded.card}"
    padding: "{spacing.card}"
  mode-selected:
    backgroundColor: "{colors.raised}"
    textColor: "{colors.text}"
    rounded: "{rounded.mode}"
  sheet:
    backgroundColor: "{colors.surface}"
---

# Design System: TV Remote

## Overview

**Creative North Star: "The familiar Android remote"**

Keep the existing remote's dark palette, control geometry and button order. Setup, settings and menus use React Native Paper's Android Material 3 components. PRODUCT.md supplies this binding direction; MainActivity.java is the reference for the incumbent remote, ahead of older saved screenshots.

This is a code-derived record of the Expo implementation, not a runtime visual approval. Sources are `app/theme.ts`, `app/components/`, `App.tsx` and the installed Paper Android theme. Frontmatter uses portable px strings for numeric native dimensions; interpret them as React Native layout units and native text sizes, not browser sizing requirements.

**Key Characteristics:**

- Dark tonal surfaces with a blue central confirmation control.
- Familiar Material forms, lists and modal menus.
- Preserved remote order and generous central controls.

## Colors

### Primary

`accent` is the saturated blue OK control. `primary` is the lighter Material action, selection and icon color; its container pair supports setup imagery. Keep these distinct roles.

### Secondary

`secondary` is the Paper secondary role. Secondary containers reuse `raised` with `text` foreground.

### Neutral

`background` is the app canvas; `surface` holds remote buttons and sheets; `raised` separates navigation, selected modes and device summaries. `text` and `muted` distinguish primary labels from explanations. `outline` and `outline-variant` belong to the Material field/divider vocabulary. Tonal elevation colors live in the sidecar.

`power` marks power, active mute and disconnected remote status. The softer `error` pair marks Material errors and the explicit Forget TV action; error containers retain their own foreground pair.

**The Two Blues Rule.** Preserve the remote's accent blue; use Paper primary for Material actions and selections.

## Typography

Android Paper uses native sans-serif and sans-serif-medium. The implemented setup heading uses headline-large; sheet headings use headline-small. Title-medium identifies sections and devices; title-small labels settings groups. Body-large, body-medium and body-small support instructions, secondary copy and status. Label-large serves Paper buttons. No custom display font is installed.

The remote retains its own title, ordinary label and bold OK sizes. Status and rocker labels are small (12); mode labels use 14. These inherit Paper text defaults unless overridden; do not invent a new type ramp or normalize them into large marketing headings.

## Layout

The safe-area app contains a centered, scrollable phone layout capped at 600 native units. Remote side padding is 16, setup and menu content generally use 24. Remote rows use a 10-unit gap, while vertical groups commonly use 12. Setup begins with 32 top padding. Scroll and wrapping action rows accommodate available space; there is no custom breakpoint system.

**The Remote Order Rule.** Keep header → power/input/mute → volume/apps/keyboard/channel → playback → mode selector → central pad → 72-unit landing zone → back/home/TV settings.

The D-pad is a three-by-three arrangement of 84-unit circular cells with 8-unit gaps; the pad region has a minimum height of 268. Volume and channel rockers are 64 wide and 144 tall. Remote buttons have a minimum height of 48; bottom navigation uses 52. Sheets occupy at most 90% of screen height, cap width at 600, and pad their bottom by the larger of 24 or the safe-area inset. These are current geometry, not a claim of tested behavior at every font scale or device size.

## Elevation & Depth

The custom remote uses tonal layering with no authored drop shadows. Setup notices use Paper Surface elevation 1; Paper supplies its native elevation behavior. Sheets separate from the canvas through their surface tone and a translucent black scrim. Preserve the theme's elevation ramp rather than inventing a web shadow system.

## Shapes

Remote keys are rounded rectangles; D-pad controls and rockers use the circular-control radius. Cards use the card radius, list/radio rows use the row radius, and the mode selector uses rounded inner choices inside a sheet-radius outer pill. Sheets round only their upper corners. Paper theme roundness is 4, which produces the Material button radius in frontmatter; it is not a universal 4-unit corner rule.

## Components

- **Remote buttons:** centered text or MaterialCommunityIcons (26), clipped ripple, selection haptic and a raised pressed fill. Disabled controls use opacity 0.44. Repeat controls expose a hold hint and stop on release or app backgrounding.
- **D-pad and rockers:** retain the source geometry. OK carries the blue accent; rocker labels remain muted between plus and minus keys.
- **Mode selector and navigation:** mode choices have a minimum height of 48, with raised selected fill and muted unselected text. Bottom navigation retains raised fill and source order.
- **Material buttons and fields:** contained primary actions, outlined secondary actions and text actions use Paper defaults. IP and keyboard inputs are outlined with labels and HelperText; errors and disabled state remain visible. Forget TV requires its existing confirmation step and error-colored action.
- **Cards and lists:** setup devices use surface cards, settings device summaries use raised cards, and app/input menus use Paper list rows with icons and trailing chevrons or progress. Search uses a raised Searchbar. No chip component is currently implemented.
- **Sheets and feedback:** shared heading, drag-handle silhouette, explicit close action, backdrop dismissal and Android back dismissal. The handle is visual; the implementation does not provide dragging. Modal fade is disabled when reduced motion is enabled. Snackbars expose Dismiss and use a 6500ms duration.

The sidecar's HTML/CSS samples translate selected native controls for the design panel. Browser focus treatment is preview-only; native Paper, ripple, accessibility and gesture behavior remain authoritative.

## Do's and Don'ts

### Do:

- **Do** preserve the remote control order, central geometry and landing zone.
- **Do** reuse the dark palette and Paper theme for new setup or menu surfaces.
- **Do** retain accessible labels, selected/disabled states and reduced-motion handling.

### Don't:

- **Don't** use stale screenshots to rearrange the current source remote.
- **Don't** replace the remote with a generic Material dashboard.
- **Don't** treat code-derived dimensions or panel samples as runtime visual approval.
