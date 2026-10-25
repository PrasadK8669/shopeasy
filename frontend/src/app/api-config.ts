export const AUTH_API = 'http://localhost:8083/api/auth';

// The token is only ever sent to these origins, never to another website
export const API_ORIGINS = [
  'http://localhost:8081',
  'http://localhost:8082',
  'http://localhost:8083',
];

export const PROTECTED_PREFIXES = ['/checkout', '/orders', '/admin'];

export function isProtectedUrl(url: string): boolean {
  return PROTECTED_PREFIXES.some(prefix => url.startsWith(prefix));
}

// Only allow in-app paths as a "return to" target, so a crafted link can't redirect elsewhere
export function safeReturnUrl(url: string | null): string {
  return url && url.startsWith('/') && !url.startsWith('//') ? url : '/';
}

const DEVICE_KEY = 'shopeasy.device';
const DEVICE_ID_FORMAT = /^[A-Za-z0-9-]{16,64}$/;
let memoryDeviceId: string | null = null;

function newDeviceId(): string {
  const c = globalThis.crypto;
  if (typeof c.randomUUID === 'function') {
    return c.randomUUID();
  }
  // Older or non-secure contexts: build a random id from random bytes
  const bytes = new Uint8Array(16);
  c.getRandomValues(bytes);
  return Array.from(bytes, b => b.toString(16).padStart(2, '0')).join('');
}

// A random id that stays on this browser. The server stores only its hash, to spot new devices.
export function getDeviceId(): string {
  try {
    const stored = localStorage.getItem(DEVICE_KEY);
    if (stored && DEVICE_ID_FORMAT.test(stored)) {
      return stored;
    }
    const id = newDeviceId();
    localStorage.setItem(DEVICE_KEY, id);
    return id;
  } catch {
    // Storage blocked: use an id that lasts until the page is reloaded
    memoryDeviceId ??= newDeviceId();
    return memoryDeviceId;
  }
}