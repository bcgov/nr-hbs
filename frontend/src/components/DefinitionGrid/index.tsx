import type { FC, ReactNode } from 'react';
import './DefinitionGrid.css';

export interface DefinitionItem {
  label: string;
  value: ReactNode;
}

/**
 * Responsive label/value grid for the fact panels inside detail-screen tabs
 * (e.g. a tenure's AAC panel, a cutting permit's details). Consistent with
 * the Tombstone treatment but for in-tab content rather than the pinned header.
 */
const DefinitionGrid: FC<{ items: DefinitionItem[] }> = ({ items }) => (
  <dl className="fta-facts">
    {items.map((item, i) => (
      <div key={`${item.label}-${i}`}>
        <dt className="fta-facts__label">{item.label}</dt>
        <dd className="fta-facts__value">{item.value}</dd>
      </div>
    ))}
  </dl>
);

export default DefinitionGrid;
