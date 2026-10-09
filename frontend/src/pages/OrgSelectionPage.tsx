import {
  Button,
  Column,
  Grid,
  Loading,
  RadioButton,
  RadioButtonGroup,
} from '@carbon/react';
import { ArrowRight } from '@carbon/icons-react';
import { useEffect, useMemo, useRef, useState, type FC } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';

import { listClientOrgs } from '@/context/auth/authUtils';
import { ROLE_LABELS, type ROLE_TYPE } from "@/context/auth/types";
import { useAuth } from '@/context/auth/useAuth';
import { useOrg } from '@/context/org/useOrg';
import { useTheme } from '@/context/theme/useTheme';
import { defaultRouteForUser } from '@/routes/access';
import { getClient } from '@/services/clientSearch';

// Reuses the Landing/Unauthorized split-screen stylesheet so all three
// pre-app screens share the same logo + content / forest-image right
// side treatment.
import './LandingPage.scss';


interface OrgRow {
  clientNumber: string;
  /** Display label — name when we manage to fetch it, "Client #…" otherwise. */
  label: string;
  /** The client-tied roles the user holds for this org. */
  roles: ROLE_TYPE[];
}

const buildFallbackLabel = (cn: string) => `Client #${cn}`;

/**
 * Post-login page for BCeID users who hold client-tied HBS roles for
 * more than one forest-client number. Forces an explicit pick so every
 * downstream API call has an unambiguous org context (the active client
 * number flows via the X-HBS-Active-Org-Client-Number header). Once
 * chosen, the user is redirected back to the SPA's normal landing.
 *
 * <p>Single-org BCeID users and IDIR users never see this page — they
 * land on the post-login redirect directly (handled in App.tsx).
 */
const OrgSelectionPage: FC = () => {
  const { user } = useAuth();
  const { theme } = useTheme();
  const {
    availableOrgClientNumbers,
    activeOrgClientNumber,
    needsOrgSelection,
    setActiveOrgClientNumber,
  } = useOrg();
  const navigate = useNavigate();
  const location = useLocation();
  // Marks whether the user clicked Continue. Used by the post-gate
  // useEffect below so a user who revisits /org-select after picking
  // doesn't get auto-redirected; only an explicit Continue triggers
  // the navigate-to-data-submission flow.
  const continueClicked = useRef(false);
  const [rows, setRows] = useState<OrgRow[] | null>(null);
  const [selected, setSelected] = useState<string>(
    activeOrgClientNumber ?? availableOrgClientNumbers[0] ?? '',
  );
  const [loading, setLoading] = useState(false);

  // clientNumber → the role the user holds for that org.
  const rolesByClient = useMemo(() => {
    const map = new Map<string, ROLE_TYPE[]>();
    if (user?.privileges) {
      for (const o of listClientOrgs(user.privileges)) map.set(o.clientNumber, o.roles);
    }
    return map;
  }, [user?.privileges]);

  // Post-pick landing is the Home dashboard.
  const landingRoute = defaultRouteForUser(user);

  const logoSrc = theme === 'g100' ? '/bc-gov-logo-rev.png' : '/bc-gov-logo.png';

  // Enrich each client number with its forest-client name in parallel.
  // One Forest Client API lookup per client number (via the backend);
  // a failed lookup falls back to the bare number.
  useEffect(() => {
    if (availableOrgClientNumbers.length === 0) return;
    let cancelled = false;
    setLoading(true);
    Promise.all(
      availableOrgClientNumbers.map(async (cn): Promise<OrgRow> => {
        const roles = rolesByClient.get(cn) ?? [];
        try {
          const r = await getClient(cn);
          return {
            clientNumber: cn,
            label: r?.clientName
              ? `${r.clientName.trim()} (${cn})`
              : buildFallbackLabel(cn),
            roles,
          };
        } catch {
          return { clientNumber: cn, label: buildFallbackLabel(cn), roles };
        }
      }),
    )
      .then((enriched) => {
        if (!cancelled) setRows(enriched);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [availableOrgClientNumbers, rolesByClient]);

  const onContinue = () => {
    if (!selected) return;
    continueClicked.current = true;
    setActiveOrgClientNumber(selected);
    navigate(landingRoute, { replace: true });
  };

  // When the gate flips from true→false the App.tsx route branch
  // switches from gated→authenticated. If a navigate to a non-gated
  // path (e.g. /data-submission) fired *before* the branch swap, the
  // gated catch-all bounces the URL back to /org-select. This effect
  // catches that bounce and re-issues the navigate once the
  // authenticated branch is mounted. Gated on the click marker so a
  // post-selection revisit (user dropped in to switch orgs) isn't
  // immediately redirected away.
  useEffect(() => {
    if (
      continueClicked.current &&
      activeOrgClientNumber &&
      !needsOrgSelection &&
      location.pathname === '/org-select'
    ) {
      navigate(landingRoute, { replace: true });
    }
  }, [activeOrgClientNumber, needsOrgSelection, location.pathname, navigate, landingRoute]);

  return (
    <div className="landing-grid-container">
      <Grid fullWidth className="landing-grid">
        <Column className="landing-content-col" sm={4} md={8} lg={8}>
          <div className="landing-content-wrapper">
            <div>
              <img src={logoSrc} alt="BC Government" width={160} className="logo" />
            </div>

            <h1 data-testid="org-select-title" className="landing-title">
              Select organization
            </h1>

            <h2 className="landing-subtitle">
              {user?.displayName ? `${user.displayName}, choose` : 'Choose'} the client you want to work
              under for this session.
            </h2>

            <div className="landing-actions">
              {loading && (
                <div role="status" aria-live="polite">
                  <Loading description="Loading organizations…" withOverlay={false} small />
                </div>
              )}
              {rows && (
                <>
                  <RadioButtonGroup
                    legendText="Organization"
                    name="org-pick"
                    valueSelected={selected}
                    orientation="vertical"
                    onChange={(v) => setSelected(String(v))}
                    className="landing-org-list"
                  >
                    {rows.map((r) => (
                      <RadioButton
                        key={r.clientNumber}
                        id={`org-pick-${r.clientNumber}`}
                        labelText={`${r.label} — ${r.roles.map((x) => ROLE_LABELS[x]).join(', ')}`}
                        value={r.clientNumber}
                      />
                    ))}
                  </RadioButtonGroup>
                  <div className="buttons-container single-row">
                    <Button
                      type="button"
                      onClick={onContinue}
                      size="md"
                      renderIcon={ArrowRight}
                      data-testid="org-select-button__continue"
                      disabled={!selected}
                      className="login-btn"
                    >
                      Continue
                    </Button>
                  </div>
                </>
              )}
              <p className="landing-note">
                You are registered with more than one forest-client organization; your roles for each
                are shown above. Sign out and back in to switch later.
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

export default OrgSelectionPage;
