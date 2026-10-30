import type { FC, ReactNode } from 'react';

import './RequiredLabel.css';

/**
 * A field label with the required marker: "Label *", the asterisk in the
 * error red. `suffix` follows the marker ("To * (up to 12 months)").
 */
export const RequiredLabel: FC<{ text: ReactNode; required?: boolean; suffix?: ReactNode }> = ({
  text,
  required = true,
  suffix,
}) => (
  <>
    {text}
    {required && <span className="hbs-required-marker"> *</span>}
    {suffix ? <> {suffix}</> : null}
  </>
);

export default RequiredLabel;
