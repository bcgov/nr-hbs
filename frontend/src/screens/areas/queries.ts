import type { ColumnDef, FieldDef, Option, ReportLinkDef, ScreenDef } from '../types';

/**
 * Queries tab (legacy P400 "Queries Menu"). Backend:
 * catalog/QueriesCatalog.java + catalog/QueriesReportsCatalog.java, and the
 * bespoke StatementDocumentController (GET /api/v1/hbs/statements/{type}/{number}.{pdf|xml}).
 * Mapping to the legacy screens: docs/areas/queries.md.
 *
 * Issued-document rows carry `documentUrl` / `documentXmlUrl` (null when the
 * document is older than seven years or has no stored file). Those endpoints
 * need the bearer token, so the SPA must offer them as a download action
 * (apiFetch/getBlob) rather than a plain anchor — see docs/areas/queries.md.
 */

// ── Shared option lists ─────────────────────────────────────────────────────

const DOCUMENT_TYPES: Option[] = [
  { value: 'PSI', label: 'Piece Scale Invoice' },
  { value: 'WSI', label: 'Weight Scale Invoice' },
  { value: 'PSV', label: 'Piece Scale Volume Statement' },
  { value: 'WSV', label: 'Weight Scale Volume Statement' },
  { value: 'CSS', label: 'Compiled Sample Statement' },
  { value: 'RS', label: 'Ratio Statement' },
];

/**
 * Management unit types — a fixed list in legacy HBS (OpqConstants.MGMT_TYPE_CODE),
 * not a code table. The two combined choices match any of their types.
 */
const MGMT_UNIT_TYPES: Option[] = [
  { value: 'F', label: 'Woodlot' },
  { value: 'T', label: 'Tree Farm Licence' },
  { value: 'U', label: 'Timber Supply Area' },
  { value: 'V', label: 'Timber Supply Block' },
  { value: 'U,V', label: 'Timber Supply Area or Block' },
  { value: 'F,T,U,V', label: 'Other' },
];

const SCALE_TYPES: Option[] = [
  { value: 'P', label: 'Piece Scale' },
  { value: 'W', label: 'Weight Scale' },
];

const BILLING_TYPES: Option[] = [
  { value: 'Normal', label: 'Normal' },
  { value: 'Cruise', label: 'Cruise Based' },
  { value: 'Waste', label: 'Waste' },
  { value: 'Beachcomb', label: 'Beachcomb' },
];

/** P432 / P442 "Detail Lines Displayed" — INVSMRY_DISPDTLS = Volume/Value/Value-Volume flags. */
const DETAIL_LINES: Option[] = [
  { value: 'TTT', label: 'Volume (M3), Value ($), Value/Volume ($/M3)' },
  { value: 'TTF', label: 'Volume (M3), Value ($)' },
  { value: 'TFT', label: 'Volume (M3), Value/Volume ($/M3)' },
  { value: 'TFF', label: 'Volume (M3)' },
  { value: 'FTT', label: 'Value ($), Value/Volume ($/M3)' },
  { value: 'FTF', label: 'Value ($)' },
  { value: 'FFT', label: 'Value/Volume ($/M3)' },
];

/** INVSMRY_DISPSPG — Include Species / Products / Grades Groups. */
const SPG_GROUPS: Option[] = [
  { value: 'FFF', label: 'None' },
  { value: 'TFF', label: 'Species' },
  { value: 'FFT', label: 'Grades' },
  { value: 'TFT', label: 'Species and Grades' },
  { value: 'FTF', label: 'Products' },
  { value: 'TTF', label: 'Species and Product' },
];

/** INVSMRY_GRPTYPE (OpqConstants.PARAM_*). */
const BILLING_GROUPS: Option[] = [
  { value: '7', label: 'Region Billed' },
  { value: '1', label: 'Region Harvested and District Harvested' },
  { value: '5', label: 'File Type' },
  { value: '2', label: 'District and Mark' },
  { value: '3', label: 'Licence and Mark' },
  { value: '4', label: 'Client and Licence' },
  { value: '8', label: 'Mgmt Unit Type and Mgmt Unit No' },
];

const SCALING_GROUPS: Option[] = [
  { value: '7', label: 'Region Scaled' },
  { value: '1', label: 'Region Scaled and District Scaled' },
  { value: '6', label: 'District Scaled and Scale Site' },
  { value: '5', label: 'File Type' },
  { value: '2', label: 'District and Mark' },
  { value: '3', label: 'Licence and Mark' },
  { value: '4', label: 'Client and Licence' },
  { value: '8', label: 'Mgmt Unit Type and Mgmt Unit No' },
];

const CLIENT_GROUP = 'Client Association';

const CLIENT_ASSOCIATION_FIELDS: FieldDef[] = [
  { name: 'clientNumber', label: 'Both - Client No.', type: 'client', group: CLIENT_GROUP },
  { name: 'sendToClientNumber', label: 'Original - Client No.', type: 'client', group: CLIENT_GROUP },
  { name: 'copyToClientNumber', label: 'Copy To - Client No.', type: 'client', group: CLIENT_GROUP },
];

// ── Issued documents (P449 / P450 → P451 … P476) ────────────────────────────

const ISSUED_DOC_COLUMNS: ColumnDef[] = [
  { key: 'statementNumber', header: 'Statement', sortable: true },
  { key: 'documentType', header: 'Type' },
  { key: 'documentControlNumber', header: 'SDN' },
  { key: 'issueDate', header: 'Issue Date', format: 'date', sortable: true },
  { key: 'txnType', header: 'Txn Type' },
  { key: 'scaleSite', header: 'Site', sortable: true },
  { key: 'scaleDate', header: 'Scale Date', format: 'date', sortable: true },
  { key: 'popStrYr', header: 'Pop/Str/Yr', sortable: true },
  { key: 'timberMark', header: 'Timber Mark', sortable: true },
  { key: 'statementTo', header: 'Statement To' },
  { key: 'copyTo', header: 'Copy To' },
  { key: 'totalVolume', header: 'Total Volume', format: 'volume' },
  { key: 'totalValue', header: 'Total Value', format: 'money' },
  { key: 'netWeight', header: 'Total Weight', format: 'number' },
  { key: 'documentUrl', header: 'PDF', format: 'download' },
  { key: 'documentXmlUrl', header: 'XML', format: 'download' },
];

