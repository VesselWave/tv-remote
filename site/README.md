# Download site

https://tvremote.vwave.me is a one-page site for downloading the latest TV Remote APK.

- Cloudflare Worker `tvremote` on the `vwave.me` zone, configured by [`wrangler.json`](wrangler.json).
- `public/` holds the static page. Its palette and screenshot come from the app (see `../DESIGN.md`).
- [`src/worker.js`](src/worker.js) reads the latest GitHub release through the GitHub API, cached at the edge for ten minutes. It fills in the version, size, date and SHA-256 on `/`, redirects `/download` to the release's `tv-remote-v*.apk`, and serves the same data at `/latest.json`. If GitHub can't be reached, the page shows generic text and `/download` opens the releases page.

New releases appear automatically; the site only needs redeploying when it changes:

```sh
cd site && wrangler deploy
```

Verify with `curl -fsS https://tvremote.vwave.me/latest.json` and `curl -fsSI https://tvremote.vwave.me/download`. The second should return a 302 to the release APK.
