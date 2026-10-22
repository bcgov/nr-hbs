import { Button, InlineNotification, Link as CarbonLink, Loading } from '@carbon/react';
import { ArrowLeft, Password, Search, UserProfile } from '@carbon/icons-react';
import { useCallback, useEffect, useState, type FC, type FormEvent } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';

import DefinitionGrid from '@/components/DefinitionGrid';
import SectionTile from '@/components/SectionTile';
import FieldGrid from '@/components/screens/FieldGrid';
import { useNotification } from '@/context/notification/useNotification';
import { useScopedUser } from '@/context/org/useOrg';
import { can } from '@/routes/access';
import { getJson, HBS_API, toQuery } from '@/services/common';
import { runCommand } from '@/services/screens';
import type { FieldDef } from '@/screens/types';

import ForbiddenPage from './ForbiddenPage';
import PageLayout from './PageLayout';
import '@/components/screens/screens.scss';

/** Backend ScalerAuthKeyService.RequestForm (legacy P362Form after loadForm). */
interface RequestForm {
  selfService: boolean;
  licenceNumber: string;
  clientNumber: string;
  domain: string | null;
  userId: string | null;
  hasKey: boolean;
  fromDate: string;
  maxFromDate: string;
  toDate: string;
  agingDays: number;
}

interface LoadResponse {
  form: RequestForm;
  keyIssuingAvailable: boolean;
}

/** P363 Authentication Key Confirmation — the command result. */
interface Confirmation {
  licenceNumber: string;
  authenticationKey: string;
  activeFrom: string;
  activeTo: string;
}

const addDays = (iso: string, days: number): string => {
  const d = new Date(`${iso}T00:00:00Z`);
  if (Number.isNaN(d.getTime())) return '';
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString().slice(0, 10);
};

/**
 * P362 "Update Scaler Profile" (Submit) → P363 "Authentication Key
 * Confirmation". Bespoke because the new key (requested key + check digit) is
 * shown exactly once, on this page, and never put in a URL or stored.
 *
 * HBS_SCALE_ADMIN maintains any scaler (licence, the key's HBS user, the
 * From date); every other scaler requests a new key for their own active
 * scaler profile only (legacy self service — From is tomorrow). The backend
 * (`profiles.scalers.requestKey`) enforces both.
 */
