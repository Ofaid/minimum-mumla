import { describe, expect, it } from 'vitest';
import { BOT_ID_PROTECTED_ROUTES } from './botid-routes';

function isProtected(pathname: string, method: string) {
  return BOT_ID_PROTECTED_ROUTES.some((route) => {
    const pattern = new RegExp(`^${route.path
      .replace(/[.?+^$[\]\\(){}|-]/g, '\\$&')
      .replaceAll('*', '.*')}$`);
    return route.method === method && pattern.test(pathname);
  });
}

describe('BotID client route protection', () => {
  it('protects Save Configuration for dynamic device IDs', () => {
    expect(isProtected('/api/devices/ABC123', 'PATCH')).toBe(true);
  });

  it('protects delete and token rotation without using Next.js parameter syntax', () => {
    expect(isProtected('/api/devices/ABC123', 'DELETE')).toBe(true);
    expect(isProtected('/api/devices/ABC123/token', 'POST')).toBe(true);
    expect(BOT_ID_PROTECTED_ROUTES.some((route) => route.path.includes(':'))).toBe(false);
  });

  it('does not add BotID headers to read-only device requests', () => {
    expect(isProtected('/api/devices/ABC123', 'GET')).toBe(false);
  });
});
