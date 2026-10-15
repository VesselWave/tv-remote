// Renders the download page with the latest GitHub release and redirects /download to its APK.
import { renderToString } from 'react-dom/server';
import App from '../src/App';
import { RELEASES_URL, type Release } from '../src/release';

type Env = { ASSETS: Fetcher };

const API = 'https://api.github.com/repos/VesselWave/tv-remote/releases/latest';

// Cached at the edge for ten minutes to stay well inside GitHub's anonymous API limit.
async function latest(): Promise<Release | null> {
  try {
    const response = await fetch(API, {
      headers: { Accept: 'application/vnd.github+json', 'User-Agent': 'tvremote.vwave.me' },
      cf: { cacheTtl: 600, cacheEverything: true },
    });
    if (!response.ok) return null;
    const release = (await response.json()) as {
      tag_name: string;
      published_at: string;
      html_url: string;
      assets: { name: string; size: number; digest?: string; browser_download_url: string }[];
    };
    const apk = release.assets.find((asset) => /^tv-remote-v[\d.]+\.apk$/.test(asset.name));
    if (!apk) return null;
    return {
      version: release.tag_name.replace(/^v/, ''),
      tag: release.tag_name,
      file: apk.name,
      size: apk.size,
      sha256: apk.digest?.replace(/^sha256:/, '') ?? '',
      published: release.published_at,
      url: apk.browser_download_url,
      page: release.html_url,
    };
  } catch {
    return null;
  }
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (url.pathname === '/latest.json') {
      const release = await latest();
      if (!release) return new Response('Latest release unavailable', { status: 503 });
      return Response.json(release, { headers: { 'Cache-Control': 'max-age=300' } });
    }

    if (url.pathname === '/download') {
      const release = await latest();
      return Response.redirect(release?.url ?? RELEASES_URL, 302);
    }

    const [template, release] = await Promise.all([
      env.ASSETS.fetch(new URL('/', url)).then((response) => response.text()),
      latest(),
    ]);
    const json = JSON.stringify(release).replace(/</g, '\\u003c');
    const html = template
      .replace('<!--app-->', renderToString(<App release={release} />))
      .replace('<!--release-->', `<script id="release" type="application/json">${json}</script>`);
    return new Response(html, {
      headers: { 'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'max-age=300' },
    });
  },
} satisfies ExportedHandler<Env>;
