import type { FC } from 'react';

import SectionTile from '@/components/SectionTile';
import { useTheme } from '@/context/theme/useTheme';
import PageLayout from '@/pages/PageLayout';

/**
 * "Invoice Information - Explanation of Symbols" — the reference page the
 * statement e-mails link to (legacy batch property `invoice_general_info`,
 * `EmailTranmissionDelivery` template argument {3}). Ported from the static
 * legacy page `hbs-web/src/main/webapp/hbs/opq/cis/invoiceGeneralInformation.html`;
 * public, like the legacy page: it is reachable without signing in.
 *
 * URL to configure in the legacy batch (`cfg_b_general_info`):
 * `<new app base URL>/invoice-information`.
 */

type Code = [code: string, description: string];

const KIND_OF_SCALE: Code[] = [
  ['M', 'Metric'],
  ['E', 'Pieces'],
];

const COAST_GRADES: Code[] = [
  ['B', 'No. 2 Peeler Fir'],
  ['C', 'No. 3 Peeler Fir'],
  ['D', 'No. 1 Lumber'],
  ['D', 'No. 1 Premium Spruce'],
  ['E', 'No. 2 Premium Spruce'],
  ['F', 'No. 1 Lumber Spruce'],
  ['F', 'No. 2 Lumber'],
  ['G', 'No. 2 Lumber Spruce'],
  ['H', 'No. 2 Sawlog'],
  ['I', 'No. 3 Sawlog'],
  ['J', 'No. 4 Sawlog'],
  ['K', 'No. 1 Shingle Cedar'],
  ['K', 'No. 2 Sawlog Cypress (stainfree)'],
  ['L', 'No. 2 Shingle Cedar'],
  ['L', 'No. 3 Sawlog Cypress (stainfree)'],
  ['M', 'No. 3 Shingle Cedar'],
  ['M', 'No. 4 Sawlog Cypress (stainfree)'],
  ['U', 'No. 5 Utility'],
  ['X', 'No. 6 Utility'],
  ['Y', 'No. 7 Wood Chipper'],
  ['Z', 'Firmwood Reject'],
];

const INTERIOR_GRADES: Code[] = [
  ['(blank)', 'Sawlog'],
  ['3', 'Dead and Dry Sawlog'],
  ['4', 'Lumber Reject'],
  ['5', 'Dead and Dry Lumber Reject'],
  ['6', 'Undersized Log Grade'],
  ['Z', 'Firmwood Reject'],
];

const RATE: Code[] = [
  ['M', 'Per cubic metres'],
  ['E', 'Per piece'],
];

const KIND_OF_CHARGE: Code[] = [
  ['E', 'Scaling Expenses'],
  ['S', 'Stumpage'],
  ['T', 'Time Charge'],
  ['X', 'Fee in lieu of manufacture'],
];

const CodeTable: FC<{ caption: string; rows: Code[] }> = ({ caption, rows }) => (
  <table className="cds--data-table cds--data-table--sm" style={{ marginBottom: '1rem', maxWidth: '40rem' }}>
    <caption className="cds--visually-hidden">{caption}</caption>
    <thead>
      <tr>
        <th scope="col" style={{ width: '6rem' }}>Code</th>
        <th scope="col">Description</th>
      </tr>
    </thead>
    <tbody>
      {rows.map(([code, description]) => (
        <tr key={code + description}>
          <td>
            <strong>{code}</strong>
          </td>
          <td>{description}</td>
        </tr>
      ))}
    </tbody>
  </table>
);

const Content: FC = () => (
  <PageLayout
    title="Invoice Information - Explanation of Symbols"
    subtitle="Codes used on Harvest Billing System invoices and statements"
  >
    <SectionTile title="Kind of Scale">
      <CodeTable caption="Kind of scale" rows={KIND_OF_SCALE} />
    </SectionTile>
    <SectionTile title="Grade">
      <h3 className="cds--type-heading-compact-01" style={{ margin: '0.5rem 0' }}>
        Coast Timber Grade Codes
      </h3>
      <CodeTable caption="Coast timber grade codes" rows={COAST_GRADES} />
      <h3 className="cds--type-heading-compact-01" style={{ margin: '0.5rem 0' }}>
        Interior Timber Grade Codes
      </h3>
      <CodeTable caption="Interior timber grade codes" rows={INTERIOR_GRADES} />
    </SectionTile>
    <SectionTile
      title="Rate"
      description="Unless otherwise specified in descriptive notations, rate is based as follows:"
    >
      <CodeTable caption="Rate" rows={RATE} />
    </SectionTile>
    <SectionTile
      title="Kind of Charge"
      description="Unless otherwise specified in descriptive notations, rate is based as follows:"
    >
      <CodeTable caption="Kind of charge" rows={KIND_OF_CHARGE} />
    </SectionTile>
    <SectionTile title="Others">
      <p>Y.T.D. - Sampling year to date (Weight Scale Only)</p>
    </SectionTile>
  </PageLayout>
);

/**
 * @param standalone render without the signed-in shell (anonymous visitors,
 *   users without an HBS role, the organization gate) — just the BC logo above.
 */
const InvoiceInformationPage: FC<{ standalone?: boolean }> = ({ standalone }) => {
  const { theme } = useTheme();
  if (!standalone) return <Content />;
  const logoSrc = theme === 'g100' ? '/bc-gov-logo-rev.png' : '/bc-gov-logo.png';
  return (
    <div style={{ padding: '2rem', maxWidth: '72rem', margin: '0 auto' }}>
      <img src={logoSrc} alt="BC Government" width={160} style={{ marginBottom: '1.5rem' }} />
      <Content />
    </div>
  );
};

export default InvoiceInformationPage;
