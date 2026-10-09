import { Button, InlineNotification } from '@carbon/react';
import { DocumentAdd, Edit } from '@carbon/icons-react';
import { useEffect, useMemo, useState, type FC, type FormEvent } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';

import AsyncBoundary from '@/components/AsyncBoundary';
import SectionTile from '@/components/SectionTile';
import { useNotification } from '@/context/notification/useNotification';
import { useNavOrigin } from '@/lib/navOrigin';
import { availableCommands, oneQuery, runCommand } from '@/services/screens';
import type { FormScreenDef } from '@/screens/types';

import FieldGrid from './FieldGrid';
import { linkHref } from './links';
import ScreenLayout from './ScreenLayout';

/**
 * Generic add / update form — the template behind the legacy "Add …" /
 * "Update …" maintenance screens (profiles, rates, sampling plans …), laid
 * out as nr-fta's forms: one titled section tile holding the field grid, with
 * Cancel / Save bottom-right. Edit forms pre-fill from a single-row query
 * keyed by URL params; save runs the registry command (one HBS_CREATE_* /
 * HBS_STORE_* proc call). While there are unsaved changes the back link is
 * disabled — leaving would drop them without asking — and Cancel is the way
 * out.
 */
export const FormScreen: FC<{ screen: FormScreenDef }> = ({ screen }) => {
  const navigate = useNavigate();
  const origin = useNavOrigin();
  const [searchParams] = useSearchParams();
  const { display } = useNotification();

  const keyString = searchParams.toString();
  const keys = useMemo(() => {
    const k: Record<string, string> = {};
    new URLSearchParams(keyString).forEach((v, n) => {
      k[n] = v;
    });
    return k;
  }, [keyString]);
  const isEdit = !!screen.loadQuery && (screen.keys ?? []).every((k) => keys[k]);

  const initial = useMemo(() => {
    const init: Record<string, string> = {};
    screen.fields.forEach((f) => {
      if (f.defaultValue) init[f.name] = f.defaultValue;
    });
    return { ...init, ...keys };
  }, [keys, screen.fields]);

  const [loaded, setLoaded] = useState<Record<string, string>>(initial);
  const [values, setValues] = useState<Record<string, string>>(initial);
  const [loading, setLoading] = useState(isEdit);
  const [loadError, setLoadError] = useState<string | undefined>();
  const [attempt, setAttempt] = useState(0);
  const [saving, setSaving] = useState(false);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [registered, setRegistered] = useState<boolean | null>(null);

  useEffect(() => {
    let cancelled = false;
    availableCommands().then((ids) => !cancelled && setRegistered(ids.has(screen.command)));
    return () => {
      cancelled = true;
    };
  }, [screen.command]);

  useEffect(() => {
    if (!isEdit || !screen.loadQuery) return;
    let cancelled = false;
    setLoading(true);
    setLoadError(undefined);
    oneQuery(screen.loadQuery, keys)
      .then((row) => {
        if (cancelled) return;
        const v: Record<string, string> = { ...initial };
        screen.fields.forEach((f) => {
          const raw = row[f.name];
          if (raw !== null && raw !== undefined) v[f.name] = String(raw).slice(0, f.type === 'date' ? 10 : undefined);
        });
        setLoaded(v);
        setValues(v);
      })
      .catch((e) => !cancelled && setLoadError((e as Error).message))
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
  }, [initial, isEdit, keys, screen.fields, screen.loadQuery, attempt]);

  const dirty = screen.fields.some((f) => (values[f.name] ?? '') !== (loaded[f.name] ?? ''));
  const leave = () => (origin ? navigate(origin.path) : navigate(-1));

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    const next: Record<string, string> = {};
    screen.fields.forEach((f) => {
      if (f.required && !values[f.name]?.trim()) next[f.name] = `${f.label} is required`;
    });
    setErrors(next);
    if (Object.keys(next).length > 0) {
      display({
        kind: 'error',
        title: 'Some fields need attention',
        subtitle: 'Fill in the required fields marked below.',
        timeout: 6000,
      });
      return;
    }
    setSaving(true);
    try {
      const result = await runCommand(screen.command, values);
      display({ kind: 'success', title: `${screen.title} saved`, timeout: 5000 });
      setLoaded(values);
      const href = screen.then ? linkHref(screen.then, { ...values, ...result }) : null;
      if (href) navigate(href, { replace: true });
      else leave();
    } catch (err) {
      display({ kind: 'error', title: 'Save failed', subtitle: (err as Error).message, timeout: 8000 });
    } finally {
      setSaving(false);
    }
  };

  return (
    <ScreenLayout screen={screen} backDisabled={dirty && !saving}>
      {registered === false && (
        <InlineNotification
          kind="warning"
          lowContrast
          hideCloseButton
          title="Saving isn't available yet"
          subtitle="This screen's save logic is still being ported from the legacy system, or you don't have permission to save it."
        />
      )}
      <AsyncBoundary
        loading={loading}
        error={loadError}
        onRetry={() => setAttempt((n) => n + 1)}
        loadingText="Loading record…"
      >
        <SectionTile title={isEdit ? 'Details' : 'New record details'} icon={isEdit ? Edit : DocumentAdd}>
          <form className="hbs-form" onSubmit={onSubmit} noValidate>
            <FieldGrid
              fields={screen.fields}
              values={values}
              onChange={(n, v) => {
                setValues((p) => ({ ...p, [n]: v }));
                if (errors[n]) setErrors(({ [n]: _gone, ...rest }) => rest);
              }}
              idPrefix={screen.id}
              errors={errors}
              readOnly={(f) => (isEdit && !!f.readOnlyOnEdit) || saving}
              mode="form"
            />
            <div className="detail-edit__actions">
              <Button kind="tertiary" size="md" type="button" onClick={leave} disabled={saving}>
                Cancel
              </Button>
              <Button kind="primary" size="md" type="submit" disabled={saving || registered !== true}>
                {saving ? 'Saving…' : (screen.submitLabel ?? (isEdit ? 'Save changes' : 'Save'))}
              </Button>
            </div>
          </form>
        </SectionTile>
      </AsyncBoundary>
    </ScreenLayout>
  );
};

export default FormScreen;
