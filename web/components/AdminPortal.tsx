'use client';

import {
  Activity, AlertCircle, Check, ChevronRight, Copy, KeyRound, LayoutDashboard,
  LogOut, Plus, RadioTower, RefreshCw, Save, Settings2, ShieldCheck, Trash2,
  X
} from 'lucide-react';
import { FormEvent, useEffect, useMemo, useState } from 'react';
import type { DeviceSummary, MinimumConfig } from '@/lib/types';
import { emptyConfig } from '@/lib/default-config';

type SessionState = {
  loading: boolean;
  configured: boolean;
  authenticated: boolean;
  username: string | null;
};

type DeviceRecord = {
  deviceId: string;
  label: string;
  model: string;
  config: MinimumConfig;
  tokenCreatedAt: string;
  createdAt: string;
  updatedAt: string;
};

type Notice = { tone: 'success' | 'error' | 'info'; text: string } | null;

async function api<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, {
    ...init,
    headers: { ...(init?.body ? { 'Content-Type': 'application/json' } : {}), ...(init?.headers || {}) },
    cache: 'no-store'
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(typeof data?.error === 'string' ? data.error : 'Request failed');
  return data as T;
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat('en-US', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));
}

function Field({ label, value, onChange, type = 'text', placeholder, autoComplete }: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  type?: string;
  placeholder?: string;
  autoComplete?: string;
}) {
  return (
    <label className="field">
      <span>{label}</span>
      <input value={value} onChange={(event) => onChange(event.target.value)} type={type} placeholder={placeholder} autoComplete={autoComplete} />
    </label>
  );
}

function Logo({ compact = false }: { compact?: boolean }) {
  return (
    <div className={`brand ${compact ? 'brand-compact' : ''}`}>
      <img src="/minimum-logo.svg" alt="Minimum" />
      {!compact && <span><strong>MINIMUM</strong><small>ADMIN CONSOLE</small></span>}
    </div>
  );
}

function NoticeBar({ notice, onClose }: { notice: Notice; onClose: () => void }) {
  if (!notice) return null;
  return (
    <div className={`notice notice-${notice.tone}`} role="status">
      {notice.tone === 'success' ? <Check size={16} /> : <AlertCircle size={16} />}
      <span>{notice.text}</span>
      <button className="icon-button notice-close" type="button" onClick={onClose} aria-label="Dismiss notice" title="Dismiss"><X size={15} /></button>
    </div>
  );
}

function SetupView({ onReady }: { onReady: () => void }) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true); setError('');
    try {
      await api('/api/setup', { method: 'POST', body: JSON.stringify({ username, password, confirmation }) });
      onReady();
    } catch (err) { setError(err instanceof Error ? err.message : 'Setup failed'); }
    finally { setBusy(false); }
  }
  return (
    <main className="auth-page">
      <div className="auth-grid-line" />
      <section className="auth-card">
        <Logo />
        <div className="auth-heading"><span className="eyebrow">FIRST-RUN SETUP</span><h1>Create administrator</h1><p>Secure the configuration console before registering devices.</p></div>
        <form onSubmit={submit} className="stack-form">
          <Field label="Username" value={username} onChange={setUsername} autoComplete="username" placeholder="operator" />
          <Field label="Password" value={password} onChange={setPassword} type="password" autoComplete="new-password" placeholder="12 characters minimum" />
          <Field label="Confirm password" value={confirmation} onChange={setConfirmation} type="password" autoComplete="new-password" placeholder="Repeat password" />
          {error && <div className="form-error"><AlertCircle size={15} />{error}</div>}
          <button className="primary-button full-width" type="submit" disabled={busy}>{busy ? 'Creating account...' : 'Create administrator'}<ChevronRight size={17} /></button>
        </form>
        <div className="auth-footer"><ShieldCheck size={15} /> Credentials are stored as a one-way hash.</div>
      </section>
    </main>
  );
}

function LoginView({ onReady }: { onReady: () => void }) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true); setError('');
    try { await api('/api/login', { method: 'POST', body: JSON.stringify({ username, password }) }); onReady(); }
    catch (err) { setError(err instanceof Error ? err.message : 'Login failed'); }
    finally { setBusy(false); }
  }
  return (
    <main className="auth-page">
      <div className="auth-grid-line" />
      <section className="auth-card">
        <Logo />
        <div className="auth-heading"><span className="eyebrow">SECURE ACCESS</span><h1>Sign in</h1><p>Manage Minimum device configurations.</p></div>
        <form onSubmit={submit} className="stack-form">
          <Field label="Username" value={username} onChange={setUsername} autoComplete="username" placeholder="operator" />
          <Field label="Password" value={password} onChange={setPassword} type="password" autoComplete="current-password" placeholder="Your password" />
          {error && <div className="form-error"><AlertCircle size={15} />{error}</div>}
          <button className="primary-button full-width" type="submit" disabled={busy}>{busy ? 'Verifying...' : 'Sign in'}<ChevronRight size={17} /></button>
        </form>
        <div className="auth-footer"><ShieldCheck size={15} /> Session expires after eight hours.</div>
      </section>
    </main>
  );
}

