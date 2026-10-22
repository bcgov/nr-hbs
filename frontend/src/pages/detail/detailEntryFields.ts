import type { FieldDef } from '@/screens/types';

/**
 * Data-entry fields of the legacy detail pages, per document type, with the
 * legacy labels (P550/P553 LogTallyInputFields.jsp, P600/P603
 * WeighSlipInputFields.jsp, P650/P653 SampleLogTallyInputFields.jsp,
 * P700/P703, P750/P753). `avail` is the legacy field name in the
 * field-availability matrices (PSDetailDocumentFields, WSDetailDocumentFields,
 * SSDetailDocumentFields, SPSFields, LedgerFields) served by
 * GET /detail-returns/fields — a field shows only when the matrix lists it for
 * the return's event type. `name` is the camelCase version-table column the
 * backend accepts (DetailPayload).
 */
export interface EntryField extends FieldDef {
  avail?: string;
}

export type DocTypeCode = 'P' | 'W' | 'S' | 'F' | 'A' | 'D';

export const DOC_TYPES: { code: DocTypeCode; label: string; legacy: string; matrix: string }[] = [
  { code: 'P', label: 'Piece Scale (Log Tally)', legacy: 'P550', matrix: 'LOG_TALLY' },
  { code: 'W', label: 'Weight Scale (Weigh Slip)', legacy: 'P600', matrix: 'WEIGH_SLIP' },
  { code: 'S', label: 'Sample Scale (Sample Log Tally)', legacy: 'P650', matrix: 'SAMPLE_LOG_TALLY' },
  { code: 'A', label: 'Arrival Ledger Entry', legacy: 'P700', matrix: 'LEDGER' },
  { code: 'D', label: 'Departure Ledger Entry', legacy: 'P700', matrix: 'LEDGER' },
  { code: 'F', label: 'Special Forest Product Tally', legacy: 'P750', matrix: 'SFP_TALLY' },
];

/** Scale event types offered per type (P004 / the P5xx–P7xx scenario radios). */
export const EVENT_TYPES: Record<DocTypeCode, { code: string; label: string }[]> = {
  P: [
    { code: 'PS', label: 'Primary Scale' },
    { code: 'FD', label: 'From Site Deck' },
    { code: 'RT', label: 'Local Red Tag' },
    { code: 'RR', label: 'Remote Red Tag' },
    { code: 'CS', label: 'Check Scale' },
    { code: '4R', label: 'SB4 Rescale' },
    { code: 'CU', label: 'Company Use' },
  ],
  W: [
    { code: 'PS', label: 'Primary Scale' },
    { code: 'SS', label: 'Local Sample' },
    { code: 'RS', label: 'Remote Sample' },
    { code: 'PD', label: 'Primary Departure' },
    { code: 'RT', label: 'Local Red Tag' },
    { code: 'RR', label: 'Remote Red Tag' },
    { code: '4R', label: 'SB4 Rescale' },
    { code: 'DP', label: 'Departure' },
    { code: 'CU', label: 'Company Use' },
  ],
  S: [
    { code: 'SS', label: 'Local Sample' },
    { code: 'RS', label: 'Remote Sample' },
    { code: 'CS', label: 'Check Scale' },
  ],
  F: [
    { code: 'PS', label: 'Primary Scale' },
    { code: 'CS', label: 'Check Scale' },
  ],
  A: [
    { code: 'PS', label: 'Primary Scale' },
    { code: 'B4', label: 'Scaled Before' },
    { code: '4R', label: 'SB4 Rescale' },
    { code: 'TD', label: 'To Site Deck' },
    { code: 'FD', label: 'From Site Deck' },
    { code: 'RS', label: 'Remote Sample' },
    { code: 'RR', label: 'Remote Red Tag' },
  ],
  D: [{ code: 'DP', label: 'Departure' }],
};

const yesNo = (name: string, label: string, avail: string): EntryField => ({ name, label, type: 'checkbox', avail });

const SCALE_METHOD: EntryField = {
  name: 'volumeCalcMethodType',
  label: 'Scale Method',
  type: 'select',
  avail: 'SCALE_METHOD',
  options: [
    { value: 'G', label: 'Gross' },
    { value: 'N', label: 'Net' },
  ],
};

