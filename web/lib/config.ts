import Ajv2020 from 'ajv/dist/2020';
import schema from './config-schema.json';
import type { MinimumConfig } from './types';
import { validDeviceId } from './security';
export { emptyConfig } from './default-config';

const validator = new Ajv2020({ allErrors: true, strict: false });
const validateSchema = validator.compile(schema);

export function canonicalize(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(canonicalize);
  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.entries(value as Record<string, unknown>)
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([key, nested]) => [key, canonicalize(nested)]));
  }
  return value;
}

export function configsEqual(left: unknown, right: unknown) {
  return JSON.stringify(canonicalize(left)) === JSON.stringify(canonicalize(right));
}

export function validateConfig(value: unknown, deviceId?: string) {
  const errors: string[] = [];
  if (!validateSchema(value)) {
    for (const error of validateSchema.errors || []) {
      errors.push(`${error.instancePath || '$'} ${error.message || 'is invalid'}`);
    }
  }
  const config = value as Partial<MinimumConfig> | null;
  if (!config || typeof config !== 'object') errors.push('$ must be an object');
  if (config && config.schemaVersion !== 3) errors.push('$.schemaVersion must be 3');
  if (config && (!Number.isInteger(config.configVersion) || Number(config.configVersion) < 1)) {
    errors.push('$.configVersion must be a positive integer');
  }
  if (deviceId && config && config.deviceId !== '*' && config.deviceId !== deviceId) {
    errors.push(`$.deviceId must be ${deviceId} or *`);
  }
  if (config && typeof config === 'object' && Array.isArray(config.channels)
      && config.connections && typeof config.connections === 'object') {
    const channels = config.channels as Array<Record<string, unknown>>;
    const connections = config.connections as Record<string, unknown>;
    const channelIds = new Set<string>();
    for (const [index, channel] of channels.entries()) {
      const id = typeof channel?.id === 'string' ? channel.id : '';
      if (id && channelIds.has(id)) errors.push(`$.channels[${index}].id must be unique`);
      if (id) channelIds.add(id);
      const connectionId = typeof channel?.connectionId === 'string' ? channel.connectionId : '';
      if (connectionId && !Object.prototype.hasOwnProperty.call(connections, connectionId)) {
        errors.push(`$.channels[${index}].connectionId must reference an existing connection`);
      }
    }
    const defaultChannel = config.radio && typeof config.radio === 'object'
      ? (config.radio as Record<string, unknown>).defaultChannel : undefined;
    if (typeof defaultChannel === 'string' && !channelIds.has(defaultChannel)) {
      errors.push('$.radio.defaultChannel must reference an existing channel');
    }
  }
  return { valid: errors.length === 0, errors };
}

export function assertDeviceConfig(value: unknown, deviceId: string) {
  if (!validDeviceId(deviceId)) throw new Error('Invalid device ID');
  const result = validateConfig(value, deviceId);
  if (!result.valid) throw new Error(result.errors.join('; '));
  return value as MinimumConfig;
}
