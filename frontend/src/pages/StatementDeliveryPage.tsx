import { Download, Renew } from '@carbon/icons-react';
import { Button, InlineLoading, InlineNotification } from '@carbon/react';
import { useCallback, useEffect, useMemo, useRef, useState, type FC } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';

import SectionTile from '@/components/SectionTile';
import { useOrg, useScopedUser } from '@/context/org/useOrg';
import ForbiddenPage from '@/pages/ForbiddenPage';
import PageLayout from '@/pages/PageLayout';
import { can, type Capability } from '@/routes/access';
import { clearReturnTo, rememberReturnTo } from '@/routes/returnTo';
import { getBlob, HBS_API } from '@/services/common';
import { triggerBrowserDownload } from '@/utils/download';

/**
 * Landing page for the legacy StatementDelivery links (legacy servlet
 * `presentation/dad/StatementServlet`):
 *
 * - `/StatementDelivery/transmissions/<id>.pdf|.xml` — the links in the "Your HBS
 *   statements are ready" e-mails that legacy batch B2065 still sends
 *   (`EmailTranmissionDelivery`, base URL `statement.delivery.webapp.url`);
 *   a ministry user may add `?clientNumber=` as in legacy.
 * - `/StatementDelivery/statements/<type>/<number>.pdf|.xml` — one issued
 *   invoice/statement.
 *
 * The route sits behind the normal sign-in / organization gates (App.tsx keeps
 * the link across them, routes/returnTo.ts). The file is fetched with the
 * bearer token and handed to the browser as a download.
 */

const STATEMENT_TYPES: Record<string, string> = {
  PSI: 'Piece Scale Invoice',
  PSV: 'Piece Scale Volume Statement',
  WSI: 'Weight Scale Invoice',
  WSV: 'Weight Scale Volume Statement',
  CSS: 'Compiled Sample Statement',
  RS: 'Ratio Statement',
};

interface Target {
  apiPath: string;
  filename: string;
  title: string;
  capability: Capability;
}

function resolveTarget(
  kind: 'transmissions' | 'statements',
  type: string | undefined,
  file: string | undefined,
  search: string,
): Target | null {
  const m = /^([A-Za-z0-9]{1,32})\.(pdf|xml)$/i.exec(file ?? '');
  if (!m) return null;
  const [, id, ext] = m;
  const format = ext.toUpperCase();
  if (kind === 'transmissions') {
    if (!/^\d+$/.test(id)) return null;
    const client = new URLSearchParams(search).get('clientNumber');
    const query = client && /^\d{8}$/.test(client) ? `?clientNumber=${client}` : '';
    return {
      apiPath: `${HBS_API}/statements/transmissions/${id}.${ext.toLowerCase()}${query}`,
      filename: `${id}.${ext.toLowerCase()}`,
      title: `Statement transmission ${id} (${format})`,
      capability: 'ANY_USER',
    };
  }
  const code = (type ?? '').toUpperCase();
  if (!STATEMENT_TYPES[code]) return null;
  return {
    apiPath: `${HBS_API}/statements/${code}/${id.toUpperCase()}.${ext.toLowerCase()}`,
    filename: `${id.toUpperCase()}.${ext.toLowerCase()}`,
    title: `${STATEMENT_TYPES[code]} ${id.toUpperCase()} (${format})`,
    capability: 'QUERIES_VIEW',
  };
}

const ACTIONS = { display: 'flex', gap: '1rem', marginTop: '1rem' } as const;

type Status =
  | { state: 'loading' }
  | { state: 'done'; blob: Blob; filename: string }
  | { state: 'error'; message: string };

const StatementDeliveryPage: FC<{ kind: 'transmissions' | 'statements' }> = ({ kind }) => {
  const { type, file } = useParams();
  const { pathname, search } = useLocation();
  const navigate = useNavigate();
  const user = useScopedUser();
  const { availableOrgClientNumbers } = useOrg();
  const target = useMemo(() => resolveTarget(kind, type, file, search), [kind, type, file, search]);
  const allowed = !!target && can(user, target.capability);
  const [status, setStatus] = useState<Status>({ state: 'loading' });
  const started = useRef<string | null>(null);

  const fetchFile = useCallback(async () => {
    if (!target) return;
    setStatus({ state: 'loading' });
    try {
      const { blob, filename } = await getBlob(target.apiPath, 'Download');
      const name = filename ?? target.filename;
      triggerBrowserDownload(blob, name);
      setStatus({ state: 'done', blob, filename: name });
    } catch (e) {
      setStatus({ state: 'error', message: (e as Error).message || 'The file could not be downloaded.' });
    }
  }, [target]);

  // The link has been followed: it no longer needs to survive a sign-in.
  useEffect(() => clearReturnTo(), []);

  // Start the download once per link (StrictMode runs effects twice).
  useEffect(() => {
    if (!target || !allowed || started.current === target.apiPath) return;
    started.current = target.apiPath;
    void fetchFile();
  }, [target, allowed, fetchFile]);

  if (target && !allowed) return <ForbiddenPage />;

  const otherOrgs = availableOrgClientNumbers.length > 1;

  return (
    <PageLayout
      title="Statement Delivery"
      subtitle={target ? target.title : 'Invoices, statements and statement transmissions'}
    >
      <SectionTile title={target ? target.filename : 'Download'}>
        {!target && (
          <InlineNotification
            kind="error"
            lowContrast
            hideCloseButton
            title="Invalid link"
            subtitle="This statement link is not valid. Check that the whole link from the e-mail was used."
          />
        )}

        {target && status.state === 'loading' && (
          <InlineLoading description={`Preparing ${target.filename}…`} status="active" />
        )}

        {target && status.state === 'done' && (
          <>
            <InlineNotification
              kind="success"
              lowContrast
              hideCloseButton
              title="Download started"
              subtitle={`${status.filename} has been sent to your browser's downloads.`}
            />
            <div style={ACTIONS}>
              <Button
                kind="tertiary"
                size="md"
                renderIcon={Download}
                onClick={() => triggerBrowserDownload(status.blob, status.filename)}
              >
                Download again
              </Button>
            </div>
          </>
        )}

        {target && status.state === 'error' && (
          <>
            <InlineNotification
              kind="error"
              lowContrast
              hideCloseButton
              title="The file could not be downloaded"
              subtitle={
                otherOrgs
                  ? `${status.message} If you work for more than one client, switch to the client the statements were sent to and try again.`
                  : status.message
              }
            />
            <div style={ACTIONS}>
              <Button kind="primary" size="md" renderIcon={Renew} onClick={() => void fetchFile()}>
                Try again
              </Button>
              {otherOrgs && (
                <Button
                  kind="tertiary"
                  size="md"
                  onClick={() => {
                    // Come back to this link once the other client is picked.
                    rememberReturnTo(pathname + search);
                    navigate('/org-select');
                  }}
                >
                  Switch client
                </Button>
              )}
            </div>
          </>
        )}
      </SectionTile>
    </PageLayout>
  );
};

export default StatementDeliveryPage;
