export const BOT_ID_PROTECTED_ROUTES = [
  { path: '/api/setup', method: 'POST' },
  { path: '/api/login', method: 'POST' },
  { path: '/api/devices', method: 'POST' },
  { path: '/api/devices/*', method: 'PATCH' },
  { path: '/api/devices/*', method: 'DELETE' }
] as const;
