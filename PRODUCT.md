# Wave Remote

<!-- impeccable:product-schema 1 -->

## Platform
android

## Stack
Expo, React Native, TypeScript and Bun, requested by the user. Local Android module retains the existing webOS protocol engine.

## Users
Owners of LG webOS TVs controlling their TV from an Android phone on the same local network.

## Product Purpose
Replace the existing native Android interface with a polished Expo app while preserving the current remote-control scope.

## Operating Context
Phone and TV share a local network. Pairing requires approving a prompt on the TV. Wake-up depends on TV settings and previously learned addresses.

## Capabilities and Constraints
Preserve SSDP and manual connection, pairing and pinned certificates, D-pad with held-key acceleration, touchpad and natural/traditional scrolling, volume/channel repeats, phone volume keys, mute, navigation, playback, inputs, app launcher, keyboard, power off, Wake-on-LAN and BLE wake-up. Preserve locally stored pairing and preferences. Android-first. No account, cloud service, analytics or ads. No development servers or builds without an explicit user request; checks are permitted.

## Evidence on Hand
Protocol engine in src/me/vwave/remote; protocol notes in docs/PROTOCOL.md; mock TV in tools and JVM tests in tests. Existing screenshots may predate the current code.

## Brand Commitments
User likes the existing remote UI and button positions. Preserve the current source order and dark palette; modernize setup, settings and menus with familiar Android Material components. The saved screenshots are from an older layout and must not override MainActivity.java. Keep header, power/input/mute, volume/apps/keyboard/channel, playback, mode selector, central pad, 72dp landing zone, and back/home/TV settings in that order.

## Open Decisions
iOS support is outside the current scope.
