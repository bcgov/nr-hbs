import { Button } from '@carbon/react';
import { Download, Password } from '@carbon/icons-react';
import { useState, type FC } from 'react';

import SectionTile from '@/components/SectionTile';
import { useNotification } from '@/context/notification/useNotification';
import { useScopedUser } from '@/context/org/useOrg';
import { can } from '@/routes/access';
import { getBlob, HBS_API } from '@/services/common';
import { triggerBrowserDownload } from '@/utils/download';

import ForbiddenPage from './ForbiddenPage';
import PageLayout from './PageLayout';

/**
 * P002 "Scale Return Menu" › HBS Public Key › Download HBS Public Key (legacy
 * DownloadPublicKeyServlet, MR28). Any signed-in HBS user may download
 * HBS_Public_Key.xml — the RSA public keys scale software uses to encrypt the
 * scaler signature of each submitted document.
 */
const PublicKeyPage: FC = () => {
  const user = useScopedUser();
  const { display } = useNotification();
  const [busy, setBusy] = useState(false);

  if (!can(user, 'ANY_USER')) return <ForbiddenPage />;

  const download = async () => {
    setBusy(true);
    try {
      const { blob, filename } = await getBlob(`${HBS_API}/signature/public-key`, 'Public key download');
      triggerBrowserDownload(blob, filename ?? 'HBS_Public_Key.xml');
    } catch (e) {
      display({ kind: 'error', title: 'Download failed', subtitle: (e as Error).message, timeout: 7000 });
    } finally {
      setBusy(false);
    }
  };

  return (
    <PageLayout title="HBS Public Key" subtitle="The public keys scale software uses to sign detail scale data.">
      <SectionTile
        title="Download HBS Public Key"
        icon={Password}
        description="HBS_Public_Key.xml (schema HBS_Public_Key.xsd): each key's identifier, signing date range, modulus and exponent."
      >
        <Button size="md" renderIcon={Download} disabled={busy} onClick={() => void download()}>
          {busy ? 'Downloading…' : 'Download Harvest Billing System Public Key'}
        </Button>
      </SectionTile>
    </PageLayout>
  );
};

export default PublicKeyPage;
