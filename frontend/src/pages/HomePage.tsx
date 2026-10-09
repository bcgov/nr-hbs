import { ClickableTile, InlineNotification } from '@carbon/react';
import { Apps, ListChecked } from '@carbon/icons-react';
import { useEffect, useState, type FC } from 'react';
import { Link, useNavigate } from 'react-router-dom';

import SectionTile from '@/components/SectionTile';
import { useAuth } from '@/context/auth/useAuth';
import { useScopedUser } from '@/context/org/useOrg';
import type { ROLE_TYPE } from '@/context/auth/types';
import { originState } from '@/lib/navOrigin';
import { can } from '@/routes/access';
import { AREA_ICONS } from '@/routes/routePaths';
import { AREAS, SCREENS_BY_ID, screenPath, type AreaDef } from '@/screens/registry';
import { listQuery } from '@/services/screens';

import PageLayout from './PageLayout';
import './HomePage.scss';

/**
 * Home — the legacy home.jsp work-queue dashboard (mainmenu_ministry /
 * mainmenu_industry / mainmenu_spcuser). Each block is shown only to the
 * roles that saw it in the legacy menu; each link deep-links into a registry
 * search screen with the same pre-filled criteria the legacy URL carried
 * (e.g. ?returnType=P&status=ERR). Links whose target screen isn't
 * registered render as plain text rather than breaking.
 */

interface HomeLink {
  label: string;
  screen: string;
  params?: Record<string, string>;
  /** Extra role guard for this single link (legacy nested rolePresent). */
  roles?: ROLE_TYPE[];
}

interface HomeRow {
  label: string;
  links: HomeLink[];
  roles?: ROLE_TYPE[];
}

interface HomeBlock {
  title: string;
  roles: ROLE_TYPE[];
  rows: HomeRow[];
}

const P_W_S = (screen: string, params: Record<string, string>): HomeLink[] => [
  { label: 'Piece', screen, params: { ...params, returnType: 'P' } },
  { label: 'Weight', screen, params: { ...params, returnType: 'W' } },
  { label: 'Sample', screen, params: { ...params, returnType: 'S' } },
];

const detailTypes = (
  screen: string,
  params: Record<string, string>,
  departureRoles: ROLE_TYPE[],
  withLedgers = true,
): HomeLink[] => [
  { label: 'Log Tallies', screen, params: { ...params, returnType: 'P' } },
  { label: 'Weigh Slips', screen, params: { ...params, returnType: 'W' } },
  { label: 'Sample Tallies', screen, params: { ...params, returnType: 'S' } },
  ...(withLedgers
    ? [
        { label: 'Arrival Ledgers', screen, params: { ...params, returnType: 'A' } },
        { label: 'Departure Ledgers', screen, params: { ...params, returnType: 'D' }, roles: departureRoles },
      ]
    : []),
];

const ANOMALY_LINKS: HomeRow[] = [
  {
    label: 'Gaps and Duplicates',
    links: [
      { label: 'Scaling Events', screen: 'scale-anomalies', params: { anomalyType: 'SCALING' } },
      { label: 'Weighing Events', screen: 'scale-anomalies', params: { anomalyType: 'WEIGHING' } },
      { label: 'Arrival Events', screen: 'scale-anomalies', params: { anomalyType: 'ARRIVAL' } },
    ],
  },
  {
    label: 'Mismatches',
    links: [
      { label: 'Arrivals without Departures', screen: 'scale-anomalies', params: { anomalyType: 'ARR_DEP' } },
      { label: 'Departures without Arrivals', screen: 'scale-anomalies', params: { anomalyType: 'DEP_ARR' } },
      { label: 'Weigh Slips without Samples', screen: 'scale-anomalies', params: { anomalyType: 'WEIGH_SAMPLE' } },
      { label: 'Weigh Slips without Red Tags', screen: 'scale-anomalies', params: { anomalyType: 'WEIGH_RED' } },
      { label: 'Red Tags without Weigh Slips', screen: 'scale-anomalies', params: { anomalyType: 'RED_WEIGH' } },
    ],
  },
];

const planLinks = (extra: Record<string, string>): HomeLink[] => [
  { label: 'Active', screen: 'sampling-plans', params: { ...extra, planStatus: 'ACT', currentYear: 'Y' } },
  { label: 'Awaiting Approval', screen: 'sampling-plans', params: { ...extra, planStatus: 'AWP' } },
  { label: 'In Progress', screen: 'sampling-plans', params: { ...extra, planStatus: 'PRO' } },
  { label: 'Rejected', screen: 'sampling-plans', params: { ...extra, planStatus: 'REJ', currentYear: 'Y' } },
];

