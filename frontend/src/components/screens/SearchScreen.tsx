import {
  Button,
  DataTable,
  DataTableSkeleton,
  Loading,
  Pagination,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeader,
  TableRow,
  Tile,
} from '@carbon/react';
import { Add, Search as SearchIcon } from '@carbon/icons-react';
import { useCallback, useEffect, useMemo, useRef, useState, type FC, type FormEvent } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';

import { EmptyState } from '@/components/EmptyState/EmptyState';
import ExportCsvButton from '@/components/ExportCsvButton';
import { StatusTag } from '@/components/StatusTag/StatusTag';
import { useNotification } from '@/context/notification/useNotification';
import { useScopedUser } from '@/context/org/useOrg';
import { useSessionState, type LastSearch } from '@/hooks/useSessionState';
import { originState, useHereAsOrigin } from '@/lib/navOrigin';
import { can } from '@/routes/access';
import { exportQuery, runQuery, type Row } from '@/services/screens';
import type { ColumnDef, SearchScreenDef } from '@/screens/types';

import ActionButton from './ActionButton';
import DownloadCell from './DownloadCell';
import FieldGrid from './FieldGrid';
import { formatValue } from './format';
import { linkHref } from './links';
import ReportButton from './ReportButton';
import ScreenLayout from './ScreenLayout';

const SearchingIcon = () => <Loading small withOverlay={false} description="" />;

/** nr-fta's paging (services/paging.ts). */
export const DEFAULT_PAGE_SIZE = 10;
export const PAGE_SIZES = [10, 25, 50, 100];

type Criteria = Record<string, string>;
type Sort = { by?: string; dir: 'asc' | 'desc' };
type Persisted = LastSearch<Criteria> & { sort: Sort };

/** A results cell, formatted as its column says. */
const Cell: FC<{ column: ColumnDef; row: Row }> = ({ column, row }) => {
  const value = row[column.key];
  if (column.format === 'download') return <DownloadCell url={value} />;
  if (column.format === 'status' && value) return <StatusTag status={String(value)} />;
  return <>{formatValue(value, column.format)}</>;
};

const NOWRAP = new Set(['date', 'datetime', 'money', 'volume']);

/**
 * Generic search screen — the template behind every legacy "Search for …"
 * + "List of …" screen pair (e.g. P450 → P451), laid out as nr-fta's search
 * screens: criteria in a white tile over the 4-column field grid with Clear
 * all / Search bottom-right, then a full-bleed grey results panel whose count
 * banner (with the export and report actions), table and pagination footer
 * read as one block. Errors are toasts.
 *
 * The criteria and the last search are kept per browser tab, and coming back
 * to the screen runs that search again — fresh results on the same page,
 * without jumping to them. URL query params pre-fill criteria (deep links
 * from Home and from other screens) and search immediately.
 */
