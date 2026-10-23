import { getJson, HBS_API } from '@/services/common';
import { runCommand, type Row } from '@/services/screens';

/**
 * Detail-return data entry and workflow (backend detailreturns package):
 * GET /detail-returns/{ddn}/versions/{version} for the entry page, and the
 * detailReturns.* commands (add, save, release, hold, …) for every button.
 */

export interface DetailLine {
  logdtlId: number | null;
  fields: Record<string, unknown>;
  defects: { logDefectId: number | null; fields: Record<string, unknown> }[];
}

export interface DetailVersionDoc {
  ddn: string;
  version: number;
  docType: string;
  documentState: string;
  versionState: string;
  active: boolean;
  updateTimestamp: string | null;
  fields: Record<string, unknown>;
  logs: DetailLine[];
  /** Command ids the caller may run on this version (legacy Buttons.jsp rules). */
  actions: string[];
  editable: boolean;
  eventTypes: string[];
}

export function getDetailVersion(ddn: string, version: string | number): Promise<DetailVersionDoc> {
  return getJson<DetailVersionDoc>(
    `${HBS_API}/detail-returns/${encodeURIComponent(ddn)}/versions/${encodeURIComponent(String(version))}`,
    'Detail return load',
  );
}

let availability: Promise<Record<string, string[]>> | null = null;

/** Legacy field availability by "MATRIX/event" (cached). */
export function getFieldAvailability(): Promise<Record<string, string[]>> {
  if (!availability) {
    availability = getJson<Record<string, string[]>>(`${HBS_API}/detail-returns/fields`, 'Field list');
    availability.catch(() => {
      availability = null;
    });
  }
  return availability;
}

export function runDetailCommand(command: string, body: Record<string, unknown>): Promise<Row> {
  return runCommand(command, body);
}
