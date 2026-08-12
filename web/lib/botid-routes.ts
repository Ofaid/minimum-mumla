export const BOT_ID_PROTECTED_ROUTES = [
  { path: '/api/setup', method: 'POST' },
  { path: '/api/login', method: 'POST' },
  { path: '/api/devices', method: 'POST' },
  { path: '/api/devices/*', method: 'PATCH' },
  { path: '/api/devices/*', method: 'DELETE' },
  { path: '/api/devices/*/token', method: 'POST' }
] as const;
