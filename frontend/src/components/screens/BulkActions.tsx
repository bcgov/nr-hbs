import { Button, Checkbox, InlineNotification } from '@carbon/react';
import { useEffect, useState, type FC } from 'react';

import ConfirmationModal from '@/components/ConfirmationModal';
import { useScopedUser } from '@/context/org/useOrg';
import { can } from '@/routes/access';
import { availableCommands, runCommand, type Row } from '@/services/screens';
import type { BulkActionDef } from '@/screens/types';

import { conditionsMet } from './links';

/** One row's outcome as the bulk commands report it (DetailScaleDocumentProcessStatus). */
interface BulkResult {
  label?: string;
  status?: string;
  message?: string;
}

const STATUS_TEXT: Record<string, string> = {
  PROCESSED: 'Processed',
  NOTPROCESSED: 'Not processed',
  ERROR: 'Error',
};

/** Bulk actions the user may run whose command is registered (as ActionButton hides reserved ids). */
export function useBulkActions(actions: BulkActionDef[] | undefined): BulkActionDef[] {
  const user = useScopedUser();
  const [registered, setRegistered] = useState<Set<string> | null>(null);
  useEffect(() => {
    if (!actions?.length) return;
    let cancelled = false;
    availableCommands().then((ids) => !cancelled && setRegistered(ids));
    return () => {
      cancelled = true;
    };
  }, [actions]);
  if (!actions?.length || !registered) return [];
  return actions.filter((a) => can(user, a.capability) && registered.has(a.command));
}

/** True when some offered bulk action applies to the row (its check box is enabled). */
export const rowSelectable = (actions: BulkActionDef[], row: Row): boolean =>
  actions.some((a) => conditionsMet(a.showWhen, row));

/** The check box of one row (or, with `all`, of the header). */
export const SelectCell: FC<{
  id: string;
  label: string;
  checked: boolean;
  disabled?: boolean;
  onChange: (checked: boolean) => void;
}> = ({ id, label, checked, disabled, onChange }) => (
  <Checkbox
    id={id}
    labelText={label}
    hideLabel
    checked={checked}
    disabled={disabled}
    onChange={(_, { checked: c }) => onChange(c)}
  />
);

interface BulkActionBarProps {
  actions: BulkActionDef[];
  /** The rows currently ticked. */
  selected: Row[];
  /** Record (detail) or criteria (search) the actions' `params` read. */
  source: Record<string, unknown>;
  /** Called after a run (reload the table, clear the selection). */
  onDone: () => void;
}

/**
 * The "… Selected" buttons over a table with a check-box column: each runs its
 * command on the ticked rows it applies to, then shows the per-row outcome
 * (Processed / Not processed / Error), as the legacy bulk pages re-listed the
 * rows with their process status.
 */
export const BulkActionBar: FC<BulkActionBarProps> = ({ actions, selected, source, onDone }) => {
  const [pending, setPending] = useState<BulkActionDef | null>(null);
  const [outcome, setOutcome] = useState<{ action: string; results: BulkResult[] } | null>(null);

  if (actions.length === 0) return null;

  const applicable = (a: BulkActionDef) => selected.filter((r) => conditionsMet(a.showWhen, r));

  const run = async (a: BulkActionDef) => {
    const body: Record<string, unknown> = {};
    for (const [arg, key] of Object.entries(a.params ?? {})) {
      body[arg] = key.startsWith('=') ? key.slice(1) : source[key];
    }
    body.rows = applicable(a).map((r) => {
      const out: Record<string, unknown> = {};
      for (const [arg, key] of Object.entries(a.rowParams)) {
        out[arg] = key.startsWith('=') ? key.slice(1) : r[key];
      }
      return out;
    });
    const result = await runCommand(a.command, body);
    setOutcome({ action: a.label, results: Array.isArray(result.results) ? (result.results as BulkResult[]) : [] });
    setPending(null);
    onDone();
  };

  const counts = (outcome?.results ?? []).reduce<Record<string, number>>((acc, r) => {
    const k = r.status ?? 'ERROR';
    acc[k] = (acc[k] ?? 0) + 1;
    return acc;
  }, {});

  return (
    <div className="hbs-bulk">
      <div className="hbs-bulk__actions">
        {actions.map((a) => {
          const n = applicable(a).length;
          return (
            <Button
              key={a.id}
              size="sm"
              kind={a.danger ? 'danger--tertiary' : 'tertiary'}
              disabled={n === 0}
              onClick={() => setPending(a)}
            >
              {a.label} ({n})
            </Button>
          );
        })}
      </div>
      {outcome && (
        <InlineNotification
          kind={counts.ERROR ? 'warning' : 'success'}
          lowContrast
          title={`${outcome.action}:`}
          subtitle={Object.entries(counts)
            .map(([k, n]) => `${n} ${(STATUS_TEXT[k] ?? k).toLowerCase()}`)
            .join(', ')}
          onCloseButtonClick={() => setOutcome(null)}
          className="hbs-screen__notes"
        >
          <ul className="hbs-bulk__results">
            {outcome.results.map((r, i) => (
              <li key={i}>
                {r.label ?? `Row ${i + 1}`}: {STATUS_TEXT[r.status ?? ''] ?? r.status}
                {r.message ? ` — ${r.message}` : ''}
              </li>
            ))}
          </ul>
        </InlineNotification>
      )}
      <ConfirmationModal
        open={!!pending}
        onClose={() => setPending(null)}
        heading={pending?.label ?? ''}
        confirmLabel={pending?.label}
        danger={pending?.danger}
        onConfirm={() => (pending ? run(pending) : undefined)}
      >
        <p className="detail-dialog__subtitle">
          {pending?.confirm ??
            `Apply "${pending?.label}" to ${pending ? applicable(pending).length : 0} selected row(s)? Each row is re-checked; rows that no longer qualify are not processed.`}
        </p>
      </ConfirmationModal>
    </div>
  );
};

export default BulkActionBar;
