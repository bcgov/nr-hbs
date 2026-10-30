import { ComboBox } from '@carbon/react';
import { useEffect, useMemo, useRef, useState, type FC, type ReactNode } from 'react';

import { getClientLocations, searchClientsAuto, type ClientSearchResult } from '@/services/clientSearch';

/** Characters typed before the first request (an all-digit client number goes sooner). */
const MIN_CHARS = 3;

/** Idle time before the request goes out, so a fast typist makes one call, not eight. */
const DEBOUNCE_MS = 350;

type ClientOption = {
  id: string;
  clientNumber: string;
  label: string;
};

const optionFrom = (c: ClientSearchResult): ClientOption => {
  const number = c.clientNumber ?? '';
  const name = c.clientName ?? '';
  const acronym = c.clientAcronym ? ` (${c.clientAcronym})` : '';
  return { id: number, clientNumber: number, label: `${number} — ${name}${acronym}` };
};

const isDigits = (s: string) => /^\d+$/.test(s);

interface ClientComboBoxProps {
  id: string;
  /** The client number criterion / form value. */
  value: string;
  onChange: (clientNumber: string) => void;
  /**
   * The field's location partner, if any: a picked client with exactly one
   * location fills it, as the legacy lookup filled both fields.
   */
  onLocationPick?: (locationCode: string) => void;
  titleText: ReactNode;
  helperText?: string;
  placeholder?: string;
  invalid?: boolean;
  invalidText?: string;
  disabled?: boolean;
}

/**
 * Client number field as a type-ahead (nr-fta's ClientComboBox, adapted to
 * HBS's client-number-only criteria). Suggestions come from the Forest Client
 * API by number, acronym or name. Picking one sets the client number; typing
 * digits without picking keeps them as the number, so a known number can still
 * be entered directly.
 */
const ClientComboBox: FC<ClientComboBoxProps> = ({
  id,
  value,
  onChange,
  onLocationPick,
  titleText,
  helperText = 'Type a client number, acronym or name',
  placeholder = 'Client number, acronym or name',
  invalid,
  invalidText,
  disabled,
}) => {
  const [options, setOptions] = useState<ClientOption[]>([]);
  const [typed, setTyped] = useState('');
  // Guards against an earlier, slower response overwriting a later one.
  const latest = useRef(0);
  // Carbon reports the label it writes into the input after a selection through
  // onInputChange too (for a click, before onChange), so recognising the text
  // as an offered label is what tells an echo from typing.
  const offeredLabels = useMemo(() => new Set(options.map((o) => o.label)), [options]);
  // Carbon owns the input text (no value prop), so a value changed from outside
  // (a screen's "Clear", a preset) needs a remount to show. Values this field
  // emitted itself are remembered so the user's own typing never remounts it.
  const emitted = useRef(value);
  const [resetKey, setResetKey] = useState(0);
  const emit = (clientNumber: string) => {
    emitted.current = clientNumber;
    onChange(clientNumber);
  };

  useEffect(() => {
    if (value !== emitted.current) {
      emitted.current = value;
      setTyped('');
      setResetKey((k) => k + 1);
    }
  }, [value]);

  useEffect(() => {
    const text = typed.trim();
    if (text.length < MIN_CHARS && !(text && isDigits(text))) {
      setOptions([]);
      return;
    }
    const ticket = ++latest.current;
    const timer = setTimeout(() => {
      searchClientsAuto(text)
        .then((rows) => {
          if (latest.current === ticket) setOptions(rows.filter((r) => r.clientNumber).map(optionFrom));
        })
        .catch(() => {
          // Not worth a toast mid-word; a typed number still works.
          if (latest.current === ticket) setOptions([]);
        });
    }, DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [typed]);

  // Derived from the value, so a screen's "Clear" or a preset number shows
  // without this component tracking it.
  const selectedItem = useMemo(() => {
    if (!value) return null;
    return options.find((o) => o.clientNumber === value) ?? { id: value, clientNumber: value, label: value };
  }, [options, value]);

  const fillLocation = (clientNumber: string) => {
    if (!onLocationPick) return;
    getClientLocations(clientNumber, 0, 2)
      .then((page) => {
        const only = page.content.length === 1 ? page.content[0].clientLocnCode : null;
        if (only) onLocationPick(only);
      })
      .catch(() => {
        // The location stays for the user to enter.
      });
  };

  return (
    <ComboBox
      key={resetKey}
      id={id}
      titleText={titleText}
      placeholder={placeholder}
      helperText={helperText}
      invalid={invalid}
      invalidText={invalidText}
      disabled={disabled}
      items={options}
      itemToString={(item) => item?.label ?? ''}
      selectedItem={selectedItem}
      // The server has already matched on number, acronym and name.
      shouldFilterItem={() => true}
      // Keeps a typed number that matches no suggestion.
      allowCustomValue
      onInputChange={(input) => {
        const text = input ?? '';
        if (offeredLabels.has(text) || text === selectedItem?.label) return;
        setTyped(text);
        const t = text.trim();
        // Digits are the number itself; other text only searches.
        emit(isDigits(t) ? t : '');
      }}
      onChange={({ selectedItem: picked }) => {
        const number = picked?.clientNumber ?? '';
        emit(number);
        if (number) fillLocation(number);
      }}
    />
  );
};

export default ClientComboBox;
