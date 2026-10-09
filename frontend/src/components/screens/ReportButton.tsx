import { Button, Loading } from '@carbon/react';
import { DocumentPdf } from '@carbon/icons-react';
import { useState, type FC } from 'react';

import { useNotification } from '@/context/notification/useNotification';
import { runReport } from '@/services/screens';
import type { ReportLinkDef } from '@/screens/types';
import { triggerBrowserDownload } from '@/utils/download';

import { reportParams } from './links';
import '@/components/ExportCsvButton/ExportCsvButton.css';

interface ReportButtonProps {
  report: ReportLinkDef;
  source: Record<string, unknown>;
  /** Small ghost in a search's results banner (beside the CSV export); tertiary on a record. */
  size?: 'sm' | 'md';
  kind?: 'ghost' | 'tertiary';
}

/**
 * Runs a legacy Jasper report for the current criteria / record and downloads
 * it. While it generates, a spinner sits beside the button and the button
 * stays disabled — the same treatment as the CSV export.
 */
export const ReportButton: FC<ReportButtonProps> = ({ report, source, size = 'md', kind = 'tertiary' }) => {
  const { display } = useNotification();
  const [busy, setBusy] = useState(false);
  const format = report.format ?? 'PDF';
  const onClick = async () => {
    setBusy(true);
    try {
      const { blob, filename } = await runReport(report.reportId, reportParams(report, source), format);
      triggerBrowserDownload(blob, filename ?? `${report.reportId}.${format.toLowerCase()}`);
    } catch (e) {
      display({ kind: 'error', title: 'Report failed', subtitle: (e as Error).message, timeout: 7000 });
    } finally {
      setBusy(false);
    }
  };
  return (
    <span className="export-csv">
      <Button kind={kind} size={size} renderIcon={DocumentPdf} disabled={busy} onClick={() => void onClick()}>
        {report.label}
      </Button>
      {busy && (
        <span className="export-csv__spinner" role="status" aria-live="polite">
          <Loading small withOverlay={false} description={`Generating ${report.label}`} />
        </span>
      )}
    </span>
  );
};

export default ReportButton;