export const SearchScreen: FC<{ screen: SearchScreenDef }> = ({ screen }) => {
  const navigate = useNavigate();
  const user = useScopedUser();
  const [searchParams] = useSearchParams();
  const { display } = useNotification();
  const here = useHereAsOrigin(screen.title);

  const defaults = useMemo(() => {
    const d: Criteria = {};
    screen.criteria.forEach((f) => {
      if (f.defaultValue) d[f.name] = f.defaultValue;
    });
    return d;
  }, [screen.criteria]);

  const fromUrl = useMemo(() => {
    const d: Criteria = {};
    screen.criteria.forEach((f) => {
      const v = searchParams.get(f.name);
      if (v) d[f.name] = v;
    });
    return d;
  }, [screen.criteria, searchParams]);
  const hasUrlCriteria = Object.keys(fromUrl).length > 0;

  const [form, setForm] = useSessionState<Criteria>(`hbs.screen.${screen.id}.criteria`, defaults);
  const [lastSearch, setLastSearch] = useSessionState<Persisted | null>(`hbs.screen.${screen.id}.last`, null);

  const [rows, setRows] = useState<Row[] | null>(null);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(lastSearch?.size ?? DEFAULT_PAGE_SIZE);
  const [sort, setSort] = useState<Sort>(lastSearch?.sort ?? { dir: 'asc' });
  const [loading, setLoading] = useState(false);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [searched, setSearched] = useState<Criteria | null>(null);

  const validate = (criteria: Criteria): boolean => {
    const next: Record<string, string> = {};
    screen.criteria.forEach((f) => {
      if (f.required && !criteria[f.name]?.trim()) next[f.name] = `${f.label} is required`;
    });
    setErrors(next);
    if (screen.requireOneOf && !screen.requireOneOf.some((n) => criteria[n]?.trim())) {
      // Two groups can share a label ("Client No" under Trading Partner and
      // under Site Owner), so a repeated label carries its group.
      const fields = screen.requireOneOf.map((n) => screen.criteria.find((f) => f.name === n));
      const labels = fields
        .map((f, i) => {
          if (!f) return screen.requireOneOf![i];
          const repeated = fields.filter((o) => o?.label === f.label).length > 1;
          return repeated && f.group ? `${f.label} (${f.group})` : f.label;
        })
        .join(', ');
      setFormError(`Enter at least one of: ${labels}.`);
      return false;
    }
    setFormError(null);
    return Object.keys(next).length === 0;
  };

  // Once a search the user ran (or a page change) finishes, bring the results
  // into view — not for the search re-run on coming back, which shouldn't jump.
  const resultsRef = useRef<HTMLDivElement>(null);
  const scrollPending = useRef(false);
  useEffect(() => {
    if (loading || !scrollPending.current) return;
    scrollPending.current = false;
    const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    resultsRef.current?.scrollIntoView({ behavior: reduceMotion ? 'auto' : 'smooth', block: 'start' });
  }, [loading]);

  const execute = useCallback(
    async (criteria: Criteria, nextPage: number, nextSize: number, nextSort: Sort, scroll = true) => {
      scrollPending.current = scroll;
      setLoading(true);
      try {
        const res = await runQuery(screen.query, criteria, {
          page: nextPage,
          size: nextSize,
          sortBy: nextSort.by,
          sortDir: nextSort.dir,
        });
        setRows(res.content);
        setTotal(res.page.totalElements);
        setPage(res.page.number);
        setPageSize(res.page.size);
        setSearched({ ...criteria });
        setLastSearch({ criteria, page: res.page.number, size: res.page.size, sort: nextSort });
      } catch (e) {
        display({ kind: 'error', title: 'Search failed', subtitle: (e as Error).message, timeout: 7000 });
        setRows([]);
        setTotal(0);
      } finally {
        setLoading(false);
      }
    },
    [display, screen.query, setLastSearch],
  );

  // On open: a deep link searches its criteria; coming back re-runs the last
  // search; a list with no mandatory criteria searches straight away.
  useEffect(() => {
    if (hasUrlCriteria) {
      const criteria = { ...defaults, ...fromUrl };
      setForm(criteria);
      if (validate(criteria)) void execute(criteria, 0, pageSize, sort, false);
    } else if (lastSearch) {
      void execute(lastSearch.criteria, lastSearch.page, lastSearch.size, lastSearch.sort, false);
    } else if (screen.autoSearch && validate(form)) {
      void execute(form, 0, pageSize, sort, false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [screen.id, searchParams]);

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    if (!validate(form)) return;
    void execute(form, 0, pageSize, sort);
  };

  const onClear = () => {
    setForm({ ...defaults });
    setLastSearch(null);
    setRows(null);
    setSearched(null);
    setTotal(0);
    setPage(0);
    setErrors({});
    setFormError(null);
  };

  const onSort = (key: string) => {
    const next: Sort = { by: key, dir: sort.by === key && sort.dir === 'asc' ? 'desc' : 'asc' };
    setSort(next);
    void execute(searched ?? form, 0, pageSize, next);
  };

  const headers = screen.columns.map((c) => ({ key: c.key, header: c.header }));
  const tableRows = (rows ?? []).map((r, i) => ({ id: `row-${page}-${i}` })) as { id: string }[];
  const hasRowActions = (screen.rowActions?.length ?? 0) > 0;

  const createVisible = screen.createLink && can(user, screen.createLink.capability);
  const createHref = screen.createLink ? linkHref(screen.createLink, form) : null;
  const open = (href: string) => navigate(href, { state: originState(here) });

  return (
    <ScreenLayout
      screen={screen}
      actions={
        createVisible && createHref ? (
          <Button size="md" kind="tertiary" renderIcon={Add} onClick={() => open(createHref)}>
            {screen.createLink?.label ?? 'Add'}
          </Button>
        ) : undefined
      }
    >
      <Tile className="fsp-search__tile">
        <form className="fsp-search__form" onSubmit={onSubmit} noValidate>
          <FieldGrid
            fields={screen.criteria}
            values={form}
            onChange={(n, v) => setForm((p) => ({ ...p, [n]: v }))}
            idPrefix={screen.id}
            errors={errors}
          />
          {formError && (
            <p className="hbs-screen__form-error" role="alert">
              {formError}
            </p>
          )}
          <div className="fsp-search__actions">
            <Button kind="tertiary" size="md" type="button" onClick={onClear} disabled={loading}>
              Clear all
            </Button>
            <Button type="submit" size="md" renderIcon={loading ? SearchingIcon : SearchIcon} disabled={loading}>
              {loading ? 'Searching...' : 'Search'}
            </Button>
          </div>
        </form>
      </Tile>

      {(loading || rows !== null) && (
        <div className="fsp-search__results-fullbleed" ref={resultsRef}>
          <div className="fsp-search__results">
            {loading ? (
              <>
                <div className="fsp-search__results-header">
                  <span className="fsp-search__results-count fsp-search__results-count--searching">
                    Searching
                    <span className="fsp-search__searching-spinner" aria-hidden="true" />
                  </span>
                </div>
                <div className="fsp-search__table">
                  <DataTableSkeleton
                    headers={headers}
                    columnCount={headers.length}
                    rowCount={Math.min(pageSize, 10)}
                    showHeader={false}
                    showToolbar={false}
                    aria-label="Loading search results"
                  />
                </div>
              </>
            ) : rows && rows.length > 0 ? (
              <>
                <div className="fsp-search__results-header">
                  <span className="fsp-search__results-count">
                    {total.toLocaleString()} {total === 1 ? 'result' : 'results'} found
                  </span>
                  <span className="hbs-search__results-actions">
                    {(screen.reports ?? []).map((r) => (
                      <ReportButton key={r.reportId + r.label} report={r} source={searched ?? form} size="sm" kind="ghost" />
                    ))}
                    <ExportCsvButton
                      onExport={() => exportQuery(screen.query, searched ?? form)}
                      fallbackName={`${screen.id}.csv`}
                    />
                  </span>
                </div>

                <div className="fsp-search__table">
                  <DataTable rows={tableRows} headers={headers} isSortable={false}>
                    {({ rows: dtRows, headers: dtHeaders, getTableProps, getHeaderProps, getRowProps }) => (
                      <TableContainer>
                        <Table {...getTableProps()} size="md">
                          <TableHead>
                            <TableRow>
                              {dtHeaders.map((h) => {
                                const col = screen.columns.find((c) => c.key === h.key);
                                const { key: _k, ...hp } = getHeaderProps({ header: h });
                                return (
                                  <TableHeader
                                    key={h.key}
                                    {...hp}
                                    isSortable={!!col?.sortable}
                                    isSortHeader={sort.by === h.key}
                                    sortDirection={
                                      sort.by === h.key ? (sort.dir === 'asc' ? 'ASC' : 'DESC') : 'NONE'
                                    }
                                    onClick={col?.sortable ? () => onSort(h.key) : undefined}
                                  >
                                    {h.header}
                                  </TableHeader>
                                );
                              })}
                              {hasRowActions && <TableHeader>Actions</TableHeader>}
                            </TableRow>
                          </TableHead>
                          <TableBody>
                            {dtRows.map((dtRow, i) => {
                              const source = rows[i] ?? {};
                              const href = screen.rowLink ? linkHref(screen.rowLink, source) : null;
                              const { key: _rk, ...rp } = getRowProps({ row: dtRow });
                              return (
                                <TableRow
                                  key={dtRow.id}
                                  {...rp}
                                  className={href ? 'fsp-search__row--selectable' : undefined}
                                  onClick={href ? () => open(href) : undefined}
                                  onKeyDown={
                                    href
                                      ? (e) => {
                                          if (e.key === 'Enter' || e.key === ' ') {
                                            e.preventDefault();
                                            open(href);
                                          }
                                        }
                                      : undefined
                                  }
                                  tabIndex={href ? 0 : undefined}
                                  role={href ? 'link' : undefined}
                                >
                                  {screen.columns.map((c) => (
                                    <TableCell
                                      key={c.key}
                                      className={NOWRAP.has(c.format ?? '') ? 'fsp-search__cell--nowrap' : undefined}
                                    >
                                      <Cell column={c} row={source} />
                                    </TableCell>
                                  ))}
                                  {hasRowActions && (
                                    <TableCell className="detail-tab__row-action" onClick={(e) => e.stopPropagation()}>
                                      <div className="hbs-search__row-actions">
                                        {screen.rowActions!.map((a) => (
                                          <ActionButton
                                            key={a.id}
                                            action={a}
                                            record={source}
                                            size="sm"
                                            onDone={() => void execute(searched ?? form, page, pageSize, sort, false)}
                                          />
                                        ))}
                                      </div>
                                    </TableCell>
                                  )}
                                </TableRow>
                              );
                            })}
                          </TableBody>
                        </Table>
                      </TableContainer>
                    )}
                  </DataTable>
                </div>

                <Pagination
                  page={page + 1}
                  pageSize={pageSize}
                  pageSizes={PAGE_SIZES}
                  totalItems={total}
                  onChange={({ page: p, pageSize: s }) => void execute(searched ?? form, p - 1, s, sort)}
                  size="md"
                />
              </>
            ) : (
              <EmptyState
                title="No results found"
                body={
                  <>
                    No records match your search criteria.
                    <br />
                    Try adjusting your filters and searching again.
                  </>
                }
              />
            )}
          </div>
        </div>
      )}
    </ScreenLayout>
  );
};

export default SearchScreen;
