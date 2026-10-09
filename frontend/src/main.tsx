import React from 'react';
import { createRoot } from 'react-dom/client';

import App from './App';
import { AuthProvider } from './context/auth/AuthProvider';
import { NotificationProvider } from './context/notification/NotificationProvider';
import OrgProvider from './context/org/OrgProvider';
import ThemeProvider from './context/theme/ThemeProvider';

import './index.scss';

// Auth is BC Gov SSO (Keycloak) via oidc-client-ts — see services/keycloak.ts.
// Nothing to configure at boot: the UserManager is created lazily, after
// window.config (runtime env) is available.

const container = document.getElementById('root');
if (!container) throw new Error('Root container #root not found');

createRoot(container).render(
  <React.StrictMode>
    <AuthProvider>
      <OrgProvider>
        <ThemeProvider>
          <NotificationProvider>
            <App />
          </NotificationProvider>
        </ThemeProvider>
      </OrgProvider>
    </AuthProvider>
  </React.StrictMode>,
);
