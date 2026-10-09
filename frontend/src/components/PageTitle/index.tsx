import type { FC, ReactNode } from 'react';

import './PageTitle.css';

type Props = {
  /** Text, or text with markup — e.g. a detail screen's id in a pill. */
  title: ReactNode;
  subtitle?: string;
  /** Screen-level buttons, laid out level with the title. */
  actions?: ReactNode;
};

/**
 * The heading every screen opens with.
 *
 * An `h1`, so a screen reader's heading list starts where it should. The size
 * is set in CSS, which is where a size belongs — the element level says what
 * the heading *is*, not how big it looks.
 *
 * Ported from nr-fam's PageTitle so the two applications open a screen the
 * same way.
 */
export const PageTitle: FC<Props> = ({ title, subtitle, actions }) => (
  <div className="fta-page-title">
    <div className="fta-page-title__heading">
      <h1 className="fta-page-title__title">{title}</h1>
      {actions ? <div className="fta-page-title__actions">{actions}</div> : null}
    </div>
    {subtitle ? <p className="fta-page-title__subtitle">{subtitle}</p> : null}
  </div>
);

export default PageTitle;
