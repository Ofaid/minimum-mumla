import { errorResponse, jsonResponse, readJson, requireAdmin, requireAdminMutation } from '@/lib/api';
import { assertDeviceConfig, configsEqual } from '@/lib/config';
import { validDeviceId } from '@/lib/security';
import { validModelProfile } from '@/lib/model-profiles';
import { deleteDevice, getDevice, putDevice } from '@/lib/storage';
import type { MinimumConfig } from '@/lib/types';

export const runtime = 'nodejs';

type Context = { params: Promise<{ deviceId: string }> };

export async function GET(request: Request, context: Context) {
  const auth = await requireAdmin(request);
  if ('response' in auth) return auth.response;
  const { deviceId } = await context.params;
  if (!validDeviceId(deviceId)) return errorResponse('Invalid device ID', 400);
  const device = await getDevice(deviceId);
  if (!device) return errorResponse('Device not found', 404);
  const { tokenHash: _tokenHash, ...safeDevice } = device;
  return jsonResponse({ device: safeDevice });
}

export async function PATCH(request: Request, context: Context) {
  const auth = await requireAdminMutation(request);
  if ('response' in auth) return auth.response;
  const { deviceId } = await context.params;
  if (!validDeviceId(deviceId)) return errorResponse('Invalid device ID', 400);
  const device = await getDevice(deviceId);
  if (!device) return errorResponse('Device not found', 404);
  const body = await readJson(request);
  const label = typeof body?.label === 'string' ? body.label.trim() : device.label;
  const model = typeof body?.model === 'string' ? body.model.trim() : device.model;
  if (!label || label.length > 128 || !validModelProfile(model)) return errorResponse('Label and a supported model profile are required');
  let config: MinimumConfig;
  try {
    config = assertDeviceConfig(body?.config, deviceId);
  } catch (error) {
    return errorResponse(error instanceof Error ? error.message : 'Invalid configuration');
  }
  if (!configsEqual(config, device.config) && config.configVersion <= device.config.configVersion) {
    return errorResponse(`configVersion must advance beyond ${device.config.configVersion}`, 409);
  }
  const updated = { ...device, label, model, config, updatedAt: new Date().toISOString() };
  await putDevice(updated);
  const { tokenHash: _tokenHash, ...safeDevice } = updated;
  return jsonResponse({ device: safeDevice });
}

export async function DELETE(request: Request, context: Context) {
  const auth = await requireAdminMutation(request);
  if ('response' in auth) return auth.response;
  const { deviceId } = await context.params;
  if (!validDeviceId(deviceId)) return errorResponse('Invalid device ID', 400);
  if (!(await getDevice(deviceId))) return errorResponse('Device not found', 404);
  await deleteDevice(deviceId);
  return jsonResponse({ ok: true });
}
