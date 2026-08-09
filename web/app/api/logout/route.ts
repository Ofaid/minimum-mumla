import { jsonResponse } from '@/lib/api';
import { sessionCookieName } from '@/lib/security';

export const runtime = 'nodejs';

export async function POST() {
  const response = jsonResponse({ ok: true });
  response.cookies.set({ name: sessionCookieName(), value: '', httpOnly: true, sameSite: 'strict', secure: process.env.NODE_ENV === 'production', path: '/', maxAge: 0 });
  return response;
}