export const HEADER_FIELDS: Record<DocTypeCode, EntryField[]> = {
  P: [
    { name: 'scaleSiteIdNmbr', label: 'Scale Site', upper: true, maxLength: 4, avail: 'SITE', required: true },
    { name: 'scaleDate', label: 'Scale Date', type: 'date', avail: 'SCALE_DATE', required: true },
    { name: 'primaryLicenseNumber', label: 'Primary Scaler', upper: true, maxLength: 4, avail: 'PRIMARY_SCALER', required: true },
    { name: 'secondaryLicenseNumber', label: 'Secondary Scaler', upper: true, maxLength: 4, avail: 'SECONDARY_SCALER' },
    { name: 'signingLicenseNumber', label: 'Signing Scaler', upper: true, maxLength: 4, avail: 'SIGNING_SCALER' },
    { name: 'returnNumber', label: 'Return Number', type: 'number', maxLength: 4, avail: 'RETURN_NUMBER', required: true },
    { name: 'logCount', label: 'Log Count', type: 'number', maxLength: 4, avail: 'LOG_COUNT' },
    SCALE_METHOD,
    { name: 'timberMark', label: 'Timber Mark', upper: true, maxLength: 6, avail: 'TIMBER_MARK' },
    { name: 'timberBrand', label: 'Timber Brand', upper: true, maxLength: 7, avail: 'TIMBER_BRAND' },
    { name: 'cutBlockId', label: 'Cut Block', upper: true, maxLength: 10, avail: 'CUT_BLOCK' },
    { name: 'loadArrivalNumber', label: 'Load Arrival', maxLength: 10, avail: 'LOAD_ARRIVAL' },
    { name: 'incomingLdsNumber', label: 'LDS No', maxLength: 10, avail: 'LDS_NO' },
    { name: 'transportIdentifier', label: 'Transport ID', maxLength: 16, avail: 'TRANSPORT_ID' },
    { name: 'redTagWeighSlip', label: 'Weigh Slip', maxLength: 10, avail: 'WEIGH_SLIP' },
    { name: 'parcelIdentifier', label: 'Parcel ID', type: 'number', maxLength: 1, avail: 'PARCEL_ID' },
    { name: 'parcelCount', label: 'Parcels', type: 'number', maxLength: 1, avail: 'PARCELS' },
    yesNo('fieldScaleInd', 'Field Scale', 'FIELD_SCALE'),
    { name: 'fieldScaleDeckId', label: 'Deck ID', upper: true, maxLength: 1, avail: 'DECK_ID' },
    yesNo('beachcombInd', 'Beachcomb', 'BEACHCOMB'),
    { name: 'origRedtagScaleSiteIdNmbr', label: 'Originating Scale Site', upper: true, maxLength: 4, avail: 'ORIGINAL_SITE_NUMBER' },
    yesNo('checkReplacesOriginalInd', 'Replacement', 'REPLACEMENT'),
    { name: 'origChkSclLicenseNumber', label: 'Original Scaler', upper: true, maxLength: 4, avail: 'ORIGINAL_SCALER' },
    { name: 'origChkSclReturnNumber', label: 'Original Scale Return', type: 'number', maxLength: 4, avail: 'ORIGINAL_RETURN' },
    { name: 'originalDdn', label: 'Original DDN', upper: true, maxLength: 13, avail: 'ORIGINAL_DDN' },
    { name: 'logTallyComment', label: 'Comment', type: 'textarea', maxLength: 240, avail: 'COMMENT', span: 4 },
  ],
  W: [
    { name: 'scaleSiteIdNmbr', label: 'Scale Site', upper: true, maxLength: 4, avail: 'SITE', required: true },
    { name: 'weighSlipNumber', label: 'Weigh Slip', maxLength: 10, avail: 'WEIGH_SLIP', required: true },
    { name: 'scaleDate', label: 'Scale Date / Time (yyyy-mm-ddThh:mm)', avail: 'SCALE_DATE', required: true },
    { name: 'primaryLicenseNumber', label: 'Primary Scaler', upper: true, maxLength: 4, avail: 'PRIMARY_SCALER' },
    { name: 'secondaryLicenseNumber', label: 'Secondary Scaler', upper: true, maxLength: 4, avail: 'SECONDARY_SCALER' },
    { name: 'signingLicenseNumber', label: 'Signing Scaler', upper: true, maxLength: 4, avail: 'SIGNING_SCALER' },
    { name: 'timberMark', label: 'Timber Mark', upper: true, maxLength: 6, avail: 'TIMBER_MARK' },
    { name: 'timberBrand', label: 'Timber Brand', upper: true, maxLength: 7, avail: 'TIMBER_BRAND' },
    { name: 'cutBlockId', label: 'Cut Block', upper: true, maxLength: 10, avail: 'CUT_BLOCK' },
    { name: 'populationNumber', label: 'Population', upper: true, maxLength: 4, avail: 'POP_STRAT_YEAR' },
    { name: 'stratumNumber', label: 'Stratum', upper: true, maxLength: 2, avail: 'POP_STRAT_YEAR' },
    { name: 'samplingYear', label: 'Sampling Year', type: 'number', maxLength: 4, avail: 'POP_STRAT_YEAR' },
    { name: 'companyUseStratum', label: 'Company Stratum', upper: true, maxLength: 8, avail: 'COMPANY_STRATUM' },
    { name: 'grossWeight', label: 'Gross Weight', type: 'number', maxLength: 6, avail: 'GROSS_WEIGHT' },
    { name: 'tareWeight', label: 'Tare Weight', type: 'number', maxLength: 6, avail: 'TARE_WEIGHT' },
    { name: 'sampleDeductionWeight', label: 'Grapple Weight', type: 'number', maxLength: 6, avail: 'GRAPPLE_WEIGHT' },
    {
      name: 'hbsSampleTypeCode',
      label: 'Sample',
      type: 'select',
      codeList: 'codes.sampleTypes',
      avail: 'SAMPLE',
    },
    { name: 'incomingLdsNumber', label: 'LDS In', maxLength: 10, avail: 'LDS_IN' },
    { name: 'outgoingLdsNumber', label: 'LDS Out', maxLength: 10, avail: 'LDS_OUT' },
    { name: 'destination', label: 'Destination', upper: true, maxLength: 4, avail: 'DESTINATION' },
    { name: 'transportIdentifier', label: 'Transport ID', maxLength: 16, avail: 'TRANSPORT_ID' },
    yesNo('fieldScaleInd', 'Field Scale', 'FIELD_SCALE'),
    { name: 'fieldScaleDeckId', label: 'Deck ID', upper: true, maxLength: 1, avail: 'DECK_ID' },
    { name: 'weighSlipComment', label: 'Comment', type: 'textarea', maxLength: 240, avail: 'COMMENT', span: 4 },
  ],
  S: [
    { name: 'scaleSiteIdNmbr', label: 'Scale Site', upper: true, maxLength: 4, avail: 'SCALE_SITE', required: true },
    { name: 'scaleDate', label: 'Scale Date', type: 'date', avail: 'SCALE_DATE', required: true },
    { name: 'primaryLicenseNumber', label: 'Primary Scaler', upper: true, maxLength: 4, avail: 'PRIMARY_SCALER', required: true },
    { name: 'secondaryLicenseNumber', label: 'Secondary Scaler', upper: true, maxLength: 4, avail: 'SECONDARY_SCALER' },
    { name: 'signingLicenseNumber', label: 'Signing Scaler', upper: true, maxLength: 4, avail: 'SIGNING_SCALER' },
    { name: 'returnNumber', label: 'Return Number', type: 'number', maxLength: 4, avail: 'RETURN_NUMBER', required: true },
    { name: 'logCount', label: 'Log Count', type: 'number', maxLength: 4, avail: 'LOG_COUNT' },
    SCALE_METHOD,
    { name: 'timberMark', label: 'Timber Mark', upper: true, maxLength: 6, avail: 'TIMBER_MARK' },
    { name: 'populationNumber', label: 'Population', upper: true, maxLength: 4, avail: 'POP_STRAT_YEAR' },
    { name: 'stratumNumber', label: 'Stratum', upper: true, maxLength: 2, avail: 'POP_STRAT_YEAR' },
    { name: 'samplingYear', label: 'Sampling Year', type: 'number', maxLength: 4, avail: 'POP_STRAT_YEAR' },
    { name: 'sampledWeighSlip', label: 'Weigh Slip', maxLength: 10, avail: 'WEIGH_SLIP' },
    { name: 'sampleWeight', label: 'Sample Weight', type: 'number', maxLength: 6, avail: 'SAMPLE_WEIGHT' },
    { name: 'loadArrivalNumber', label: 'Load Arrival', maxLength: 10, avail: 'LOAD_ARRIVAL' },
    { name: 'incomingLdsNumber', label: 'LDS In', maxLength: 10, avail: 'LDS_IN' },
    { name: 'transportIdentifier', label: 'Transport ID', maxLength: 16, avail: 'TRANSPORT_ID' },
    { name: 'origRedtagScaleSiteIdNmbr', label: 'Originating Scale Site', upper: true, maxLength: 4, avail: 'ORIGINAL_SITE_NUMBER' },
    yesNo('checkReplacesOriginalInd', 'Replacement', 'REPLACEMENT'),
    { name: 'origChkSclLicenseNumber', label: 'Original Scaler', upper: true, maxLength: 4, avail: 'ORIGINAL_SCALER' },
    { name: 'origChkSclReturnNumber', label: 'Original Scale Return', type: 'number', maxLength: 4, avail: 'ORIGINAL_RETURN' },
    { name: 'originalDdn', label: 'Original DDN', upper: true, maxLength: 13, avail: 'ORIGINAL_DDN' },
    { name: 'sampleLogTallyComment', label: 'Comment', type: 'textarea', maxLength: 240, avail: 'COMMENT', span: 4 },
  ],
  F: [
    { name: 'scaleSiteIdNmbr', label: 'Scale Site', upper: true, maxLength: 4, avail: 'SITE', required: true },
    { name: 'scaleDate', label: 'Scale Date', type: 'date', avail: 'SCALE_DATE', required: true },
    { name: 'primaryLicenseNumber', label: 'Primary Scaler', upper: true, maxLength: 4, avail: 'PRIMARY_SCALER', required: true },
    { name: 'secondaryLicenseNumber', label: 'Secondary Scaler', upper: true, maxLength: 4, avail: 'SECONDARY_SCALER' },
    { name: 'signingLicenseNumber', label: 'Signing Scaler', upper: true, maxLength: 4, avail: 'SIGNING_SCALER' },
    { name: 'returnNumber', label: 'Return Number', type: 'number', maxLength: 4, avail: 'RETURN_NUMBER', required: true },
    {
      name: 'hbsSfpScaleTypeCode',
      label: 'Scale Method',
      type: 'select',
      codeList: 'codes.detailReturns.sfpScaleTypes',
      avail: 'SFP_SCALE_TYPE_CODE',
      required: true,
    },
    { name: 'logCount', label: 'Log Count', type: 'number', maxLength: 4, avail: 'LOG_COUNT' },
    { name: 'samplePieceCount', label: 'Total Piece Count', type: 'number', maxLength: 4, avail: 'TOTAL_PIECE_COUNT' },
    { name: 'timberMark', label: 'Timber Mark', upper: true, maxLength: 6, avail: 'TIMBER_MARK' },
    { name: 'cutBlockId', label: 'Cut Block', upper: true, maxLength: 10, avail: 'CUT_BLOCK' },
    { name: 'loadArrivalNumber', label: 'Load Arrival', maxLength: 10, avail: 'LOAD_ARRIVAL' },
    { name: 'incomingLdsNumber', label: 'LDS No', maxLength: 10, avail: 'LDS_NO' },
    { name: 'transportIdentifier', label: 'Transport ID', maxLength: 16, avail: 'TRANSPORT_ID' },
    { name: 'parcelIdentifier', label: 'Parcel ID', type: 'number', maxLength: 1, avail: 'PARCEL_ID' },
    { name: 'parcelCount', label: 'Parcels', type: 'number', maxLength: 1, avail: 'PARCELS' },
    yesNo('fieldScaleInd', 'Field Scale', 'FIELD_SCALE'),
    { name: 'fieldScaleDeckId', label: 'Deck ID', upper: true, maxLength: 1, avail: 'DECK_ID' },
    yesNo('checkReplacesOriginalInd', 'Replacement', 'REPLACEMENT'),
    { name: 'origChkSclLicenseNumber', label: 'Original Scaler', upper: true, maxLength: 4, avail: 'ORIGINAL_SCALER' },
    { name: 'origChkSclReturnNumber', label: 'Original Scale Return', maxLength: 4, avail: 'ORIGINAL_RETURN' },
    { name: 'sfpTallyComment', label: 'Comment', type: 'textarea', maxLength: 240, avail: 'COMMENT', span: 4 },
  ],
  A: [
    { name: 'scaleSiteIdNmbr', label: 'Scale Site', upper: true, maxLength: 4, avail: 'SITE', required: true },
    { name: 'arrivalDate', label: 'Arrival Date', type: 'date', avail: 'ARRIVAL_DATE' },
    { name: 'scaleDate', label: 'Scale Date', type: 'date', avail: 'SCALE_DATE' },
    { name: 'licenseNumber', label: 'Primary Scaler', upper: true, maxLength: 4, avail: 'PRIMARY_SCALER' },
    { name: 'returnNumber', label: 'Return Number', type: 'number', maxLength: 4, avail: 'RETURN_NUMBER' },
    { name: 'timberMark', label: 'Timber Mark', upper: true, maxLength: 6, avail: 'TIMBER_MARK' },
    { name: 'timberBrand', label: 'Timber Brand', upper: true, maxLength: 7, avail: 'TIMBER_BRAND' },
    { name: 'cutBlockId', label: 'Cut Block', upper: true, maxLength: 10, avail: 'CUT_BLOCK' },
    { name: 'arrivalLedgerWeighSlip', label: 'Weigh Slip', maxLength: 10, avail: 'WEIGH_SLIP' },
    { name: 'loadArrivalNumber', label: 'Load Arrival', maxLength: 10, avail: 'LOAD_ARRIVAL' },
    { name: 'incomingLdsNumber', label: 'LDS No In', maxLength: 10, avail: 'LDS_IN' },
    { name: 'transportIdentifier', label: 'Transport ID In', maxLength: 16, avail: 'TRANSPORT_ID_IN' },
    { name: 'origScaleSite', label: 'Originating Scale Site', upper: true, maxLength: 4, avail: 'ORIGINAL_SITE_NUMBER' },
    { name: 'parcelIdentifier', label: 'Parcel ID', type: 'number', maxLength: 1, avail: 'PARCEL_ID' },
    { name: 'parcelCount', label: 'Parcels', type: 'number', maxLength: 1, avail: 'PARCELS' },
    yesNo('fieldScaleInd', 'Field Scale', 'FIELD_SCALE'),
    { name: 'fieldScaleDeckId', label: 'Deck ID', upper: true, maxLength: 1, avail: 'DECK_ID' },
  ],
  D: [
    { name: 'scaleSiteIdNmbr', label: 'Scale Site', upper: true, maxLength: 4, avail: 'SITE', required: true },
    { name: 'departureDate', label: 'Departure Date', type: 'date', avail: 'DEPARTURE_DATE', required: true },
    { name: 'outgoingTransportId', label: 'Transport ID Out', maxLength: 16, avail: 'TRANSPORT_ID_OUT', required: true },
    { name: 'outgoingLdsNumber', label: 'LDS No Out', maxLength: 10, avail: 'LDS_OUT', required: true },
    { name: 'loadDepartureNumber', label: 'Departure Number', maxLength: 10, avail: 'LOAD_DEPARTURE', required: true },
    { name: 'destination', label: 'Destination', upper: true, maxLength: 4, avail: 'DESTINATION' },
  ],
};

