import { Button, Loading } from '@carbon/react';
import { DocumentPdf } from '@carbon/icons-react';
import { useState, type FC } from 'react';

import { Modal } from '@/components/Modal';
import { useNotification } from '@/context/notification/useNotification';
import { useSessionState } from '@/hooks/useSessionState';
import { runReport } from '@/services/screens';
import type { ReportDialogDef, ReportLinkDef } from '@/screens/types';
import { triggerBrowserDownload } from '@/utils/download';

import FieldGrid from './FieldGrid';
import { reportFileName, reportParams } from './links';

interface ReportDialogButtonProps {
  dialog: ReportDialogDef;
  /** The search criteria; the modal's fields are added on top. */
  source: Record<string, unknown>;
  /** Session key for the modal's values. */
  storageKey: string;
}

/**
 * A button that opens a modal of report-only prompts (P403's estimated average
 * rates) with a button per report. The values stay for the session, so closing
 * and re-opening the modal keeps them; a report closes the modal once downloaded.
 */
export const ReportDialogButton: FC<ReportDialogButtonProps> = ({ dialog, source, storageKey }) => {
  const { display } = useNotification();
  const [open, setOpen] = useState(false);
  const [values, setValues] = useSessionState<Record<string, string>>(storageKey, {});
  const [busy, setBusy] = useState<string | null>(null);

  const run = async (report: ReportLinkDef) => {
    setBusy(report.reportId);
    const merged = { ...source, ...values };
    try {
      const format = report.format ?? 'PDF';
      const { blob, filename } = await runReport(report.reportId, reportParams(report, merged), format);
      const ext = format.toLowerCase();
      triggerBrowserDownload(blob, reportFileName(report, merged, ext) ?? filename ?? `${report.reportId}.${ext}`);
      setOpen(false);
    } catch (e) {
      display({ kind: 'error', title: 'Report failed', subtitle: (e as Error).message, timeout: 7000 });
    } finally {
      setBusy(null);
    }
  };

  return (
    <>
      <Button kind="ghost" size="sm" renderIcon={DocumentPdf} onClick={() => setOpen(true)}>
        {dialog.label}
      </Button>
      <Modal
        open={open}
        modalHeading={dialog.heading}
        passiveModal
        size="sm"
        className="detail-dialog"
        onRequestClose={() => busy === null && setOpen(false)}
        preventCloseOnClickOutside
      >
        <div className="detail-dialog__body">
          <FieldGrid
            fields={dialog.fields}
            values={values}
            onChange={(n, v) => setValues((p) => ({ ...p, [n]: v }))}
            idPrefix={`${storageKey}-dialog`}
            mode="form"
          />
        </div>
        <div className="detail-dialog__actions">
          <Button kind="tertiary" disabled={busy !== null} onClick={() => setOpen(false)}>
            Cancel
          </Button>
          {dialog.reports.map((r) => (
            <Button
              key={r.reportId + r.label}
              kind="primary"
              disabled={busy !== null}
              renderIcon={busy === r.reportId ? BusyIcon : undefined}
              onClick={() => void run(r)}
            >
              {r.label}
            </Button>
          ))}
        </div>
      </Modal>
    </>
  );
};

// Same Carbon spinner shape as the other dialog buttons.
const BusyIcon = () => <Loading small withOverlay={false} description="" />;

export default ReportDialogButton;