/** SDN column → the summary return (legacy summaryScaleReturnSearch.do?…&actionType=View+Return+Summary). */
const SDN_LINK = {
  screen: 'summary-return',
  params: { documentControlNumber: 'documentControlNumber', version: 'version', returnType: 'returnType' },
};

const ISSUED_DOC_REPORT_PARAMS = {
  RB_TYPE: 'documentType',
  RB_DOC_NUMBER: 'statementNumber',
  RB_SCALE_DATE_FROM: 'scaleDateFrom',
  RB_SCALE_DATE_TO: 'scaleDateTo',
  RB_ISSUE_DATE_FROM: 'issueDateFrom',
  RB_ISSUE_DATE_TO: 'issueDateTo',
  RB_TIMBER_MARK: 'timberMark',
  RB_SCALE_SITE: 'scaleSite',
  RB_POPULATION: 'population',
  RB_STRATUM: 'stratum',
  RB_SAMPLINGYEAR: 'samplingYear',
  RB_REGION_SCALED: 'regionScaled',
  RB_REGION_HARVESTED: 'regionHarvested',
  RB_CLIENT_ASSOCIATION: '=BOTH',
  RB_CLIENT_NUMBER: 'clientNumber',
  RB_CLIENT_LOCATION_CODE: 'clientLocation',
  RB_TRANSMISSION_ID: 'transmissionId',
  RB_ORDER_BY: '=0',
};

const ISSUED_DOC_REPORTS: ReportLinkDef[] = [
  { reportId: 'HBS4R452', label: 'Print (Piece Scale)', params: ISSUED_DOC_REPORT_PARAMS },
  { reportId: 'HBS4R462', label: 'Print (Weight Scale)', params: ISSUED_DOC_REPORT_PARAMS },
  { reportId: 'HBS4R472', label: 'Print (Compiled Sample)', params: ISSUED_DOC_REPORT_PARAMS },
  { reportId: 'HBS4R477', label: 'Print (Ratio)', params: ISSUED_DOC_REPORT_PARAMS },
  { reportId: 'HBS3R451', label: 'Print Client Register (Piece Scale)', params: ISSUED_DOC_REPORT_PARAMS },
  { reportId: 'HBS3R461', label: 'Print Client Register (Weight Scale)', params: ISSUED_DOC_REPORT_PARAMS },
  { reportId: 'HBS3R471', label: 'Print Client Register (Compiled Sample)', params: ISSUED_DOC_REPORT_PARAMS },
  { reportId: 'HBS3R476', label: 'Print Client Register (Ratio)', params: ISSUED_DOC_REPORT_PARAMS },
];

// ── Harvest history (P431 / P441) ───────────────────────────────────────────

const HISTORY_SELECTION_FIELDS: FieldDef[] = [
  { name: 'regionDistrict', label: 'Region/District Harvested', codeList: 'codes.orgUnits', group: 'Selection' },
  { name: 'clientNumber', label: 'Mark Holder - Client No', type: 'client', group: 'Selection' },
  { name: 'clientLocation', label: 'Loc', maxLength: 2, group: 'Selection' },
  { name: 'fileType', label: 'File Type', codeList: 'codes.queries.fileTypes', group: 'Selection' },
  { name: 'mgmtUnitType', label: 'Mgmt Unit Type', type: 'select', options: MGMT_UNIT_TYPES, group: 'Selection' },
  { name: 'mgmtUnitNo', label: 'Mgmt Unit No', upper: true, maxLength: 4, group: 'Selection' },
  {
    name: 'forestFileId',
    label: 'Forest File ID',
    upper: true,
    maxLength: 110,
    helperText: 'Up to 10, comma-separated',
    group: 'Selection',
  },
  {
    name: 'timberMarks',
    label: 'Timber Mark',
    upper: true,
    maxLength: 70,
    helperText: 'Up to 10, comma-separated',
    group: 'Selection',
  },
];

const HISTORY_FILTER_FIELDS: FieldDef[] = [
  { name: 'species', label: 'Species', codeList: 'codes.queries.species', group: 'Filters' },
  { name: 'product', label: 'Product', codeList: 'codes.queries.products', group: 'Filters' },
  { name: 'grade', label: 'Grade', codeList: 'codes.queries.grades', group: 'Filters' },
  { name: 'billingType', label: 'Billing Type', type: 'select', options: BILLING_TYPES, group: 'Filters' },
];

const historyReportConfig = (groups: Option[]): FieldDef[] => [
  { name: 'groupBy', label: 'Group output by', type: 'select', options: groups, defaultValue: '1', group: 'Report Configuration' },
  { name: 'detailLines', label: 'Detail Lines Displayed', type: 'select', options: DETAIL_LINES, defaultValue: 'TTT', group: 'Report Configuration' },
  { name: 'spgGroups', label: 'Include Species / Products / Grades Groups', type: 'select', options: SPG_GROUPS, defaultValue: 'FFF', group: 'Report Configuration' },
];

const historyReportParams = (from: string, to: string): Record<string, string> => ({
  INVSMRY_FROM: from,
  INVSMRY_TO: to,
  INVSMRY_REGDIST: 'regionDistrict',
  INVSMRY_CLIENTNO: 'clientNumber',
  INVSMRY_CLIENTLOC: 'clientLocation',
  INVSMRY_LICMARK01: 'timberMarks',
  INVSMRY_LICMARKTYPE: '=2',
  INVSMRY_SPECIES: 'species',
  INVSMRY_PRODUCT: 'product',
  INVSMRY_GRADE: 'grade',
  INVSMRY_BILLINGTYPE: 'billingType',
  INVSMRY_GRPTYPE: 'groupBy',
  INVSMRY_DISPSPG: 'spgGroups',
  INVSMRY_DISPDTLS: 'detailLines',
  INVSMRY_MGMTUNITTYPECODE: 'mgmtUnitType',
  INVSMRY_MGMTUNITNO: 'mgmtUnitNo',
});

