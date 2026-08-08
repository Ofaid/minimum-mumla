import type { StoredAdmin, StoredDevice } from './types';

declare global {
  // Shared across Next development route bundles; production never uses this store.
  var __minimumKvMemoryStore: Map<string, string> | undefined;
}

const memory = globalThis.__minimumKvMemoryStore || new Map<string, string>();
if (process.env.NODE_ENV !== 'production') globalThis.__minimumKvMemoryStore = memory;

type CloudflareConfig = {
  accountId: string;
  apiToken: string;
  namespaceId: string;
  baseUrl: string;
};

function cloudflareConfig(): CloudflareConfig | null {
  const accountId = process.env.CLOUDFLARE_ACCOUNT_ID;
  const apiToken = process.env.CLOUDFLARE_API_TOKEN;
  const namespaceId = process.env.CLOUDFLARE_KV_NAMESPACE_ID;
  if (!accountId || !apiToken || !namespaceId) {
    if (process.env.NODE_ENV !== 'production') return null;
    throw new Error('Cloudflare KV is not configured');
  }
  return {
    accountId,
    apiToken,
    namespaceId,
    baseUrl: process.env.CLOUDFLARE_KV_API_BASE || 'https://api.cloudflare.com/client/v4'
  };
}

function kvUrl(config: CloudflareConfig, suffix: string) {
  return `${config.baseUrl}/accounts/${encodeURIComponent(config.accountId)}/storage/kv/namespaces/${encodeURIComponent(config.namespaceId)}${suffix}`;
}

async function kvRequest(config: CloudflareConfig, suffix: string, init?: RequestInit) {
  return fetch(kvUrl(config, suffix), {
    ...init,
    headers: {
      Authorization: `Bearer ${config.apiToken}`,
      ...(init?.headers || {})
    },
    cache: 'no-store'
  });
}

export async function kvGet<T>(key: string): Promise<T | null> {
  const config = cloudflareConfig();
  if (!config) {
    const value = memory.get(key);
    return value ? JSON.parse(value) as T : null;
  }
  const response = await kvRequest(config, `/values/${encodeURIComponent(key)}`);
  if (response.status === 404) return null;
  if (!response.ok) throw new Error(`Cloudflare KV read failed (${response.status})`);
  return JSON.parse(await response.text()) as T;
}

export async function kvPut<T>(key: string, value: T) {
  const serialized = JSON.stringify(value);
  const config = cloudflareConfig();
  if (!config) {
    memory.set(key, serialized);
    return;
  }
  const response = await kvRequest(config, `/values/${encodeURIComponent(key)}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: serialized
  });
  if (!response.ok) throw new Error(`Cloudflare KV write failed (${response.status})`);
}

export async function kvDelete(key: string) {
  const config = cloudflareConfig();
  if (!config) {
    memory.delete(key);
    return;
  }
  const response = await kvRequest(config, `/values/${encodeURIComponent(key)}`, { method: 'DELETE' });
  if (!response.ok && response.status !== 404) throw new Error(`Cloudflare KV delete failed (${response.status})`);
}

export async function kvList(prefix: string) {
  const config = cloudflareConfig();
  if (!config) return [...memory.keys()].filter((key) => key.startsWith(prefix));
  const keys: string[] = [];
  let cursor = '';
  do {
    const query = new URLSearchParams({ prefix, limit: '1000' });
    if (cursor) query.set('cursor', cursor);
    const response = await kvRequest(config, `/keys?${query.toString()}`);
    if (!response.ok) throw new Error(`Cloudflare KV list failed (${response.status})`);
    const body = await response.json() as { result?: Array<{ name: string }>; result_info?: { cursor?: string } };
    keys.push(...(body.result || []).map((item) => item.name));
    cursor = body.result_info?.cursor || '';
    if (keys.length > 10000) throw new Error('Cloudflare KV key listing exceeds safety limit');
  } while (cursor);
  return keys;
}

export async function getAdmin() {
  return kvGet<StoredAdmin>('admin:account');
}

export async function putAdmin(admin: StoredAdmin) {
  return kvPut('admin:account', admin);
}

export async function getDevice(deviceId: string) {
  return kvGet<StoredDevice>(`device:${deviceId}`);
}

export async function putDevice(device: StoredDevice) {
  return kvPut(`device:${device.deviceId}`, device);
}

export async function deleteDevice(deviceId: string) {
  return kvDelete(`device:${deviceId}`);
}

export async function listDevices() {
  const keys = await kvList('device:');
  const devices = await Promise.all(keys.map((key) => kvGet<StoredDevice>(key)));
  return devices.filter((device): device is StoredDevice => Boolean(device));
}

export function resetMemoryStore() {
  memory.clear();
}
