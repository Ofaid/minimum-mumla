import { errorResponse, jsonResponse } from '@/lib/api';
import { validDeviceId } from '@/lib/security';
import { getDevice, putDevice, recordPendingDeviceRequest } from '@/lib/storage';
import { effectiveConfigsEqual, prepareConfigForSave } from '@/lib/config';

export const runtime = 'nodejs';

type Context = { params: Promise<{ deviceId: string }> };

/** Device-ID lookup endpoint used by managed Android clients. */
export async function GET(_request: Request, context: Context) {
  const { deviceId } = await context.params;
  if (!validDeviceId(deviceId)) return errorResponse('Not found', 404);
  const device = await getDevice(deviceId);
  if (!device) {
    await recordPendingDeviceRequest(deviceId);
    return errorResponse('Not found', 404);
  }
  const config = prepareConfigForSave({ ...(device.config as Record<string, unknown>), modelProfile: device.model }, device.config, device.deviceId);
  if (config.configVersion !== device.config.configVersion || !effectiveConfigsEqual(config, device.config)) {
    await putDevice({ ...device, config, updatedAt: new Date().toISOString() });
  }
  return jsonResponse(config);
}
