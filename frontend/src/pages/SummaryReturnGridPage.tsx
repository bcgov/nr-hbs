import {
  Button,
  Checkbox,
  InlineNotification,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeader,
  TableRow,
  TextInput,
} from '@carbon/react';
import { Add, ArrowLeft, TrashCan } from '@carbon/icons-react';
import { useEffect, useMemo, useState, type FC } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';

import AsyncBoundary from '@/components/AsyncBoundary';
import SectionTile from '@/components/SectionTile';
import { useNotification } from '@/context/notification/useNotification';
import { useScopedUser } from '@/context/org/useOrg';
import { can } from '@/routes/access';
import { listQuery, oneQuery, runCommand, type Row } from '@/services/screens';

import ForbiddenPage from './ForbiddenPage';
import PageLayout from './PageLayout';
import '@/components/screens/screens.scss';

type Grid = 'segregations' | 'notations' | 'dailyLoads';

interface Column {
  key: string;
  header: string;
  kind?: 'flag' | 'number';
  maxLength?: number;
  upper?: boolean;
  /** Piece returns only. */
  pieceOnly?: boolean;
}

/** Legacy columns of P057/P157 (segregations), P058/P109/P158 (notations) and P110 (daily loads). */
const GRIDS: Record<Grid, { title: string; legacy: string; query: string; command: string; listKey: string; idKey?: string; columns: Column[] }> = {
  segregations: {
    title: 'Segregations',
    legacy: 'P057 / P157',
    query: 'summaryReturns.segregationRows',
    command: 'summaryReturns.saveSegregations',
    listKey: 'segregations',
    idKey: 'segregationId',
    columns: [
      { key: 'species', header: 'Species', upper: true, maxLength: 2 },
      { key: 'product', header: 'Product', upper: true, maxLength: 2 },
      { key: 'grade', header: 'Grade', upper: true, maxLength: 1 },
      { key: 'otherDescription', header: 'Other Description', upper: true, maxLength: 3, pieceOnly: true },
      { key: 'pieces', header: 'Pieces', kind: 'number', maxLength: 6 },
      { key: 'volume', header: 'Volume', kind: 'number', maxLength: 10 },
      { key: 'sb4', header: 'SB4', kind: 'flag', pieceOnly: true },
      { key: 'nmv', header: 'NMV', kind: 'flag', pieceOnly: true },
      { key: 'avoidable', header: 'Avoidable', kind: 'flag', pieceOnly: true },
      { key: 'bcombMark', header: 'Beachcomb Mark', upper: true, maxLength: 6, pieceOnly: true },
    ],
  },
  notations: {
    title: 'Notations',
    legacy: 'P058 / P109 / P158',
    query: 'summaryReturns.notations',
    command: 'summaryReturns.saveNotations',
    listKey: 'notations',
    idKey: 'ntnId',
    columns: [
      { key: 'notationNumber', header: 'Notation', kind: 'number', maxLength: 2 },
      { key: 'text', header: 'Text', maxLength: 80 },
    ],
  },
  dailyLoads: {
    title: 'Daily Load Summaries',
    legacy: 'P110',
    query: 'summaryReturns.dailyLoads',
    command: 'summaryReturns.saveDailyLoads',
    listKey: 'dailyLoads',
    columns: [
      { key: 'day', header: 'Day', kind: 'number', maxLength: 2 },
      { key: 'loads', header: 'Loads', kind: 'number', maxLength: 4 },
      { key: 'weight', header: 'Weight', kind: 'number', maxLength: 9 },
    ],
  },
};

type EditRow = Record<string, string>;

/**
 * The legacy edit grids of a summary return — segregations (P057 piece, P157
 * sample), notations (P058/P109/P158) and weight daily load summaries (P110) —
 * as one bespoke page. The whole list is saved in one command (rows removed
 * here are deleted, rows without an id are added); the backend applies the
 * legacy validations, re-runs the edits and answers with the legacy messages.
 * The version's UPDATE_TIMESTAMP is sent back for the optimistic lock.
 */
