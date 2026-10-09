import { Button, ComboBox, Loading, RadioButton, RadioButtonGroup, TextInput } from '@carbon/react';
import { DocumentPdf, Report } from '@carbon/icons-react';
import { useEffect, useMemo, useState, type FC, type FormEvent } from 'react';

import AsyncBoundary from '@/components/AsyncBoundary';
import SectionTile from '@/components/SectionTile';
import { useNotification } from '@/context/notification/useNotification';
import { listReports, runReport, type ReportDefinition } from '@/services/screens';
import { triggerBrowserDownload } from '@/utils/download';

import PageLayout from './PageLayout';
import '@/components/screens/screens.scss';

/** Prompts the backend fills from the JWT — never shown to the user. */
const SERVER_FILLED = /_(USERTYPE|USERTYPECD|USERCLILOC|JOBNO)$/i;

/** PSR_SCALEDATEFROM → "Scale Date From". */
const humanize = (prompt: string): string => {
  const tail = prompt.includes('_') ? prompt.slice(prompt.indexOf('_') + 1) : prompt;
  return tail
    .replace(/_/g, ' ')
    .replace(/([a-z])([A-Z])/g, '$1 $2')
    .toLowerCase()
    .replace(/\b(from|to|date|no|id|num|number|dist|cli|loc)\b/g, (w) => ` ${w} `)
    .replace(/\s+/g, ' ')
    .trim()
    .replace(/\b\w/g, (c) => c.toUpperCase());
};

const isDatePrompt = (p: string) => /(DATE|FROM|TO)$/i.test(p) && !/(TYPE|STATUS)/i.test(p);

/**
 * Reports — runs any of the 79 legacy HBS JasperReports units (formerly
 * Crystal / the shared JCRS server) in-process on the backend. Replaces the
 * legacy per-screen "View PDF" / "Send" buttons and the e-mailed report drop
 * folders (P410 Recent Queries): reports now stream straight to the browser.
 * Screen-specific report buttons (e.g. "Print Client Register" on the
 * issued-invoice list) call the same endpoint with pre-mapped prompts.
 */
const ReportsPage: FC = () => {
  const { display } = useNotification();
  const [reports, setReports] = useState<ReportDefinition[] | null>(null);
  const [selected, setSelected] = useState<ReportDefinition | null>(null);
  const [values, setValues] = useState<Record<string, string>>({});
  const [format, setFormat] = useState<'PDF' | 'CSV'>('PDF');
  const [running, setRunning] = useState(false);

  useEffect(() => {
    listReports()
      .then(setReports)
      .catch((e) => {
        setReports([]);
        display({ kind: 'error', title: 'Could not load reports', subtitle: (e as Error).message, timeout: 6000 });
      });
  }, [display]);

  const prompts = useMemo(
    () => (selected?.parameters ?? []).filter((p) => !SERVER_FILLED.test(p)),
    [selected],
  );

  const onRun = async (e: FormEvent) => {
    e.preventDefault();
    if (!selected) return;
    setRunning(true);
    try {
      const { blob, filename } = await runReport(selected.id, values, format);
      triggerBrowserDownload(blob, filename ?? `${selected.id}.${format.toLowerCase()}`);
    } catch (err) {
      display({ kind: 'error', title: 'Report failed', subtitle: (err as Error).message, timeout: 8000 });
    } finally {
      setRunning(false);
    }
  };

  return (
    <PageLayout
      title="Reports"
      subtitle="Run a Harvest Billing System report. Industry users only see reports about their own client."
    >
      <AsyncBoundary loading={reports === null} loadingText="Loading reports…">
        <SectionTile
          title="Run a report"
          icon={Report}
          description="Leave a prompt blank to include everything. Dates use YYYY-MM-DD."
        >
          <form className="hbs-form" onSubmit={onRun} noValidate>
            <div className="fsp-search__field-grid">
              <div className="fsp-search__wide-cell">
                <ComboBox
                  id="report-select"
                  titleText="Report"
                  placeholder="Search by number or title"
                  items={reports ?? []}
                  itemToString={(r: ReportDefinition | null) => (r ? `${r.id} — ${r.title}` : '')}
                  selectedItem={selected}
                  onChange={({ selectedItem }: { selectedItem?: ReportDefinition | null }) => {
                    setSelected(selectedItem ?? null);
                    setValues({});
                  }}
                />
              </div>
              {selected && prompts.length > 0 && (
                <p className="fsp-search__group-heading">Prompts</p>
              )}
              {selected &&
                prompts.map((p) => (
                  <TextInput
                    key={p}
                    id={`report-${p}`}
                    labelText={humanize(p)}
                    helperText={p}
                    placeholder={isDatePrompt(p) ? 'YYYY-MM-DD' : undefined}
                    value={values[p] ?? ''}
                    onChange={(ev) => setValues((v) => ({ ...v, [p]: ev.target.value }))}
                  />
                ))}
              {selected && (
                <div className="fsp-search__sort-cell">
                  <RadioButtonGroup
                    legendText="Format"
                    name="report-format"
                    valueSelected={format}
                    onChange={(v) => setFormat(v === 'CSV' ? 'CSV' : 'PDF')}
                  >
                    <RadioButton id="fmt-pdf" labelText="PDF" value="PDF" />
                    <RadioButton id="fmt-csv" labelText="CSV" value="CSV" />
                  </RadioButtonGroup>
                </div>
              )}
            </div>
            <div className="detail-edit__actions">
              <Button
                type="submit"
                size="md"
                renderIcon={running ? RunningIcon : DocumentPdf}
                disabled={!selected || running}
              >
                {running ? 'Generating…' : 'Run report'}
              </Button>
            </div>
          </form>
        </SectionTile>
      </AsyncBoundary>
    </PageLayout>
  );
};

const RunningIcon = () => <Loading small withOverlay={false} description="" />;

export default ReportsPage;
