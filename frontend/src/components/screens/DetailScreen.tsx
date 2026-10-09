import {
  Button,
  Tab,
  TabList,
  TabPanel,
  TabPanels,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeader,
  TableRow,
  Tabs,
} from '@carbon/react';
import {
  Calendar,
  Catalog,
  Document,
  Edit,
  Information,
  ListBulleted,
  Location,
  Money,
  RecentlyViewed,
  Tag,
  UserMultiple,
  WarningAlt,
} from '@carbon/icons-react';
import type { CarbonIconType } from '@carbon/icons-react';
import { useCallback, useEffect, useMemo, useState, type FC } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';

import AsyncBoundary from '@/components/AsyncBoundary';
import DetailTile from '@/components/DetailTile';
import { EmptyState } from '@/components/EmptyState/EmptyState';
import { StatusTag } from '@/components/StatusTag/StatusTag';
import Tombstone from '@/components/Tombstone';
import { useScopedUser } from '@/context/org/useOrg';
import { useLazyTabs } from '@/hooks/useLazyTabs';
import { originState, useHereAsOrigin } from '@/lib/navOrigin';
import { can } from '@/routes/access';
import { listQuery, oneQuery, type Row } from '@/services/screens';
import type { DetailScreenDef, DetailSection } from '@/screens/types';

import ActionButton from './ActionButton';
import DownloadCell from './DownloadCell';
import { formatValue } from './format';
import { linkHref } from './links';
import ReportButton from './ReportButton';
import ScreenLayout from './ScreenLayout';

type Field = NonNullable<DetailSection['fields']>[number];

/** A section's icon, read from its title — the same idea as the nav's. */
function sectionIcon(title: string): CarbonIconType {
  const t = title.toLowerCase();
  if (/error|anomal|violation|warning/.test(t)) return WarningAlt;
  if (/history|version|log|audit/.test(t)) return RecentlyViewed;
  if (/date|period|month|schedule/.test(t)) return Calendar;
  if (/client|user|scaler|holder|agent|contact/.test(t)) return UserMultiple;
  if (/site|district|location|address/.test(t)) return Location;
  if (/bill|invoice|statement|amount|rate|value|price|charge/.test(t)) return Money;
  if (/mark/.test(t)) return Tag;
  if (/species|grade|product|segregation|tally|line/.test(t)) return ListBulleted;
  if (/parameter|setting|code/.test(t)) return Catalog;
  if (/summary|general|detail|information/.test(t)) return Information;
  return Document;
}

const fieldValue = (f: Field, record: Row) => {
  if (f.format === 'download') return <DownloadCell url={record[f.key]} />;
  if (f.format === 'status' && record[f.key]) return <StatusTag status={String(record[f.key])} />;
  return formatValue(record[f.key], f.format);
};

/** A field holding long free text spans the whole tile, keeping its line breaks. */
const isLongText = (f: Field, record: Row) => {
  const v = record[f.key];
  return typeof v === 'string' && (v.length > 80 || v.includes('\n'));
};

