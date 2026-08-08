import type { MinimumConfig } from './types';

export function emptyConfig(deviceId: string): MinimumConfig {
  return {
    schemaVersion: 3,
    configVersion: 1,
    deviceId,
    service: { name: 'Minimum Radio' },
    radio: { defaultChannel: 'main', autoConnect: false, autoReconnect: true },
    connections: {
      'public-main': {
        name: 'Minimum Radio',
        host: 'voice.example.invalid',
        port: 64738,
        username: 'MINIMUM',
        autoTrustServerCertificate: true
      }
    },
    channels: [{
      id: 'main',
      label: 'Main room',
      connectionId: 'public-main',
      path: '/PUBLIC/MAIN',
      access: { mode: 'none' }
    }],
    ui: { profile: 'small-radio', language: 'en', showChat: false, showUserList: false },
    ptt: { maximumTxSeconds: 120, allowScreenOff: true, releaseOnNetworkLoss: true },
    tracking: { enabled: false, pttTriggered: true, aprs: { enabled: false } },
    hardware: { profile: 'generic-radio', pttKeyCodes: [], locationTrackingSupported: false }
  };
}
