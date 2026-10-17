Wave Remote 0.6.2 fixes a few ways the remote could misbehave:

- A button press made while the remote is still connecting to the TV's button and touchpad channel is no longer sent seconds later. If the TV never opens that channel, the next press tries again.
- Connecting gives up after 5 seconds if the TV stops answering, instead of showing "Connecting…" forever.
- Holding a button repeats for at most 10 seconds, so a missed release can't keep turning the volume up.
- A TV that closes the connection while starting up no longer shows "TV certificate changed", and the remote keeps trying to reconnect.
- Pairings are no longer copied to a new phone in a device-to-device transfer, matching backups. Pair the TV again on a new phone.

Installs over 0.6.1 and keeps your pairing. To check the download: `sha256sum --check wave-remote-v0.6.2.apk.sha256`.

Setup and troubleshooting are in the [README](https://github.com/VesselWave/wave-remote#readme). Not affiliated with LG.