const BLOCKS: HomeBlock[] = [
  // ── Ministry ────────────────────────────────────────────────
  {
    title: 'Submitted Summary Scale Return Status',
    roles: ['HBS_SUMM_DATA_CORR', 'HBS_BILL_ADMIN'],
    rows: [
      { label: 'In Error', links: P_W_S('summary-returns', { status: 'ERR', generated: 'N' }) },
      { label: 'Held', links: P_W_S('summary-returns', { status: 'HLD', generated: 'N' }) },
    ],
  },
  {
    title: 'Detail Scale Return Status',
    roles: ['HBS_SCALE_ADMIN', 'HBS_INV_CORR_APP', 'HBS_MOF_SCALER', 'HBS_CLI_SITE_ADMIN', 'HBS_CLI_SCALER'],
    rows: [
      {
        label: 'In Error',
        roles: ['HBS_SCALE_ADMIN', 'HBS_MOF_SCALER', 'HBS_CLI_SITE_ADMIN', 'HBS_CLI_SCALER'],
        links: detailTypes('detail-workbench', { status: 'ERR' }, ['HBS_SCALE_ADMIN', 'HBS_CLI_SITE_ADMIN']),
      },
      {
        label: 'Digital Signature Failure',
        roles: ['HBS_SCALE_ADMIN'],
        links: detailTypes('detail-workbench', { status: 'DSF' }, [], false),
      },
      {
        label: 'Corrections Requiring Approval',
        roles: ['HBS_INV_CORR_APP'],
        links: detailTypes('detail-workbench', { status: 'AWP' }, [], false),
      },
      {
        label: 'Replaced By Check Scale',
        roles: ['HBS_SCALE_ADMIN', 'HBS_CLI_SITE_ADMIN', 'HBS_CLI_SCALER'],
        links: [
          { label: 'Log Tallies', screen: 'detail-workbench', params: { returnType: 'P', mode: 'BY_CHECK_SCALE' } },
          { label: 'Sample Tallies', screen: 'detail-workbench', params: { returnType: 'S', mode: 'BY_CHECK_SCALE' } },
        ],
      },
      {
        label: 'Check Scale Replacements',
        roles: ['HBS_SCALE_ADMIN', 'HBS_MOF_SCALER', 'HBS_CLI_SITE_ADMIN'],
        links: [
          { label: 'Log Tallies', screen: 'detail-workbench', params: { returnType: 'P', mode: 'CS_REPLACEMENT' } },
          { label: 'Sample Tallies', screen: 'detail-workbench', params: { returnType: 'S', mode: 'CS_REPLACEMENT' } },
        ],
      },
    ],
  },
  {
    title: 'Error Categories',
    roles: ['HBS_MOF_SCALER', 'HBS_SCALE_ADMIN', 'HBS_CLI_SCALER', 'HBS_CLI_SITE_ADMIN'],
    rows: [
      {
        label: 'Industry Responsibility',
        links: detailTypes('detail-error-categories', { status: 'ERR', responsibility: 'I' }, ['HBS_SCALE_ADMIN', 'HBS_CLI_SITE_ADMIN']),
      },
      {
        label: 'Ministry Responsibility',
        links: detailTypes('detail-error-categories', { status: 'ERR', responsibility: 'M' }, ['HBS_SCALE_ADMIN', 'HBS_CLI_SITE_ADMIN']),
      },
    ],
  },
  {
    title: 'Data Submission Status',
    roles: ['HBS_DTL_DATA_ENT', 'HBS_MOF_SCALER', 'HBS_SCALE_ADMIN', 'HBS_CLI_SITE_ADMIN', 'HBS_CLI_SCALER', 'HBS_SPC_SUBM_AGNT'],
    rows: [
      { label: 'XML File Transmissions', links: [{ label: 'XML', screen: 'xml-transmissions' }] },
      {
        label: 'XML Batches',
        links: [
          ...detailTypes('detail-batches', {}, ['HBS_SCALE_ADMIN', 'HBS_CLI_SITE_ADMIN', 'HBS_SPC_SUBM_AGNT']),
          { label: 'SFP Tallies', screen: 'detail-batches', params: { returnType: 'F' }, roles: ['HBS_SPC_SUBM_AGNT'] },
        ],
      },
    ],
  },
  {
    title: 'Generated Summary Scale Return Status',
    roles: ['HBS_SUMM_DATA_CORR', 'HBS_BILL_ADMIN'],
    rows: [{ label: 'In Error', links: P_W_S('summary-returns', { status: 'ERR', generated: 'Y' }) }],
  },
  {
    title: 'Production Control',
    roles: ['HBS_PROD_CTL'],
    rows: [
      {
        label: 'Settings',
        links: [
          { label: 'HBS Alert and Processing Parameters', screen: 'processing-parameters' },
          { label: 'Anomaly Assessment Windows', screen: 'anomaly-windows' },
        ],
      },
    ],
  },
  {
    title: 'Sampling Plans',
    roles: ['HBS_SMP_ADMIN'],
    rows: [{ label: 'Plans', links: planLinks({ districtScope: 'ASSOCIATED' }) }],
  },
  {
    title: 'Sampling Plans',
    roles: ['HBS_CLI_SMP_ADMIN'],
    rows: [{ label: 'Plans', links: planLinks({}) }],
  },
  { title: 'Recent Anomalies', roles: ['HBS_SCALE_ADMIN', 'HBS_CLI_SITE_ADMIN'], rows: ANOMALY_LINKS },
  {
    title: 'Issued Statements',
    roles: ['HBS_CLI_DOC_RCVR'],
    rows: [
      {
        label: 'Transmissions',
        links: [{ label: 'Recent Transmissions of Issued Statements', screen: 'statement-transmissions', params: { recent: 'Y' } }],
      },
    ],
  },
  {
    title: 'Scale Control',
    roles: ['HBS_SPC_SFTWR_VNDR'],
    rows: [{ label: 'Scale Site Software', links: [{ label: 'Site Software Use', screen: 'software-use' }] }],
  },
  {
    title: 'User Services',
    roles: ['HBS_CLI_ADMIN', 'HBS_SPC_ADMIN', 'HBS_MOF_ADMIN'],
    rows: [{ label: 'Users', links: [{ label: 'User Data Domains', screen: 'user-data-domains' }] }],
  },
];

