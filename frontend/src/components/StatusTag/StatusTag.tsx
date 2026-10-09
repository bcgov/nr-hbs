import type { FC } from 'react';
import './StatusTag.css';

// Map a status description to a pill variant. Substring matching keeps it
// resilient to the slightly different wordings each list endpoint returns
// (e.g. "Submitted" vs "Submitted - under review").
const statusVariant = (desc: string): string => {
  const d = desc.toLowerCase();
  if (d.includes('draft')) return 'draft';
  // The Workflow tab labels a returned-to-draft (DFT) decision as
  // "Clarification Requested" — same lifecycle state as Draft, so share
  // its colour.
  if (d.includes('clarification')) return 'draft';
  if (d.includes('submit')) return 'submitted';
  if (d.includes('in effect') || d.includes('in-effect') || d.includes('effective'))
    return 'in-effect';
  if (d.includes('approv')) return 'approved';
  // Client (FOREST_CLIENT) status: "Active" is green. Guard against
  // "inactive"/"deactivated" so only a truly active client goes green.
  if (d.includes('active') && !d.includes('inactive')) return 'approved';
  if (d.includes('reject')) return 'rejected';
  if (d.includes('expir')) return 'expired';
  if (d.includes('cancel')) return 'cancelled';
  if (d.includes('retir')) return 'retired';
  if (d.includes('delet')) return 'deleted';
  if (d.includes('heard') || d.includes('opportun')) return 'opportunity';
  if (d.includes('updat')) return 'update';
  // HBS scale-return / invoice / plan states (the words FTA never uses).
  if (d.includes('error') || d.includes('fail')) return 'rejected';
  if (d.includes('hold') || d.includes('held') || d.includes('suspend')) return 'opportunity';
  if (d.includes('releas') || d.includes('pending') || d.includes('progress') || d.includes('await'))
    return 'submitted';
  if (d.includes('billed') || d.includes('invoiced') || d.includes('issued') || d.includes('complete'))
    return 'approved';
  if (d.includes('replac') || d.includes('discard') || d.includes('void')) return 'retired';
  return 'default';
};

interface StatusTagProps {
  /** The status description text shown inside the pill. */
  status: string;
  /**
   * The colour, when the caller knows it from the status code — for code sets
   * whose descriptions the matcher below cannot read (e.g. private marks).
   * Falls back to matching on `status` when absent.
   */
  variant?: string;
  /** Extra classes, e.g. to size the pill for a page title. */
  className?: string;
}

/**
 * FSP status pill, shared across every list page so the size, shape, and
 * colour palette are identical everywhere.
 */
export const StatusTag: FC<StatusTagProps> = ({ status, variant, className }) => (
  <span
    className={`bc-status-tag bc-status-tag--${variant ?? statusVariant(status)}${
      className ? ` ${className}` : ''
    }`}
  >
    {status}
  </span>
);

export default StatusTag;
