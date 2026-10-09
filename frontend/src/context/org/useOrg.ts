import { useContext, useMemo } from 'react';

import { rolesForClient } from '@/context/auth/authUtils';
import type { FamLoginUser } from '@/context/auth/types';
import { useAuth } from '@/context/auth/useAuth';

import { OrgContext, type OrgContextShape } from './OrgContext';

/** Hook accessor for {@link OrgContext}. Throws if used outside the provider. */
export const useOrg = (): OrgContextShape => {
  const ctx = useContext(OrgContext);
  if (!ctx) {
    throw new Error('useOrg() must be used inside <OrgProvider>');
  }
  return ctx;
};

/**
 * Read-only accessor that doesn't require the React render context —
 * use from non-React modules (e.g. apiFetch). Reads from sessionStorage
 * directly so it stays in sync with what OrgProvider wrote there.
 */
export const getActiveOrgClientNumber = (): string | null => {
  try {
    return sessionStorage.getItem('hbs:activeOrgClientNumber');
  } catch {
    return null;
  }
};

/**
 * The signed-in user with client-tied roles narrowed to the active client —
 * the user object every access check (routes/access.ts `can()`) should see.
 * Mirrors backend RequestUtil.getCurrentRoles().
 */
export const useScopedUser = (): FamLoginUser | undefined => {
  const { user } = useAuth();
  const { activeOrgClientNumber } = useOrg();
  return useMemo(() => {
    if (!user) return undefined;
    return { ...user, roles: rolesForClient(user.privileges, activeOrgClientNumber) };
  }, [user, activeOrgClientNumber]);
};
