import {
  Button,
  InlineNotification,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeader,
  TableRow,
} from '@carbon/react';
import { Calculator, Search } from '@carbon/icons-react';
import { useCallback, useState, type FC, type FormEvent } from 'react';

import DefinitionGrid from '@/components/DefinitionGrid';
import SectionTile from '@/components/SectionTile';
import FieldGrid from '@/components/screens/FieldGrid';
import { formatValue } from '@/components/screens/format';
import { useScopedUser } from '@/context/org/useOrg';
import { can } from '@/routes/access';
import { getJson, HBS_API } from '@/services/common';
import type { FieldDef } from '@/screens/types';

import ForbiddenPage from './ForbiddenPage';
import PageLayout from './PageLayout';
import '@/components/screens/screens.scss';

/** P230 criteria (legacy labels). Product " " is Logs, so the query string is built without trimming. */
const FIELDS: FieldDef[] = [
  { name: 'timberMark', label: 'Timber Mark', required: true, upper: true, maxLength: 6 },
  {
    name: 'fieldScaleDeckId',
    label: 'Field Scale Deck ID',
    upper: true,
    maxLength: 1,
    helperText: 'Optional: an upper case letter A–Z or a number 0–9',
  },
  { name: 'species', label: 'Species', type: 'select', codeList: 'codes.rating.species', required: true },
  { name: 'product', label: 'Product', type: 'select', codeList: 'codes.rating.products' },
  { name: 'grade', label: 'Grade', type: 'select', codeList: 'codes.rating.grades', required: true },
  { name: 'scaleDate', label: 'Scale Date', type: 'date', required: true },
  {
    name: 'returnType',
    label: 'Return Type',
    type: 'select',
    required: true,
    defaultValue: 'Normal',
    options: [
      { value: 'Normal', label: 'Normal' },
      { value: 'Beachcomb', label: 'Beachcomb' },
      { value: 'Waste', label: 'Waste' },
    ],
  },
  {
    name: 'beachcombTimberMark',
    label: 'Beachcomb Timber Mark',
    upper: true,
    maxLength: 6,
    helperText: 'Beachcomb only (BCOMB1–3 or a timber mark)',
  },
  { name: 'cutBlock', label: 'Cut Block', upper: true, maxLength: 10, helperText: 'Waste only' },
];

interface StumpageRate {
  totalRate?: number;
  rateSource?: string;
  reserveStumpage?: number;
  bonusBid?: number;
  silvLevy?: number;
  devLevy?: number;
  overrideRuleType?: string;
  overrideRuleId?: number;
  overrideRateDate?: string;
}

interface LookupResult {
  appraisal: Record<string, unknown>;
  rate: StumpageRate | null;
}

const money = (v: unknown) => formatValue(v, 'money');

/**
 * P230 / P231 Search for Stumpage Rate. Bespoke because the legacy lookup is
 * the HBS_RATES OUT-parameter procedures (backend StumpageRateController), not
 * a registry query. Shows the rate the billing would apply to one segregation,
 * its components and source, any override rule, and the mark's appraisal.
 */
const StumpageRatePage: FC = () => {
  const user = useScopedUser();
  const [values, setValues] = useState<Record<string, string>>({ returnType: 'Normal' });
  const [criteria, setCriteria] = useState<Record<string, string> | null>(null);
  const [result, setResult] = useState<LookupResult | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const onChange = useCallback((n: string, v: string) => setValues((p) => ({ ...p, [n]: v })), []);

  if (!can(user, 'RATING_VIEW')) return <ForbiddenPage />;

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    setResult(null);
    const qs = new URLSearchParams();
    Object.entries(values).forEach(([k, v]) => {
      if (v !== undefined && v !== '') qs.set(k, v);
    });
    try {
      const r = await getJson<LookupResult>(`${HBS_API}/rating/stumpage-rate?${qs.toString()}`, 'Stumpage rate search');
      setCriteria({ ...values });
      setResult(r);
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusy(false);
    }
  };

  const appraisal = result?.appraisal ?? {};
  const method = String(appraisal.appraisalMethod ?? '');
  const rate = result?.rate;

  return (
    <PageLayout title="Search for Stumpage Rate" subtitle="The stumpage rate HBS would apply to one segregation. P230">
      <SectionTile title="Search criteria" icon={Search}>
        <form className="hbs-form" onSubmit={onSubmit} noValidate>
          <FieldGrid fields={FIELDS} values={values} onChange={onChange} idPrefix="stumpage-rate" mode="form" />
          <div className="detail-edit__actions">
            <Button kind="primary" size="md" type="submit" disabled={busy}>
              {busy ? 'Searching…' : 'Search'}
            </Button>
          </div>
        </form>
      </SectionTile>

      {error && (
        <InlineNotification kind="error" lowContrast title="Search failed" subtitle={error} className="hbs-screen__notes" />
      )}

      {result && criteria && (
        <SectionTile title="Stumpage Rate Query Result" icon={Calculator}>
          <DefinitionGrid
            items={[
              { label: 'Timber Mark', value: criteria.timberMark?.toUpperCase() || '—' },
              { label: 'Field Scale Deck ID', value: criteria.fieldScaleDeckId || '—' },
              { label: 'Scale Date', value: formatValue(criteria.scaleDate, 'date') },
              { label: 'Return Type', value: criteria.returnType },
              { label: 'Beachcomb Timber Mark', value: criteria.beachcombTimberMark || '—' },
              { label: 'Cut Block', value: criteria.cutBlock || '—' },
              {
                label: 'Appraisal Method',
                value: method === 'N/A' ? 'N/A' : String(appraisal.appraisalMethodDescription ?? method),
              },
              { label: 'Appraisal Effective Date', value: formatValue(appraisal.appEffectiveDate, 'date') },
              { label: 'Appraisal Expiry Date', value: formatValue(appraisal.appExpiryDate, 'date') },
              { label: 'Admin Org', value: formatValue(appraisal.adminOrg) },
              { label: 'Region', value: formatValue(appraisal.region) },
            ]}
          />
          <TableContainer>
            <Table size="md" useZebraStyles>
              <TableHead>
                <TableRow>
                  {[
                    'Stumpage Rate',
                    'Rate Source',
                    'Reserve Rate',
                    'Bonus Bid',
                    'Silviculture Levy',
                    'Development Levy',
                    'Override Rule Type',
                    'Override Rule Number',
                    'Override Date',
                  ].map((h) => (
                    <TableHeader key={h}>{h}</TableHeader>
                  ))}
                </TableRow>
              </TableHead>
              <TableBody>
                {rate ? (
                  <TableRow>
                    <TableCell>{money(rate.totalRate)}</TableCell>
                    <TableCell>{formatValue(rate.rateSource)}</TableCell>
                    <TableCell>{money(rate.reserveStumpage)}</TableCell>
                    <TableCell>{money(rate.bonusBid)}</TableCell>
                    <TableCell>{money(rate.silvLevy)}</TableCell>
                    <TableCell>{money(rate.devLevy)}</TableCell>
                    <TableCell>{rate.overrideRuleId == null ? 'None' : formatValue(rate.overrideRuleType)}</TableCell>
                    <TableCell>{formatValue(rate.overrideRuleId)}</TableCell>
                    <TableCell>{formatValue(rate.overrideRateDate, 'date')}</TableCell>
                  </TableRow>
                ) : (
                  <TableRow>
                    <TableCell />
                    <TableCell>Not Found</TableCell>
                    <TableCell colSpan={7} />
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </TableContainer>
        </SectionTile>
      )}
    </PageLayout>
  );
};

export default StumpageRatePage;