function EmptyState({ onAdd }: { onAdd: () => void }) {
  return <div className="empty-state"><div className="empty-icon"><RadioTower size={25} /></div><h2>No devices registered</h2><p>Provision a device profile to start publishing configuration.</p><button className="primary-button" type="button" onClick={onAdd}><Plus size={17} /> Add device</button></div>;
}

export function AdminPortal() {
  const [session, setSession] = useState<SessionState>({ loading: true, configured: false, authenticated: false, username: null });
  const [notice, setNotice] = useState<Notice>(null);
  const refreshSession = async () => {
    try { const data = await api<Omit<SessionState, 'loading'>>('/api/session'); setSession({ loading: false, ...data }); }
    catch { setSession((current) => ({ ...current, loading: false })); }
  };
  useEffect(() => { void refreshSession(); }, []);
  if (session.loading) return <div className="boot-screen"><Logo compact /><span>Checking console...</span></div>;
  if (!session.configured) return <SetupView onReady={refreshSession} />;
  if (!session.authenticated) return <LoginView onReady={refreshSession} />;
  return <Dashboard username={session.username || 'operator'} setNotice={setNotice} notice={notice} onLogout={refreshSession} />;
}

function Dashboard({ username, setNotice, notice, onLogout }: { username: string; setNotice: (notice: Notice) => void; notice: Notice; onLogout: () => void }) {
  const [devices, setDevices] = useState<DeviceSummary[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [selected, setSelected] = useState<DeviceRecord | null>(null);
  const [loading, setLoading] = useState(true);
  const [showAdd, setShowAdd] = useState(false);
  const [activeNav, setActiveNav] = useState('Devices');
  const loadDevices = async () => {
    setLoading(true);
    try {
      const data = await api<{ devices: DeviceSummary[] }>('/api/devices');
      setDevices(data.devices);
      if (selectedId && !data.devices.some((device) => device.deviceId === selectedId)) { setSelectedId(null); setSelected(null); }
    } catch (err) { setNotice({ tone: 'error', text: err instanceof Error ? err.message : 'Could not load devices' }); }
    finally { setLoading(false); }
  };
  useEffect(() => { void loadDevices(); }, []);
  const chooseDevice = async (deviceId: string) => {
    setSelectedId(deviceId); setSelected(null);
    try { const data = await api<{ device: DeviceRecord }>(`/api/devices/${deviceId}`); setSelected(data.device); }
    catch (err) { setNotice({ tone: 'error', text: err instanceof Error ? err.message : 'Could not load device' }); }
  };
  async function logout() { await api('/api/logout', { method: 'POST' }).catch(() => undefined); onLogout(); }
  return (
    <div className="portal-shell">
      <aside className="sidebar">
        <Logo />
        <div className="sidebar-label">WORKSPACE</div>
        <nav className="side-nav">
          <button className={activeNav === 'Overview' ? 'nav-item active' : 'nav-item'} onClick={() => setActiveNav('Overview')}><LayoutDashboard size={17} /> Overview</button>
          <button className={activeNav === 'Devices' ? 'nav-item active' : 'nav-item'} onClick={() => setActiveNav('Devices')}><RadioTower size={17} /> Devices <span className="nav-count">{devices.length}</span></button>
          <button className={activeNav === 'Activity' ? 'nav-item active' : 'nav-item'} onClick={() => setActiveNav('Activity')}><Activity size={17} /> Activity</button>
        </nav>
        <div className="sidebar-bottom">
          <div className="secure-chip"><ShieldCheck size={15} /><span><strong>Protected</strong><small>Admin session active</small></span></div>
          <button className="nav-item logout-item" onClick={logout}><LogOut size={17} /> Sign out</button>
        </div>
      </aside>
      <main className="main-content">
        <header className="topbar"><div><span className="breadcrumb">MINIMUM / {activeNav.toUpperCase()}</span><h1>{activeNav === 'Devices' ? 'Device registry' : activeNav}</h1></div><div className="topbar-actions"><span className="kv-status"><span className="status-dot" />KV REST ready</span><span className="user-chip">{username.slice(0, 1).toUpperCase()}</span></div></header>
        <NoticeBar notice={notice} onClose={() => setNotice(null)} />
        {activeNav === 'Devices' && <section className="workspace-grid">
          <section className="registry-panel surface">
            <div className="panel-heading"><div><span className="eyebrow">CONFIG PROFILES</span><h2>Devices <span className="heading-count">{devices.length.toString().padStart(2, '0')}</span></h2></div><div className="panel-actions"><button className="icon-button" onClick={() => void loadDevices()} title="Refresh devices" aria-label="Refresh devices"><RefreshCw size={17} /></button><button className="primary-button" onClick={() => setShowAdd(true)}><Plus size={17} /> Add device</button></div></div>
            {showAdd && <AddDeviceForm onClose={() => setShowAdd(false)} onSaved={(token, device) => { setShowAdd(false); setNotice({ tone: 'success', text: `Device ${device.deviceId} created. Copy the token before closing.` }); void loadDevices(); setSelectedId(device.deviceId); setSelected({ ...device, config: emptyConfig(device.deviceId) }); }} />}
            {loading ? <div className="loading-row"><RefreshCw size={17} className="spin" />Loading registry...</div> : devices.length === 0 ? <EmptyState onAdd={() => setShowAdd(true)} /> : <div className="device-table"><div className="table-head"><span>PROFILE</span><span>MODEL</span><span>VERSION</span><span>UPDATED</span><span /></div>{devices.map((device) => <button className={`device-row ${selectedId === device.deviceId ? 'selected' : ''}`} key={device.deviceId} onClick={() => void chooseDevice(device.deviceId)}><span className="device-identity"><span className="device-mark"><RadioTower size={16} /></span><span><strong>{device.deviceId}</strong><small>{device.label}</small></span></span><span className="muted mono">{device.model}</span><span className="version-pill">v{device.configVersion}</span><span className="muted">{formatDate(device.updatedAt)}</span><ChevronRight size={16} className="row-chevron" /></button>)}</div>}
          </section>
          {selectedId && <DeviceEditor key={selectedId} device={selected} onSaved={(updated) => { setSelected(updated); setNotice({ tone: 'success', text: `${updated.deviceId} saved at config v${updated.config.configVersion}.` }); void loadDevices(); }} onDeleted={() => { setSelected(null); setSelectedId(null); setNotice({ tone: 'success', text: 'Device removed.' }); void loadDevices(); }} onToken={(token) => setNotice({ tone: 'success', text: `New device token: ${token}` })} />}
        </section>}
        {activeNav !== 'Devices' && <section className="placeholder-panel surface"><div className="empty-icon"><Settings2 size={24} /></div><h2>{activeNav} is quiet</h2><p>Operational history will appear here as devices report state.</p></section>}
      </main>
    </div>
  );
}

function AddDeviceForm({ onClose, onSaved }: { onClose: () => void; onSaved: (token: string, device: DeviceRecord) => void }) {
  const [deviceId, setDeviceId] = useState('');
  const [label, setLabel] = useState('');
  const [model, setModel] = useState('generic-radio');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  async function submit(event: FormEvent) {
    event.preventDefault(); setBusy(true); setError('');
    try {
      const normalized = deviceId.toUpperCase();
      const data = await api<{ token: string; device: DeviceSummary }>('/api/devices', { method: 'POST', body: JSON.stringify({ deviceId: normalized, label, model, config: emptyConfig(normalized) }) });
      onSaved(data.token, { ...data.device, config: emptyConfig(normalized) });
    } catch (err) { setError(err instanceof Error ? err.message : 'Could not create device'); }
    finally { setBusy(false); }
  }
  return <div className="inline-form"><div className="inline-form-head"><div><span className="eyebrow">NEW PROFILE</span><h3>Register device</h3></div><button className="icon-button" onClick={onClose} aria-label="Close" title="Close"><X size={17} /></button></div><form onSubmit={submit} className="form-grid"><Field label="Device ID" value={deviceId} onChange={setDeviceId} placeholder="ABC123" /><Field label="Display label" value={label} onChange={setLabel} placeholder="Field unit 01" /><Field label="Model profile" value={model} onChange={setModel} placeholder="generic-radio" /><div className="form-actions"><button className="quiet-button" type="button" onClick={onClose}>Cancel</button><button className="primary-button" type="submit" disabled={busy}>{busy ? 'Creating...' : 'Create profile'}<Plus size={16} /></button></div>{error && <div className="form-error grid-span"><AlertCircle size={15} />{error}</div>}</form></div>;
}

function DeviceEditor({ device, onSaved, onDeleted, onToken }: { device: DeviceRecord | null; onSaved: (device: DeviceRecord) => void; onDeleted: () => void; onToken: (token: string) => void }) {
  const [label, setLabel] = useState('');
  const [model, setModel] = useState('');
  const [configText, setConfigText] = useState('');
  const [tab, setTab] = useState<'config' | 'security'>('config');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [token, setToken] = useState('');
  useEffect(() => { if (device) { setLabel(device.label); setModel(device.model); setConfigText(JSON.stringify(device.config, null, 2)); setError(''); setToken(''); } }, [device]);
  const parsed = useMemo(() => { try { return { value: JSON.parse(configText) as MinimumConfig, error: '' }; } catch { return { value: null, error: 'JSON syntax error' }; } }, [configText]);
  if (!device) return <section className="editor-panel surface loading-editor"><RefreshCw size={17} className="spin" />Loading profile...</section>;
  const currentDevice = device;
  async function save() {
    setBusy(true); setError(parsed.error); if (parsed.error) { setBusy(false); return; }
    try { const data = await api<{ device: DeviceRecord }>(`/api/devices/${currentDevice.deviceId}`, { method: 'PATCH', body: JSON.stringify({ label, model, config: parsed.value }) }); onSaved(data.device); }
    catch (err) { setError(err instanceof Error ? err.message : 'Could not save profile'); }
    finally { setBusy(false); }
  }
  async function rotateToken() {
    if (!window.confirm('Rotate this device token? The existing token will stop working immediately.')) return;
    try { const data = await api<{ token: string }>(`/api/devices/${currentDevice.deviceId}/token`, { method: 'POST' }); setToken(data.token); onToken(data.token); }
    catch (err) { setError(err instanceof Error ? err.message : 'Could not rotate token'); }
  }
  async function remove() {
    if (!window.confirm(`Delete ${currentDevice.deviceId}? This cannot be undone.`)) return;
    try { await api(`/api/devices/${currentDevice.deviceId}`, { method: 'DELETE' }); onDeleted(); }
    catch (err) { setError(err instanceof Error ? err.message : 'Could not delete device'); }
  }
  return <section className="editor-panel surface"><div className="editor-heading"><div className="editor-title"><span className="device-mark large"><RadioTower size={19} /></span><div><span className="eyebrow">DEVICE PROFILE</span><h2>{device.deviceId}</h2><span className="muted">{device.label}</span></div></div><div className="editor-actions"><span className="version-pill">v{device.config.configVersion}</span><button className="icon-button danger-icon" onClick={() => void remove()} title="Delete device" aria-label="Delete device"><Trash2 size={17} /></button></div></div><div className="editor-meta"><span><small>MODEL</small><strong>{device.model}</strong></span><span><small>LAST UPDATED</small><strong>{formatDate(device.updatedAt)}</strong></span><span><small>DEVICE API</small><strong className="green-text">Bearer enabled</strong></span></div><div className="editor-tabs"><button className={tab === 'config' ? 'editor-tab active' : 'editor-tab'} onClick={() => setTab('config')}><Settings2 size={15} /> Configuration</button><button className={tab === 'security' ? 'editor-tab active' : 'editor-tab'} onClick={() => setTab('security')}><KeyRound size={15} /> Security</button></div>{tab === 'config' ? <div className="config-editor"><div className="form-grid"><Field label="Display label" value={label} onChange={setLabel} /><Field label="Model profile" value={model} onChange={setModel} /></div><label className="field json-field"><span>Schema 3 configuration <em>{parsed.error || 'Valid JSON'}</em></span><textarea value={configText} onChange={(event) => setConfigText(event.target.value)} spellCheck={false} /></label>{error && <div className="form-error"><AlertCircle size={15} />{error}</div>}<div className="editor-footer"><span className="muted">Effective changes require a higher configVersion.</span><button className="primary-button" onClick={() => void save()} disabled={busy || Boolean(parsed.error)}><Save size={16} />{busy ? 'Saving...' : 'Save configuration'}</button></div></div> : <SecurityPanel token={token} onRotate={() => void rotateToken()} />}</section>;
}

function SecurityPanel({ token, onRotate }: { token: string; onRotate: () => void }) {
  const [copied, setCopied] = useState(false);
  async function copyToken() { if (!token) return; await navigator.clipboard?.writeText(token); setCopied(true); setTimeout(() => setCopied(false), 1800); }
  return <div className="security-panel"><div className="security-row"><div className="security-icon"><KeyRound size={18} /></div><div><h3>Device API token</h3><p>Only the token hash is stored. Rotation revokes the previous token.</p></div><span className="status-badge"><span className="status-dot" />Active</span></div><div className="token-box">{token ? <><code>{token}</code><button className="icon-button" onClick={() => void copyToken()} title="Copy token" aria-label="Copy token">{copied ? <Check size={16} /> : <Copy size={16} />}</button></> : <span className="token-placeholder">Token hidden. Rotate to issue a new one.</span>}</div><div className="security-actions"><button className="primary-button" onClick={onRotate}><RefreshCw size={16} /> Rotate token</button><span className="muted">The device endpoint uses <code>Authorization: Bearer</code>.</span></div></div>;
}
