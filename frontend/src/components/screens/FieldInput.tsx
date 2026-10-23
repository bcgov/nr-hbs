import {
  Checkbox,
  DatePicker,
  DatePickerInput,
  RadioButton,
  RadioButtonGroup,
  Select,
  SelectItem,
  TextArea,
  TextInput,
} from '@carbon/react';
import { useEffect, useState, type FC } from 'react';

import ClientComboBox from '@/components/ClientComboBox';
import type { CodeOption } from '@/services/common';
import type { FieldDef } from '@/screens/types';

import { useScreenApi } from './screenApi';

/** Local yyyy-mm-dd (toISOString would shift the day for Pacific users). */
const toIsoDate = (d: Date | undefined): string => {
  if (!d) return '';
  const mm = String(d.getMonth() + 1).padStart(2, '0');
  const dd = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${mm}-${dd}`;
};

interface FieldInputProps {
  field: FieldDef;
  value: string;
  onChange: (value: string) => void;
  idPrefix: string;
  readOnly?: boolean;
  invalidText?: string;
  /** A select's empty choice: "Any" in search criteria, "Choose…" on a form. */
  emptyOption?: string;
  /**
   * A client field's location partner: picking a client location in the
   * lookup also fills it, as the legacy popup filled both fields.
   */
  onLocationPick?: (locationCode: string) => void;
}

/**
 * One criteria / form field rendered as the matching Carbon input. Shared by
 * SearchScreen criteria tiles, FormScreen and action-prompt modals so every
 * HBS screen's inputs look and validate the same way.
 */
export const FieldInput: FC<FieldInputProps> = ({
  field,
  value,
  onChange,
  idPrefix,
  readOnly,
  invalidText,
  emptyOption = 'Any',
  onLocationPick,
}) => {
  const id = `${idPrefix}-${field.name}`;
  const label = field.required ? `${field.label} *` : field.label;
  const [codes, setCodes] = useState<CodeOption[] | null>(null);
  const { codeList } = useScreenApi();

  useEffect(() => {
    if (!field.codeList) return;
    let cancelled = false;
    codeList(field.codeList)
      .then((c) => !cancelled && setCodes(c))
      .catch(() => !cancelled && setCodes([]));
    return () => {
      cancelled = true;
    };
  }, [codeList, field.codeList]);

  const common = {
    id,
    labelText: label,
    invalid: !!invalidText,
    invalidText,
    helperText: field.helperText,
    disabled: readOnly,
  };

  // A code list is a dropdown even when the definition doesn't say 'select'.
  switch (field.type ?? (field.codeList ? 'select' : 'text')) {
    case 'date':
      return (
        // Carbon's DatePicker copies its own `invalid` onto the input it wraps,
        // so the flag must be set here too or the error never shows.
        <DatePicker
          datePickerType="single"
          dateFormat="Y-m-d"
          value={value ? [value] : []}
          invalid={!!invalidText}
          onChange={(dates: Date[]) => onChange(toIsoDate(dates[0]))}
        >
          <DatePickerInput
            id={id}
            labelText={label}
            placeholder="YYYY-MM-DD"
            pattern="\d{4}-\d{2}-\d{2}"
            invalid={!!invalidText}
            invalidText={invalidText}
            disabled={readOnly}
          />
        </DatePicker>
      );
    case 'select':
    case 'yesno': {
      const options =
        field.type === 'yesno'
          ? [
              { value: 'Y', label: 'Yes' },
              { value: 'N', label: 'No' },
            ]
          : (field.options ??
            (codes ?? []).map((c) => ({
              value: c.code,
              label: c.label ?? (c.description ? `${c.code} - ${c.description}` : c.code),
            })));
      return (
        <Select {...common} value={value} onChange={(e) => onChange(e.target.value)}>
          <SelectItem value="" text={field.codeList && codes === null ? 'Loading…' : emptyOption} />
          {options.map((o) => (
            <SelectItem key={o.value} value={o.value} text={o.label} />
          ))}
        </Select>
      );
    }
    case 'radio':
      return (
        <RadioButtonGroup
          legendText={label}
          helperText={field.helperText}
          name={id}
          valueSelected={value}
          onChange={(v) => onChange(String(v ?? ''))}
          disabled={readOnly}
          orientation="vertical"
        >
          {(field.options ?? []).map((o) => (
            <RadioButton key={o.value} id={`${id}-${o.value}`} labelText={o.label} value={o.value} />
          ))}
        </RadioButtonGroup>
      );
    case 'checkbox':
      return (
        <Checkbox
          id={id}
          labelText={field.label}
          checked={value === 'Y'}
          disabled={readOnly}
          onChange={(_, { checked }) => onChange(checked ? 'Y' : 'N')}
        />
      );
    case 'textarea':
      return (
        <TextArea
          {...common}
          value={value}
          maxCount={field.maxLength}
          enableCounter={!!field.maxLength}
          onChange={(e) => onChange(e.target.value)}
        />
      );
    case 'client':
      // Type-ahead over the Forest Client API (nr-fta's client field); a
      // picked client with a single location also fills the partner Loc field.
      return (
        <ClientComboBox
          id={id}
          titleText={label}
          value={value}
          onChange={onChange}
          onLocationPick={onLocationPick}
          helperText={field.helperText}
          placeholder={field.placeholder}
          invalid={!!invalidText}
          invalidText={invalidText}
          disabled={readOnly}
        />
      );
    case 'number':
      return (
        <TextInput
          {...common}
          inputMode="decimal"
          value={value}
          maxLength={field.maxLength}
          placeholder={field.placeholder}
          onChange={(e) => onChange(e.target.value.replace(/[^\d.-]/g, ''))}
        />
      );
    default:
      return (
        <TextInput
          {...common}
          value={value}
          maxLength={field.maxLength}
          placeholder={field.placeholder}
          onChange={(e) => onChange(field.upper ? e.target.value.toUpperCase() : e.target.value)}
        />
      );
  }
};

export default FieldInput;