/** A child table of a detail record (segregations, history, versions …), on the grey tab canvas. */
const SectionTable: FC<{ section: DetailSection; record: Row; origin: ReturnType<typeof useHereAsOrigin> }> = ({
  section,
  record,
  origin,
}) => {
  const navigate = useNavigate();
  const table = section.table!;
  const [rows, setRows] = useState<Row[] | null>(null);
  const [error, setError] = useState<string | undefined>();
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    const params: Record<string, string> = {};
    for (const [p, key] of Object.entries(table.params)) {
      const v = key.startsWith('=') ? key.slice(1) : record[key];
      if (v !== undefined && v !== null) params[p] = String(v);
    }
    let cancelled = false;
    setError(undefined);
    setRows(null);
    listQuery(table.query, params)
      .then((r) => !cancelled && setRows(r))
      .catch((e) => !cancelled && setError((e as Error).message));
    return () => {
      cancelled = true;
    };
  }, [record, table.params, table.query, attempt]);

  return (
    <AsyncBoundary
      loading={rows === null && !error}
      error={error}
      onRetry={() => setAttempt((n) => n + 1)}
      loadingText={`Loading ${section.title.toLowerCase()}…`}
    >
      {rows && rows.length === 0 ? (
        <EmptyState title={`No ${section.title.toLowerCase()}`} body="There is nothing recorded here for this record." />
      ) : rows ? (
        <div className="bordered-table">
          <TableContainer>
            <Table size="md" useZebraStyles>
              <TableHead>
                <TableRow>
                  {table.columns.map((c) => (
                    <TableHeader key={c.key}>{c.header}</TableHeader>
                  ))}
                </TableRow>
              </TableHead>
              <TableBody>
                {rows.map((r, i) => {
                  const href = table.rowLink ? linkHref(table.rowLink, r) : null;
                  const open = () => href && navigate(href, { state: originState(origin) });
                  return (
                    <TableRow
                      key={i}
                      className={href ? 'fsp-search__row--selectable' : undefined}
                      onClick={href ? open : undefined}
                      onKeyDown={
                        href
                          ? (e) => {
                              if (e.key === 'Enter' || e.key === ' ') {
                                e.preventDefault();
                                open();
                              }
                            }
                          : undefined
                      }
                      tabIndex={href ? 0 : undefined}
                      role={href ? 'link' : undefined}
                    >
                      {table.columns.map((c) => (
                        <TableCell
                          key={c.key}
                          className={c.format === 'date' || c.format === 'datetime' ? 'fsp-search__cell--nowrap' : undefined}
                        >
                          {c.format === 'download' ? (
                            <DownloadCell url={r[c.key]} />
                          ) : c.format === 'status' && r[c.key] ? (
                            <StatusTag status={String(r[c.key])} />
                          ) : (
                            formatValue(r[c.key], c.format)
                          )}
                        </TableCell>
                      ))}
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          </TableContainer>
        </div>
      ) : null}
    </AsyncBoundary>
  );
};

/**
 * Generic read-only record view — the template behind the legacy "View …"
 * screens (e.g. P481 Timber Mark Query, P052 summary return), laid out as
 * nr-fta's record pages: a back link above the title, a tombstone of the
 * record's identifiers, then Carbon contained tabs over a full-bleed grey
 * pane — "Details" first (each field section a white icon-titled tile), then
 * one tab per child table. A record with a single section skips the tab bar.
 * Record-level actions (Edit, legacy commands, reports) sit level with the
 * title; commands are gated by capability.
 */
export const DetailScreen: FC<{ screen: DetailScreenDef }> = ({ screen }) => {
  const navigate = useNavigate();
  const user = useScopedUser();
  const [searchParams] = useSearchParams();
  const [record, setRecord] = useState<Row | null>(null);
  const [error, setError] = useState<string | undefined>();
  const here = useHereAsOrigin(screen.title);

  const keyString = searchParams.toString();
  const keys = useMemo(() => {
    const k: Record<string, string> = {};
    new URLSearchParams(keyString).forEach((v, n) => {
      k[n] = v;
    });
    return k;
  }, [keyString]);
  const missing = screen.keys.filter((k) => !keys[k]);
  const tabs = useLazyTabs(keyString);

  const reload = useCallback(() => {
    if (missing.length > 0) return;
    setError(undefined);
    setRecord(null);
    oneQuery(screen.query, keys)
      .then(setRecord)
      .catch((e) => setError((e as Error).message));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [screen.query, keys]);

  useEffect(() => {
    reload();
  }, [reload]);

  const fieldSections = screen.sections.filter((s) => s.fields?.length);
  const tableSections = screen.sections.filter((s) => s.table);

  // The record's identifiers — its key fields, plus its status — pinned above
  // the tabs, as FTA's tombstone pins a tenure's file id, type and status.
  const tombstone = useMemo(() => {
    if (!record) return [];
    const all = fieldSections.flatMap((s) => s.fields ?? []);
    const picked = [
      ...screen.keys.map((k) => all.find((f) => f.key === k)).filter((f): f is Field => !!f),
      ...all.filter((f) => f.format === 'status').slice(0, 1),
    ];
    return picked.map((f) => ({ label: f.label, value: fieldValue(f, record) }));
  }, [fieldSections, record, screen.keys]);

  const editHref =
    record && screen.editLink && can(user, screen.editLink.capability) ? linkHref(screen.editLink, record) : null;
  const actions =
    record && (editHref || screen.actions?.length || screen.reports?.length) ? (
      <>
        {(screen.reports ?? []).map((r) => (
          <ReportButton key={r.reportId + r.label} report={r} source={{ ...keys, ...record }} />
        ))}
        {(screen.actions ?? []).map((a) => (
          <ActionButton key={a.id} action={a} record={record} onDone={reload} />
        ))}
        {editHref && (
          <Button size="md" renderIcon={Edit} onClick={() => navigate(editHref, { state: originState(here) })}>
            {screen.editLink?.label ?? 'Edit'}
          </Button>
        )}
      </>
    ) : undefined;

  if (missing.length > 0) {
    return (
      <ScreenLayout screen={screen}>
        <EmptyState title="Nothing selected" body="Open this record from a search result." />
      </ScreenLayout>
    );
  }

  const detailsPanel = fieldSections.length > 0 && record && (
    <div className="fsp-info__tab-panel">
      {fieldSections.map((s, i) => (
        <DetailTile
          key={`${s.title}-${i}`}
          title={s.title}
          icon={sectionIcon(s.title)}
          fields={(s.fields ?? []).map((f) => ({
            label: f.label,
            value: fieldValue(f, record),
            wide: isLongText(f, record),
          }))}
        />
      ))}
    </div>
  );

  const panes = record
    ? [
        ...(detailsPanel ? [{ label: 'Details', icon: Information, render: () => detailsPanel }] : []),
        ...tableSections.map((s) => ({
          label: s.title,
          icon: sectionIcon(s.title),
          render: () => <SectionTable section={s} record={record} origin={here} />,
        })),
      ]
    : [];

  return (
    <ScreenLayout screen={screen} actions={actions}>
      <AsyncBoundary loading={!record && !error} error={error} onRetry={reload} loadingText="Loading record…">
        {record && (
          <>
            {tombstone.length > 0 && <Tombstone ariaLabel={`${screen.title} summary`} items={tombstone} />}
            {panes.length > 1 ? (
              // Carbon's <Tabs> renders no DOM of its own, so the grey full-bleed
              // pane is styled through this wrapper (styles/_detail.scss).
              <div className="fsp-info__page-tabs">
                <Tabs selectedIndex={tabs.selected} onChange={tabs.onChange}>
                  <TabList aria-label={`${screen.title} sections`} contained>
                    {panes.map((p, i) => (
                      <Tab key={`${p.label}-${i}`} renderIcon={p.icon}>
                        {p.label}
                      </Tab>
                    ))}
                  </TabList>
                  <TabPanels>
                    {panes.map((p, i) => (
                      <TabPanel key={`${p.label}-${i}`}>{tabs.isOpened(i) && p.render()}</TabPanel>
                    ))}
                  </TabPanels>
                </Tabs>
              </div>
            ) : (
              panes.length === 1 && <div className="hbs-detail__pane">{panes[0].render()}</div>
            )}
          </>
        )}
      </AsyncBoundary>
    </ScreenLayout>
  );
};

export default DetailScreen;
