import { writable } from 'svelte/store';
import { browser } from '$app/environment';

export type ThemeId = 'default' | 'warm' | 'dark' | 'glass' | 'bold';

const COOKIE = 'fi-theme';
const MAX_AGE = 60 * 60 * 24 * 365; // 1 year

function readCookie(): ThemeId {
  if (!browser) return 'default';
  const found = document.cookie.split('; ').find(r => r.startsWith(COOKIE + '='));
  return (found?.split('=')[1] as ThemeId) ?? 'default';
}

function apply(id: ThemeId) {
  document.documentElement.setAttribute('data-theme', id);
  document.cookie = `${COOKIE}=${id}; path=/; max-age=${MAX_AGE}; samesite=strict`;
}

const store = writable<ThemeId>('default');

export const theme = {
  subscribe: store.subscribe,
  init() {
    const saved = readCookie();
    store.set(saved);
    apply(saved);
  },
  set(id: ThemeId) {
    store.set(id);
    apply(id);
  },
};

export const themes: { id: ThemeId; name: string; desc: string; swatches: [string, string, string] }[] = [
  { id: 'default', name: 'Fresh Green',  desc: 'Original clean green',      swatches: ['#16a34a', '#f3f4f6', '#111827'] },
  { id: 'warm',    name: 'Warm Organic', desc: 'Cream & sage tones',        swatches: ['#5c7a3e', '#faf7f2', '#2d2416'] },
  { id: 'dark',    name: 'Dark Pantry',  desc: 'Premium dark mode',         swatches: ['#4ade80', '#141414', '#f5f5f5'] },
  { id: 'glass',   name: 'Airy Glass',   desc: 'Frosted glass on gradient', swatches: ['#2e7d32', '#e8f5e9', '#1a1a2e'] },
  { id: 'bold',    name: 'Bold Type',    desc: 'Confident blue palette',    swatches: ['#0052cc', '#f8f9fa', '#091e42'] },
];
