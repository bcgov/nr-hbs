import { Button } from '@carbon/react';
import { Download } from '@carbon/icons-react';
import { useState, type FC } from 'react';

import { useNotification } from '@/context/notification/useNotification';
import type { ColumnDef } from '@/screens/types';
import { triggerBrowserDownload } from '@/utils/download';

import { useScreenApi } from './screenApi';

/**
 * File download for a 'download' column — a plain <a href> would not carry
 * the Keycloak bearer token (the public area's ScreenApi fetches without one). The backend returns API paths
 * (/api/v1/hbs/...); apiFetch prepends the /api base, so strip it here.
 */
export const DownloadCell: FC<{ url: unknown; label?: string }> = ({ url, label }) => {
  const { display } = useNotification();
  const api = useScreenApi();
  const [busy, setBusy] = useState(false);
  if (typeof url !== 'string' || !url) return <>—</>;
  const path = url.replace(/^\/api(?=\/)/, '');
  const ext = (/\.(\w+)$/.exec(path)?.[1] ?? 'file').toUpperCase();
  const onClick = async (e: React.MouseEvent) => {
    e.stopPropagation();
    setBusy(true);
    try {
      const { blob, filename } = await api.download(path, 'Download');
      triggerBrowserDownload(blob, filename ?? path.split('/').pop() ?? 'download');
    } catch (err) {
      display({ kind: 'error', title: 'Download failed', subtitle: (err as Error).message, timeout: 6000 });
    } finally {
      setBusy(false);
    }
  };
  return (
    <Button kind="ghost" size="sm" renderIcon={Download} disabled={busy} onClick={onClick}>
      {label ?? ext}
    </Button>
  );
};

/** A 'download' column's cell: one button, or one per file of a multi-file ("Actions") column. */
export const DownloadColumnCell: FC<{ column: ColumnDef; row: Record<string, unknown> }> = ({ column, row }) => {
  if (!column.downloads) return <DownloadCell url={row[column.key]} />;
  const files = column.downloads.filter((d) => typeof row[d.key] === 'string' && row[d.key]);
  if (files.length === 0) return <>—</>;
  return (
    <span className="hbs-download-actions">
      {files.map((d) => (
        <DownloadCell key={d.key} url={row[d.key]} label={d.label} />
      ))}
    </span>
  );
};

export default DownloadCell;