const SummaryReturnGridPage: FC = () => {
  const user = useScopedUser();
  const navigate = useNavigate();
  const { display } = useNotification();
  const [params] = useSearchParams();
  const grid = (params.get('grid') ?? 'segregations') as Grid;
  const def = GRIDS[grid] ?? GRIDS.segregations;
  const dcn = params.get('documentControlNumber') ?? '';
  const version = params.get('version') ?? '';
  const [record, setRecord] = useState<Row | null>(null);
  const [rows, setRows] = useState<EditRow[]>([]);
  const [error, setError] = useState<string | undefined>();
  const [saving, setSaving] = useState(false);
  const [problems, setProblems] = useState<string | undefined>();

  const keys = useMemo(() => ({ documentControlNumber: dcn, version }), [dcn, version]);

  useEffect(() => {
    if (!dcn || !version) return;
    let cancelled = false;
    setError(undefined);
    Promise.all([oneQuery('summaryReturns.detail', keys), listQuery(def.query, keys)])
      .then(([rec, list]) => {
        if (cancelled) return;
        setRecord(rec);
        setRows(
          list.map((r) => {
            const e: EditRow = {};
            if (def.idKey && r[def.idKey] != null) e[def.idKey] = String(r[def.idKey]);
            def.columns.forEach((c) => {
              const v = r[c.key];
              e[c.key] = v == null ? '' : String(v);
            });
            return e;
          }),
        );
      })
      .catch((e) => !cancelled && setError((e as Error).message));
    return () => {
      cancelled = true;
    };
  }, [def, keys, dcn, version]);

  if (!can(user, 'SUMMARY_RETURN_EDIT')) return <ForbiddenPage />;

  const piece = record?.returnType === 'P';
  const columns = def.columns.filter((c) => piece || !c.pieceOnly);
  const detailHref = `/scale-returns/summary-return?documentControlNumber=${dcn}&version=${version}`;

  const setCell = (i: number, key: string, value: string) =>
    setRows((prev) => prev.map((r, j) => (j === i ? { ...r, [key]: value } : r)));

  const onSave = async () => {
    setSaving(true);
    setProblems(undefined);
    try {
      const list = rows.map((r) => {
        const out: Record<string, unknown> = {};
        if (def.idKey && r[def.idKey]) out[def.idKey] = r[def.idKey];
        columns.forEach((c) => {
          out[c.key] = c.kind === 'flag' ? (r[c.key] === 'Y' ? 'Y' : 'N') : r[c.key] || null;
        });
        return out;
      });
      const result = await runCommand(def.command, {
        ...keys,
        updateTimestamp: record?.updateTimestamp,
        returnType: record?.returnType,
        [def.listKey]: list,
      });
      display({ kind: 'success', title: `${def.title} saved`, timeout: 5000 });
      const v = result.version ?? version;
      navigate(`/scale-returns/summary-return?documentControlNumber=${dcn}&version=${v}`, { replace: true });
    } catch (e) {
      setProblems((e as Error).message);
    } finally {
      setSaving(false);
    }
  };

  return (
    <PageLayout
      title={`Update ${def.title}`}
      subtitle={`Summary scale return ${dcn}, version ${version}. Legacy ${def.legacy}.`}
      backLink={
        <Link to={detailHref} className="back-link">
          <ArrowLeft size={16} /> Back to Summary Scale Return
        </Link>
      }
    >
      <AsyncBoundary loading={!record && !error} error={error} loadingText="Loading…">
        <SectionTile title={def.title} icon={Add}>
          {problems && (
            <InlineNotification kind="error" lowContrast title="Not saved" subtitle={problems} onClose={() => setProblems(undefined)} />
          )}
          <TableContainer>
            <Table size="sm" aria-label={def.title}>
              <TableHead>
                <TableRow>
                  {columns.map((c) => (
                    <TableHeader key={c.key}>{c.header}</TableHeader>
                  ))}
                  <TableHeader>Delete</TableHeader>
                </TableRow>
              </TableHead>
              <TableBody>
                {rows.map((r, i) => (
                  <TableRow key={i}>
                    {columns.map((c) => (
                      <TableCell key={c.key}>
                        {c.kind === 'flag' ? (
                          <Checkbox
                            id={`${grid}-${i}-${c.key}`}
                            labelText=""
                            hideLabel
                            checked={r[c.key] === 'Y'}
                            onChange={(_, { checked }) => setCell(i, c.key, checked ? 'Y' : 'N')}
                          />
                        ) : (
                          <TextInput
                            id={`${grid}-${i}-${c.key}`}
                            labelText={c.header}
                            hideLabel
                            size="sm"
                            maxLength={c.maxLength}
                            value={r[c.key] ?? ''}
                            onChange={(e) => setCell(i, c.key, c.upper ? e.target.value.toUpperCase() : e.target.value)}
                          />
                        )}
                      </TableCell>
                    ))}
                    <TableCell>
                      <Button
                        kind="ghost"
                        size="sm"
                        hasIconOnly
                        renderIcon={TrashCan}
                        iconDescription="Delete row"
                        onClick={() => setRows((prev) => prev.filter((_, j) => j !== i))}
                      />
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
          <div className="detail-edit__actions">
            <Button kind="ghost" size="md" renderIcon={Add} onClick={() => setRows((prev) => [...prev, {}])} disabled={saving}>
              Add row
            </Button>
            <Button kind="tertiary" size="md" onClick={() => navigate(detailHref)} disabled={saving}>
              Cancel
            </Button>
            <Button kind="primary" size="md" onClick={onSave} disabled={saving}>
              {saving ? 'Saving…' : 'Save'}
            </Button>
          </div>
        </SectionTile>
      </AsyncBoundary>
    </PageLayout>
  );
};

export default SummaryReturnGridPage;
