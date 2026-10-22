import {
  Button,
  Checkbox,
  InlineNotification,
  Select,
  SelectItem,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeader,
  TableRow,
  TextInput,
} from '@carbon/react';
import { ListChecked, Search } from '@carbon/icons-react';
import { useState, type FC } from 'react';
import { Link } from 'react-router-dom';

import ConfirmationModal from '@/components/ConfirmationModal';
import SectionTile from '@/components/SectionTile';
import FieldGrid from '@/components/screens/FieldGrid';
import { useScopedUser } from '@/context/org/useOrg';
import { can } from '@/routes/access';
import { runDetailCommand } from '@/services/detailReturns';
import { runQuery, type Row } from '@/services/screens';
import type { FieldDef } from '@/screens/types';

import ForbiddenPage from '../ForbiddenPage';
import PageLayout from '../PageLayout';
import '@/components/screens/screens.scss';
import './detailEntry.scss';

/** P560A "Request" choices ({@code ActionType.StateChange}) → the statuses P570Action selects for each. */
const ACTIONS: { value: string; label: string; status?: string; update?: boolean }[] = [
  { value: 'RELEASE', label: 'Release', status: 'HLD' },
  { value: 'REEDIT', label: 'Re-Edit', status: 'ERR' },
  { value: 'HOLD', label: 'Hold (and update)', status: 'ERR', update: true },
  { value: 'UPDATE_RETURN_DATA', label: 'Update Return Data', status: 'HLD', update: true },
  { value: 'DISCARD', label: 'Discard', status: 'HLD' },
  { value: 'DISCARD_RETURN', label: 'Discard Return (signature failure)', status: 'DSF' },
  { value: 'CLEAR_SIGNATURE_FAILED', label: 'Clear Digital Signature Failure', status: 'DSF' },
  { value: 'CANCEL', label: 'Cancel', status: 'ISS' },
  { value: 'CANCEL_REPLACE_CHANGES', label: 'Cancel and Replace with Changes', status: 'ISS', update: true },
  { value: 'CANCEL_REPLACE_NO_CHANGES', label: 'Cancel and Replace without Changes', status: 'ISS' },
  { value: 'REPLACE_CHANGES', label: 'Replace with Changes', status: 'CAN', update: true },
  { value: 'REPLACE_NO_CHANGES', label: 'Replace without Changes', status: 'CAN' },
  { value: 'APPROVE_CHANGE', label: 'Approve Change', status: 'AWP' },
  { value: 'REJECT_CHANGE', label: 'Reject Change', status: 'AWP' },
];

const UPDATE_FIELDS = [
  { value: 'NO_UPDATE', label: 'No update' },
  { value: 'SCALE_SITE', label: 'Scale Site' },
  { value: 'SCALE_DATE', label: 'Scale Date (yyyymmdd)' },
  { value: 'TIMBER_MARK', label: 'Timber Mark' },
  { value: 'POP_STRAT_YEAR', label: 'Pop/Strat/Year' },
];

const CRITERIA: FieldDef[] = [
  {
    name: 'returnType',
    label: 'Return Type',
    type: 'select',
    required: true,
    options: [
      { value: 'P', label: 'Log Tallies' },
      { value: 'W', label: 'Weigh Slips' },
      { value: 'S', label: 'Sample Tallies' },
      { value: 'A', label: 'Arrival Ledgers' },
      { value: 'D', label: 'Departure Ledgers' },
    ],
  },
  { name: 'scaleDateFrom', label: 'Scale Date From', type: 'date', required: true },
  { name: 'scaleDateTo', label: 'Scale Date To', type: 'date' },
  { name: 'scaleSite', label: 'Single Site', maxLength: 4, upper: true },
  { name: 'timberMark', label: 'Single Mark', maxLength: 6, upper: true },
  { name: 'scalerLicence', label: 'Primary Scaler Licence', maxLength: 4, upper: true },
];

interface Result {
  ddn: string;
  version: number;
  status: string;
  message?: string;
}

