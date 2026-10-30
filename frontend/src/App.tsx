import { BrowserRouter, Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { useRef, type ReactNode } from 'react';

import Layout from './components/Layout';
import ScreenPage from './components/screens/ScreenPage';
import SessionTimeout from './components/SessionTimeout';
import DismissToastsOnNavigate from './context/notification/DismissToastsOnNavigate';
import { useAuth } from './context/auth/useAuth';
import { useOrg } from './context/org/useOrg';
import { defaultRouteForUser } from './routes/access';
import { rememberReturnTo } from './routes/returnTo';

// Pages
import LandingPage from './pages/LandingPage';
import UnauthorizedPage from './pages/UnauthorizedPage';
import OrgSelectionPage from './pages/OrgSelectionPage';
import HomePage from './pages/HomePage';
import StumpageRatePage from './pages/StumpageRatePage';
import SubmitScaleDataPage from './pages/SubmitScaleDataPage';
import ScalerKeyRequestPage from './pages/ScalerKeyRequestPage';
import PublicKeyPage from './pages/PublicKeyPage';
import SummaryReturnGridPage from './pages/SummaryReturnGridPage';
import DetailEntryPage from './pages/detail/DetailEntryPage';
import DetailBulkPage from './pages/detail/DetailBulkPage';
import AreaLandingPage from './pages/AreaLandingPage';
import AuthCallback from './pages/AuthCallback';
import InvoiceInformationPage from './pages/InvoiceInformationPage';
import StatementDeliveryPage from './pages/StatementDeliveryPage';
import PublicLayout from './pages/public/PublicLayout';
import PublicHomePage from './pages/public/PublicHomePage';
import PublicAccessPage from './pages/public/PublicAccessPage';
import PublicScreenPage from './pages/public/PublicScreenPage';

import './App.css';

// Wraps a page in the Carbon UI Shell — same helper as nr-fsp-new.
const withLayout = (node: ReactNode) => <Layout>{node}</Layout>;

/**
 * Keeps a legacy StatementDelivery link (e-mailed by batch B2065) across the
 * sign-in and organization gates, then renders the gate itself.
 */
const KeepLink = ({ children }: { children: ReactNode }) => {
  const { pathname, search } = useLocation();
  const kept = useRef(false);
  if (!kept.current) {
    kept.current = true;
    rememberReturnTo(pathname + search);
  }
  return <>{children}</>;
};

// The public (anonymous) read-only area — legacy mainmenu_public.jsp. Outside
// every auth gate: anyone, logged in or not, with or without HBS roles, can
// open /public/… (docs/public-access.md). Every public page lives here.
const withPublicLayout = (node: ReactNode) => <PublicLayout>{node}</PublicLayout>;
const publicRoutes = (
  <>
    <Route path="/public">
      <Route index element={withPublicLayout(<PublicHomePage />)} />
      <Route path="access" element={withPublicLayout(<PublicAccessPage />)} />
      {/* The statement e-mails' "Explanation of Symbols" page. */}
      <Route path="invoice-information" element={withPublicLayout(<InvoiceInformationPage />)} />
      <Route path=":screenId" element={withPublicLayout(<PublicScreenPage />)} />
    </Route>
    {/* Its old address, still in statement e-mails already sent and in the
        legacy batch setting until that is changed (docs/coexistence.md). */}
    <Route path="/invoice-information" element={<Navigate to="/public/invoice-information" replace />} />
  </>
);

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
      <DismissToastsOnNavigate />
      {isLoggedIn && <SessionTimeout />}
      {isLoggedIn && !hasHbsRole ? (
        <Routes>
          {publicRoutes}
          <Route path="/authCallback" element={<AuthCallback />} />
          <Route path="*" element={<UnauthorizedPage />} />
        </Routes>
      ) : isLoggedIn && needsOrgSelection ? (
        <Routes>
          {publicRoutes}
          <Route path="/authCallback" element={<AuthCallback />} />
          <Route path="/org-select" element={<OrgSelectionPage />} />
          <Route
            path="/StatementDelivery/*"
            element={
              <KeepLink>
                <Navigate to="/org-select" replace />
              </KeepLink>
            }
          />
          <Route path="*" element={<Navigate to="/org-select" replace />} />
        </Routes>
      ) : isLoggedIn ? (
        <Routes>
          {publicRoutes}
          {/* A reload of a spent callback URL — the session already exists. */}
          <Route path="/authCallback" element={<Navigate to={defaultRouteForUser(user)} replace />} />
          <Route path="/" element={<Navigate to={defaultRouteForUser(user)} replace />} />
          <Route path="/org-select" element={<OrgSelectionPage />} />

          {/* Legacy home.jsp — role-driven work-queue dashboard. */}
          <Route path="/home" element={withLayout(<HomePage />)} />

          {/* Bespoke pages (custom interaction beyond search/detail/form). */}
          <Route path="/scale-returns/submit" element={withLayout(<SubmitScaleDataPage />)} />
          <Route path="/scale-returns/public-key" element={withLayout(<PublicKeyPage />)} />
          <Route path="/profiles/scaler-key-request" element={withLayout(<ScalerKeyRequestPage />)} />

          {/* Legacy StatementDelivery links: the statement e-mails (legacy batch
              B2065) and statement bookmarks. Fetch with the token, then download. */}
          <Route
            path="/StatementDelivery/transmissions/:file"
            element={withLayout(<StatementDeliveryPage kind="transmissions" />)}
          />
          <Route
            path="/StatementDelivery/statements/:type/:file"
            element={withLayout(<StatementDeliveryPage kind="statements" />)}
          />
          {/* P230/P231 — HBS_RATES OUT-parameter procs, not a registry query. */}
          <Route path="/rating/stumpage-rate" element={withLayout(<StumpageRatePage />)} />
          <Route path="/scale-returns/summary-return-children" element={withLayout(<SummaryReturnGridPage />)} />
          {/* Detail-return data entry + workflow (P004, P550–P760) and bulk requests (P560A/P561). */}
          <Route path="/scale-returns/detail-entry" element={withLayout(<DetailEntryPage />)} />
          <Route path="/scale-returns/detail-bulk" element={withLayout(<DetailBulkPage />)} />

          {/* Legacy tab landing pages (P400, P002, P850 …) and every
              registry screen: /:area/:screenId. */}
          <Route path="/:area" element={withLayout(<AreaLandingPage />)} />
          <Route path="/:area/:screenId" element={withLayout(<ScreenPage />)} />

          <Route path="*" element={<Navigate to={defaultRouteForUser(user)} replace />} />
        </Routes>
      ) : (
        <Routes>
          {publicRoutes}
          {/* MUST stay above the catch-all: the session doesn't exist until
              this route exchanges the authorization code (same as nr-fta). */}
          <Route path="/authCallback" element={<AuthCallback />} />
          <Route
            path="/StatementDelivery/*"
            element={
              <KeepLink>
                <LandingPage />
              </KeepLink>
            }
          />
          <Route path="*" element={<LandingPage />} />
        </Routes>
      )}
    </BrowserRouter>
  );
}
