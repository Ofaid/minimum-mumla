import type { Metadata } from 'next';
import { BotIdClient } from 'botid/client';
import './globals.css';

export const metadata: Metadata = {
  title: 'Minimum Admin',
  description: 'Secure configuration console for Minimum radio devices'
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en">
      <body>
        {process.env.NODE_ENV === 'production' && <BotIdClient protect={[
          { path: '/api/setup', method: 'POST' },
          { path: '/api/login', method: 'POST' },
          { path: '/api/devices', method: 'POST' },
          { path: '/api/devices/:deviceId', method: 'PATCH' },
          { path: '/api/devices/:deviceId', method: 'DELETE' },
          { path: '/api/devices/:deviceId/token', method: 'POST' }
        ]} />}
        {children}
      </body>
    </html>
  );
}
