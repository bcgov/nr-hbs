import type { FC, ReactNode } from 'react';

import './RequiredLabel.css';

/**
 * A field label with the required marker: "Label *", the asterisk in the
 * error red. `suffix` follows the marker ("To * (up to 12 months)").
 *
 * The spaces are non-breaking: Carbon lays labels out as flex containers,
 * which drop ordinary spaces at the edges of each part ("To*(up to …").
 */
export const RequiredLabel: FC<{ text: ReactNode; required?: boolean; suffix?: ReactNode }> = ({
  text,
  required = true,
  suffix,
}) => (
  <>
    {text}
    {required && <span className="hbs-required-marker">{'\u00a0*'}</span>}
    {suffix ? <span>{'\u00a0'}{suffix}</span> : null}
  </>
);

export default RequiredLabel;