const HISTORY_COMMON_COLUMNS: ColumnDef[] = [
  { key: 'speciesCode', header: 'Species' },
  { key: 'productCode', header: 'Product' },
  { key: 'gradeCode', header: 'Grade' },
  { key: 'billingTypeDescription', header: 'Billing Type' },
];

const HISTORY_HOLDER_COLUMNS: ColumnDef[] = [
  { key: 'districtHarvestedCode', header: 'District Harvested' },
  { key: 'forestFileId', header: 'Forest File ID' },
  { key: 'fileTypeCode', header: 'File Type' },
  { key: 'clientNumber', header: 'Mark Holder' },
  { key: 'clientLocnCode', header: 'Loc' },
  { key: 'clientName', header: 'Mark Holder Name' },
];

// ── P401 Scale Site Summary report variants ─────────────────────────────────

const siteSummaryParams = (groupBy: string): Record<string, string> => ({
  RB_DISTRICT_SCALED: 'districtScaled',
  RB_DISTRICT_HARVESTED: 'districtHarvested',
  RB_CLIENT_ASSOCIATION: 'clientAssociation',
  RB_CLIENT_NUMBER: 'clientNumber',
  RB_CLIENT_LOCATION: 'clientLocation',
  RB_VERSION_STATUS: 'versionStatus',
  RB_SCALED_FROM: 'scaleDateFrom',
  RB_SCALED_TO: 'scaleDateTo',
  RB_TIMBER_MARK: 'timberMark',
  RB_SCALE_SITE: 'scaleSite',
  RB_SCALER_LICENSE: 'scalerLicence',
  RB_POPULATION: 'population',
  RB_STRATUM: 'stratum',
  RB_SAMPLING_YEAR: 'samplingYear',
  RB_GROUP_BY: `=${groupBy}`,
});

// ── Screens ────────────────────────────────────────────────────────────────

