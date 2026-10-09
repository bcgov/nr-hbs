import { createContext } from 'react';

export type LayoutContextType = {
  isSideNavExpanded: boolean;
  toggleSideNav: () => void;
  /**
   * Expand the nav regardless of its current state. Distinct from
   * {@link toggleSideNav}: the collapsed rail's section icons must always
   * open the nav, never close one that something else already opened.
   */
  openSideNav: () => void;
  closeSideNav: () => void;
  isHeaderPanelOpen: boolean;
  toggleHeaderPanel: () => void;
  closeHeaderPanel: () => void;
};

export const LayoutContext = createContext<LayoutContextType | undefined>(undefined);
