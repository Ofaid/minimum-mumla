import { errorResponse, jsonResponse } from '@/lib/api';
import { verifyDeviceToken, validDeviceId } from '@/lib/security';
import { getDevice, recordPendingDeviceRequest } from '@/lib/storage';

export const runtime = 'nodejs';

type Context = { params: Promise<{ deviceId: string }> };

export async function GET(request: Request, context: Context) {
  const { deviceId } = await context.params;
  if (!validDeviceId(deviceId)) return errorResponse('Not found', 404);
  const authorization = request.headers.get('authorization') || '';
  const match = authorization.match(/^Bearer\s+([A-Za-z0-9_-]{43})$/i);
  if (!match) return errorResponse('Unauthorized', 401);
  const device = await getDevice(deviceId);
  if (!device) {
    await recordPendingDeviceRequest(deviceId);
    return errorResponse('Unauthorized', 401);
  }
  if (!verifyDeviceToken(match[1], device.tokenHash)) return errorResponse('Unauthorized', 401);
  return jsonResponse(device.config);
}
