import { errorResponse, jsonResponse, requireAdminMutation } from '@/lib/api';
import { createDeviceToken, hashDeviceToken, validDeviceId } from '@/lib/security';
import { getDevice, putDevice } from '@/lib/storage';

export const runtime = 'nodejs';

type Context = { params: Promise<{ deviceId: string }> };

export async function POST(request: Request, context: Context) {
  const auth = await requireAdminMutation(request);
  if ('response' in auth) return auth.response;
  const { deviceId } = await context.params;
  if (!validDeviceId(deviceId)) return errorResponse('Invalid device ID', 400);
  const device = await getDevice(deviceId);
  if (!device) return errorResponse('Device not found', 404);
  const token = createDeviceToken();
  const tokenCreatedAt = new Date().toISOString();
  await putDevice({ ...device, tokenHash: hashDeviceToken(token), tokenCreatedAt, updatedAt: tokenCreatedAt });
  return jsonResponse({ token, tokenCreatedAt });
}
