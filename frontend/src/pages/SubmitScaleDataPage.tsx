import { Button, InlineNotification, Loading } from '@carbon/react';
import { ArrowRight, DocumentImport, Upload, WarningAlt } from '@carbon/icons-react';
import { useState, type FC } from 'react';
import { useNavigate } from 'react-router-dom';

import DragDropFileInput from '@/components/DragDropFileInput';
import SectionTile from '@/components/SectionTile';
import { useScopedUser } from '@/context/org/useOrg';
import { can } from '@/routes/access';
import { apiFetch, readErrorMessage } from '@/services/apiFetch';
import { HBS_API } from '@/services/common';

import ForbiddenPage from './ForbiddenPage';
import PageLayout from './PageLayout';
import '@/components/screens/screens.scss';

interface SubmissionResult {
  transmissionId: number;
  fileName: string;
  errors: string[];
}

/**
 * P505 / P506 — Submit File of Detail Returns. Uploads one XML file of detail
 * scale data (log tallies, weigh slips, sample tallies, ledgers, SFP); the
 * backend virus-scans it, validates it against HBS_Schema_V6_1b, records the
 * transmission and queues it for the intake batch. Processing is asynchronous
 * — progress is visible on "Search for Detail Scale Transmissions".
 */
const SubmitScaleDataPage: FC = () => {
  const user = useScopedUser();
  const navigate = useNavigate();
  const [file, setFile] = useState<File | null>(null);
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState<SubmissionResult | null>(null);
  const [error, setError] = useState<string | null>(null);

  if (!can(user, 'XML_SUBMIT')) return <ForbiddenPage />;

  const onSubmit = async () => {
    if (!file) return;
    setBusy(true);
    setError(null);
    setResult(null);
    try {
      const form = new FormData();
      form.append('file', file);
      const res = await apiFetch(`${HBS_API}/submissions`, { method: 'POST', body: form });
      if (res.ok || res.status === 422) {
        const body = (await res.json()) as SubmissionResult;
        setResult(body);
        if (res.ok) setFile(null);
      } else {
        setError((await readErrorMessage(res)) || `Submission failed (${res.status})`);
      }
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <PageLayout
      title="Submit File of Detail Returns"
      subtitle="Send an XML file of detail scale returns to HBS. Files must conform to the HBS scale data schema (HBS_Schema_V6_1b). · Legacy screen P505/P506"
    >
      <SectionTile
        title="Detail return file"
        icon={DocumentImport}
        description="Select the XML file, then submit it. It is virus-scanned and checked against the schema before it is accepted."
      >
        <div className="hbs-form">
          <DragDropFileInput
            label="Find File"
            helperText="XML only"
            accept={['.xml', 'text/xml', 'application/xml']}
            file={file}
            disabled={busy}
            onSelect={(f) => {
              setFile(f);
              setResult(null);
            }}
            onRemove={() => setFile(null)}
          />
          <div className="detail-edit__actions">
            <Button
              size="md"
              renderIcon={busy ? SubmittingIcon : Upload}
              disabled={!file || busy}
              onClick={() => void onSubmit()}
            >
              {busy ? 'Submitting…' : 'Submit'}
            </Button>
          </div>
        </div>
      </SectionTile>

      {error && (
        <InlineNotification kind="error" lowContrast title="Submission failed" subtitle={error} className="hbs-screen__notes" />
      )}

      {result && result.errors.length === 0 && (
        <InlineNotification
          kind="success"
          lowContrast
          hideCloseButton
          title="Detail return file received"
          subtitle={`${result.fileName} was received as transmission ${result.transmissionId}. It will be unpacked and edited by the next intake run.`}
          className="hbs-screen__notes"
        >
          <Button
            kind="ghost"
            size="sm"
            renderIcon={ArrowRight}
            onClick={() => navigate('/scale-returns/xml-transmissions')}
          >
            Search for Detail Scale Transmissions
          </Button>
        </InlineNotification>
      )}
      {result && result.errors.length > 0 && (
        <SectionTile
          title="The file failed schema validation"
          icon={WarningAlt}
          description={`${result.errors.length} problem${result.errors.length === 1 ? '' : 's'} found. Correct the file and submit it again.`}
        >
          <ul className="hbs-submit__errors">
            {result.errors.map((e, i) => (
              <li key={i}>{e}</li>
            ))}
          </ul>
        </SectionTile>
      )}
    </PageLayout>
  );
};

const SubmittingIcon = () => <Loading small withOverlay={false} description="" />;

export default SubmitScaleDataPage;
