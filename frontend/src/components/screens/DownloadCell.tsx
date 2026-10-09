import { Button } from '@carbon/react';
import { Download } from '@carbon/icons-react';
import { useState, type FC } from 'react';

import { useNotification } from '@/context/notification/useNotification';
import { getBlob } from '@/services/common';
import { triggerBrowserDownload } from '@/utils/download';

/**
 * Authenticated file download for a 'download' column — a plain <a href>
 * would not carry the Keycloak bearer token. The backend returns API paths
 * (/api/v1/hbs/...); apiFetch prepends the /api base, so strip it here.
 */
export const DownloadCell: FC<{ url: unknown; label?: string }> = ({ url, label }) => {
  const { display } = useNotification();
  const [busy, setBusy] = useState(false);
  if (typeof url !== 'string' || !url) return <>—</>;
  const path = url.replace(/^\/api(?=\/)/, '');
  const ext = (/\.(\w+)$/.exec(path)?.[1] ?? 'file').toUpperCase();
  const onClick = async (e: React.MouseEvent) => {
    e.stopPropagation();
    setBusy(true);
    try {
      const { blob, filename } = await getBlob(path, 'Download');
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

export default DownloadCell;
