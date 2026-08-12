import { beforeEach, describe, expect, it } from 'vitest';
import { getDevice, kvGet, kvPut, resetMemoryStore } from './storage';

describe('stored device migration', () => {
  beforeEach(() => resetMemoryStore());

  it('removes retired token fields from legacy device records', async () => {
    await kvPut('device:A1B2C3', {
      deviceId: 'A1B2C3',
      label: 'Radio',
      model: 'ryks',
      config: { schemaVersion: 3, configVersion: 12, deviceId: 'A1B2C3' },
      tokenHash: 'retired-hash',
      tokenCreatedAt: '2026-08-12T00:00:00.000Z',
      createdAt: '2026-08-12T00:00:00.000Z',
      updatedAt: '2026-08-12T00:00:00.000Z'
    });

    const device = await getDevice('A1B2C3');
    const stored = await kvGet<Record<string, unknown>>('device:A1B2C3');

    expect(device?.config.configVersion).toBe(12);
    expect(stored).not.toHaveProperty('tokenHash');
    expect(stored).not.toHaveProperty('tokenCreatedAt');
  });
});
