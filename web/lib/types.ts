export type MinimumConfig = {
  schemaVersion: number;
  configVersion: number;
  deviceId: string;
  [key: string]: unknown;
};

export type StoredAdmin = {
  username: string;
  passwordHash: string;
  createdAt: string;
  updatedAt: string;
};

export type StoredDevice = {
  deviceId: string;
  label: string;
  model: ModelProfile;
  config: MinimumConfig;
  createdAt: string;
  updatedAt: string;
};

export type DeviceSummary = Omit<StoredDevice, 'config'> & {
  configVersion: number;
};

export type PendingDeviceRequest = {
  deviceId: string;
  firstSeenAt: string;
  lastSeenAt: string;
  requestCount: number;
};

export type PendingDeviceRequestSummary = PendingDeviceRequest;
import type { ModelProfile } from './model-profiles';
