import { errorResponse, jsonResponse, readJson, requireAdmin, requireAdminMutation } from '@/lib/api';
import { assertDeviceConfig, emptyConfig } from '@/lib/config';
import { createDeviceToken, hashDeviceToken, validDeviceId } from '@/lib/security';
import { getDevice, listDevices, putDevice } from '@/lib/storage';
import type { DeviceSummary, MinimumConfig, StoredDevice } from '@/lib/types';

export const runtime = 'nodejs';

function summary(device: StoredDevice): DeviceSummary {
  return {
    deviceId: device.deviceId,
    label: device.label,
    model: device.model,
    configVersion: device.config.configVersion,
    tokenCreatedAt: device.tokenCreatedAt,
    createdAt: device.createdAt,
    updatedAt: device.updatedAt,
    tokenHint: `rotated ${new Date(device.tokenCreatedAt).toLocaleDateString('en-US', { month: 'short', year: 'numeric' })}`
  };
}

export async function GET(request: Request) {
  const auth = await requireAdmin(request);
  if ('response' in auth) return auth.response;
  try {
    return jsonResponse({ devices: (await listDevices()).map(summary) });
  } catch {
    return errorResponse('Device store unavailable', 503);
  }
}

export async function POST(request: Request) {
  const auth = await requireAdminMutation(request);
  if ('response' in auth) return auth.response;
  const body = await readJson(request);
  const deviceId = typeof body?.deviceId === 'string' ? body.deviceId.toUpperCase() : '';
  const label = typeof body?.label === 'string' ? body.label.trim() : '';
  const model = typeof body?.model === 'string' ? body.model.trim() : '';
  if (!validDeviceId(deviceId) || !label || label.length > 128 || !model || model.length > 64) {
    return errorResponse('Device ID, label and model are required');
  }
  if (await getDevice(deviceId)) return errorResponse('Device already exists', 409);
  let config: MinimumConfig;
  try {
    config = assertDeviceConfig(body?.config || emptyConfig(deviceId), deviceId);
  } catch (error) {
    return errorResponse(error instanceof Error ? error.message : 'Invalid configuration');
  }
  const token = createDeviceToken();
  const now = new Date().toISOString();
  const device: StoredDevice = {
    deviceId,
    label,
    model,
    config,
    tokenHash: hashDeviceToken(token),
    tokenCreatedAt: now,
    createdAt: now,
    updatedAt: now
  };
  try {
    await putDevice(device);
    return jsonResponse({ device: summary(device), token }, 201);
  } catch {
    return errorResponse('Device store unavailable', 503);
  }
}
