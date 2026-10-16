---
name: Wave Remote
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
  web-primary-hover: "#5A8FFF"
  web-on-primary: "#FFFFFF"
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
  web-display: {fontFamily: "Archivo Variable, ui-sans-serif, system-ui, sans-serif", fontSize: "clamp(3.5rem, 9vw, 6rem)", fontWeight: 780, lineHeight: 0.9, letterSpacing: "-0.035em", fontVariation: "'wdth' 112"}
  web-lede: {fontFamily: "Archivo Variable, ui-sans-serif, system-ui, sans-serif", fontSize: "clamp(1.125rem, 2vw, 1.3125rem)", fontWeight: 400, lineHeight: 1.375}
  web-body: {fontFamily: "Archivo Variable, ui-sans-serif, system-ui, sans-serif", fontSize: "17px", fontWeight: 400, lineHeight: 1.5}
  web-action: {fontFamily: "Archivo Variable, ui-sans-serif, system-ui, sans-serif", fontSize: "19px", fontWeight: 700}
  web-label: {fontFamily: "Archivo Variable, ui-sans-serif, system-ui, sans-serif", fontSize: "15px", fontWeight: 500}
  web-small: {fontFamily: "Archivo Variable, ui-sans-serif, system-ui, sans-serif", fontSize: "13px", fontWeight: 400}
rounded:
  row: "12px"
  card: "16px"
  remote-button: "18px"
  material-button: "20px"
  mode: "24px"
  sheet: "28px"
  round-control: "42px"
  web-full: "9999px"
  web-phone-screen: "34px"
  web-phone-frame: "44px"
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
  web-button-primary:
    backgroundColor: "{colors.accent}"
    textColor: "{colors.web-on-primary}"
    typography: "{typography.web-action}"
    rounded: "{rounded.web-full}"
    padding: "0px 32px 0px 24px"
    height: "60px"
  web-button-primary-hover:
    backgroundColor: "{colors.web-primary-hover}"
  web-button-ghost:
    textColor: "{colors.muted}"
    rounded: "{rounded.web-full}"
    padding: "0px 20px"
    height: "44px"
  web-button-ghost-hover:
    backgroundColor: "{colors.raised}"
    textColor: "{colors.text}"
  web-mode-toggle:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.muted}"
    typography: "{typography.web-label}"
    rounded: "{rounded.sheet}"
    padding: "4px"
  web-mode-toggle-selected:
    backgroundColor: "{colors.raised}"
    textColor: "{colors.text}"
    rounded: "{rounded.mode}"
    height: "48px"
  web-phone-frame:
    backgroundColor: "{colors.surface}"
    rounded: "{rounded.web-phone-frame}"
    padding: "10px"
---

# Design System: Wave Remote

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

Android Paper uses native sans-serif and sans-serif-medium. The implemented setup heading uses headline-large; sheet headings use headline-small. Title-medium identifies sections and devices; title-small labels settings groups. Body-large, body-medium and body-small support instructions, secondary copy and status. Label-large serves Paper buttons. The native app installs no custom display font; the web download site is the only surface with its own face (below).

The remote retains its own title, ordinary label and bold OK sizes. Status and rocker labels are small (12); mode labels use 14. These inherit Paper text defaults unless overridden; do not invent a new type ramp or normalize them into large marketing headings.

**Web download site (`site/`) only.** Archivo Variable, self-hosted with its width axis (`@fontsource-variable/archivo/wdth.css`; `public/fonts/archivo-latin-wdth.woff2` for the static 404), serves display and body; ui-monospace serves only the footer checksum. `web-display` sets the product name once, widened (font-stretch 112%), heavy and tightly tracked; the 404 heading reuses the weight and width at 32px. `web-lede` carries the one sentence in muted text, capped near 30ch; `web-body` is the page base; `web-action` labels the download; `web-label` serves mode choices, facts and the ghost link; `web-small` the footer. Version and size use tabular numerals.

**The Archivo Stays On The Web Rule.** Archivo belongs to the browser surface; native screens keep Paper's system faces.

## Layout

