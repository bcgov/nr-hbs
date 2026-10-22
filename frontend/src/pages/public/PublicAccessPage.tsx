import { UserFollow } from '@carbon/icons-react';
import type { FC } from 'react';
import { Link } from 'react-router-dom';

import SectionTile from '@/components/SectionTile';
import PageLayout from '@/pages/PageLayout';

/**
 * Replaces the legacy public "Register" link (uss/public/restrictedClientAdminVer.jsp
 * → /uss/Enrollment: "on-line HBS enrolment and client verification" with a
 * client number/location and Registration Key, ending in an HBS_USER_REQUEST
 * that a ministry user administrator approved). HBS no longer keeps its own
 * user accounts: people log in with IDIR or Business BCeID, and HBS roles are
 * granted in FAM (docs/roles-and-security.md), so there is nothing to register
 * here — this page says how to get access instead.
 */
const SUPPORT_EMAIL = 'FORHVAP.HBSHELP@gov.bc.ca';

const PublicAccessPage: FC = () => (
  <PageLayout
    title="How to get access"
    subtitle="HBS enrolment and client verification"
    backLink={
      <Link to="/public" className="back-link">
        Back to Public Queries
      </Link>
    }
  >
    <SectionTile title="Getting access to the Harvest Billing System" icon={UserFollow}>
      <div className="hbs-public__access">
        <p>
          The public queries (harvest reports, invoice and statement copies, timber mark information and the stratum
          advisor) need no login. Everything else — scale returns, XML submissions, sampling plan administration,
          billing and profiles — needs an HBS account.
        </p>

        <h3>Industry users (licensees, scale sites, submission agents, software vendors)</h3>
        <ol>
          <li>
            Get a <strong>Business BCeID</strong> for your organization if you do not have one (bceid.ca).
          </li>
          <li>
            Ask your organization&apos;s HBS user administrator (the person who used to verify your client with the
            Registration Key) to grant you HBS roles for your client number. If your organization has no
            administrator yet, e-mail the HBS help desk with:
            <ul>
              <li>your name, e-mail address and phone number;</li>
              <li>your Business BCeID user id;</li>
              <li>your Client No/Loc (8-digit client number and 2-digit location);</li>
              <li>the role(s) you need, e.g. Industry User, Industry Scaler, Industry Site Administrator, Industry
                Document Receiver, Data Submission Agent or Software Vendor.</li>
            </ul>
          </li>
          <li>Once the roles are granted, log in with Business BCeID.</li>
        </ol>

        <h3>Ministry staff</h3>
        <ol>
          <li>Log in with your IDIR.</li>
          <li>
            Ask your HBS Ministry User Administrator, or e-mail the help desk with your IDIR user id and the role(s)
            you need.
          </li>
        </ol>

        <p>
          HBS help desk: <a href={`mailto:${SUPPORT_EMAIL}`}>{SUPPORT_EMAIL}</a>
        </p>
        <p>
          <Link to="/">Log in to HBS</Link>
        </p>
      </div>
    </SectionTile>
  </PageLayout>
);

export default PublicAccessPage;