const hasAny = (userRoles: readonly string[], roles?: readonly string[]) =>
  !roles || roles.some((r) => userRoles.includes(r));

const HomeLinkView: FC<{ link: HomeLink }> = ({ link }) => {
  const target = SCREENS_BY_ID.get(link.screen);
  if (!target) return <span className="home-queue__link--missing">{link.label}</span>;
  const qs = new URLSearchParams(link.params ?? {}).toString();
  return (
    <Link to={`${screenPath(target)}${qs ? `?${qs}` : ''}`} state={originState(HOME_ORIGIN)}>
      {link.label}
    </Link>
  );
};

const HOME_ORIGIN = { path: '/home', label: 'Home' };

/** Area quick links: the legacy tab landing pages. */
const QuickLink: FC<{ area: AreaDef }> = ({ area }) => {
  const navigate = useNavigate();
  const Icon = AREA_ICONS[area.id];
  return (
    <ClickableTile onClick={() => navigate(`/${area.id}`)} className="welcome__tile">
      <Icon size={24} />
      <span className="welcome__tile-label">{area.label}</span>
      <span className="welcome__tile-desc">{area.description}</span>
    </ClickableTile>
  );
};

const HomePage: FC = () => {
  const user = useScopedUser();
  const { user: authUser } = useAuth();
  const roles = (user?.roles ?? []) as string[];
  const [alertText, setAlertText] = useState<string | null>(null);
  const firstName = authUser?.firstName || authUser?.displayName || 'there';

  // The legacy alert banner (ProcessParam share file) — now a DB-backed
  // setting maintained on Administration › Processing Parameters.
  useEffect(() => {
    listQuery('admin.alertText')
      .then((rows) => setAlertText(rows[0]?.alertText ? String(rows[0].alertText) : null))
      .catch(() => setAlertText(null));
  }, []);

  const blocks = BLOCKS.filter((b) => hasAny(roles, b.roles))
    .map((b) => ({
      ...b,
      rows: b.rows
        .filter((r) => hasAny(roles, r.roles))
        .map((r) => ({ ...r, links: r.links.filter((l) => hasAny(roles, l.roles)) }))
        .filter((r) => r.links.length > 0),
    }))
    .filter((b) => b.rows.length > 0);

  const areas = AREAS.filter((a) => can(user, a.capability));

  return (
    <PageLayout title="Harvest Billing System" subtitle={`Welcome, ${firstName}.`}>
      <p className="welcome__intro">
        Scale returns, billing, rating and scale control for timber harvested on Crown and private land.
        Pick up a work queue below, choose an area, or use the menu.
      </p>
      {alertText && (
        <InlineNotification
          kind="warning"
          lowContrast
          hideCloseButton
          title="Notice"
          subtitle={alertText}
          className="home__alert"
        />
      )}

      {blocks.length > 0 && (
        <SectionTile title="Work queues" icon={ListChecked}>
          <div className="home-queue__grid">
            {blocks.map((b, i) => (
              <section key={`${b.title}-${i}`} className="home-queue" aria-label={b.title}>
                <h3 className="home-queue__title">{b.title}</h3>
                <dl className="home-queue__rows">
                  {b.rows.map((r) => (
                    <div key={r.label}>
                      <dt>{r.label}</dt>
                      <dd>
                        {r.links.map((l) => (
                          <HomeLinkView key={l.label + JSON.stringify(l.params)} link={l} />
                        ))}
                      </dd>
                    </div>
                  ))}
                </dl>
              </section>
            ))}
          </div>
        </SectionTile>
      )}

      <SectionTile title="Quick links" icon={Apps}>
        <div className="welcome__tiles">
          {areas.map((a) => (
            <QuickLink key={a.id} area={a} />
          ))}
        </div>
      </SectionTile>
    </PageLayout>
  );
};

export default HomePage;
