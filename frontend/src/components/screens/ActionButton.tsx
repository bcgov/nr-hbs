import { Button } from '@carbon/react';
import { useEffect, useState, type FC } from 'react';
import { useNavigate } from 'react-router-dom';

import ConfirmationModal from '@/components/ConfirmationModal';
import { useNotification } from '@/context/notification/useNotification';
import { useScopedUser } from '@/context/org/useOrg';
import { can } from '@/routes/access';
import { availableCommands, runCommand, type Row } from '@/services/screens';
import type { ActionDef } from '@/screens/types';

import FieldGrid from './FieldGrid';
import { linkHref } from './links';

interface ActionButtonProps {
  action: ActionDef;
  record: Row;
  onDone?: () => void;
  size?: 'sm' | 'md' | 'lg';
}

/**
 * A registry command (legacy Struts action that called an HBS_* proc) as a
 * button + confirmation modal. Hidden when the user lacks the capability —
 * the backend re-checks, this is only the affordance.
 */
export const ActionButton: FC<ActionButtonProps> = ({ action, record, onDone, size = 'md' }) => {
  const user = useScopedUser();
  const navigate = useNavigate();
  const { display } = useNotification();
  const [open, setOpen] = useState(false);
  const [values, setValues] = useState<Record<string, string>>({});
  const [registered, setRegistered] = useState(false);

  useEffect(() => {
    let cancelled = false;
    availableCommands().then((ids) => !cancelled && setRegistered(ids.has(action.command)));
    return () => {
      cancelled = true;
    };
  }, [action.command]);

  // Hidden when the user lacks the capability, or when the command is a
  // reserved id whose workflow service isn't ported yet.
  if (!can(user, action.capability) || !registered) return null;

  const onConfirm = async () => {
    const body: Record<string, unknown> = { ...values };
    for (const [arg, key] of Object.entries(action.params ?? {})) {
      body[arg] = key.startsWith('=') ? key.slice(1) : record[key];
    }
    const result = await runCommand(action.command, body);
    setOpen(false);
    display({ kind: 'success', title: `${action.label} complete`, timeout: 4000 });
    if (action.then) {
      const href = linkHref(action.then, { ...record, ...result });
      if (href) navigate(href);
    } else {
      onDone?.();
    }
  };

  return (
    <>
      <Button size={size} kind={action.danger ? 'danger--tertiary' : 'tertiary'} onClick={() => setOpen(true)}>
        {action.label}
      </Button>
      <ConfirmationModal
        open={open}
        onClose={() => setOpen(false)}
        heading={action.label}
        confirmLabel={action.label}
        danger={action.danger}
        onConfirm={onConfirm}
      >
        {action.confirm && <p className="detail-dialog__subtitle">{action.confirm}</p>}
        {action.fields && action.fields.length > 0 && (
          <FieldGrid
            fields={action.fields}
            values={values}
            onChange={(n, v) => setValues((p) => ({ ...p, [n]: v }))}
            idPrefix={`action-${action.id}`}
            mode="form"
          />
        )}
      </ConfirmationModal>
    </>
  );
};

export default ActionButton;
