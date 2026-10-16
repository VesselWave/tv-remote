// Renders og/card.html to public/og.png through any Chromium with remote debugging on CDP_URL.
// Local files are inlined as data URLs, so the browser doesn't need access to this checkout.
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require('playwright-core');

const dir = __dirname;
const types = { '.woff2': 'font/woff2', '.svg': 'image/svg+xml', '.webp': 'image/webp' };
const html = fs.readFileSync(path.join(dir, 'card.html'), 'utf8').replace(/(src|url)(=|\()'?"?(\.\.\/[^'")]+)'?"?/g, (match, attr, open, file) => {
  const data = fs.readFileSync(path.join(dir, file)).toString('base64');
  const url = `data:${types[path.extname(file)]};base64,${data}`;
  return attr === 'src' ? `src="${url}"` : `url('${url}'`;
});

(async () => {
  const browser = await chromium.connectOverCDP(process.env.CDP_URL ?? process.env.GPU_BROWSER_CDP_HTTP ?? 'http://127.0.0.1:9222');
  const page = await browser.contexts()[0].newPage();
  await page.setViewportSize({ width: 1200, height: 630 });
  await page.setContent(html, { waitUntil: 'load' });
  await page.evaluate(() => document.fonts.ready);
  await page.screenshot({ path: path.join(dir, '../public/og.png') });
  await page.close();
  await browser.close();
})();