/** A log / piece line column (P556/P558, P656/P658, P756/P758). */
export interface LineColumn {
  name: string;
  header: string;
  type?: 'number' | 'yesno';
  upper?: boolean;
  width?: string;
}

export const LINE_COLUMNS: Partial<Record<DocTypeCode, LineColumn[]>> = {
  P: [
    { name: 'logNumber', header: 'Log Number', type: 'number' },
    { name: 'inputBcombTimberMark', header: 'Beachcomb Mark', upper: true },
    { name: 'nmvInd', header: 'NMV', type: 'yesno' },
    { name: 'scaleSpeciesCode', header: 'Species', upper: true },
    { name: 'scaleGradeCode', header: 'Grade', upper: true },
    { name: 'length', header: 'Length', type: 'number' },
    { name: 'top', header: 'Top', type: 'number' },
    { name: 'butt', header: 'Butt', type: 'number' },
  ],
  S: [
    { name: 'logNumber', header: 'Log Number', type: 'number' },
    { name: 'nmvInd', header: 'NMV', type: 'yesno' },
    { name: 'scaleSpeciesCode', header: 'Species', upper: true },
    { name: 'scaleGradeCode', header: 'Grade', upper: true },
    { name: 'length', header: 'Length', type: 'number' },
    { name: 'top', header: 'Top', type: 'number' },
    { name: 'butt', header: 'Butt', type: 'number' },
  ],
  F: [
    { name: 'sfpNumber', header: 'Number', type: 'number' },
    { name: 'scaleSpeciesCode', header: 'Species', upper: true },
    { name: 'scaleProductCode', header: 'Product', upper: true },
    { name: 'scaleGradeCode', header: 'Grade', upper: true },
    { name: 'pieceCount', header: 'Piece Count', type: 'number' },
    { name: 'prodLogLength', header: 'Length', type: 'number' },
    { name: 'prodLogTop', header: 'Top', type: 'number' },
    { name: 'prodLogButt', header: 'Butt', type: 'number' },
    { name: 'stackLength', header: 'Stack Length', type: 'number' },
    { name: 'stackWidth', header: 'Stack Width', type: 'number' },
    { name: 'stackHeight', header: 'Stack Height', type: 'number' },
    { name: 'conversionFactor', header: 'Conversion Factor', type: 'number' },
    { name: 'netVolume', header: 'Net Volume', type: 'number' },
  ],
};

