import { ChartColumn, Login, Receipt, RecentlyViewed, Tag, UserFollow } from '@carbon/icons-react';
import { ClickableTile } from '@carbon/react';
import type { ComponentType, FC } from 'react';
import { useNavigate } from 'react-router-dom';

import SectionTile from '@/components/SectionTile';
import PageLayout from '@/pages/PageLayout';
import '@/pages/HomePage.scss';

import { PUBLIC_SCREENS_BY_ID } from './publicScreens';

type Item = { label: string; to: string; description?: string; legacy?: string };
type Section = { title: string; icon: ComponentType<{ size?: number }>; description?: string; items: Item[] };

/**
 * The legacy public menu (include/mainmenu_public.jsp — "You can request the
 * following reports on-line"), with the legacy section and link labels.
 */
const SECTIONS: Section[] = [
  {
    title: 'Harvest Reports',
    icon: RecentlyViewed,
    items: [
      { label: 'By Date of Invoice', description: 'Billing History', to: 'billing-history' },
      { label: 'By Date of Scale', description: 'Scaling History', to: 'scaling-history' },
    ],
  },
  {
    title: 'Invoice Copies',
    icon: Receipt,
    items: [
      { label: 'Single Invoice', to: 'issued-document' },
      { label: 'Multiple Invoices', to: 'issued-documents' },
    ],
  },
  {
    title: 'Ratio, Sample, and Volume Statement Copies',
    icon: Receipt,
    items: [
      { label: 'Single Statement', to: 'issued-document' },
      { label: 'Multiple Statements', to: 'issued-documents' },
    ],
  },
  {
    title: 'Other Queries and Menus',
    icon: Tag,
    items: [
      {
        label: 'Timber Mark Information',
        to: 'timber-mark-search',
        description: "A mark's tenure, holder, appraisal and stumpage rates.",
      },
    ],
  },
  {
    title: 'Stratum Advisor',
    icon: ChartColumn,
    items: [
      { label: 'Sampling Plans', to: 'sampling-plans' },
      { label: 'Populations', to: 'sampling-populations' },
    ],
  },
];

const PublicHomePage: FC = () => {
  const navigate = useNavigate();
  return (
    <PageLayout
      title="Harvest Billing System"
      subtitle="You can request the following reports on-line, without logging in. Personal information of individual clients is not releasable and is removed."
    >
      <div className="hbs-public__sections">
        {SECTIONS.map((section) => {
          const items = section.items.filter((i) => PUBLIC_SCREENS_BY_ID.has(i.to));
          if (items.length === 0) return null;
          return (
            <SectionTile key={section.title} title={section.title} icon={section.icon} description={section.description}>
              <div className="welcome__tiles">
                {items.map((item) => (
                  <ClickableTile
                    key={item.label}
                    onClick={() => navigate(`/public/${item.to}`)}
                    className="welcome__tile"
                  >
                    <span className="welcome__tile-label">{item.label}</span>
                    <span className="welcome__tile-desc">
                      {item.description ?? PUBLIC_SCREENS_BY_ID.get(item.to)?.title}
                    </span>
                  </ClickableTile>
                ))}
              </div>
            </SectionTile>
          );
        })}

        <SectionTile title="Public User Services" icon={UserFollow}>
          <div className="welcome__tiles">
            <ClickableTile onClick={() => navigate('/public/access')} className="welcome__tile">
              <span className="welcome__tile-label">How to get access</span>
              <span className="welcome__tile-desc">
                HBS enrolment: log in with IDIR or Business BCeID and have HBS roles granted.
              </span>
            </ClickableTile>
            <ClickableTile onClick={() => navigate('/')} className="welcome__tile">
              <Login size={24} />
              <span className="welcome__tile-label">Log in</span>
              <span className="welcome__tile-desc">Scale returns, submissions and everything else need a login.</span>
            </ClickableTile>
          </div>
        </SectionTile>
      </div>
    </PageLayout>
  );
};

export default PublicHomePage;
