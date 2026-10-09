import { Download } from '@carbon/icons-react';
import { Button, Loading } from '@carbon/react';
import { useState, type FC } from 'react';

import { useNotification } from '@/context/notification/useNotification';
import { triggerBrowserDownload } from '@/utils/download';

import './ExportCsvButton.css';

interface ExportCsvButtonProps {
  /** Fetches the export; resolves to the file and its server-given name. */
  onExport: () => Promise<{ blob: Blob; filename?: string | null }>;
  /** Used when the server sends no file name. */
  fallbackName: string;
  /** Disabled while a search is in flight, or when there is nothing to export. */
  disabled?: boolean;
}

/**
 * Exports the current search results to CSV — nr-fta's ExportCsvButton.
 *
 * Every matching row is exported, not just the page on screen, so a large
 * result set can take a while. A spinner appears beside the button for the
 * duration, and the button stays disabled until the file arrives — a second
 * click would run the whole query again.
 */
const ExportCsvButton: FC<ExportCsvButtonProps> = ({ onExport, fallbackName, disabled }) => {
  const [exporting, setExporting] = useState(false);
  const { display } = useNotification();

  const run = async () => {
    setExporting(true);
    try {
      const { blob, filename } = await onExport();
      triggerBrowserDownload(blob, filename ?? fallbackName);
    } catch (e) {
      display({
        kind: 'error',
        title: 'Export failed',
        subtitle: (e as Error).message || 'The results could not be exported.',
        timeout: 7000,
      });
    } finally {
      setExporting(false);
    }
  };

  return (
    <span className="export-csv">
      <Button
        kind="ghost"
        size="sm"
        renderIcon={Download}
        disabled={disabled || exporting}
        onClick={() => void run()}
      >
        Export results to CSV
      </Button>
      {exporting && (
        <span className="export-csv__spinner" role="status" aria-live="polite">
          <Loading small withOverlay={false} description="Preparing CSV export" />
        </span>
      )}
    </span>
  );
};

export default ExportCsvButton;
