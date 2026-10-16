// Renders the download page with the latest GitHub release and redirects /download to its APK.
import { renderToString } from 'react-dom/server';
import App from '../src/App';
import { RELEASES_URL, type Release } from '../src/release';

type Env = { ASSETS: Fetcher };

const API = 'https://api.github.com/repos/VesselWave/wave-remote/releases/latest';

// Matches public/_headers, which covers the static files.
const SECURITY_HEADERS = {
  'Strict-Transport-Security': 'max-age=31536000',
  'Content-Security-Policy': [
    "default-src 'self'",
    "script-src 'self' https://static.cloudflareinsights.com",
    "connect-src 'self' https://cloudflareinsights.com",
    "style-src-attr 'unsafe-inline'",
    "img-src 'self' data:",
    "object-src 'none'",
    "base-uri 'none'",
    "form-action 'none'",
    "frame-ancestors 'none'",
  ].join('; '),
  'X-Content-Type-Options': 'nosniff',
  'Referrer-Policy': 'strict-origin-when-cross-origin',
  'Permissions-Policy': 'camera=(), microphone=(), geolocation=()',
};

function secured(response: Response): Response {
  const headers = new Headers(response.headers);
  for (const [name, value] of Object.entries(SECURITY_HEADERS)) headers.set(name, value);
  return new Response(response.body, { status: response.status, statusText: response.statusText, headers });
}

// Cached at the edge for ten minutes to stay well inside GitHub's anonymous API limit.
async function latest(): Promise<Release | null> {
  try {
    const response = await fetch(API, {
      headers: { Accept: 'application/vnd.github+json', 'User-Agent': 'remote.vwave.me' },
      cf: { cacheTtl: 600, cacheEverything: true },
    });
    if (!response.ok) return null;
    const release = (await response.json()) as {
      tag_name: string;
      published_at: string;
      html_url: string;
      assets: { name: string; size: number; digest?: string; browser_download_url: string }[];
    };
    // 0.2 and earlier shipped as tv-remote-v*.apk, before the rename.
    const apk = release.assets.find((asset) => /^(?:wave|tv)-remote-v[\d.]+\.apk$/.test(asset.name));
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

async function respond(request: Request, env: Env): Promise<Response> {
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
}

export default {
  async fetch(request, env) {
    return secured(await respond(request, env));
  },
} satisfies ExportedHandler<Env>;
