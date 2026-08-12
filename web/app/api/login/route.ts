import { jsonResponse, errorResponse, readJson } from '@/lib/api';
import { recordAdminActivity } from '@/lib/activity';
import { requireHumanMutation } from '@/lib/botid';
import { allowLoginAttempt, createSession, sameOrigin, sessionCookieOptions, verifySecret } from '@/lib/security';
import { getAdmin } from '@/lib/storage';

export const runtime = 'nodejs';

export async function POST(request: Request) {
  if (!sameOrigin(request) || !(await requireHumanMutation())) return errorResponse('Browser verification required', 403);
  const identifier = request.headers.get('x-forwarded-for')?.split(',')[0]?.trim() || 'unknown';
  if (!allowLoginAttempt(identifier)) return errorResponse('Too many attempts; try again later', 429);
  const body = await readJson(request);
  const username = typeof body?.username === 'string' ? body.username : '';
  const password = typeof body?.password === 'string' ? body.password : '';
  const admin = await getAdmin();
  if (!admin || admin.username !== username || !verifySecret(password, admin.passwordHash)) {
    return errorResponse('Invalid username or password', 401);
  }
  const response = jsonResponse({ ok: true, username });
  response.cookies.set({ ...sessionCookieOptions(), value: createSession(username) });
  await recordAdminActivity({ action: 'admin.login.succeeded', administrator: username, resource: { type: 'system' } }).catch(() => undefined);
  return response;
}