export const screens: ScreenDef[] = [
  // P431 / P432 — Harvest Reports "By Date of Invoice"
  {
    kind: 'search',
    id: 'billing-history',
    legacy: 'P431/P432',
    area: 'queries',
    title: 'Mark Monthly Billing History',
    navLabel: 'Billing History (By Date of Invoice)',
    description:
      'In addition to the Month Billed Interval, please specify one or more of the following: Region/District Harvested, Mark Holder, File Type, Mgmt Unit Type, Forest File ID, or Timber Mark.',
    capability: 'QUERIES_VIEW',
    query: 'harvestHistory.billing',
    criteria: [
      { name: 'billingFrom', label: 'Month Billed Interval (up to 12 months) - From', type: 'date', required: true, group: 'Month Billed Interval' },
      { name: 'billingTo', label: 'To', type: 'date', required: true, group: 'Month Billed Interval' },
      ...HISTORY_SELECTION_FIELDS,
      ...HISTORY_FILTER_FIELDS,
      ...historyReportConfig(BILLING_GROUPS),
    ],
    requireOneOf: ['regionDistrict', 'clientNumber', 'fileType', 'mgmtUnitType', 'forestFileId', 'timberMarks'],
    columns: [
      { key: 'timberMark', header: 'Timber Mark', sortable: true },
      { key: 'billingPeriod', header: 'Month Billed', format: 'date', sortable: true },
      ...HISTORY_COMMON_COLUMNS,
      { key: 'totalVolumeBilled', header: 'Volume (M3)', format: 'volume', sortable: true },
      { key: 'totalAmountBilled', header: 'Value ($)', format: 'money' },
      ...HISTORY_HOLDER_COLUMNS,
    ],
    reports: [
      { reportId: 'HBS3R431', label: 'Send PDF Report', params: historyReportParams('billingFrom', 'billingTo') },
      { reportId: 'HBS3R431', label: 'Data File (CSV)', format: 'CSV', params: historyReportParams('billingFrom', 'billingTo') },
    ],
  },

  // P441 / P442 — Harvest Reports "By Date of Scale"
  {
    kind: 'search',
    id: 'scaling-history',
    legacy: 'P441/P442',
    area: 'queries',
    title: 'Mark Monthly Scaling History',
    navLabel: 'Scaling History (By Date of Scale)',
    description:
      'In addition to the Month Scaled Interval, please specify one or more of the following: Region/District Harvested, Mark Holder, File Type, Mgmt Unit Type, Forest File ID, Timber Mark, Region/District Scaled, or Scale Site.',
    capability: 'QUERIES_VIEW',
    query: 'harvestHistory.scaling',
    criteria: [
      { name: 'scalingFrom', label: 'Month Scaled Interval (up to 12 months) - From', type: 'date', required: true, group: 'Month Scaled Interval' },
      { name: 'scalingTo', label: 'To', type: 'date', required: true, group: 'Month Scaled Interval' },
      ...HISTORY_SELECTION_FIELDS,
      { name: 'regionScaled', label: 'Region/District Scaled', codeList: 'codes.orgUnits', group: 'Selection' },
      { name: 'scaleSite', label: 'Scale Site', upper: true, maxLength: 4, group: 'Selection' },
      ...HISTORY_FILTER_FIELDS,
      ...historyReportConfig(SCALING_GROUPS),
    ],
    requireOneOf: [
      'regionDistrict',
      'clientNumber',
      'fileType',
      'mgmtUnitType',
      'forestFileId',
      'timberMarks',
      'regionScaled',
      'scaleSite',
    ],
    columns: [
      { key: 'timberMark', header: 'Timber Mark', sortable: true },
      { key: 'scalingPeriod', header: 'Month Scaled', format: 'date', sortable: true },
      { key: 'scaleSite', header: 'Scale Site', sortable: true },
      { key: 'districtScaledCode', header: 'District Scaled' },
      ...HISTORY_COMMON_COLUMNS,
      { key: 'volumeScaled', header: 'Volume (M3)', format: 'volume' },
      { key: 'totalAmount', header: 'Value ($)', format: 'money' },
      ...HISTORY_HOLDER_COLUMNS,
    ],
    reports: [
      {
        reportId: 'HBS3R441',
        label: 'Send PDF Report',
        params: {
          ...historyReportParams('scalingFrom', 'scalingTo'),
          INVSMRY_REGDISTSCALED: 'regionScaled',
          INVSMRY_SCALESITE: 'scaleSite',
        },
      },
    ],
  },

  // P449 — Search for A Single Issued Document
  {
    kind: 'search',
    id: 'issued-document',
    legacy: 'P449',
    area: 'queries',
    title: 'Search for A Single Issued Document',
    navLabel: 'Single Invoice / Statement',
    description: 'Enter the statement number of the document you would like to view.',
    capability: 'QUERIES_VIEW',
    notes: 'Statements older than seven years (or issued before 2003-11-01) cannot be retrieved.',
    query: 'issuedDocuments.search',
    criteria: [
      { name: 'statementNumber', label: 'Statement Number', required: true, upper: true, maxLength: 8, group: 'Statement Selection' },
    ],
    columns: ISSUED_DOC_COLUMNS,
    rowLink: SDN_LINK,
  },

  // P450 → P451 / P461 / P456 / P466 / P471 / P476
  {
    kind: 'search',
    id: 'issued-documents',
    legacy: 'P450/P451/P461/P456/P466/P471/P476',
    area: 'queries',
    title: 'Search for Issued Documents',
    navLabel: 'Multiple Invoices / Statements',
    capability: 'QUERIES_VIEW',
    notes: 'Please enter at least one date range. Statements older than seven years cannot be retrieved.',
    query: 'issuedDocuments.search',
    criteria: [
      {
        name: 'documentType',
        label: 'Document Type',
        type: 'radio',
        options: DOCUMENT_TYPES,
        defaultValue: 'PSI',
        required: true,
        span: 4,
        group: 'General Criteria',
      },
      { name: 'statementNumber', label: 'Issued Document Number', upper: true, maxLength: 8, group: 'General Criteria' },
      { name: 'scaleDateFrom', label: 'Scale Date From', type: 'date', group: 'General Criteria' },
      { name: 'scaleDateTo', label: 'Scale Date To', type: 'date', group: 'General Criteria' },
      { name: 'issueDateFrom', label: 'Issue Date From', type: 'date', group: 'General Criteria' },
      { name: 'issueDateTo', label: 'Issue Date To', type: 'date', group: 'General Criteria' },
      { name: 'timberMark', label: 'Timber Mark', upper: true, maxLength: 6, group: 'General Criteria' },
      { name: 'scaleSite', label: 'Scale Site', upper: true, maxLength: 4, group: 'General Criteria' },
      { name: 'population', label: 'Population', maxLength: 4, group: 'Population/Stratum/Year (P, P/Y or P/S/Y)' },
      { name: 'stratum', label: 'Stratum', maxLength: 2, group: 'Population/Stratum/Year (P, P/Y or P/S/Y)' },
      { name: 'samplingYear', label: 'Year', type: 'number', maxLength: 4, group: 'Population/Stratum/Year (P, P/Y or P/S/Y)' },
      { name: 'regionScaled', label: 'Region / District Scaled', codeList: 'codes.orgUnits', group: 'General Criteria' },
      { name: 'regionHarvested', label: 'Region / District Harvested', codeList: 'codes.orgUnits', group: 'General Criteria' },
      ...CLIENT_ASSOCIATION_FIELDS,
      { name: 'clientLocation', label: 'Loc', maxLength: 2, group: CLIENT_GROUP },
      { name: 'transmissionId', label: 'Transmission ID', type: 'number', group: CLIENT_GROUP },
    ],
    requireOneOf: ['scaleDateFrom', 'scaleDateTo', 'issueDateFrom', 'issueDateTo', 'transmissionId', 'statementNumber'],
    columns: ISSUED_DOC_COLUMNS,
    rowLink: SDN_LINK,
    reports: ISSUED_DOC_REPORTS,
  },

  // P452 / P453 — Transmissions of Issued Statements (Home: ?recent=Y)
  {
    kind: 'search',
    id: 'statement-transmissions',
    legacy: 'P452/P453',
    area: 'queries',
    title: 'Search for Transmissions of Issued Statements',
    navLabel: 'Transmission Records for Invoices and Statements',
    description: 'Transmissions Between the From Date and To Date, or the last 30 days.',
    capability: 'QUERIES_VIEW',
    query: 'statementTransmissions.search',
    criteria: [
      { name: 'clientNumber', label: 'Client Number', type: 'client' },
      { name: 'clientLocation', label: 'Client Location', maxLength: 2, helperText: 'Blank for All Locations' },
      { name: 'fromDate', label: 'From Date', type: 'date', group: 'Transmissions Between ...' },
      { name: 'toDate', label: 'To Date', type: 'date', group: 'Transmissions Between ...' },
      {
        name: 'recipientType',
        label: 'Recipient Type',
        type: 'select',
        options: [
          { value: 'SEND_TO', label: 'Send To' },
          { value: 'COPY_TO', label: 'Copy To' },
        ],
        helperText: 'Blank for Both',
      },
      { name: 'recent', label: 'Recently Issued (last 30 days)', type: 'checkbox' },
    ],
    requireOneOf: ['fromDate', 'recent'],
    columns: [
      { key: 'clientLocnCode', header: 'Loc', sortable: true },
      { key: 'clientLocnName', header: 'Name' },
      { key: 'deliveryMethod', header: 'Method' },
      { key: 'transmissionId', header: 'Transmission ID', sortable: true },
      { key: 'fileName', header: 'XML File' },
      { key: 'pdfFileName', header: 'PDF File' },
      { key: 'creationDate', header: 'Date Created', format: 'date', sortable: true },
      { key: 'recipientTypeDescription', header: 'Recipient Type' },
      { key: 'psInvoiceCount', header: 'Piece Scale Invoices', format: 'number' },
      { key: 'psVolStatementCount', header: 'Piece Scale Volume Statements', format: 'number' },
      { key: 'wsInvoiceCount', header: 'Weight Scale Invoices', format: 'number' },
      { key: 'wsVolStatementCount', header: 'Weight Scale Volume Statements', format: 'number' },
      { key: 'compiledSampleStmtCount', header: 'Compiled Sample Statements', format: 'number' },
      { key: 'ratioStatementCount', header: 'Ratio Statements', format: 'number' },
    ],
    // P453 count links → P450 results for the transmission (pick the type there).
    rowLink: { screen: 'issued-documents', params: { transmissionId: 'transmissionId' }, label: 'Documents' },
    reports: [
      {
        reportId: 'HBS3R453',
        label: 'Print',
        params: {
          RB_CLIENT_NUMBER: 'clientNumber',
          RB_CLIENT_LOC_CODE: 'clientLocation',
          RB_CLI_LOC_OPTION: '=S',
          RB_FROM_DATE: 'fromDate',
          RB_TO_DATE: 'toDate',
          RB_RECIPIENT_TYPE: 'recipientType',
        },
      },
    ],
  },

  // P421 — Billing Summary Selection ("List of Invoices and Volume Statements")
  {
    kind: 'search',
    id: 'billing-summary',
    legacy: 'P421',
    area: 'queries',
    title: 'Billing Summary Selection',
    navLabel: 'List of Invoices and Volume Statements',
    description:
      'You must explicitly specify at least one of Region/District of Harvest, File Type, Mgmt Unit Type, Forest File ID, or Timber Mark and either Region/District of Scale or Scale Site.',
    capability: 'QUERIES_VIEW',
    query: 'billingSummary.search',
    criteria: [
      { name: 'scaleType', label: 'Scale Type', type: 'radio', options: SCALE_TYPES, defaultValue: 'P', required: true, span: 2 },
      { name: 'issueDateFrom', label: 'Billing Date Range From', type: 'date', group: 'Please enter at least one date range (up to 12 months)' },
      { name: 'issueDateTo', label: 'Billing Date Range To', type: 'date', group: 'Please enter at least one date range (up to 12 months)' },
      { name: 'scaleDateFrom', label: 'Scale Date Range From', type: 'date', group: 'Please enter at least one date range (up to 12 months)' },
      { name: 'scaleDateTo', label: 'Scale Date Range To', type: 'date', group: 'Please enter at least one date range (up to 12 months)' },
      { name: 'regionHarvested', label: 'Region / District of Harvest', codeList: 'codes.orgUnits', group: 'Harvest' },
      { name: 'fileType', label: 'File Type', codeList: 'codes.queries.fileTypes', group: 'Harvest' },
      { name: 'mgmtUnitType', label: 'Mgmt Unit Type', type: 'select', options: MGMT_UNIT_TYPES, group: 'Harvest' },
      { name: 'mgmtUnitNo', label: 'Mgmt Unit No', upper: true, maxLength: 4, group: 'Harvest' },
      { name: 'forestFileId', label: 'Forest File ID', upper: true, maxLength: 10, group: 'Harvest' },
      { name: 'timberMark', label: 'Timber Mark', upper: true, maxLength: 6, group: 'Harvest' },
      { name: 'regionScaled', label: 'Region / District of Scale', codeList: 'codes.orgUnits', group: 'Scale' },
      { name: 'scaleSite', label: 'Scale Site', upper: true, maxLength: 4, group: 'Scale' },
      { name: 'population', label: 'Pop', maxLength: 4, group: 'Pop/Strat/Year (P, P/Y, P/S/Y) (Weigh scale only)' },
      { name: 'stratum', label: 'Strat', maxLength: 2, group: 'Pop/Strat/Year (P, P/Y, P/S/Y) (Weigh scale only)' },
      { name: 'samplingYear', label: 'Year', type: 'number', maxLength: 4, group: 'Pop/Strat/Year (P, P/Y, P/S/Y) (Weigh scale only)' },
      { name: 'returnCategory', label: 'Return Category', codeList: 'codes.queries.returnCategories' },
      ...CLIENT_ASSOCIATION_FIELDS,
    ],
    requireOneOf: ['issueDateFrom', 'issueDateTo', 'scaleDateFrom', 'scaleDateTo'],
    columns: [
      { key: 'timberMark', header: 'Timber Mark', sortable: true },
      { key: 'returnCategory', header: 'Return Category' },
      { key: 'statementNumber', header: 'Statement', sortable: true },
      { key: 'documentType', header: 'Type' },
      { key: 'issueDate', header: 'Issue Date', format: 'date', sortable: true },
      { key: 'scaleSite', header: 'Site' },
      { key: 'scaleDate', header: 'Scale Date', format: 'date', sortable: true },
      { key: 'districtHarvested', header: 'Dist Harv' },
      { key: 'districtScaled', header: 'Dist Scaled' },
      { key: 'logCount', header: 'Pieces', format: 'number' },
      { key: 'statementTo', header: 'Bill To' },
      { key: 'copyTo', header: 'Copy To' },
      { key: 'totalVolume', header: 'Total Volume', format: 'volume' },
      { key: 'totalValue', header: 'Total Value', format: 'money' },
    ],
    reports: [
      {
        reportId: 'HBS3R421',
        label: 'View PDF Report (Piece Scale)',
        params: {
          RB_ISSUE_DATE_FROM: 'issueDateFrom',
          RB_ISSUE_DATE_TO: 'issueDateTo',
          RB_SCALE_DATE_FROM: 'scaleDateFrom',
          RB_SCALE_DATE_TO: 'scaleDateTo',
          RB_REGION_HARVESTED: 'regionHarvested',
          RB_MGMT_UNIT_TYPE_CODE: 'mgmtUnitType',
          RB_MGMT_UNIT_NUMBER: 'mgmtUnitNo',
          RB_LICENCE: 'forestFileId',
          RB_TIMBER_MARK: 'timberMark',
          RB_REGION_SCALED: 'regionScaled',
          RB_SCALE_SITE: 'scaleSite',
          RB_RETURN_CATEGORY: 'returnCategory',
          RB_CLIENT_ASSOCIATION: '=BOTH',
          RB_CLIENT_NUMBER: 'clientNumber',
        },
      },
      {
        reportId: 'HBS3R422',
        label: 'View PDF Report (Weight Scale)',
        params: {
          RB_ISSUE_DATE_FROM: 'issueDateFrom',
          RB_ISSUE_DATE_TO: 'issueDateTo',
          RB_SCALE_DATE_FROM: 'scaleDateFrom',
          RB_SCALE_DATE_TO: 'scaleDateTo',
          RB_REGION_HARVESTED: 'regionHarvested',
          RB_MGMT_UNIT_TYPE_CODE: 'mgmtUnitType',
          RB_MGMT_UNIT_NUMBER: 'mgmtUnitNo',
          RB_LICENCE: 'forestFileId',
          RB_TIMBER_MARK: 'timberMark',
          RB_REGION_SCALED: 'regionScaled',
          RB_SCALE_SITE: 'scaleSite',
          RB_POPULATION: 'population',
          RB_STRATUM: 'stratum',
          RB_SAMPLINGYEAR: 'samplingYear',
          RB_RETURN_CATEGORY: 'returnCategory',
          RB_CLIENT_ASSOCIATION: '=BOTH',
          RB_CLIENT_NUMBER: 'clientNumber',
        },
      },
    ],
  },

  // P401 — Configure Scale Site Summary Report
  {
    kind: 'search',
    id: 'scale-site-summary',
    legacy: 'P401',
    area: 'queries',
    title: 'Configure Scale Site Summary Report',
    navLabel: 'Scale Site Summary',
    capability: 'QUERIES_VIEW',
    notes: 'The list shows the active summary-return versions selected; the PDF reports group and total them (Piece Scale cannot be grouped by Scale Site and Population).',
    query: 'scaleSiteSummary.search',
    criteria: [
      { name: 'returnType', label: 'Return Type', type: 'radio', required: true, defaultValue: 'P', options: [...SCALE_TYPES, { value: 'S', label: 'Sample Scale' }], span: 2, group: 'General Criteria' },
      { name: 'versionStatus', label: 'Version Status', codeList: 'codes.versionStates', group: 'General Criteria' },
      { name: 'scaleDateFrom', label: 'Scale Date From', type: 'date', required: true, group: 'General Criteria' },
      { name: 'scaleDateTo', label: 'Scale Date To', type: 'date', required: true, group: 'General Criteria' },
      { name: 'districtScaled', label: 'Sites in Region/District', codeList: 'codes.orgUnits', group: 'Scale Site' },
      { name: 'scaleSite', label: 'Single Site', upper: true, maxLength: 4, group: 'Scale Site' },
      { name: 'districtHarvested', label: 'Marks in Region/District', codeList: 'codes.orgUnits', group: 'Timber Mark' },
      { name: 'timberMark', label: 'Single Mark', upper: true, maxLength: 6, group: 'Timber Mark' },
      { name: 'scalerLicence', label: 'Scaler Licence', upper: true, maxLength: 4, group: 'General Criteria' },
      { name: 'population', label: 'Population', maxLength: 4, group: 'Population/Stratum/Year (P, P/Y or P/S/Y)' },
      { name: 'stratum', label: 'Stratum', maxLength: 2, group: 'Population/Stratum/Year (P, P/Y or P/S/Y)' },
      { name: 'samplingYear', label: 'Year', type: 'number', maxLength: 4, group: 'Population/Stratum/Year (P, P/Y or P/S/Y)' },
      {
        name: 'clientAssociation',
        label: 'Client Association',
        type: 'radio',
        defaultValue: 'ALL',
        options: [
          { value: 'ALL', label: 'All' },
          { value: 'MARK HOLDER', label: 'Mark Holder' },
          { value: 'SITE OWNER', label: 'Site Owner' },
          { value: 'STRATUM OWNER', label: 'Stratum Owner' },
        ],
        span: 4,
        group: CLIENT_GROUP,
      },
      { name: 'clientNumber', label: 'Client No.', type: 'client', group: CLIENT_GROUP },
      { name: 'clientLocation', label: 'Loc', maxLength: 2, group: CLIENT_GROUP },
    ],
    columns: [
      { key: 'districtScaledCode', header: 'District', sortable: true },
      { key: 'scaleSite', header: 'Scale Site', sortable: true },
      { key: 'siteName', header: 'Site Name' },
      { key: 'timberMark', header: 'Timber Mark', sortable: true },
      { key: 'documentControlNumber', header: 'SDN' },
      { key: 'version', header: 'Version' },
      { key: 'sclRtnVersionStateCode', header: 'Status' },
      { key: 'scaleDate', header: 'Scale Date', format: 'date', sortable: true },
      { key: 'scalerLicence', header: 'Scaler Licence' },
      { key: 'populationNumber', header: 'Pop' },
      { key: 'stratumNumber', header: 'Str' },
      { key: 'samplingYear', header: 'Yr' },
    ],
    rowLink: SDN_LINK,
    reports: [
      { reportId: 'HBS2R401GroupDistScale', label: 'Piece - District and Scale Site', params: siteSummaryParams('DISTRICTSCALESITE') },
      { reportId: 'HBS2R401GroupScaleTM', label: 'Piece - Scale Site and Timber Mark', params: siteSummaryParams('SCALESITETIMBERMARK') },
      { reportId: 'HBS2R402GroupDistScale', label: 'Weight - District and Scale Site', params: siteSummaryParams('DISTRICTSCALESITE') },
      { reportId: 'HBS2R402GroupScaleTM', label: 'Weight - Scale Site and Timber Mark', params: siteSummaryParams('SCALESITETIMBERMARK') },
      { reportId: 'HBS2R402GroupScalePop', label: 'Weight - Scale Site and Population', params: siteSummaryParams('SCALESITEPOPULATION') },
      {
        reportId: 'HBS2R403GroupDistScale',
        label: 'Sample - District and Scale Site',
        params: { ...siteSummaryParams('DISTRICTSCALESITE') },
      },
      { reportId: 'HBS2R403GroupScaleTM', label: 'Sample - Scale Site and Timber Mark', params: siteSummaryParams('SCALESITETIMBERMARK') },
      { reportId: 'HBS2R403GroupScalePop', label: 'Sample - Scale Site and Population', params: siteSummaryParams('SCALESITEPOPULATION') },
    ],
  },

  // P403 — Configure Aged Unbilled Scale Report (ministry only)
  {
    kind: 'search',
    id: 'aged-unbilled-scale',
    legacy: 'P403',
    area: 'queries',
    title: 'Configure Aged Unbilled Scale Report',
    navLabel: 'Aged Unbilled Scale',
    capability: 'MINISTRY',
    query: 'agedUnbilled.search',
    criteria: [
      { name: 'scaleMethod', label: 'Scale Method', type: 'radio', options: SCALE_TYPES, defaultValue: 'P', required: true, span: 2 },
      { name: 'scaledPriorTo', label: 'Scaled Prior To', type: 'date', required: true },
      { name: 'coastRate', label: 'Coast Area $', type: 'number', maxLength: 6, group: 'Estimated Average Rates (report only)' },
      { name: 'northRate', label: 'North Area $', type: 'number', maxLength: 6, group: 'Estimated Average Rates (report only)' },
      { name: 'southRate', label: 'South Area $', type: 'number', maxLength: 6, group: 'Estimated Average Rates (report only)' },
    ],
    columns: [
      { key: 'regionName', header: 'Region' },
      { key: 'documentControlNumber', header: 'Document' },
      { key: 'version', header: 'Version' },
      { key: 'sclRtnVersionStateCode', header: 'Status' },
      { key: 'scaleDate', header: 'Scale Date', format: 'date', sortable: true },
      { key: 'scaleSite', header: 'Scale Site', sortable: true },
      { key: 'volume', header: 'Volume (M3)', format: 'volume' },
      { key: 'weight', header: 'Weight', format: 'number' },
    ],
    reports: [
      {
        reportId: 'HBS2R411',
        label: 'View PDF Report (Piece Scale)',
        params: {
          Avg_Rate_Coast: 'coastRate',
          Avg_Rate_NInterior: 'northRate',
          Avg_Rate_SInterior: 'southRate',
          PSAGED_SCALED_PRIORTO: 'scaledPriorTo',
        },
      },
      {
        reportId: 'HBS2R412',
        label: 'View PDF Report (Weight Scale)',
        params: {
          Avg_Rate_Coast: 'coastRate',
          Avg_Rate_NInterior: 'northRate',
          Avg_Rate_SInterior: 'southRate',
          WSAGED_SCALED_PRIORTO: 'scaledPriorTo',
          WSAGED_JOB: '=0',
        },
      },
    ],
  },

  // P490 — Cut to Cruise Comparison Report
  {
    kind: 'search',
    id: 'cut-to-cruise',
    legacy: 'P490',
    area: 'queries',
    title: 'Cut to Cruise Comparison Report',
    navLabel: 'Cut to Cruise Comparison',
    description: 'One of Timber Mark, Forest File ID, Client, or (Client and Location) must be specified.',
    capability: 'QUERIES_VIEW',
    query: 'cutToCruise.search',
    criteria: [
      { name: 'timberMark', label: 'Timber Mark', upper: true, maxLength: 6 },
      { name: 'forestFileId', label: 'Forest File ID', upper: true, maxLength: 10 },
      { name: 'clientNumber', label: 'Client', type: 'client' },
      { name: 'clientLocation', label: 'Loc', maxLength: 2 },
      { name: 'expiryDate', label: 'Exclude Marks Expiring On or Before', type: 'date', required: true },
    ],
    requireOneOf: ['timberMark', 'forestFileId', 'clientNumber'],
    columns: [
      { key: 'clientNumber', header: 'Client' },
      { key: 'clientLocnCode', header: 'Loc' },
      { key: 'licence', header: 'Forest File ID', sortable: true },
      { key: 'cuttingPermitId', header: 'CP' },
      { key: 'timberMark', header: 'Timber Mark', sortable: true },
      { key: 'fileTypeDescr', header: 'File Type' },
      { key: 'markExpiryDate', header: 'Expiry Date', format: 'date' },
      { key: 'markExtendDate', header: 'Extended Date', format: 'date' },
      { key: 'minBilledDate', header: 'First Billed', format: 'date' },
      { key: 'maxBilledDate', header: 'Last Billed', format: 'date' },
      { key: 'billedVolume', header: 'Billed Volume', format: 'volume' },
      { key: 'appraisalVolume', header: 'Cruise Volume', format: 'volume' },
    ],
    rowLink: { screen: 'timber-mark', params: { timberMark: 'timberMark' } },
    reports: [
      {
        reportId: 'HBS3R490',
        label: 'Print Report',
        params: {
          PTIMBERMARK: 'timberMark',
          PLICENSE: 'forestFileId',
          PCLIENTNUM: 'clientNumber',
          PCLIENTLOC: 'clientLocation',
          PEXPDATE: 'expiryDate',
        },
      },
    ],
  },

  // P480 — Search for Timber Mark
  {
    kind: 'search',
    id: 'timber-mark-search',
    legacy: 'P480',
    area: 'queries',
    title: 'Timber Mark Query',
    navLabel: 'Timber Mark Information',
    description: 'Please enter Timber Mark',
    capability: 'QUERIES_VIEW',
    query: 'timberMarks.search',
    criteria: [{ name: 'timberMark', label: 'Timber Mark', required: true, upper: true, maxLength: 6, group: 'Timber Mark Selection' }],
    columns: [
      { key: 'timberMark', header: 'Timber Mark', sortable: true },
      { key: 'cuttingPermitId', header: 'Cutting Permit' },
      { key: 'forestFileId', header: 'Licence', sortable: true },
      { key: 'markStatusDescription', header: 'Status' },
      { key: 'districtCode', header: 'Admin Org' },
      { key: 'markIssueDate', header: 'Issued Date', format: 'date' },
      { key: 'markExpiryDate', header: 'Expiry Date', format: 'date' },
      { key: 'clientNumber', header: 'Client No' },
      { key: 'clientName', header: 'Client Name' },
    ],
    rowLink: { screen: 'timber-mark', params: { timberMark: 'timberMark' } },
  },

  // P481 — Timber Mark Query (detail)
  {
    kind: 'detail',
    id: 'timber-mark',
    legacy: 'P481',
    area: 'queries',
    title: 'Timber Mark Query',
    nav: false,
    capability: 'QUERIES_VIEW',
    query: 'timberMarks.detail',
    keys: ['timberMark'],
    sections: [
      {
        title: 'Timber Mark Info',
        fields: [
          { key: 'timberMark', label: 'Timber Mark' },
          { key: 'cuttingPermitId', label: 'Cutting Permit' },
          { key: 'saleMethodDescription', label: 'Sale Method' },
          { key: 'markStatusDescription', label: 'Status' },
          { key: 'catastrophicInd', label: 'Catastrophic', format: 'yesno' },
          { key: 'adminOrgName', label: 'Admin Org' },
          { key: 'markExpiryDate', label: 'Expiry Date', format: 'date' },
          { key: 'cruiseBasedInd', label: 'Cruise or Area Based' },
          { key: 'geoOrgName', label: 'Geo Org' },
          { key: 'markExtendDate', label: 'Extended Date', format: 'date' },
          { key: 'sbCategory', label: 'SB Category' },
          { key: 'quotaTypeDescription', label: 'Quota Type' },
          { key: 'markIssueDate', label: 'Issued Date', format: 'date' },
          { key: 'sbFundInd', label: 'SB Fund', format: 'yesno' },
          { key: 'regionName', label: 'Region' },
          { key: 'location', label: 'Location' },
        ],
      },
      {
        title: 'Tenure Info',
        fields: [
          { key: 'licence', label: 'Licence' },
          { key: 'fileTypeDescription', label: 'File Type' },
          { key: 'aac', label: 'AAC', format: 'number' },
          { key: 'fileStatusDescription', label: 'File Status' },
          { key: 'awardedDate', label: 'Awarded Date', format: 'date' },
          { key: 'paymentMethodDescription', label: 'Payment Method' },
          { key: 'tenureExpiryDate', label: 'Expiry Date', format: 'date' },
          { key: 'mgmtUnitTypeDescription', label: 'MGU Type' },
          { key: 'tenureExtendedDate', label: 'Extended Date', format: 'date' },
          { key: 'mgmtUnitId', label: 'MGU Id' },
          { key: 'extensions', label: 'Extensions', format: 'number' },
        ],
      },
      {
        title: 'Client Info',
        fields: [
          { key: 'clientNumber', label: 'Client No' },
          { key: 'clientName', label: 'Client Name' },
          { key: 'clientLocnCode', label: 'Location' },
          { key: 'clientLocnName', label: 'Location Name' },
          { key: 'clientTypeDescription', label: 'Type' },
          { key: 'clientStatusDescription', label: 'Status' },
          { key: 'address1', label: 'Address' },
          { key: 'address2', label: '' },
          { key: 'address3', label: '' },
          { key: 'city', label: 'City' },
          { key: 'province', label: 'Province' },
          { key: 'country', label: 'Country' },
          { key: 'postalCode', label: 'Postal Code' },
        ],
      },
      {
        title: 'Appraisal Info',
        fields: [
          { key: 'appraisalStsSt', label: 'Status' },
          { key: 'appEffectiveDate', label: 'Effective', format: 'date' },
          { key: 'rateCalcMthdCd', label: 'Rate Calc' },
          { key: 'appraisalMethodDescription', label: 'Method' },
          { key: 'appExpiryDate', label: 'Expiry', format: 'date' },
          { key: 'adjustQrterlyInd', label: 'Adj. Quarterly', format: 'yesno' },
          { key: 'ttlMerchntblArea', label: 'Total Merchantable Area (Ha)', format: 'number' },
          { key: 'othersTiedToCd', label: 'Tied To' },
          { key: 'conifStandRateEligDescription', label: 'Coniferous Stand Rate Eligibility' },
          { key: 'decidStandRateEligDescription', label: 'Deciduous Stand Rate Eligibility' },
          { key: 'netCruiseVolume', label: 'Net', format: 'volume' },
          { key: 'intDeciduousVol', label: 'Deciduous', format: 'volume' },
          { key: 'totalCruiseVolume', label: 'Total', format: 'volume' },
        ],
      },
      {
        title: 'Appraised Species / Cruise Volume',
        table: {
          query: 'timberMarks.species',
          params: { timberMark: 'timberMark' },
          columns: [
            { key: 'species', header: 'Appraised Species' },
            { key: 'speciesVolume', header: 'Cruise Volume', format: 'volume' },
          ],
        },
      },
      {
        title: 'Stumpage Rates',
        table: {
          query: 'timberMarks.stumpageRates',
          params: { timberMark: 'timberMark' },
          columns: [
            { key: 'stmpgRteEfctvDt', header: 'Rate Effective Date', format: 'date' },
            { key: 'reserveRate', header: 'Reserve Rate', format: 'money' },
            { key: 'developmentLevy', header: 'Dev. Levy', format: 'money' },
            { key: 'silvicultureLevy', header: 'Silv. Levy', format: 'money' },
            { key: 'bonusBidAmount', header: 'Bonus Bid', format: 'money' },
            { key: 'standRate', header: 'Stand Rate', format: 'money' },
          ],
        },
      },
      {
        title: 'Species / Grade / Product Rates',
        table: {
          query: 'timberMarks.spgRates',
          params: { timberMark: 'timberMark' },
          columns: [
            { key: 'speciesCode', header: 'Species Code' },
            { key: 'gradeCode', header: 'Grade Code' },
            { key: 'productCode', header: 'Product Code' },
            { key: 'reserveRate', header: 'Reserve Rate', format: 'money' },
            { key: 'developmentLevy', header: 'Dev. Levy', format: 'money' },
            { key: 'silvicultureLevy', header: 'Silv. Levy', format: 'money' },
            { key: 'bonusBidAmount', header: 'Bonus Bid', format: 'money' },
            { key: 'fixedRate', header: 'Fixed Rate', format: 'money' },
          ],
        },
      },
      {
        title: 'Appraisal History',
        table: {
          query: 'timberMarks.appraisals',
          params: { timberMark: 'timberMark' },
          columns: [
            { key: 'appEffectiveDate', header: 'Effective', format: 'date' },
            { key: 'appExpiryDate', header: 'Expiry', format: 'date' },
            { key: 'appraisalStsSt', header: 'Status' },
            { key: 'rateCalcMthdCd', header: 'Rate Calc' },
            { key: 'appraisalMethodDescription', header: 'Method' },
            { key: 'adjustQrterlyInd', header: 'Adj. Quarterly' },
            { key: 'ttlMerchntblArea', header: 'Total Merchantable Area (Ha)', format: 'number' },
            { key: 'netCruiseVolume', header: 'Net Cruise Volume', format: 'volume' },
          ],
        },
      },
    ],
  },
];
