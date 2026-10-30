import { Email } from '@carbon/icons-react';
import { SideNav, SideNavItems, SideNavLink, SideNavMenu, SideNavMenuItem } from '@carbon/react';
import { useEffect, useState, type FC } from 'react';
import { Link, useLocation } from 'react-router-dom';

import { useLayout } from '@/context/layout/useLayout';
import { useScopedUser } from '@/context/org/useOrg';
import {
  getMenuSections,
  getRootLinks,
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
 *   <li><b>Expanded</b> — root links (Home) first, then the legacy HBS tab
 *       bar's areas as headings, each a collapsible {@code SideNavMenu}
 *       holding its screens. Root links and headings carry an icon; the
 *       screens inside a section are plain labels. The section containing the
 *       current route opens on load; the rest stay closed.
 *   <li><b>Collapsed</b> — a 48px icon rail: one icon per root link, which
 *       navigates, then one per section, which does not — clicking it expands
 *       the nav and opens that section. Hovering names either.
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
  const rootLinks = getRootLinks(user);
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

  // A root link looks and behaves the same in both states: an icon that
  // navigates, with its label beside it (expanded) or as a hover tooltip (rail).
  const renderRootLink = (link: MenuLeaf) => (
    <SideNavLink
      data-testid={`side-nav-link-${link.id}`}
      key={link.id}
      as={Link}
      to={link.path}
      isActive={link.path === location.pathname}
      renderIcon={link.icon}
      className="side-nav-root-link"
    >
      {link.label}
    </SideNavLink>
  );

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

  // Destinations inside a section are labels only; the section heading
  // carries the icon. The label is wrapped so LayoutSideNav.css can ellipsize it.
  const renderItem = (route: MenuLeaf) => (
    <SideNavMenuItem
      data-testid={`side-nav-link-${route.id}`}
      key={route.id}
      as={Link}
      to={route.path}
      isActive={route.path === location.pathname}
    >
      <span className="side-nav-item__label" title={route.label}>
        {route.label}
      </span>
    </SideNavMenuItem>
  );

  const renderSection = (section: MenuSection) => (
    <SideNavMenu
      key={section.id}
      data-testid={`side-nav-menu-${section.id}`}
      title={section.label}
      renderIcon={section.icon}
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
        {rootLinks.map(renderRootLink)}
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
