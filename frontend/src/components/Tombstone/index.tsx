import { Column, Grid } from '@carbon/react';

import type { FC, ReactNode } from 'react';
// The status colours behind a `bc-status-accent--*` class, which a page may use
// without rendering a status pill (the pill's import is what loads them).
import '../StatusTag/StatusTag.css';
import './Tombstone.css';

export interface TombstoneItem {
  label: string;
  value: ReactNode;
}

interface TombstoneProps {
  /** Key/value identifiers pinned to the top of a detail screen. */
  items: TombstoneItem[];
  /** Optional right-aligned action (e.g. an Edit button, admin-gated). */
  action?: ReactNode;
  /** Accessible label for the summary region. */
  ariaLabel?: string;
  /** Extra classes, e.g. a `bc-status-accent--*` to colour the left bar by status. */
  className?: string;
}

/**
 * Persistent "tombstone" summary bar shown at the top of FTA record-detail
 * screens (mirrors the legacy ftaTombstone.jsp include). Renders key
 * identifiers as a responsive grid of label/value pairs.
 */
const Tombstone: FC<TombstoneProps> = ({
  items,
  action,
  ariaLabel = 'Record summary',
  className,
}) => (
  <section className={`fta-tombstone${className ? ` ${className}` : ''}`} aria-label={ariaLabel}>
    <Grid narrow>
      {items.map((item, i) => (
        <Column key={`${item.label}-${i}`} sm={2} md={2} lg={3}>
          <dt className="fta-tombstone__label">{item.label}</dt>
          <dd className="fta-tombstone__value">{item.value}</dd>
        </Column>
      ))}
    </Grid>
    {action ? <div className="fta-tombstone__action">{action}</div> : null}
  </section>
);

export default Tombstone;
