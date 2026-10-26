import type { Option } from './types';

/** Earliest sampling year in the HBS data (TEST: strata from 2000 on). */
const FIRST_SAMPLING_YEAR = 2000;

/** Sampling-year dropdown, newest first: next year (plans are set up ahead) back to 2000. */
export const SAMPLING_YEAR_OPTIONS: Option[] = Array.from(
  { length: new Date().getFullYear() + 1 - FIRST_SAMPLING_YEAR + 1 },
  (_, i) => String(new Date().getFullYear() + 1 - i),
).map((y) => ({ value: y, label: y }));
