import { describe, expect, it } from 'vitest';
import { configsEqual, emptyConfig, validateConfig } from './config';
import type { MinimumConfig } from './types';

describe('Minimum config validation', () => {
  it('accepts the schema 3 device baseline', () => {
    const config = emptyConfig('AB12C3');
    expect(validateConfig(config, 'AB12C3')).toEqual({ valid: true, errors: [] });
  });

  it('rejects a device mismatch and invalid version', () => {
    const config = emptyConfig('AB12C3');
    config.deviceId = 'ZX98Y7';
    config.configVersion = 0;
    const result = validateConfig(config, 'AB12C3');
    expect(result.valid).toBe(false);
    expect(result.errors.join(' ')).toContain('configVersion');
    expect(result.errors.join(' ')).toContain('deviceId');
  });

  it('compares configuration independent of object key order', () => {
    expect(configsEqual({ b: 2, a: 1 }, { a: 1, b: 2 })).toBe(true);
  });

  it('rejects dangling channel references before persistence', () => {
    const config = emptyConfig('AB12C3') as MinimumConfig & {
      radio: { defaultChannel: string };
      channels: Array<{ connectionId: string }>;
    };
    config.radio.defaultChannel = 'missing-channel';
    config.channels[0].connectionId = 'missing-connection';
    const result = validateConfig(config, 'AB12C3');
    expect(result.valid).toBe(false);
    expect(result.errors.join(' ')).toContain('connectionId');
    expect(result.errors.join(' ')).toContain('defaultChannel');
  });
});
