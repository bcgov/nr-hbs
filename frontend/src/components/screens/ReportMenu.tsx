import { Button, Loading, Select, SelectItem } from '@carbon/react';
import { DocumentPdf } from '@carbon/icons-react';
import { useState, type FC } from 'react';

import type { ReportLinkDef } from '@/screens/types';

import { useRunReport } from './ReportButton';
import '@/components/ExportCsvButton/ExportCsvButton.css';

interface ReportMenuProps {
  reports: ReportLinkDef[];
  source: Record<string, unknown>;
  /** Id for the select (unique on the page). */
  id: string;
}

/** The labels say "Print …" as buttons; in the list the verb is on the button. */
const optionLabel = (label: string) => label.replace(/^Print\s+/i, '');

/**
 * Several reports offered as one dropdown and a single Print button, for
 * result banners where a button per report would crowd out the rest
 * (SearchScreenDef.reportsAsMenu). Same spinner as ReportButton while it runs.
 */
export const ReportMenu: FC<ReportMenuProps> = ({ reports, source, id }) => {
  const { busy, run } = useRunReport();
  const [selected, setSelected] = useState(0);
  if (reports.length === 0) return null;
  const report = reports[Math.min(selected, reports.length - 1)];
  return (
    <span className="hbs-report-menu export-csv">
      <Select
        id={id}
        labelText="Report to print"
        hideLabel
        size="sm"
        value={String(Math.min(selected, reports.length - 1))}
        onChange={(e) => setSelected(Number(e.target.value))}
        disabled={busy}
      >
        {reports.map((r, i) => (
          <SelectItem key={r.reportId + r.label} value={String(i)} text={optionLabel(r.label)} />
        ))}
      </Select>
      <Button kind="ghost" size="sm" renderIcon={DocumentPdf} disabled={busy} onClick={() => void run(report, source)}>
        Print
      </Button>
      {busy && (
        <span className="export-csv__spinner" role="status" aria-live="polite">
          <Loading small withOverlay={false} description={`Generating ${optionLabel(report.label)}`} />
        </span>
      )}
    </span>
  );
};

export default ReportMenu;