const ScalerKeyRequestPage: FC = () => {
  const user = useScopedUser();
  const navigate = useNavigate();
  const { display } = useNotification();
  const [params] = useSearchParams();
  const managed = can(user, 'SCALE_SITE_ADMIN');

  const [licence, setLicence] = useState(params.get('licenceNumber')?.toUpperCase() ?? '');
  const [loaded, setLoaded] = useState<LoadResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [values, setValues] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [confirmation, setConfirmation] = useState<Confirmation | null>(null);

  const load = useCallback(async (licenceNumber: string) => {
    setLoading(true);
    setLoadError(null);
    setConfirmation(null);
    try {
      const res = await getJson<LoadResponse>(
        `${HBS_API}/scaler-keys/request${toQuery(licenceNumber ? { licenceNumber } : {})}`,
        'Scaler profile load',
      );
      setLoaded(res);
      setValues({
        licenceNumber: res.form.licenceNumber ?? '',
        domain: res.form.domain ?? '',
        userId: res.form.userId ?? '',
        requestedKey: '',
        fromDate: res.form.fromDate ?? '',
      });
    } catch (e) {
      setLoaded(null);
      setLoadError((e as Error).message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    const initial = params.get('licenceNumber') ?? '';
    if (!managed || initial) void load(initial);
  }, [load, managed, params]);

  if (!can(user, 'SCALER_PROFILE_EDIT')) return <ForbiddenPage />;

  const form = loaded?.form;
  const fields: FieldDef[] = [
    ...(managed
      ? ([
          { name: 'domain', label: 'Domain', type: 'select', options: [
              { value: 'IDIR', label: 'IDIR' },
              { value: 'BCEID', label: 'BCEID' },
            ] },
          { name: 'userId', label: 'User ID', maxLength: 21, upper: true },
        ] as FieldDef[])
      : []),
    {
      name: 'requestedKey',
      label: 'Requested Key',
      required: true,
      maxLength: 9,
      upper: true,
      helperText: '7 to 9 upper-case letters or digits; HBS adds a check digit',
    },
    ...(managed
      ? ([
          {
            name: 'fromDate',
            label: 'Requested Active From Scale Date',
            type: 'date',
            required: true,
            helperText: form ? `From tomorrow to ${form.maxFromDate}` : undefined,
          },
        ] as FieldDef[])
      : []),
  ];

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!form) return;
    setSaving(true);
    setSaveError(null);
    try {
      const body = managed
        ? { ...values, licenceNumber: form.licenceNumber }
        : { requestedKey: values.requestedKey };
      const result = (await runCommand('profiles.scalers.requestKey', body)) as unknown as Confirmation;
      setConfirmation(result);
      setValues((v) => ({ ...v, requestedKey: '' }));
      display({ kind: 'success', title: 'Authentication key saved', timeout: 5000 });
    } catch (err) {
      setSaveError((err as Error).message);
    } finally {
      setSaving(false);
    }
  };

  const fromDate = managed ? values.fromDate : form?.fromDate;
  const toDate = form && fromDate ? addDays(fromDate, form.agingDays) : '';

  return (
    <PageLayout
      title={confirmation ? 'Authentication Key Confirmation' : 'Update Scaler Profile'}
      subtitle={
        managed
          ? 'Issue or replace a scaler’s authentication key (P362/P363).'
          : 'Request a new authentication key for your scaler licence (P362/P363).'
      }
      backLink={
        <CarbonLink
          href="#"
          renderIcon={ArrowLeft}
          onClick={(e: React.MouseEvent) => {
            e.preventDefault();
            if (form?.licenceNumber) navigate(`/profiles/scaler-profile?licenceNumber=${form.licenceNumber}`);
            else navigate('/profiles/scaler-profile-search');
          }}
        >
          Back to Scaler Profile
        </CarbonLink>
      }
    >
      {managed && (
        <SectionTile title="Scaler" icon={Search}>
          <form
            className="hbs-form"
            onSubmit={(e) => {
              e.preventDefault();
              void load(licence.trim().toUpperCase());
            }}
          >
            <FieldGrid
              fields={[{ name: 'licence', label: 'Scaler Licence ID', maxLength: 4, upper: true, required: true }]}
              values={{ licence }}
              onChange={(_, v) => setLicence(v.toUpperCase())}
              idPrefix="scaler-key-licence"
              mode="form"
            />
            <div className="detail-edit__actions">
              <Button kind="tertiary" size="md" type="submit" disabled={!licence.trim() || loading}>
                Open
              </Button>
            </div>
          </form>
        </SectionTile>
      )}

      {loading && <Loading small withOverlay={false} description="Loading scaler profile" />}
      {loadError && (
        <InlineNotification kind="error" lowContrast title="Scaler profile" subtitle={loadError} className="hbs-screen__notes" />
      )}

      {loaded && !loaded.keyIssuingAvailable && (
        <InlineNotification
          kind="warning"
          lowContrast
          hideCloseButton
          title="Key issuing isn't available yet"
          subtitle="The HBS key-encryption secret is not configured in this environment. Contact HBS support."
          className="hbs-screen__notes"
        />
      )}

      {confirmation && (
        <SectionTile
          title="Authentication Key Confirmation"
          icon={Password}
          description="Record this key now and give it to the scaler: it is not shown again."
        >
          <DefinitionGrid
            items={[
              { label: 'Scaler Licence No', value: confirmation.licenceNumber },
              { label: 'Authentication Key', value: <code>{confirmation.authenticationKey}</code> },
              { label: 'Active From Scale Date', value: confirmation.activeFrom },
              { label: 'Active To Scale Date', value: confirmation.activeTo },
            ]}
          />
        </SectionTile>
      )}

      {form && !confirmation && (
        <SectionTile title="Scaler Profile" icon={UserProfile}>
          <DefinitionGrid
            items={[
              { label: 'Scaler Licence', value: form.licenceNumber },
              ...(managed ? [] : [{ label: 'User ID', value: `${form.domain ?? ''}\\${form.userId ?? ''}` }]),
              { label: 'Current key', value: form.hasKey ? 'Active' : 'None active' },
              ...(managed ? [] : [{ label: 'Requested Active From Scale Date', value: form.fromDate }]),
              { label: 'Requested Active To Scale Date', value: toDate || '—' },
            ]}
          />
          <form className="hbs-form" onSubmit={onSubmit} noValidate>
            <FieldGrid
              fields={fields}
              values={values}
              onChange={(n, v) => setValues((p) => ({ ...p, [n]: v }))}
              idPrefix="scaler-key"
              readOnly={() => saving}
              mode="form"
            />
            {saveError && (
              <InlineNotification kind="error" lowContrast title="Key not saved" subtitle={saveError} />
            )}
            <div className="detail-edit__actions">
              <Button
                kind="primary"
                size="md"
                type="submit"
                disabled={saving || !loaded?.keyIssuingAvailable || !values.requestedKey}
              >
                {saving ? 'Submitting…' : 'Submit'}
              </Button>
            </div>
          </form>
        </SectionTile>
      )}
    </PageLayout>
  );
};

export default ScalerKeyRequestPage;
