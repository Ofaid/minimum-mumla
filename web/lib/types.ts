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
  model: string;
  config: MinimumConfig;
  tokenHash: string;
  tokenCreatedAt: string;
  createdAt: string;
  updatedAt: string;
};

export type DeviceSummary = Omit<StoredDevice, 'tokenHash' | 'config'> & {
  configVersion: number;
  tokenHint: string;
};
