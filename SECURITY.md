# Security

Please report vulnerabilities privately through
[GitHub private vulnerability reporting](https://github.com/VesselWave/wave-remote/security/advisories/new).
Include steps to reproduce and the affected app version. Do not post TV pairing keys
or signing material in public issues. Only the latest release receives fixes.

The app trusts a TV certificate on first pairing, then pins its SHA-256 fingerprint.
Pair on a trusted home network. Legacy TVs can use unencrypted WebSockets on port 3000,
and SSDP device descriptions use HTTP. The pointer transport is selected by the TV.
This is a local remote, not a secure tunnel across untrusted networks.

Release signing material is stored outside the repository and in encrypted CI secrets.
Release builds fail when signing credentials are absent; they never generate a new key.
