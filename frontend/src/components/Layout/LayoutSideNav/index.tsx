import { Email } from '@carbon/icons-react';
import { SideNav, SideNavItems, SideNavLink, SideNavMenu, SideNavMenuItem } from '@carbon/react';
import { useEffect, useState, type FC } from 'react';
import { Link, useLocation } from 'react-router-dom';

import { useLayout } from '@/context/layout/useLayout';
import { useScopedUser } from '@/context/org/useOrg';
import {
  getMenuSections,
  sectionIdForPath,
  type MenuLeaf,
  type MenuSection,
} from '@/routes/routePaths';
import './LayoutSideNav.css';

/** Shared support mailbox reached from the nav's bottom-pinned Support link. */
const SUPPORT_EMAIL = 'FORHVAP.HBSHELP@gov.bc.ca';

/**
 * The application's side navigation — nr-fta's.
 *
 * <ul>
 *   <li><b>Expanded</b> — the legacy HBS tab bar's areas as headings, each a
 *       collapsible {@code SideNavMenu} holding its screens. The section
 *       containing the current route opens on load; the rest stay closed.
 *   <li><b>Collapsed</b> — a 48px icon rail showing one icon per section. The
 *       icons do not navigate: clicking one expands the nav and opens that
 *       section. Hovering names the section.
 * </ul>
 *
 * The open section is driven from the current route via
 * {@code defaultExpanded} (Carbon's SideNavMenu only reacts to the nav
 * collapsing when its context reports {@code isRail}, which this nav is not).
 */
export const LayoutSideNav: FC = () => {
  const { isSideNavExpanded, openSideNav } = useLayout();
  const location = useLocation();
  const user = useScopedUser();
  const sections = getMenuSections(user);

  // The section the user opened from the rail, if any. It takes precedence
  // over the route's own section so the nav expands showing what was clicked.
  const [railSectionId, setRailSectionId] = useState<string | undefined>(undefined);
  const routeSectionId = sectionIdForPath(user, location.pathname);
  const activeSectionId = railSectionId ?? routeSectionId;

  // Once the nav is closed again, forget the rail choice so reopening follows
  // the current route rather than a stale click.
  useEffect(() => {
    if (!isSideNavExpanded) setRailSectionId(undefined);
  }, [isSideNavExpanded]);

  const renderRailSection = (section: MenuSection) => (
    <SideNavLink
      data-testid={`side-nav-rail-${section.id}`}
      key={section.id}
      as="button"
      type="button"
      onClick={() => {
        setRailSectionId(section.id);
        openSideNav();
      }}
      isActive={section.id === activeSectionId}
      renderIcon={section.icon}
    >
      {section.label}
    </SideNavLink>
  );

  // SideNavMenuItem has no renderIcon, so the icon is a child and the row is
  // laid out in LayoutSideNav.css.
  const renderItem = (route: MenuLeaf) => {
    const Icon = route.icon;
    return (
      <SideNavMenuItem
        data-testid={`side-nav-link-${route.id}`}
        key={route.id}
        as={Link}
        to={route.path}
        isActive={route.path === location.pathname}
      >
        <span className="side-nav-item">
          {Icon ? (
            <span className="side-nav-item__icon" aria-hidden="true">
              <Icon />
            </span>
          ) : null}
          <span className="side-nav-item__label" title={route.label}>
            {route.label}
          </span>
        </span>
      </SideNavMenuItem>
    );
  };

  const renderSection = (section: MenuSection) => (
    <SideNavMenu
      key={section.id}
      data-testid={`side-nav-menu-${section.id}`}
      title={section.label}
      defaultExpanded={section.id === activeSectionId}
      className={`side-nav-section side-nav-section--${section.id}`}
    >
      {section.items.map(renderItem)}
    </SideNavMenu>
  );

  return (
    <SideNav
      expanded
      isPersistent={false}
      isChildOfHeader
      className={`side-nav-drawer${isSideNavExpanded ? ' side-nav-drawer--open' : ''}`}
      aria-label="Main navigation"
    >
      <SideNavItems>
        {isSideNavExpanded ? sections.map(renderSection) : sections.map(renderRailSection)}
        {/* Support — pinned to the bottom of the nav. A plain mailto: that
            opens the user's own mail client with the HBS help mailbox. */}
        <li className="side-nav-support-heading" aria-hidden="true">
          Support
        </li>
        <SideNavLink
          data-testid="side-nav-link-email-support"
          href={`mailto:${SUPPORT_EMAIL}`}
          renderIcon={Email}
        >
          Report an issue
        </SideNavLink>
      </SideNavItems>
    </SideNav>
  );
};

export default LayoutSideNav;
