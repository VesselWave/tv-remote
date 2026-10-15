// Serves the static page with the latest GitHub release filled in, and redirects /download to its APK.
const REPO = 'VesselWave/tv-remote';
const RELEASES = `https://github.com/${REPO}/releases/latest`;

// Cached at the edge for ten minutes to stay well inside GitHub's anonymous API limit.
async function latest() {
  const response = await fetch(`https://api.github.com/repos/${REPO}/releases/latest`, {
    headers: { Accept: 'application/vnd.github+json', 'User-Agent': 'tvremote.vwave.me' },
    cf: { cacheTtl: 600, cacheEverything: true },
  });
  if (!response.ok) return null;
  const release = await response.json();
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
  };
}

function megabytes(bytes) {
  return bytes < 1048576 ? `${Math.round(bytes / 1024)} KB` : `${(bytes / 1048576).toFixed(1)} MB`;
}

function date(iso) {
  return new Date(iso).toLocaleDateString('en-US', { year: 'numeric', month: 'long', day: 'numeric', timeZone: 'UTC' });
}

export default {
  async fetch(request, env) {
    const { pathname } = new URL(request.url);

    if (pathname === '/latest.json') {
      const release = await latest();
      if (!release) return new Response('Latest release unavailable', { status: 503 });
      return Response.json(release, { headers: { 'Cache-Control': 'max-age=300' } });
    }

    if (pathname === '/download') {
      const release = await latest();
      return Response.redirect(release?.url ?? RELEASES, 302);
    }

    const page = await env.ASSETS.fetch(request);
    if (pathname !== '/' && pathname !== '/index.html') return page;
    const release = await latest();
    if (!release) return page;
    const values = {
      label: `Version ${release.version}`,
      size: megabytes(release.size),
      released: `Version ${release.version}, released ${date(release.published)}`,
      sha256: release.sha256,
    };
    const rewritten = new HTMLRewriter()
      .on('[data-release]', { element(element) { element.setInnerContent(values[element.getAttribute('data-release')] ?? ''); } })
      .transform(page);
    const headers = new Headers(rewritten.headers);
    headers.set('Cache-Control', 'max-age=300');
    return new Response(rewritten.body, { status: rewritten.status, headers });
  },
};
