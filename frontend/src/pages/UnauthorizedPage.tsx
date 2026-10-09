import { Logout } from '@carbon/icons-react';
import { Button, Column, Grid } from '@carbon/react';
import type { FC } from 'react';

import { useAuth } from '@/context/auth/useAuth';
import { useTheme } from '@/context/theme/useTheme';

import './LandingPage.scss';

/**
 * Shown after a successful BC Gov SSO (Keycloak) login when the user's token carries
 * no recognised HBS_* role. AuthProvider intentionally keeps
 * isLoggedIn=true in that case so the routing layer can land here
 * instead of bouncing the user back through the IdP. The only action
 * is "sign out" — getting a role requires an out-of-band FAM admin
 * step (no self-service from inside the app).
 */
const UnauthorizedPage: FC = () => {
  const { theme } = useTheme();
  const { user, logout } = useAuth();
  const logoSrc = theme === 'g100' ? '/bc-gov-logo-rev.png' : '/bc-gov-logo.png';

  const displayName =
    user?.displayName ||
    [user?.firstName, user?.lastName].filter(Boolean).join(' ') ||
    user?.userName ||
    user?.providerUsername;

  return (
    <div className="landing-grid-container">
      <Grid fullWidth className="landing-grid">
        <Column className="landing-content-col" sm={4} md={8} lg={8}>
          <div className="landing-content-wrapper">
            <div>
              <img src={logoSrc} alt="BC Government" width={160} className="logo" />
            </div>

            <h1 data-testid="unauthorized-title" className="landing-title">
              Access not granted
            </h1>

            <h2 data-testid="unauthorized-subtitle" className="landing-subtitle">
              {displayName
                ? `You're signed in as ${displayName}, but your account isn't authorized to use HBS.`
                : 'Your account is signed in, but it isn’t authorized to use HBS.'}
            </h2>

            <div className="landing-actions">
              <div className="buttons-container single-row">
                <Button
                  type="button"
                  kind="secondary"
                  onClick={() => void logout()}
                  renderIcon={Logout}
                  size="md"
                  data-testid="unauthorized-button__logout"
                  className="login-btn"
                >
                  Sign out
                </Button>
              </div>

              {/* The only route to a role is an administrator granting one in
                  FAM, so say so — otherwise the page is a dead end. */}
              <p className="landing-note" data-testid="unauthorized-note">
                Access to HBS requires a role assigned in FAM. Contact your HBS administrator, or
                your organization&apos;s BCeID administrator, to request access.
              </p>
            </div>
          </div>
        </Column>

        <Column className="landing-img-col" sm={4} md={8} lg={8}>
          <img src="/landing.jpg" alt="Ponderosa pine forest, British Columbia" className="landing-img" />
        </Column>
      </Grid>
    </div>
  );
};

export default UnauthorizedPage;