The safe-area app contains a centered, scrollable phone layout capped at 600 native units. Remote side padding is 16, setup and menu content generally use 24. Remote rows use a 10-unit gap, while vertical groups commonly use 12. Setup begins with 32 top padding. Scroll and wrapping action rows accommodate available space; there is no custom breakpoint system.

**The Remote Order Rule.** Keep header → power/input/mute → volume/apps/keyboard/channel → playback → mode selector → central pad → 72-unit landing zone → back/home/TV settings.

The D-pad is a three-by-three arrangement of 84-unit circular cells with 8-unit gaps; the pad region has a minimum height of 268. Volume and channel rockers are 84 wide (matching the D-pad cells) and 144 tall; Apps and Keyboard take the remaining width. Remote buttons have a minimum height of 48; bottom navigation uses 52. Sheets occupy at most 90% of screen height, cap width at 600, and pad their bottom by the larger of 24 or the safe-area inset. These are current geometry, not a claim of tested behavior at every font scale or device size.

## Elevation & Depth

The custom remote uses tonal layering with no authored drop shadows. Setup notices use Paper Surface elevation 1; Paper supplies its native elevation behavior. The web download site is equally flat: its phone frame is a surface-toned bezel with a raised one-pixel ring around a canvas-toned screen, and nothing on the page casts a shadow. Sheets separate from the canvas through their surface tone and a translucent black scrim. Preserve the theme's elevation ramp rather than inventing a web shadow system.

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

### Web download site (`site/`)

A single download page and static 404 built inside this world with shadcn/ui primitives. `site/src/styles.css` maps shadcn's semantic roles onto the app palette: background → `background`, foreground → `text`, card → `surface`, secondary → `raised`, muted-foreground → `muted`, primary → `accent`, ring → `primary`. Note the inversion: shadcn "primary" is the OK blue, and the Material action blue appears only as the focus ring.

- **Download button:** fully rounded, filled OK blue with white `web-action` text and a 24px leading icon; hover lightens to `web-primary-hover`; press scales to 0.98 over 150ms (off under reduced motion). The 404 repeats it at 56px tall.
- **Ghost link button:** fully rounded, 44px, muted 15px semibold text and 20px icon; hover fills `raised` and lifts text to `text`.
- **Mode toggle:** the web mirror of the app's mode selector: a surface pill (28px radius, 4px inset) holding two choices of at least 48px with 24px corners and 18px icons; the selected choice fills `raised`. Capped at 300px wide.
- **Phone frame:** 44px-radius surface bezel, 10px padding, one-pixel `raised` ring, 34px-radius screen at the screenshot's 720:1447 ratio, mobile screen width min(350px, 100vw − 70px); from 1024px, screen height min(700px, max(200px, 100dvh − 294px)) reserves space for the header, footer, mode selector, bezel and main padding. Very short windows retain natural scrolling.
- **Screenshot crossfade:** the page's single motion moment. Switching modes fades the outgoing screen to 0 opacity with a 6px blur and the incoming one in, 300ms ease-out; reduced motion removes the transition.
- **Browser surfaces:** dark color-scheme and theme-color canvas, selection in OK blue at 40% under `text`, scrollbar thumb `raised` on the canvas track, focus rings 2px in `primary` (offset 3px from the canvas on buttons, inset on toggle choices).
- **Layout:** content capped at 1180px with 24px side padding (40px from 640px); two columns from 1024px (copy column max 34rem, figure on the right), otherwise copy then figure; desktop main padding 24px above and below; 80px header, hairline `raised` rule above the footer.

**The Large Blue Text Rule.** White on the filled OK blue measures about 3.8:1 (about 3.1:1 on hover), so it carries only large text: 19px bold or bigger. Smaller labels never sit on filled blue.

## Do's and Don'ts

### Do:

- **Do** preserve the remote control order, central geometry and landing zone.
- **Do** reuse the dark palette and Paper theme for new setup or menu surfaces.
- **Do** retain accessible labels, selected/disabled states and reduced-motion handling.
- **Do** build web surfaces from the app palette through the shadcn role mapping, with OK blue as the only filled action.

### Don't:

- **Don't** use stale screenshots to rearrange the current source remote.
- **Don't** replace the remote with a generic Material dashboard.
- **Don't** treat code-derived dimensions or panel samples as runtime visual approval.
