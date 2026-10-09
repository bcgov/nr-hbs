import type { FC } from 'react';

import type { FieldDef } from '@/screens/types';

import FieldInput from './FieldInput';

interface FieldGridProps {
  fields: FieldDef[];
  values: Record<string, string>;
  onChange: (name: string, value: string) => void;
  idPrefix: string;
  errors?: Record<string, string>;
  readOnly?: (field: FieldDef) => boolean;
  /**
   * Search criteria offer "Any" as a select's empty choice (no filter); a form
   * asks the user to choose.
   */
  mode?: 'criteria' | 'form';
}

/** Grid cell class for a field's span on the 4-column grid (styles/_search.scss). */
const spanClass = (span: FieldDef['span']): string | undefined => {
  switch (span) {
    case 2:
      return 'fsp-search__wide-cell';
    case 3:
      return 'hbs-field-grid__span-3';
    case 4:
      return 'fsp-search__full-cell';
    default:
      return undefined;
  }
};

/**
 * The location field paired with a client field: the field right after it,
 * when that is a 2-character "Loc…" code (every legacy client fieldset is
 * "Client No." then "Loc").
 */
const locationPartner = (fields: FieldDef[], i: number): FieldDef | undefined => {
  const next = fields[i + 1];
  return fields[i].type === 'client' && next && next.maxLength === 2 && /^loc/i.test(next.label)
    ? next
    : undefined;
};

/**
 * Lays fields out on nr-fta's criteria grid (`fsp-search__field-grid`: one
 * column on a phone, two at md, four at lg). Where the field `group` changes —
 * the legacy screens' "General Criteria" / "Client Association" fieldsets — a
 * full-width heading starts a fresh row, as FTA's "Date ranges" heading does.
 */
export const FieldGrid: FC<FieldGridProps> = ({
  fields,
  values,
  onChange,
  idPrefix,
  errors,
  readOnly,
  mode = 'criteria',
}) => {
  let group: string | undefined;
  return (
    <div className="fsp-search__field-grid">
      {fields.flatMap((f, i) => {
        const partner = locationPartner(fields, i);
        const cells = [];
        if (f.group !== group) {
          group = f.group;
          if (f.group) {
            cells.push(
              <p key={`group-${f.name}`} className="fsp-search__group-heading">
                {f.group}
              </p>,
            );
          }
        }
        cells.push(
          <div key={f.name} className={['hbs-field-grid__cell', spanClass(f.span)].filter(Boolean).join(' ')}>
            <FieldInput
              field={f}
              value={values[f.name] ?? ''}
              onChange={(v) => onChange(f.name, v)}
              idPrefix={idPrefix}
              readOnly={readOnly?.(f)}
              invalidText={errors?.[f.name]}
              emptyOption={mode === 'form' || f.required ? 'Choose…' : 'Any'}
              onLocationPick={
                partner && !readOnly?.(partner) ? (code) => onChange(partner.name, code) : undefined
              }
            />
          </div>,
        );
        return cells;
      })}
    </div>
  );
};

export default FieldGrid;
