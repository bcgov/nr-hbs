import { Asleep, Light, Logout } from '@carbon/icons-react';
import { SideNavLink } from '@carbon/react';
import { useEffect, useState, type FC } from 'react';
import AvatarImage from '@/components/Layout/AvatarImage';
import { ROLE_LABELS } from '@/context/auth/types';
import { useAuth } from '@/context/auth/useAuth';
import { useOrg, useScopedUser } from '@/context/org/useOrg';
import { useTheme } from '@/context/theme/useTheme';
import { getClient } from '@/services/clientSearch';
import './HeaderPanelProfile.css';

// parseToken normalises the identity provider to IDIR / BCEIDBUSINESS. Turn the
// values we care about into the labels the user actually expects to
// see in their profile. Anything we don't recognise falls back to the
// raw claim so we don't accidentally lie about the IDP.
const PROVIDER_LABEL: Record<string, string> = {
  IDIR: 'IDIR',
  BCEIDBUSINESS: 'Business BCeID',
};


const HeaderPanelProfile: FC = () => {
  const { theme, toggleTheme } = useTheme();
  const { user, logout } = useAuth();
  const scoped = useScopedUser();
  const { activeOrgClientNumber } = useOrg();
  const [orgName, setOrgName] = useState<string | null>(null);

  // Look up the active org's forest-client name once. The picker page
  // does this same enrichment via getClient; we duplicate the call
  // here so the profile panel stays self-contained (no shared cache).
  // Bounded — one request per client number per mount.
  useEffect(() => {
    if (!activeOrgClientNumber) {
      setOrgName(null);
      return;
    }
    let cancelled = false;
    getClient(activeOrgClientNumber)
      .then((client) => {
        if (cancelled) return;
        const name = client.clientName?.trim();
        setOrgName(name ?? null);
      })
      .catch(() => {
        if (!cancelled) setOrgName(null);
      });
    return () => {
      cancelled = true;
    };
  }, [activeOrgClientNumber]);

  const fullName = [user?.firstName, user?.lastName].filter(Boolean).join(' ').trim()
    || user?.displayName
    || '';

  const providerLabel = user?.idpProvider
    ? PROVIDER_LABEL[user.idpProvider] ?? user.idpProvider
    : 'IDIR';

  // HBS roles stack, so list every role held for the active client.
  const roleLabels = (scoped?.roles ?? []).map((r) => ROLE_LABELS[r]);
  const nameWithRole = fullName || 'User';

  return (
    <div className="my-profile-container">
      <div className="user-info-section">
        <div className="user-image">
          <AvatarImage userName={fullName} size="large" />
        </div>
        <div className="user-data">
          <p className="user-name">{nameWithRole}</p>
          {user?.userName ? <p>{`${providerLabel}: ${user.userName}`}</p> : null}
          {activeOrgClientNumber ? (
            <p>
              {`Organization: ${activeOrgClientNumber}${orgName ? ` — ${orgName}` : ''}`}
            </p>
          ) : null}
          {user?.email ? <p>{`Email: ${user.email}`}</p> : null}
          {roleLabels.length > 0 ? <p>{`Roles: ${roleLabels.join(', ')}`}</p> : null}
        </div>
      </div>
      <hr className="divisory" />
      <nav className="account-nav">
        <ul>
          <SideNavLink
            className="cursor-pointer"
            renderIcon={theme === 'g100' ? Light : Asleep}
            onClick={toggleTheme}
          >
            Change theme
          </SideNavLink>
          <SideNavLink className="cursor-pointer" renderIcon={Logout} onClick={logout}>
            Log out
          </SideNavLink>
        </ul>
      </nav>
    </div>
  );
};

export default HeaderPanelProfile;