/**
 * P560A / P561 / P554B — Bulk Request: search detail returns, select rows,
 * choose a request (optionally updating scale site / scale date / timber mark /
 * pop-strat-year "from → to") and run it; each row is re-checked and reported
 * Processed / Not processed / Error, as P570Action did.
 */
const DetailBulkPage: FC = () => {
  const user = useScopedUser();
  const [criteria, setCriteria] = useState<Record<string, string>>({ returnType: 'P' });
  const [action, setAction] = useState('RELEASE');
  const [rows, setRows] = useState<Row[]>([]);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [update, setUpdate] = useState({ field: 'NO_UPDATE', from: '', to: '', companyUse: false });
  const [disposition, setDisposition] = useState('SAVE');
  const [results, setResults] = useState<Result[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [confirm, setConfirm] = useState(false);
  const [searching, setSearching] = useState(false);

  if (!can(user, 'DETAIL_RETURN_EDIT') && !can(user, 'DETAIL_RETURN_APPROVE')) return <ForbiddenPage />;
  const chosen = ACTIONS.find((a) => a.value === action)!;

  const search = async () => {
    setSearching(true);
    setError(null);
    setResults(null);
    try {
      const page = await runQuery(
        'detailReturns.search',
        { ...criteria, status: chosen.status, activeOnly: 'Y' },
        { page: 0, size: 100 },
      );
      setRows(page.content);
      setSelected(new Set());
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setSearching(false);
    }
  };

  const key = (r: Row) => `${r.ddn}/${r.version}`;

  const submit = async () => {
    const body = {
      action,
      rows: rows.filter((r) => selected.has(key(r))).map((r) => ({
        ddn: r.ddn,
        version: r.version,
        updateTimestamp: r.updateTimestamp,
      })),
      update: chosen.update ? update : { field: 'NO_UPDATE' },
      disposition,
    };
    const out = await runDetailCommand('detailReturns.bulk', body);
    setResults((out.results as Result[]) ?? []);
    setConfirm(false);
  };

  return (
    <PageLayout
      title="Bulk Request for Detail Scale Returns"
      subtitle="Apply one request to several detail scale returns (P560A / P561)."
    >
      <SectionTile title="Request" icon={ListChecked}>
        <div className="hbs-form">
          <div className="fsp-search__field-grid">
            <Select id="bulk-action" labelText="Request *" value={action} onChange={(e) => setAction(e.target.value)}>
              {ACTIONS.map((a) => (
                <SelectItem key={a.value} value={a.value} text={a.label} />
              ))}
            </Select>
            {chosen.update && (
              <>
                <Select
                  id="bulk-update"
                  labelText="Update"
                  value={update.field}
                  onChange={(e) => setUpdate({ ...update, field: e.target.value })}
                >
                  {UPDATE_FIELDS.map((u) => (
                    <SelectItem key={u.value} value={u.value} text={u.label} />
                  ))}
                </Select>
                {update.field !== 'NO_UPDATE' && (
                  <>
                    <TextInput
                      id="bulk-from"
                      labelText="From"
                      value={update.from}
                      onChange={(e) => setUpdate({ ...update, from: e.target.value.toUpperCase() })}
                    />
                    <TextInput
                      id="bulk-to"
                      labelText="To"
                      value={update.to}
                      onChange={(e) => setUpdate({ ...update, to: e.target.value.toUpperCase() })}
                    />
                  </>
                )}
                {update.field === 'POP_STRAT_YEAR' && (
                  <Checkbox
                    id="bulk-company-use"
                    labelText="Company stratum"
                    checked={update.companyUse}
                    onChange={(_, { checked }) => setUpdate({ ...update, companyUse: checked })}
                  />
                )}
                <Select
                  id="bulk-disposition"
                  labelText="Then"
                  value={disposition}
                  onChange={(e) => setDisposition(e.target.value)}
                >
                  <SelectItem value="SAVE" text="Save" />
                  <SelectItem value="SAVE_RELEASE" text="Save and Release" />
                </Select>
              </>
            )}
          </div>
          <FieldGrid
            fields={CRITERIA}
            values={criteria}
            onChange={(n, v) => setCriteria((p) => ({ ...p, [n]: v }))}
            idPrefix="bulk"
            mode="form"
          />
          <div className="detail-edit__actions">
            <Button
              size="md"
              renderIcon={Search}
              disabled={searching || !criteria.returnType || !criteria.scaleDateFrom}
              onClick={() => void search()}
            >
              Find returns ({chosen.status})
            </Button>
          </div>
        </div>
      </SectionTile>

      {error && (
        <InlineNotification kind="error" lowContrast title="Request failed" subtitle={error} className="hbs-screen__notes" />
      )}

      {rows.length > 0 && (
        <SectionTile
          title={`${rows.length} return${rows.length === 1 ? '' : 's'} found`}
          icon={ListChecked}
          actions={
            <Button size="sm" disabled={selected.size === 0} onClick={() => setConfirm(true)}>
              {chosen.label} ({selected.size})
            </Button>
          }
        >
          <TableContainer>
            <Table size="sm" useZebraStyles>
              <TableHead>
                <TableRow>
                  <TableHeader>
                    <Checkbox
                      id="bulk-all"
                      labelText="Select all"
                      hideLabel
                      checked={selected.size === rows.length}
                      onChange={(_, { checked }) => setSelected(checked ? new Set(rows.map(key)) : new Set())}
                    />
                  </TableHeader>
                  <TableHeader>DDN</TableHeader>
                  <TableHeader>Vers No.</TableHeader>
                  <TableHeader>Status</TableHeader>
                  <TableHeader>Scale Site</TableHeader>
                  <TableHeader>Timber Mark</TableHeader>
                  <TableHeader>Scaler Licence</TableHeader>
                  <TableHeader>Result</TableHeader>
                </TableRow>
              </TableHead>
              <TableBody>
                {rows.map((r) => {
                  const res = results?.find((x) => x.ddn === r.ddn && String(x.version) === String(r.version));
                  return (
                    <TableRow key={key(r)}>
                      <TableCell>
                        <Checkbox
                          id={`bulk-${key(r)}`}
                          labelText={`Select ${String(r.ddn)}`}
                          hideLabel
                          checked={selected.has(key(r))}
                          onChange={(_, { checked }) =>
                            setSelected((s) => {
                              const n = new Set(s);
                              if (checked) n.add(key(r));
                              else n.delete(key(r));
                              return n;
                            })
                          }
                        />
                      </TableCell>
                      <TableCell>
                        <Link to={`/scale-returns/detail-return?ddn=${String(r.ddn)}&version=${String(r.version)}`}>
                          {String(r.ddn)}
                        </Link>
                      </TableCell>
                      <TableCell>{String(r.version)}</TableCell>
                      <TableCell>{String(r.statusDesc ?? r.status ?? '')}</TableCell>
                      <TableCell>{String(r.scaleSiteDisplay ?? '')}</TableCell>
                      <TableCell>{String(r.timberMark ?? '')}</TableCell>
                      <TableCell>{String(r.primaryLicenceDisplay ?? '')}</TableCell>
                      <TableCell>
                        {res ? (res.status === 'PROCESSED' ? 'Processed' : res.status === 'ERROR' ? `Error${res.message ? `: ${res.message}` : ''}` : 'Not processed') : ''}
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          </TableContainer>
        </SectionTile>
      )}

      <ConfirmationModal
        open={confirm}
        onClose={() => setConfirm(false)}
        heading={`Confirm ${chosen.label}`}
        confirmLabel={chosen.label}
        onConfirm={submit}
      >
        <p className="detail-dialog__subtitle">
          Apply "{chosen.label}" to {selected.size} selected return{selected.size === 1 ? '' : 's'}? Each return is
          re-checked; returns that no longer qualify are not processed.
        </p>
      </ConfirmationModal>
    </PageLayout>
  );
};

export default DetailBulkPage;
