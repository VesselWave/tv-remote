import { StrictMode } from 'react';
import { createRoot, hydrateRoot } from 'react-dom/client';
import '@fontsource-variable/archivo/wdth.css';
import './styles.css';
import App from './App';
import type { Release } from './release';

// The Worker renders the page and embeds the release it rendered with, so hydration matches.
const embedded = document.getElementById('release')?.textContent;
const release: Release | null = embedded ? JSON.parse(embedded) : null;
const root = document.getElementById('root')!;
const app = <StrictMode><App release={release} /></StrictMode>;

if (root.hasChildNodes()) hydrateRoot(root, app);
else createRoot(root).render(app);
