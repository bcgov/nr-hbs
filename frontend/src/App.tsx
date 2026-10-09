import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import type { ReactNode } from 'react';

import Layout from './components/Layout';
import ScreenPage from './components/screens/ScreenPage';
import SessionTimeout from './components/SessionTimeout';
import { useAuth } from './context/auth/useAuth';
import { useOrg } from './context/org/useOrg';
import { defaultRouteForUser } from './routes/access';

// Pages
import LandingPage from './pages/LandingPage';
import UnauthorizedPage from './pages/UnauthorizedPage';
import OrgSelectionPage from './pages/OrgSelectionPage';
import HomePage from './pages/HomePage';
import ReportsPage from './pages/ReportsPage';
import SubmitScaleDataPage from './pages/SubmitScaleDataPage';
import AreaLandingPage from './pages/AreaLandingPage';
import AuthCallback from './pages/AuthCallback';

import './App.css';

// Wraps a page in the Carbon UI Shell — same helper as nr-fsp-new.
const withLayout = (node: ReactNode) => <Layout>{node}</Layout>;

// ── App ────────────────────────────────────────────────────
// Same auth gating as nr-fsp-new: bootstrap placeholder → no-role landing →
// BCeID multi-client org picker → authenticated routes. Per-screen access is
// enforced inside ScreenPage / each page via routes/access.ts `can()` (and
// always again by the backend).
export default function App() {
  const { isLoggedIn, isLoading, user } = useAuth();
  const { needsOrgSelection } = useOrg();

  if (isLoading) {
    return <div aria-busy="true" />;
  }

  const hasHbsRole = isLoggedIn && (user?.roles?.length ?? 0) > 0;

  return (
    <BrowserRouter>
      {isLoggedIn && <SessionTimeout />}
      {isLoggedIn && !hasHbsRole ? (
        <Routes>
          <Route path="/authCallback" element={<AuthCallback />} />
          <Route path="*" element={<UnauthorizedPage />} />
        </Routes>
      ) : isLoggedIn && needsOrgSelection ? (
        <Routes>
          <Route path="/authCallback" element={<AuthCallback />} />
          <Route path="/org-select" element={<OrgSelectionPage />} />
          <Route path="*" element={<Navigate to="/org-select" replace />} />
        </Routes>
      ) : isLoggedIn ? (
        <Routes>
          {/* A reload of a spent callback URL — the session already exists. */}
          <Route path="/authCallback" element={<Navigate to={defaultRouteForUser(user)} replace />} />
          <Route path="/" element={<Navigate to={defaultRouteForUser(user)} replace />} />
          <Route path="/org-select" element={<OrgSelectionPage />} />

          {/* Legacy home.jsp — role-driven work-queue dashboard. */}
          <Route path="/home" element={withLayout(<HomePage />)} />

          {/* Bespoke pages (custom interaction beyond search/detail/form). */}
          <Route path="/scale-returns/submit" element={withLayout(<SubmitScaleDataPage />)} />
          <Route path="/reports" element={withLayout(<ReportsPage />)} />

          {/* Legacy tab landing pages (P400, P002, P850 …) and every
              registry screen: /:area/:screenId. */}
          <Route path="/:area" element={withLayout(<AreaLandingPage />)} />
          <Route path="/:area/:screenId" element={withLayout(<ScreenPage />)} />

          <Route path="*" element={<Navigate to={defaultRouteForUser(user)} replace />} />
        </Routes>
      ) : (
        <Routes>
          {/* MUST stay above the catch-all: the session doesn't exist until
              this route exchanges the authorization code (same as nr-fta). */}
          <Route path="/authCallback" element={<AuthCallback />} />
          <Route path="*" element={<LandingPage />} />
        </Routes>
      )}
    </BrowserRouter>
  );
}
