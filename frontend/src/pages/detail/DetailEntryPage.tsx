import {
  Button,
  Checkbox,
  InlineNotification,
  Loading,
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
import { Add, DocumentAdd, ListChecked, TrashCan, WarningAlt } from '@carbon/icons-react';
import { useCallback, useEffect, useMemo, useState, type FC } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';

import ConfirmationModal from '@/components/ConfirmationModal';
import RequiredLabel from '@/components/RequiredLabel';
import SectionTile from '@/components/SectionTile';
import FieldGrid from '@/components/screens/FieldGrid';
import { useNotification } from '@/context/notification/useNotification';
import { useScopedUser } from '@/context/org/useOrg';
import { can } from '@/routes/access';
import {
  getDetailVersion,
  getFieldAvailability,
  runDetailCommand,
  type DetailLine,
  type DetailVersionDoc,
} from '@/services/detailReturns';

import ForbiddenPage from '../ForbiddenPage';
import PageLayout from '../PageLayout';
import {
  DEFECT_COLUMNS,
  DOC_TYPES,
  EVENT_TYPES,
  LINE_COLUMNS,
  visibleFields,
  WORKFLOW_ACTIONS,
  type DocTypeCode,
  type LineColumn,
} from './detailEntryFields';
import '@/components/screens/screens.scss';
import './detailEntry.scss';

type Values = Record<string, string>;

interface EditLine {
  key: number;
  logdtlId: number | null;
  fields: Values;
  defects: { logDefectId: number | null; fields: Values }[];
  open?: boolean;
}

const str = (v: unknown): string => {
  if (v === null || v === undefined) return '';
  const s = String(v);
  // dates arrive as ISO date-times; the date inputs want yyyy-mm-dd
  return /^\d{4}-\d{2}-\d{2}T00:00(:00)?$/.test(s) ? s.slice(0, 10) : s;
};

const toValues = (fields: Record<string, unknown>): Values =>
  Object.fromEntries(Object.entries(fields).map(([k, v]) => [k, str(v)]));

let lineKey = 1;

const toLines = (logs: DetailLine[]): EditLine[] =>
  logs.map((l) => ({
    key: lineKey++,
    logdtlId: l.logdtlId,
    fields: toValues(l.fields),
    defects: l.defects.map((d) => ({ logDefectId: d.logDefectId, fields: toValues(d.fields) })),
  }));

const tallyTypes: DocTypeCode[] = ['P', 'S', 'F'];

/**
 * P550/P553 (log tally), P600/P603 (weigh slip), P650/P653 (sample tally),
 * P700/P703 (ledgers), P750/P753 (SFP) with their log detail and defect pages
 * (P558/P559, P658/P659, P758) on one page: the header fields the legacy
 * matrices show for the event type, the tally lines with their defects, and the
 * workflow buttons the backend allows for this version (Buttons.jsp rules).
 *
 * New return: /scale-returns/detail-entry?docType=P&event=PS (the P004 menu
 * when either is missing). Existing: ?ddn=…&version=….
 */
const DetailEntryPage: FC = () => {
  const user = useScopedUser();
  const navigate = useNavigate();
  const { display } = useNotification();
  const [params, setParams] = useSearchParams();
  const ddn = params.get('ddn');
  const version = params.get('version');
  const [docType, setDocType] = useState<DocTypeCode | ''>((params.get('docType') as DocTypeCode) ?? '');
  const [event, setEvent] = useState(params.get('event') ?? '');
  const [doc, setDoc] = useState<DetailVersionDoc | null>(null);
  const [values, setValues] = useState<Values>({});
  const [loaded, setLoaded] = useState<Values>({});
  const [lines, setLines] = useState<EditLine[]>([]);
  const [availability, setAvailability] = useState<Record<string, string[]> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [confirm, setConfirm] = useState<(typeof WORKFLOW_ACTIONS)[number] | null>(null);
  const [eventChange, setEventChange] = useState<string | null>(null);

  const adding = !ddn;

  useEffect(() => {
    getFieldAvailability().then(setAvailability).catch(() => setAvailability({}));
  }, []);

  const load = useCallback(async () => {
    if (!ddn || !version) return;
    setError(null);
    try {
      const d = await getDetailVersion(ddn, version);
      setDoc(d);
      setDocType(d.docType as DocTypeCode);
      setEvent(String(d.fields.scaleEventTypeCode ?? ''));
      const v = toValues(d.fields);
      setValues(v);
      setLoaded(v);
      setLines(toLines(d.logs));
    } catch (e) {
      setError((e as Error).message);
    }
  }, [ddn, version]);

  useEffect(() => {
    void load();
  }, [load]);

  const type = (doc?.docType ?? docType) as DocTypeCode | '';
  const fields = useMemo(
    () => (type ? visibleFields(type, event, availability) : []),
    [type, event, availability],
  );
  const lineColumns: LineColumn[] = type ? (LINE_COLUMNS[type] ?? []) : [];
  const hasDefects = type === 'P' || type === 'S';
  const editable = adding || (doc?.editable ?? false);
  const canSave = adding || (doc?.actions.includes('detailReturns.save') ?? false);

  if (!can(user, 'SCALE_RETURNS_VIEW')) return <ForbiddenPage />;

  // ── P004: choose the type and event first ──────────────────────
  if (adding && (!docType || !event)) {
    if (!can(user, 'DETAIL_RETURN_EDIT')) return <ForbiddenPage />;
    return (
      <PageLayout title="Add Detail Scale Data" subtitle="Choose the type of detail scale return and its scale event (P004).">
        <SectionTile title="Detail scale return" icon={DocumentAdd}>
          <div className="hbs-form">
            <div className="fsp-search__field-grid">
              <Select
                id="entry-doc-type"
                labelText={<RequiredLabel text="Return Type" />}
                value={docType}
                onChange={(e) => {
                  setDocType(e.target.value as DocTypeCode);
                  setEvent('');
                }}
              >
                <SelectItem value="" text="Choose…" />
                {DOC_TYPES.map((t) => (
                  <SelectItem key={t.code} value={t.code} text={`${t.label} (${t.legacy})`} />
                ))}
              </Select>
              {docType && (
                <Select id="entry-event" labelText={<RequiredLabel text="Scale Event" />} value={event} onChange={(e) => setEvent(e.target.value)}>
                  <SelectItem value="" text="Choose…" />
                  {EVENT_TYPES[docType].map((t) => (
                    <SelectItem key={t.code} value={t.code} text={t.label} />
                  ))}
                </Select>
              )}
            </div>
            <div className="detail-edit__actions">
              <Button
                size="md"
                disabled={!docType || !event}
                onClick={() => setParams({ docType: String(docType), event })}
              >
                Continue
              </Button>
            </div>
          </div>
        </SectionTile>
      </PageLayout>
    );
  }

  if (!adding && !doc && !error) return <Loading withOverlay={false} />;

  // ── body built from the form ────────────────────────────────────
  const fieldBody = (): Record<string, unknown> => {
    const out: Record<string, unknown> = {};
    for (const f of fields) {
      const v = values[f.name] ?? '';
      if (adding ? v !== '' : v !== (loaded[f.name] ?? '')) out[f.name] = v === '' ? null : v;
    }
    if (adding) out.scaleEventTypeCode = event;
    return out;
  };

  const linesBody = () =>
    lines.map((l) => ({
      logdtlId: l.logdtlId,
      fields: Object.fromEntries(
        lineColumns
          .filter((c) => !(c.name === 'netVolume' && type !== 'F'))
          .map((c) => [c.name, l.fields[c.name] === '' || l.fields[c.name] === undefined ? null : l.fields[c.name]]),
      ),
      defects: hasDefects
        ? l.defects.map((d) => ({ logDefectId: d.logDefectId, fields: d.fields }))
        : undefined,
    }));

  const keyBody = () => ({ ddn: doc?.ddn, version: doc?.version, updateTimestamp: doc?.updateTimestamp });

  const withEdits = (): Record<string, unknown> => {
    const body: Record<string, unknown> = { ...keyBody() };
    if (editable) {
      body.fields = fieldBody();
      if (tallyTypes.includes(type as DocTypeCode)) body.logs = linesBody();
    }
    return body;
  };

  const afterCommand = async (label: string, result: Record<string, unknown>) => {
    display({ kind: 'success', title: `${label} complete`, timeout: 4000 });
    const nextDdn = String(result.ddn ?? doc?.ddn ?? '');
    const nextVersion = String(result.version ?? doc?.version ?? '');
    if (nextDdn && (nextDdn !== ddn || nextVersion !== version)) {
      navigate(`/scale-returns/detail-entry?ddn=${encodeURIComponent(nextDdn)}&version=${nextVersion}`, {
        replace: true,
      });
    } else {
      await load();
    }
  };

  const run = async (command: string, label: string, body: Record<string, unknown>) => {
    setBusy(true);
    setError(null);
    try {
      const result = await runDetailCommand(command, body);
      await afterCommand(label, result);
    } catch (e) {
      setError((e as Error).message);
      throw e;
    } finally {
      setBusy(false);
    }
  };

  const onSave = () =>
    run(adding ? 'detailReturns.add' : 'detailReturns.save', adding ? 'Add' : 'Save', adding
      ? { docType: type, fields: fieldBody(), ...(tallyTypes.includes(type as DocTypeCode) ? { logs: linesBody() } : {}) }
      : withEdits()).catch(() => undefined);

  const bodyFor = (command: string) =>
    command === 'detailReturns.release' || command === 'detailReturns.reEdit' ? withEdits() : keyBody();

  const shownActions = adding ? [] : WORKFLOW_ACTIONS.filter((a) => doc?.actions.includes(a.command));

  const setLine = (key: number, change: (l: EditLine) => EditLine) =>
    setLines((ls) => ls.map((l) => (l.key === key ? change(l) : l)));

  const title = adding
    ? `Add ${DOC_TYPES.find((t) => t.code === type)?.label ?? 'Detail Scale Return'}`
    : `Detail Scale Return ${doc?.ddn ?? ''} version ${doc?.version ?? ''}`;

  return (
    <PageLayout
      title={title}
      subtitle={
        doc
          ? `State ${doc.documentState} / ${doc.versionState}${doc.active ? '' : ' (inactive)'} — event ${event}`
          : `Scale event ${EVENT_TYPES[type as DocTypeCode]?.find((e) => e.code === event)?.label ?? event}`
      }
      backLink={
        doc ? (
          <Link
            to={`/scale-returns/detail-return?ddn=${encodeURIComponent(doc.ddn)}&version=${doc.version}`}
            className="back-link"
          >
            Back to the scale return
          </Link>
        ) : undefined
      }
      actions={
        <div className="hbs-detail-entry__actions">
          {canSave && editable && (
            <Button size="md" disabled={busy} onClick={() => void onSave()}>
              {adding ? 'Add' : 'Save'}
            </Button>
          )}
          {shownActions.map((a) => (
            <Button
              key={a.command}
              size="md"
              kind={a.danger ? 'danger--tertiary' : 'tertiary'}
              disabled={busy}
              onClick={() =>
                a.confirm ? setConfirm(a) : void run(a.command, a.label, bodyFor(a.command)).catch(() => undefined)
              }
            >
              {a.label}
            </Button>
          ))}
          {!adding && doc?.actions.includes('detailReturns.changeEvent') && (
            <Button size="md" kind="tertiary" disabled={busy} onClick={() => setEventChange('')}>
              Change Event Type
            </Button>
          )}
        </div>
      }
    >
      {error && (
        <InlineNotification
          kind="error"
          lowContrast
          title="The request was not completed"
          subtitle={error}
          onClose={() => setError(null)}
          className="hbs-screen__notes"
        />
      )}
      {!adding && doc && !editable && (
        <InlineNotification
          kind="info"
          lowContrast
          hideCloseButton
          title=""
          subtitle="This version cannot be edited by you in its current state; only the actions shown are available."
          className="hbs-screen__notes"
        />
      )}

      <SectionTile title="Scale Return" icon={ListChecked}>
        <div className="hbs-form">
          <FieldGrid
            fields={fields}
            values={values}
            onChange={(n, v) => setValues((p) => ({ ...p, [n]: v }))}
            idPrefix="detail-entry"
            readOnly={() => !editable}
            mode="form"
          />
        </div>
      </SectionTile>

      {tallyTypes.includes(type as DocTypeCode) && (
        <SectionTile
          title="Log Details"
          icon={ListChecked}
          description="One row per log (piece). Net volume is calculated from the dimensions and defects when the return is saved."
          actions={
            editable ? (
              <Button
                size="sm"
                kind="ghost"
                renderIcon={Add}
                onClick={() => setLines((ls) => [...ls, { key: lineKey++, logdtlId: null, fields: {}, defects: [] }])}
              >
                Add log
              </Button>
            ) : undefined
          }
        >
          <TableContainer>
            <Table size="sm" useZebraStyles>
              <TableHead>
                <TableRow>
                  {lineColumns.map((c) => (
                    <TableHeader key={c.name}>{c.header}</TableHeader>
                  ))}
                  {type !== 'F' && <TableHeader>Net Volume</TableHeader>}
                  {hasDefects && <TableHeader>Defects</TableHeader>}
                  {editable && <TableHeader />}
                </TableRow>
              </TableHead>
              <TableBody>
                {lines.map((l, i) => (
                  <LineRows
                    key={l.key}
                    line={l}
                    index={i}
                    columns={lineColumns}
                    computedVolume={type !== 'F'}
                    hasDefects={hasDefects}
                    editable={editable}
                    onChange={(change) => setLine(l.key, change)}
                    onRemove={() => setLines((ls) => ls.filter((x) => x.key !== l.key))}
                  />
                ))}
              </TableBody>
            </Table>
          </TableContainer>
          {lines.length === 0 && <p className="hbs-detail-entry__empty">No log details.</p>}
        </SectionTile>
      )}

      <ConfirmationModal
        open={confirm !== null}
        onClose={() => setConfirm(null)}
        heading={`Confirm ${confirm?.label ?? ''}`}
        confirmLabel={confirm?.label}
        danger={confirm?.danger}
        onConfirm={async () => {
          if (!confirm) return;
          await run(confirm.command, confirm.label, bodyFor(confirm.command));
          setConfirm(null);
        }}
      >
        <p className="detail-dialog__subtitle">{confirm?.confirm}</p>
      </ConfirmationModal>

      <ConfirmationModal
        open={eventChange !== null}
        onClose={() => setEventChange(null)}
        heading="Change Event Type"
        confirmLabel="Change Event Type"
        onConfirm={async () => {
          if (!eventChange) throw new Error('Choose the new event type');
          await run('detailReturns.changeEvent', 'Change Event Type', {
            ...keyBody(),
            scaleEventTypeCode: eventChange,
          });
          setEventChange(null);
        }}
      >
        <p className="detail-dialog__subtitle">
          Fields that do not apply to the new event type are cleared and the return is saved (P562 / P612 / P662).
        </p>
        <Select
          id="entry-change-event"
          labelText="New Event Type"
          value={eventChange ?? ''}
          onChange={(e) => setEventChange(e.target.value)}
        >
          <SelectItem value="" text="Choose…" />
          {(EVENT_TYPES[type as DocTypeCode] ?? [])
            .filter((t) => t.code !== event && (doc?.eventTypes ?? []).includes(t.code))
            .map((t) => (
              <SelectItem key={t.code} value={t.code} text={t.label} />
            ))}
        </Select>
      </ConfirmationModal>
      {busy && <Loading description="Working…" />}
    </PageLayout>
  );
};

interface LineRowsProps {
  line: EditLine;
  index: number;
  columns: LineColumn[];
  computedVolume: boolean;
  hasDefects: boolean;
  editable: boolean;
  onChange: (change: (l: EditLine) => EditLine) => void;
  onRemove: () => void;
}

const Cell: FC<{
  id: string;
  column: LineColumn;
  value: string;
  editable: boolean;
  onChange: (v: string) => void;
}> = ({ id, column, value, editable, onChange }) =>
  column.type === 'yesno' ? (
    <Checkbox
      id={id}
      labelText=""
      hideLabel
      checked={value === 'Y'}
      disabled={!editable}
      onChange={(_, { checked }) => onChange(checked ? 'Y' : 'N')}
    />
  ) : (
    <TextInput
      id={id}
      labelText={column.header}
      hideLabel
      size="sm"
      value={value}
      readOnly={!editable}
      inputMode={column.type === 'number' ? 'decimal' : undefined}
      onChange={(e) => onChange(column.upper ? e.target.value.toUpperCase() : e.target.value)}
    />
  );

const LineRows: FC<LineRowsProps> = ({
  line,
  index,
  columns,
  computedVolume,
  hasDefects,
  editable,
  onChange,
  onRemove,
}) => (
  <>
    <TableRow>
      {columns.map((c) => (
        <TableCell key={c.name}>
          <Cell
            id={`line-${line.key}-${c.name}`}
            column={c}
            value={line.fields[c.name] ?? ''}
            editable={editable}
            onChange={(v) => onChange((l) => ({ ...l, fields: { ...l.fields, [c.name]: v } }))}
          />
        </TableCell>
      ))}
      {computedVolume && <TableCell>{line.fields.netVolume ?? ''}</TableCell>}
      {hasDefects && (
        <TableCell>
          <Button size="sm" kind="ghost" onClick={() => onChange((l) => ({ ...l, open: !l.open }))}>
            {line.defects.length} defect{line.defects.length === 1 ? '' : 's'}
          </Button>
        </TableCell>
      )}
      {editable && (
        <TableCell>
          <Button
            size="sm"
            kind="ghost"
            hasIconOnly
            renderIcon={TrashCan}
            iconDescription={`Delete log ${index + 1}`}
            onClick={onRemove}
          />
        </TableCell>
      )}
    </TableRow>
    {hasDefects && line.open && (
      <TableRow className="hbs-detail-entry__defects">
        <TableCell colSpan={columns.length + 3}>
          <div className="hbs-detail-entry__defect-grid">
            {line.defects.map((d, di) => (
              <div key={di} className="hbs-detail-entry__defect">
                {DEFECT_COLUMNS.map((c) => (
                  <Cell
                    key={c.name}
                    id={`defect-${line.key}-${di}-${c.name}`}
                    column={c}
                    value={d.fields[c.name] ?? ''}
                    editable={editable}
                    onChange={(v) =>
                      onChange((l) => ({
                        ...l,
                        defects: l.defects.map((x, xi) =>
                          xi === di ? { ...x, fields: { ...x.fields, [c.name]: v } } : x,
                        ),
                      }))
                    }
                  />
                ))}
                {editable && (
                  <Button
                    size="sm"
                    kind="ghost"
                    hasIconOnly
                    renderIcon={TrashCan}
                    iconDescription="Delete defect"
                    onClick={() => onChange((l) => ({ ...l, defects: l.defects.filter((_, xi) => xi !== di) }))}
                  />
                )}
              </div>
            ))}
            {editable && line.defects.length < 9 && (
              <Button
                size="sm"
                kind="ghost"
                renderIcon={Add}
                onClick={() => onChange((l) => ({ ...l, defects: [...l.defects, { logDefectId: null, fields: {} }] }))}
              >
                Add defect
              </Button>
            )}
            {line.defects.length >= 9 && (
              <p className="hbs-detail-entry__empty">
                <WarningAlt size={16} /> A log can have at most 9 defects.
              </p>
            )}
          </div>
        </TableCell>
      </TableRow>
    )}
  </>
);

export default DetailEntryPage;
