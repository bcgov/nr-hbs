import type { CarbonIconType } from '@carbon/icons-react';
import type { FC, ReactNode } from 'react';

export interface DetailField {
  label: string;
  value: ReactNode;
  /** Span the full row — for long free text that would stretch a column. */
  wide?: boolean;
  /** Start a new row, whatever the column count. */
  rowStart?: boolean;
  /** Take two columns — for a value too long for one. */
  span2?: boolean;
}

/**
 * A white section card on the grey tab canvas of a record detail page: an icon
 * heading over a label/value list — the section treatment of nr-fsp-new's FSP
 * Information tab. Styled by `styles/_detail.scss` (`fsp-info__`), so it
 * belongs inside a `.fsp-info__page-tabs` tab panel.
 */
const DetailTile: FC<{
  title: string;
  icon: CarbonIconType;
  fields: DetailField[];
  /** Right-aligned in the card header — where FSP puts its "Edit …" buttons. */
  action?: ReactNode;
}> = ({ title, icon: Icon, fields, action }) => (
  <section className="fsp-info__tile">
    <header className="fsp-info__tile-header">
      <h2 className="fsp-info__section-title">
        <Icon size={20} />
        <span>{title}</span>
      </h2>
      {action}
    </header>
    <dl className="fsp-info__field-list">
      {fields.map((f, i) => (
        <div
          // Labels can repeat within a legacy section ("Client" twice), so the index keeps keys unique.
          key={`${f.label}-${i}`}
          className={[
            'fsp-info__field',
            f.wide && 'fsp-info__field--wide',
            f.rowStart && 'fsp-info__field--row-start',
            f.span2 && 'fsp-info__field--span-2',
          ]
            .filter(Boolean)
            .join(' ')}
        >
          <dt>{f.label}</dt>
          <dd>{f.value}</dd>
        </div>
      ))}
    </dl>
  </section>
);

export default DetailTile;
