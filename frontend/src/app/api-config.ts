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