/** P559 / P659 defect columns. */
export const DEFECT_COLUMNS: LineColumn[] = [
  { name: 'defectNumber', header: 'Defect Number', type: 'number' },
  { name: 'defectType', header: 'Defect Code', upper: true },
  { name: 'volume', header: 'Defect Volume', type: 'number' },
  { name: 'descriptor', header: 'Defect Description' },
];

export const MATRIX_OF: Record<DocTypeCode, string> = {
  P: 'LOG_TALLY',
  W: 'WEIGH_SLIP',
  S: 'SAMPLE_LOG_TALLY',
  F: 'SFP_TALLY',
  A: 'LEDGER',
  D: 'LEDGER',
};

/** Fields shown for the event type (all of them when the matrix does not know the event). */
export function visibleFields(
  type: DocTypeCode,
  event: string,
  availability: Record<string, string[]> | null,
): EntryField[] {
  const fields = HEADER_FIELDS[type];
  const allowed = availability?.[`${MATRIX_OF[type]}/${event}`];
  if (!allowed) return fields;
  return fields.filter((f) => !f.avail || allowed.includes(f.avail));
}

/** Workflow buttons: command id → legacy label (Buttons.jsp) and confirmation (P554). */
export const WORKFLOW_ACTIONS: { command: string; label: string; confirm?: string; danger?: boolean }[] = [
  { command: 'detailReturns.release', label: 'Release' },
  { command: 'detailReturns.reEdit', label: 'Re-Edit' },
  { command: 'detailReturns.hold', label: 'Hold' },
  { command: 'detailReturns.cancel', label: 'Cancel', confirm: 'Cancel this scale return? The cancellation must be approved before it is issued.', danger: true },
  {
    command: 'detailReturns.cancelReplaceChanges',
    label: 'Cancel and Replace with Changes',
    confirm: 'Cancel this scale return and create a new version to replace it?',
  },
  {
    command: 'detailReturns.replaceChanges',
    label: 'Replace with Changes',
    confirm: 'Create a new version of this cancelled scale return to replace it?',
  },
  {
    command: 'detailReturns.replaceNoChanges',
    label: 'Replace without Changes',
    confirm: 'Replace this cancelled scale return without changes?',
  },
  { command: 'detailReturns.approve', label: 'Approve Change', confirm: 'Approve the requested change?' },
  { command: 'detailReturns.reject', label: 'Reject Change', confirm: 'Reject the requested change?', danger: true },
  {
    command: 'detailReturns.clearSignatureFailure',
    label: 'Clear Digital Signature Failure',
    confirm: 'Clear the digital signature failure and release this scale return?',
  },
  { command: 'detailReturns.discard', label: 'Discard', confirm: 'Discard this version of the scale return?', danger: true },
];
