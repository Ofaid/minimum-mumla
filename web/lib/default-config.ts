import type { MinimumConfig } from './types';
import type { ModelProfile } from './model-profiles';

const HARDWARE_BY_MODEL: Record<ModelProfile, Record<string, unknown>> = {
  t56: {
    profile: 't56-unipro-zx-l809', pttKeyCode: 261, pttScanCode: 216,
    pttKeyCodes: [261, 85, 79], locationTrackingSupported: true,
    p1KeyCode: 0, p2KeyCode: 0, p3KeyCode: 0
  },
  t99: {
    profile: 't99-qm011', pttKeyCode: 0, pttScanCode: 0,
    pttKeyCodes: [131, 132, 85, 79], locationTrackingSupported: false,
    p1KeyCode: 0, p2KeyCode: 0, p3KeyCode: 0
  },
  'generic-radio': {
    profile: 'generic-radio', pttKeyCode: 0, pttScanCode: 0,
    pttKeyCodes: [], locationTrackingSupported: false,
    p1KeyCode: 0, p2KeyCode: 0, p3KeyCode: 0
  }
};

export function emptyConfig(deviceId: string, model: ModelProfile = 'generic-radio'): MinimumConfig {
  return {
    schemaVersion: 3,
    configVersion: 1,
    deviceId,
    service: { name: model === 't56' ? 'Minimum T56' : model === 't99' ? 'Minimum T99' : 'Minimum Radio' },
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
    hardware: HARDWARE_BY_MODEL[model]
  };
}
