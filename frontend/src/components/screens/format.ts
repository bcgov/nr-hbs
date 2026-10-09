import type { ColumnFormat } from '@/screens/types';
import { formatDate } from '@/utils/formatDate';

const money = new Intl.NumberFormat('en-CA', { style: 'currency', currency: 'CAD' });
const volume = new Intl.NumberFormat('en-CA', { minimumFractionDigits: 3, maximumFractionDigits: 3 });
const number = new Intl.NumberFormat('en-CA', { maximumFractionDigits: 6 });

/**
 * Renders one cell / field value. Empty values become an em-dash, the same
 * placeholder nr-fsp-new's tables use (formatCellText).
 */
export function formatValue(value: unknown, format: ColumnFormat = 'text'): string {
  if (value === null || value === undefined || value === '') return '—';
  switch (format) {
    case 'date':
      return formatDate(String(value)) || '—';
    case 'datetime': {
      const s = String(value);
      const [d, t] = s.split('T');
      return t ? `${formatDate(d)} ${t.slice(0, 5)}` : formatDate(d);
    }
    case 'money': {
      const n = Number(value);
      return Number.isFinite(n) ? money.format(n) : String(value);
    }
    case 'volume': {
      const n = Number(value);
      return Number.isFinite(n) ? volume.format(n) : String(value);
    }
    case 'number': {
      const n = Number(value);
      return Number.isFinite(n) ? number.format(n) : String(value);
    }
    case 'yesno': {
      const s = String(value).toUpperCase();
      if (s === 'Y' || s === 'TRUE') return 'Yes';
      if (s === 'N' || s === 'FALSE') return 'No';
      return String(value);
    }
    default:
      return String(value).trim() || '—';
  }
}
