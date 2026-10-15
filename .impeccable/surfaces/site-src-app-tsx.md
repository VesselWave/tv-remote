---
version: 1
slug: "site-src-app-tsx"
primary_target: "site/src/App.tsx"
related_targets: ["site/worker/index.ts"]
---

# tvremote.vwave.me

Scope: the public download page (Persuade). Visitor: an LG webOS TV owner with an Android phone, often arriving from a link on the phone itself. Job: understand what this is in one glance and install the latest APK. Proof: the real app screenshots (Buttons and Touchpad modes) and the live release facts from GitHub (version, size, date, SHA-256). Constraints: minimal, mostly the download, very little text, no advertising tone; no claims beyond PRODUCT.md and the changelog.

## Direction contract

THESIS: The page is the app's own mode switch. One real control, the Buttons/Touchpad pill, swaps the phone between the two real screenshots. It refuses the category default of a feature-card grid under a centered gradient hero.

OWN-WORLD: The app's dark tonal stack (#0F1115 canvas, #1E222A surface, #2A2F3A raised), #E9ECF2 text, #A4ADBE muted, and #3D7BFF as the only filled action, the OK blue. #FF5A5F appears only as the icon's power glyph. Shapes are the app's: 18px keys, a 28px pill with a 24px inner selection, and fully rounded actions. No gradients, glows, glass or drop shadows: depth comes from tonal steps alone.

STORY: The visitor sees the phone running the remote, flips Buttons to Touchpad and sees both ways to drive the TV, then taps Download. Version, size and Android 7+ confirm what they're getting.

FIRST VIEWPORT: Desktop has two columns. On the left, from the top: the icon with TV Remote; the name set large; one sentence; the blue Download APK button (the primary action, above the fold); and a muted facts line (version · size · Android 7+). On the right, the app's pill selector sits directly above a phone-sized screenshot, sized from the viewport height after reserving the header, footer, selector and spacing so the complete desktop page fits on one screen. A GitHub link sits top right. On mobile the order is name, sentence, Download, facts, then pill and phone.

FORM: Buttons ⇄ Touchpad mode switch, structure 2 of 7 on the ordered list; surface seed key 8c077804.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
