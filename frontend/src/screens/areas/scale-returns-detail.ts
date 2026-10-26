import type { ActionDef, ColumnDef, FieldDef, LinkDef, Option, ReportLinkDef, ScreenDef } from '../types';
import { SAMPLING_YEAR_OPTIONS } from '@/screens/years';

/**
 * Scale Returns — Detail (legacy dac/detail): detail scale return searches
 * (P046 / P047 / P048, Home work queues and Error Categories), the shared
 * result list (P551 / P581 / P601 / P651 / P751 / P701) and the read-only
 * return views (P552 / P602 / P652 / P752 / P702 + P556 / P557 / P656 / P657 / P756).
 *
 * Backend: catalog/DetailReturnsCatalog.java (detailReturns.search, codes.detailReturns.*)
 * and catalog/DetailReturnsViewCatalog.java (detailReturns.version and child lists).
 * Data entry and the workflow buttons (Save / Release / Hold / Re-Edit / Discard /
 * Cancel / Replace / Approve / Reject / Clear Digital Signature Failure / bulk
 * requests) are not single table-API proc calls in the legacy app — they are
 * DetailManagerBean / WorkflowManagerBean logic — so they are not offered here;
 * see docs/areas/detail-returns.md.
 */

const RETURN_TYPES: Option[] = [
  { value: 'P', label: 'Log Tallies' },
  { value: 'W', label: 'Weigh Slips' },
  { value: 'S', label: 'Sample Tallies' },
  { value: 'A', label: 'Arrival Ledgers' },
  { value: 'D', label: 'Departure Ledgers' },
  { value: 'X', label: 'Log and Sample Tallies' },
];

const BATCH_RETURN_TYPES: Option[] = [
  { value: 'P', label: 'Log Tallies' },
  { value: 'W', label: 'Weigh Slips' },
  { value: 'S', label: 'Sample Tallies' },
  { value: 'A', label: 'Arrival Ledgers' },
  { value: 'D', label: 'Departure Ledgers' },
  { value: 'F', label: 'Special Forest Product Tallies' },
];

/** DacConstants.VERSION_STATUS — the 12 statuses offered by P046, in legacy order. */
const VERSION_STATUSES: Option[] = [
  { value: 'ACC', label: 'Accepted' },
  { value: 'DSF', label: 'Digital Signature Failure' },
  { value: 'INC', label: 'Incomplete' },
  { value: 'AWP', label: 'Awaiting Approval' },
  { value: 'RDY', label: 'Ready' },
  { value: 'ERR', label: 'In Error' },
  { value: 'HLD', label: 'Held' },
  { value: 'NTI', label: 'Not To Be Billed' },
  { value: 'LCK', label: 'Locked' },
  { value: 'ISS', label: 'Issued' },
  { value: 'CAN', label: 'Cancelled' },
  { value: 'DEL', label: 'Discarded' },
];

const ACTIVE_ONLY: FieldDef = {
  name: 'activeOnly',
  label: 'Only active versions',
  type: 'yesno',
  defaultValue: 'Y',
  helperText: 'Only active versions will be returned.',
};

const RESULT_COLUMNS: ColumnDef[] = [
  { key: 'ddn', header: 'Detail Document Number', sortable: true },
  { key: 'version', header: 'Version Number', sortable: true },
  { key: 'statusDesc', header: 'Status', sortable: true },
  { key: 'eventTypeDesc', header: 'Event Type', sortable: true },
  { key: 'scaleSiteDisplay', header: 'Scale Site', sortable: true },
  { key: 'eventDate', header: 'Scale Date', format: 'date', sortable: true },
  { key: 'popStratYear', header: 'Population/Stratum/Year', sortable: true },
  { key: 'timberMark', header: 'Timber Mark', sortable: true },
  { key: 'weighSlipNumber', header: 'Weigh Slip Number', sortable: true },
  { key: 'primaryLicenceDisplay', header: 'Scaler Licence', sortable: true },
  { key: 'returnNumber', header: 'Return Number', sortable: true },
  { key: 'loadArrivalNumber', header: 'Load Arrival Number', sortable: true },
  { key: 'sampledWeighSlip', header: 'Sampled Weigh Slip' },
  { key: 'logCount', header: 'Log Count', format: 'number' },
  { key: 'netVolume', header: 'Net Volume Total', format: 'volume' },
  { key: 'netWeight', header: 'Net Weight', format: 'number' },
  { key: 'loadDepartureNumber', header: 'Departure Number' },
  { key: 'destination', header: 'Destination Site' },
];

const TO_RETURN: LinkDef = { screen: 'detail-return', params: { ddn: 'ddn', version: 'version' } };

/** Legacy "Print" on P551 / P701: ReportingFactory2.createDetailScaleListReport. */
const LIST_REPORTS: ReportLinkDef[] = [
  {
    reportId: 'HBS2R551',
    label: 'Print Log Tally List',
    params: {
      IS_COUNT: '=N',
      PSR_DOCTYPE: '=P',
      PSR_SCALESITE: 'scaleSite',
      PSR_TIMBERMARK: 'timberMark',
      PSR_LICENCENUMBER: 'scalerLicence',
      PSR_SCALEDDATEFROM: 'scaleDateFrom',
      PSR_SCALEDDATETO: 'scaleDateTo',
      PSR_VERSIONSTATUSCODE: 'status',
      PSR_EVENTTYPE: 'eventType',
      PSR_ERRORCODE: 'errorCode',
      PSR_ACTIVEVERSIONINDICATOR: '=Y',
      PSR_LIMITYEARIND: '=Y',
      PSR_ORDERBY: '=3',
    },
  },
  {
    reportId: 'HBS2R601',
    label: 'Print Weigh Slip List',
    params: {
      IS_COUNT: '=N',
      PSR_SCALESITE: 'scaleSite',
      PSR_TIMBERMARK: 'timberMark',
      PSR_LICENCENUMBER: 'scalerLicence',
      PSR_POPULATION: 'population',
      PSR_STRATUM: 'stratum',
      PSR_SAMPLINGYEAR: 'samplingYear',
      PSR_SCALEDDATEFROM: 'scaleDateFrom',
      PSR_SCALEDDATETO: 'scaleDateTo',
      PSR_VERSIONSTATUSCODE: 'status',
      PSR_EVENTTYPE: 'eventType',
      PSR_ERRORCODE: 'errorCode',
      PSR_ACTIVEVERSIONINDICATOR: '=Y',
      PSR_LIMITYEARIND: '=Y',
      PSR_ORDERBY: '=3',
    },
  },
  {
    reportId: 'HBS2R651',
    label: 'Print Sample Tally List',
    params: {
      IS_COUNT: '=N',
      PSR_SCALESITE: 'scaleSite',
      PSR_TIMBERMARK: 'timberMark',
      PSR_LICENCENUMBER: 'scalerLicence',
      PSR_POPULATION: 'population',
      PSR_STRATUM: 'stratum',
      PSR_SAMPLINGYEAR: 'samplingYear',
      PSR_SCALEDDATEFROM: 'scaleDateFrom',
      PSR_SCALEDDATETO: 'scaleDateTo',
      PSR_VERSIONSTATUSCODE: 'status',
      PSR_EVENTTYPE: 'eventType',
      PSR_ERRORCODE: 'errorCode',
      PSR_ACTIVEVERSIONINDICATOR: '=Y',
      PSR_LIMITYEARIND: '=Y',
      PSR_ORDERBY: '=3',
    },
  },
  {
    reportId: 'HBS2R701',
    label: 'Print Ledger List',
    params: {
      IS_COUNT: '=N',
      PSR_LEDGERTYPE: 'returnType',
      PSR_SCALESITE: 'scaleSite',
      PSR_TIMBERMARK: 'timberMark',
      PSR_PRIMARYSCALER: 'scalerLicence',
      PSR_SCALEDFROMDATE: 'scaleDateFrom',
      PSR_SCALEDTODATE: 'scaleDateTo',
      PSR_VERSIONSTATUSCODE: 'status',
      PSR_EVENTTYPE: 'eventType',
      PSR_ERRORCODE: 'errorCode',
      PSR_ACTIVEVERSIONINDICATOR: '=Y',
      PSR_LIMITYEARIND: '=Y',
      PSR_ORDERBY: '=3',
    },
  },
];

/**
 * P551 / P601 / P651 "Generate PDF" (P046Action GEN_PDF → ReportingFactory2.createDetailScaleReportDocd):
 * the delivery copy of every listed return with its logs (HBS2R552docd / 602docd / 652docd).
 * The user type and job number prompts are filled by the backend; MINREC / MAXREC are left blank (all rows).
 */
const DELIVERY_REPORTS: ReportLinkDef[] = [
  {
    reportId: 'HBS2R552docd',
    label: 'Generate PDF',
    showWhen: { field: 'returnType', values: ['P'] },
    params: {
      PSVERSIONSTATUSCODE: 'status',
      PSEVENTTYPE: 'eventType',
      PSSCALEDDATEFROM: 'scaleDateFrom',
      PSSCALEDDATETO: 'scaleDateTo',
      PSTIMBERMARK: 'timberMark',
      PSSCALESITE: 'scaleSite',
      PSLICENCENUMBER: 'scalerLicence',
      PSDISTRICTSCALED: 'siteOrgUnit',
      PSDISTRICTHARVESTED: 'markOrgUnit',
      PSACTIVEVERSIONINDICATOR: 'activeOnly',
      PSORDERBY: '=0',
    },
  },
  {
    reportId: 'HBS2R602docd',
    label: 'Generate PDF',
    showWhen: { field: 'returnType', values: ['W'] },
    params: {
      WSRETURN_DISTRICTSCALED: 'siteOrgUnit',
      WSRETURN_DISTRICTHARVESTED: 'markOrgUnit',
      WSRETURN_TIMBERMARK: 'timberMark',
      WSRETURN_SCALESITE: 'scaleSite',
      WSRETURN_LICENCENUMBER: 'scalerLicence',
      WSRETURN_POPULATION: 'population',
      WSRETURN_STRATUM: 'stratum',
      WSRETURN_SAMPLINGYEAR: 'samplingYear',
      WSRETURN_SCALEDDATEFROM: 'scaleDateFrom',
      WSRETURN_SCALEDDATETO: 'scaleDateTo',
      WSRETURN_VERSIONSTATUSCODE: 'status',
      WSRETURN_EVENTTYPE: 'eventType',
      WSRETURN_ACTIVEVERSIONIND: 'activeOnly',
      WSRETURN_ORDERBY: '=0',
    },
  },
  {
    reportId: 'HBS2R652docd',
    label: 'Generate PDF',
    showWhen: { field: 'returnType', values: ['S'] },
    params: {
      SSRETURN_DISTRICTSCALED: 'siteOrgUnit',
      SSRETURN_DISTRICTHARVESTED: 'markOrgUnit',
      SSRETURN_TIMBERMARK: 'timberMark',
      SSRETURN_SCALESITE: 'scaleSite',
      SSRETURN_LICENCENUMBER: 'scalerLicence',
      SSRETURN_POPULATION: 'population',
      SSRETURN_STRATUM: 'stratum',
      SSRETURN_SAMPLINGYEAR: 'samplingYear',
      SSRETURN_SCALEDDATEFROM: 'scaleDateFrom',
      SSRETURN_SCALEDDATETO: 'scaleDateTo',
      SSRETURN_VERSIONSTATUSCODE: 'status',
      SSRETURN_EVENTTYPE: 'eventType',
      SSRETURN_ACTIVEVERSIONIND: 'activeOnly',
      SSRETURN_ORDERBY: '=0',
    },
  },
];

const SITE_CRITERIA: FieldDef[] = [
  { name: 'scaleSite', label: 'Single Site', maxLength: 4, upper: true, group: 'Scale Site' },
  { name: 'siteOrgUnit', label: 'Sites in Region/District', type: 'select', codeList: 'codes.orgUnits', group: 'Scale Site' },
];

/** P046 "Associated Sites" / "Sites in Associated Districts" (HBS2R551 PSR_SearchBySites on the user id). */
const SITE_SCOPE_OPTIONS = [
  { value: 'ASSOCIATED_SITES', label: 'Associated Sites' },
  { value: 'ASSOCIATED_DISTRICTS', label: 'Sites in Associated Districts' },
];

const SITE_SCOPE: FieldDef = {
  name: 'scope',
  label: 'Data Domain',
  type: 'select',
  options: SITE_SCOPE_OPTIONS,
  group: 'Scale Site',
};

/**
 * Home work queues (P046Action.setSearchCriteriaForHome, BT 3268 / BT 7126): scale admins see
 * sites in their associated districts, site admins their associated sites, scalers the returns of
 * their own licence; with both roles, the union.
 */
const WORKBENCH_SCOPE: FieldDef = {
  ...SITE_SCOPE,
  options: [
    ...SITE_SCOPE_OPTIONS,
    { value: 'OWN_LICENCE', label: 'My Scaler Licence' },
    { value: 'MOF_DUAL', label: 'Sites in Associated Districts and My Scaler Licence' },
    { value: 'CLIENT_DUAL', label: 'Associated Sites and My Scaler Licence' },
  ],
  roleDefaults: [
    { roles: ['HBS_SCALE_ADMIN'], unlessRoles: ['HBS_MOF_SCALER'], value: 'ASSOCIATED_DISTRICTS' },
    { roles: ['HBS_MOF_SCALER'], unlessRoles: ['HBS_SCALE_ADMIN'], value: 'OWN_LICENCE' },
    { roles: ['HBS_SCALE_ADMIN'], value: 'MOF_DUAL' },
    { roles: ['HBS_CLI_SITE_ADMIN'], unlessRoles: ['HBS_CLI_SCALER'], value: 'ASSOCIATED_SITES' },
    { roles: ['HBS_CLI_SCALER'], unlessRoles: ['HBS_CLI_SITE_ADMIN'], value: 'OWN_LICENCE' },
    { roles: ['HBS_CLI_SITE_ADMIN'], value: 'CLIENT_DUAL' },
  ],
};

/** P046 — Search for Detail Scale Data. */
const GENERAL_CRITERIA: FieldDef[] = [
  { name: 'returnType', label: 'Return Type', type: 'radio', required: true, options: RETURN_TYPES, defaultValue: 'W', group: 'General Criteria' },
  { name: 'status', label: 'Version Status', type: 'select', options: VERSION_STATUSES, group: 'General Criteria' },
  { name: 'eventType', label: 'Event Type', type: 'select', codeList: 'codes.detailReturns.eventTypes', group: 'General Criteria' },
  { name: 'errorCode', label: 'Error Code', maxLength: 6, upper: true, group: 'General Criteria' },
  {
    name: 'notErrorCode',
    label: 'Error Code - Invert Selection',
    maxLength: 6,
    upper: true,
    helperText: 'All errors except that specified.',
    group: 'General Criteria',
  },
  ACTIVE_ONLY,
  { name: 'scaleDateFrom', label: 'Scale Date From', type: 'date', group: 'Range of Dates' },
  { name: 'scaleDateTo', label: 'Scale Date To', type: 'date', group: 'Range of Dates' },
  { name: 'receivedDateFrom', label: 'Received Date From', type: 'date', group: 'Range of Dates' },
  { name: 'receivedDateTo', label: 'Received Date To', type: 'date', group: 'Range of Dates' },
  ...SITE_CRITERIA,
  SITE_SCOPE,
  { name: 'population', label: 'Population', maxLength: 4, upper: true, group: 'Population/Stratum/Year (P, P/Y or P/S/Y)' },
  { name: 'stratum', label: 'Stratum', maxLength: 2, upper: true, group: 'Population/Stratum/Year (P, P/Y or P/S/Y)' },
  { name: 'samplingYear', label: 'Year', type: 'select', options: SAMPLING_YEAR_OPTIONS, group: 'Population/Stratum/Year (P, P/Y or P/S/Y)' },
  {
    name: 'markBasis',
    label: 'Timber Mark Type',
    type: 'select',
    options: [
      { value: 'N', label: 'Scale-Based' },
      { value: 'Y', label: 'Cruise-Based' },
    ],
    group: 'Timber Mark',
  },
  { name: 'timberMark', label: 'Single Mark', maxLength: 6, upper: true, group: 'Timber Mark' },
  { name: 'cutBlock', label: 'Cut Block', maxLength: 10, upper: true, helperText: 'Use * as wildcard.', group: 'Timber Mark' },
  { name: 'markOrgUnit', label: 'Marks in Region/District', type: 'select', codeList: 'codes.orgUnits', group: 'Timber Mark' },
  {
    name: 'markScope',
    label: 'Marks in Associated Districts',
    type: 'select',
    options: [{ value: 'ASSOCIATED_DISTRICTS', label: 'Yes' }],
    group: 'Timber Mark',
  },
  { name: 'scalerLicence', label: 'Primary Scaler Licence', maxLength: 4, upper: true, group: 'Primary Scaler Licence' },
  { name: 'returnNumberFrom', label: 'Return Number From', type: 'number', maxLength: 4, group: 'Range of Numbers' },
  { name: 'returnNumberTo', label: 'Return Number To', type: 'number', maxLength: 4, group: 'Range of Numbers' },
  { name: 'loadNumberFrom', label: 'Load Number From', maxLength: 10, upper: true, group: 'Range of Numbers' },
  { name: 'loadNumberTo', label: 'Load Number To', maxLength: 10, upper: true, group: 'Range of Numbers' },
  { name: 'weighSlipFrom', label: 'Weigh Slip From', maxLength: 10, upper: true, group: 'Range of Numbers' },
  { name: 'weighSlipTo', label: 'Weigh Slip To', maxLength: 10, upper: true, group: 'Range of Numbers' },
  { name: 'transportId', label: 'Transport ID', maxLength: 16, upper: true, helperText: 'Use * as wildcard.', group: 'Range of Numbers' },
  { name: 'ldsNumber', label: 'Load Description Slip Number', maxLength: 10, upper: true, helperText: 'Use * as wildcard.', group: 'Range of Numbers' },
  { name: 'markHolderClient', label: 'Mark Holder', type: 'client', group: 'Client Association' },
  { name: 'siteOwnerClient', label: 'Site Owner', type: 'client', group: 'Client Association' },
  { name: 'stratumOwnerClient', label: 'Stratum Owner', type: 'client', group: 'Client Association' },
];

/** Implicit Home work-queue criteria (searchWorkbenchFilter / searchErrorCategory). */
const WORKBENCH_COMMON: FieldDef[] = [
  { name: 'returnType', label: 'Return Type', type: 'select', required: true, options: RETURN_TYPES },
  { name: 'status', label: 'Version Status', type: 'select', options: VERSION_STATUSES },
  { name: 'days', label: 'Scale date within the last (days)', type: 'number', defaultValue: '30', maxLength: 4 },
  ...SITE_CRITERIA,
  WORKBENCH_SCOPE,
  { name: 'scalerLicence', label: 'Primary Scaler Licence', maxLength: 4, upper: true },
];

const WORKBENCH_NOTES =
  'Work queues start limited to your associated sites / districts and, for scalers, your own scaler licence ' +
  '(Data Domain). Industry users only see returns of their client.';

const yesNo = 'yesno' as const;

/**
 * Workflow buttons of the detail pages (Buttons.jsp → backend detailreturns
 * commands). `showWhen` mirrors FunctionsComparator.isFieldAvailable on the
 * version status / document state; the backend re-checks the full rule
 * (roles, site / scaler / district authority) and refuses otherwise.
 */
const KEYS = { ddn: 'ddn', version: 'version', updateTimestamp: 'updateTimestamp' };
const ACTIVE = { field: 'activeInd', values: ['Y'] };
const DETAIL_ACTIONS: ActionDef[] = [
  {
    id: 'release',
    label: 'Release',
    command: 'detailReturns.release',
    capability: 'DETAIL_RETURN_EDIT',
    params: KEYS,
    showWhen: [{ field: 'status', values: ['INC', 'HLD'] }, ACTIVE],
    confirm: 'Release this scale return? It is edited again and becomes Ready or In Error.',
  },
  {
    id: 'reEdit',
    label: 'Re-Edit',
    command: 'detailReturns.reEdit',
    capability: 'DETAIL_RETURN_EDIT',
    params: KEYS,
    showWhen: [{ field: 'status', values: ['RDY', 'ERR', 'FAL', 'NTI'] }, { field: 'returnState', values: ['OIP', 'RCP', 'RNP'] }],
    confirm: 'Run the edits on this scale return again?',
  },
  {
    id: 'hold',
    label: 'Hold',
    command: 'detailReturns.hold',
    capability: 'DETAIL_RETURN_EDIT',
    params: KEYS,
    showWhen: [{ field: 'status', values: ['RDY', 'ERR', 'FAL', 'NTI'] }, ACTIVE],
    confirm: 'Hold this scale return? A new held version is created for editing.',
    then: { screen: 'detail-entry', params: { ddn: 'ddn', version: 'version' } },
  },
  {
    id: 'cancel',
    label: 'Cancel',
    command: 'detailReturns.cancel',
    capability: 'DETAIL_RETURN_EDIT',
    params: KEYS,
    showWhen: [{ field: 'status', values: ['ISS'] }, { field: 'returnState', values: ['OCM', 'RNC', 'RCC'] }, ACTIVE],
    confirm: 'Cancel this issued scale return? The cancellation needs approval.',
    danger: true,
  },
  {
    id: 'cancelReplaceChanges',
    label: 'Cancel and Replace with Changes',
    command: 'detailReturns.cancelReplaceChanges',
    capability: 'DETAIL_RETURN_EDIT',
    params: KEYS,
    showWhen: [{ field: 'status', values: ['ISS'] }, { field: 'returnState', values: ['OCM', 'RNC', 'RCC'] }, ACTIVE],
    confirm: 'Cancel this issued scale return and create a new version to replace it?',
    then: { screen: 'detail-entry', params: { ddn: 'ddn', version: 'version' } },
  },
  {
    id: 'replaceChanges',
    label: 'Replace with Changes',
    command: 'detailReturns.replaceChanges',
    capability: 'DETAIL_RETURN_EDIT',
    params: KEYS,
    showWhen: [{ field: 'status', values: ['CAN'] }, ACTIVE],
    confirm: 'Create a new version to replace this cancelled scale return?',
    then: { screen: 'detail-entry', params: { ddn: 'ddn', version: 'version' } },
  },
  {
    id: 'replaceNoChanges',
    label: 'Replace without Changes',
    command: 'detailReturns.replaceNoChanges',
    capability: 'DETAIL_RETURN_EDIT',
    params: KEYS,
    showWhen: [{ field: 'status', values: ['CAN'] }, ACTIVE],
    confirm: 'Replace this cancelled scale return without changes?',
  },
  {
    id: 'approve',
    label: 'Approve Change',
    command: 'detailReturns.approve',
    capability: 'DETAIL_RETURN_APPROVE',
    params: KEYS,
    showWhen: { field: 'status', values: ['AWP'] },
    confirm: 'Approve the requested change?',
  },
  {
    id: 'reject',
    label: 'Reject Change',
    command: 'detailReturns.reject',
    capability: 'DETAIL_RETURN_APPROVE',
    params: KEYS,
    showWhen: { field: 'status', values: ['AWP'] },
    confirm: 'Reject the requested change?',
    danger: true,
  },
  {
    id: 'clearSignatureFailure',
    label: 'Clear Digital Signature Failure',
    command: 'detailReturns.clearSignatureFailure',
    capability: 'DETAIL_RETURN_EDIT',
    params: KEYS,
    showWhen: { field: 'status', values: ['DSF'] },
    confirm: 'Clear the digital signature failure and release this scale return?',
  },
  {
    id: 'discard',
    label: 'Discard',
    command: 'detailReturns.discard',
    capability: 'DETAIL_RETURN_EDIT',
    params: KEYS,
    showWhen: { field: 'status', values: ['RDY', 'ERR', 'HLD', 'FAL', 'DSF', 'ACC', 'INC', 'AWP'] },
    confirm: 'Discard this version of the scale return?',
    danger: true,
  },
];

export const screens: ScreenDef[] = [
  {
    kind: 'search',
    id: 'detail-search',
    legacy: 'P046 / P551 / P581 / P601 / P651 / P701',
    area: 'scale-returns',
    title: 'Search for Detail Scale Data',
    navLabel: 'Detail Returns: A Set of Returns',
    description: 'Search for Detail Scale Returns.',
    capability: 'SCALE_RETURNS_VIEW',
    query: 'detailReturns.search',
    criteria: GENERAL_CRITERIA,
    requireOneOf: ['scaleDateFrom', 'receivedDateFrom'],
    columns: RESULT_COLUMNS,
    rowLink: TO_RETURN,
    reports: [...LIST_REPORTS, ...DELIVERY_REPORTS],
    createLink: { screen: 'detail-entry', params: {}, label: 'Add Detail Scale Data', capability: 'DETAIL_RETURN_EDIT' },
    notes:
      'Only active versions will be returned unless "Only active versions" is No. Returns older than seven years are not available online. ' +
      'Bulk requests (P560A): Scale Returns › Bulk Request for Detail Scale Returns (/scale-returns/detail-bulk).',
  },
  {
    kind: 'search',
    id: 'detail-batch-search',
    legacy: 'P047',
    area: 'scale-returns',
    title: 'Search for Scale Returns in Submitted Detail Batches',
    navLabel: 'Detail Returns: Returns in Submitted Batches',
    capability: 'SCALE_RETURNS_VIEW',
    query: 'detailReturns.search',
    criteria: [
      { name: 'returnType', label: 'Return Type', type: 'radio', required: true, options: BATCH_RETURN_TYPES, defaultValue: 'P', group: 'General Criteria' },
      { name: 'batchReceivedFrom', label: 'Batch Received Date From', type: 'date', required: true, group: 'Batch Received Date' },
      { name: 'batchReceivedTo', label: 'Batch Received Date To', type: 'date', required: true, group: 'Batch Received Date' },
      ...SITE_CRITERIA,
      { name: 'submitterClient', label: 'XML Transmission Creator', type: 'client', group: 'Submitter' },
      { name: 'inputUserId', label: 'Input User ID', maxLength: 30, upper: true, group: 'Submitter' },
      { name: 'batchId', label: 'Batch ID', type: 'number', group: 'Submitter' },
      { ...ACTIVE_ONLY, group: 'Submitter' },
    ],
    columns: RESULT_COLUMNS,
    rowLink: TO_RETURN,
    reports: LIST_REPORTS,
  },
  {
    kind: 'search',
    id: 'detail-single-search',
    legacy: 'P048',
    area: 'scale-returns',
    title: 'Search for a Single Detail Scale Return',
    navLabel: 'Detail Returns: A Single Return',
    capability: 'SCALE_RETURNS_VIEW',
    query: 'detailReturns.search',
    criteria: [
      { name: 'ddn', label: 'Detail Document Number', maxLength: 13, upper: true, group: '1. Any Document Type' },
      { name: 'scaleSite', label: 'Scale Site', maxLength: 4, upper: true, group: '2. Weigh Slip' },
      { name: 'weighSlipNumber', label: 'Weigh Slip', maxLength: 10, upper: true, group: '2. Weigh Slip' },
      {
        name: 'returnType',
        label: 'Return Type',
        type: 'select',
        options: [
          { value: 'P', label: 'Log Tally' },
          { value: 'S', label: 'Sample Tally' },
          { value: 'A', label: 'Arrival Ledger' },
        ],
        group: '3-5. Log Tally / Sample Tally / Arrival Ledger',
      },
      { name: 'scalerLicence', label: 'Scaler Licence', maxLength: 4, upper: true, group: '3-5. Log Tally / Sample Tally / Arrival Ledger' },
      { name: 'returnNumber', label: 'Return Number', type: 'number', maxLength: 4, group: '3-5. Log Tally / Sample Tally / Arrival Ledger' },
      { name: 'activeOnly', label: 'Only active versions', type: yesNo, defaultValue: 'Y', helperText: 'Only active version will be returned.' },
    ],
    requireOneOf: ['ddn', 'weighSlipNumber', 'returnNumber'],
    columns: RESULT_COLUMNS,
    rowLink: TO_RETURN,
  },
  {
    kind: 'search',
    id: 'detail-workbench',
    legacy: 'searchWorkbenchFilter (Home) / P551 / P701',
    area: 'scale-returns',
    title: 'Detail Scale Return Status',
    nav: false,
    capability: 'SCALE_RETURNS_VIEW',
    query: 'detailReturns.search',
    criteria: [
      ...WORKBENCH_COMMON,
      {
        name: 'mode',
        label: 'Check Scale',
        type: 'select',
        options: [
          { value: 'BY_CHECK_SCALE', label: 'Replaced By Check Scale' },
          { value: 'CS_REPLACEMENT', label: 'Check Scale Replacements' },
        ],
      },
      { ...ACTIVE_ONLY },
    ],
    columns: RESULT_COLUMNS,
    rowLink: TO_RETURN,
    reports: LIST_REPORTS,
    notes: WORKBENCH_NOTES,
  },
  {
    kind: 'search',
    id: 'detail-error-categories',
    legacy: 'searchErrorCategory (Home) / P551 / P701',
    area: 'scale-returns',
    title: 'Error Categories',
    nav: false,
    capability: 'SCALE_RETURNS_VIEW',
    query: 'detailReturns.search',
    criteria: [
      ...WORKBENCH_COMMON,
      {
        name: 'responsibility',
        label: 'Error Responsibility',
        type: 'select',
        required: true,
        options: [
          { value: 'I', label: 'Industry' },
          { value: 'M', label: 'Ministry' },
        ],
      },
      { ...ACTIVE_ONLY },
    ],
    columns: RESULT_COLUMNS,
    rowLink: TO_RETURN,
    notes: WORKBENCH_NOTES,
  },
  {
    kind: 'detail',
    id: 'detail-return',
    legacy: 'P552 / P602 / P652 / P752 / P702 / P556 / P656 / P756',
    area: 'scale-returns',
    title: 'View a Detail Scale Return',
    nav: false,
    capability: 'SCALE_RETURNS_VIEW',
    query: 'detailReturns.version',
    keys: ['ddn', 'version'],
    notes:
      'Fields that do not apply to this return type are shown as —. Use "Edit / Log Details" to change the return, ' +
      'its log details and defects, or to change its event type; workflow buttons appear for the states that allow them.',
    actions: DETAIL_ACTIONS,
    editLink: {
      screen: 'detail-entry',
      params: { ddn: 'ddn', version: 'version' },
      label: 'Edit / Log Details',
      capability: 'DETAIL_RETURN_EDIT',
    },
    sections: [
      {
        title: 'Version Information',
        fields: [
          { key: 'ddn', label: 'Detail Document Number' },
          { key: 'docTypeDesc', label: 'Type' },
          { key: 'returnStateDesc', label: 'State' },
          { key: 'version', label: 'Version Number' },
          { key: 'statusDesc', label: 'Status' },
          { key: 'activeInd', label: 'Active Version', format: 'yesno' },
          { key: 'eventTypeDesc', label: 'Event Type' },
          { key: 'receivedTimestamp', label: 'Received', format: 'datetime' },
        ],
      },
      {
        title: 'Scale Return',
        fields: [
          { key: 'scaleSiteDisplay', label: 'Scale Site' },
          { key: 'scaleDate', label: 'Scale Date', format: 'datetime' },
          { key: 'primaryLicenceDisplay', label: 'Primary Scaler' },
          { key: 'secondaryLicence', label: 'Secondary Scaler' },
          { key: 'signingLicence', label: 'Signing Scaler' },
          { key: 'timberMark', label: 'Timber Mark' },
          { key: 'timberBrand', label: 'Timber Brand' },
          { key: 'cutBlockId', label: 'Cut Block' },
          { key: 'returnNumber', label: 'Return Number' },
          { key: 'loadArrivalNumber', label: 'Load Arrival' },
          { key: 'ldsIn', label: 'Load Description Slip Number' },
          { key: 'transportId', label: 'Transport ID' },
          { key: 'logCount', label: 'Log Count', format: 'number' },
          { key: 'netVolume', label: 'Net Volume Total', format: 'volume' },
          { key: 'volumeCalcMethod', label: 'Scale Method' },
          { key: 'sfpScaleType', label: 'Scale method (Special Forest Product)' },
          { key: 'samplePieceCount', label: 'Sample Piece Count', format: 'number' },
          { key: 'fieldScaleInd', label: 'Field Scale', format: 'yesno' },
          { key: 'fieldScaleDeckId', label: 'Deck ID' },
          { key: 'beachcombInd', label: 'Beachcomb', format: 'yesno' },
          { key: 'parcelIdentifier', label: 'Parcel ID' },
          { key: 'parcelCount', label: 'Parcel Count' },
          { key: 'hashTotal', label: 'Hash Total' },
          { key: 'signingDateTime', label: 'Signing Date', format: 'datetime' },
          { key: 'versionComment', label: 'Comment' },
        ],
      },
      {
        title: 'Weigh Slip / Sample',
        fields: [
          { key: 'weighSlipNumber', label: 'Weigh Slip' },
          { key: 'popStratYear', label: 'Population/Stratum/Year or Company Stratum' },
          { key: 'grossWeight', label: 'Gross Weight', format: 'number' },
          { key: 'tareWeight', label: 'Tare Weight', format: 'number' },
          { key: 'netWeight', label: 'Net Weight', format: 'number' },
          { key: 'sampleDeductionWeight', label: 'Weight less Subsample', format: 'number' },
          { key: 'sampleType', label: 'Sample' },
          { key: 'sampledWeighSlip', label: 'Sampled Weigh Slip' },
          { key: 'sampleWeight', label: 'Sample Weight', format: 'number' },
          { key: 'ldsOut', label: 'Load Description Slip Out' },
          { key: 'destination', label: 'Destination' },
        ],
      },
      {
        title: 'Check Scale / Red Tag',
        fields: [
          { key: 'checkReplacesOriginalInd', label: 'Replacement', format: 'yesno' },
          { key: 'origLicence', label: 'Original Scaler' },
          { key: 'origReturnNumber', label: 'Original Scale Return' },
          { key: 'originalDdn', label: 'Original Detail Document Number' },
          { key: 'origScaleSite', label: 'Originating Scale Site' },
        ],
      },
      {
        title: 'Ledger',
        fields: [
          { key: 'arrivalDate', label: 'Arrival Date', format: 'date' },
          { key: 'departureDate', label: 'Departure Date', format: 'date' },
          { key: 'loadDepartureNumber', label: 'Departure Number' },
          { key: 'outgoingTransportId', label: 'Transport ID Out' },
        ],
      },
      {
        title: 'Harvest Billing System Edit Error',
        table: {
          query: 'detailReturns.errors',
          params: { ddn: 'ddn', version: 'version' },
          columns: [
            { key: 'code', header: 'Code' },
            { key: 'description', header: 'Description' },
            { key: 'errorLevel', header: 'Level' },
            { key: 'responsibility', header: 'Responsibility' },
          ],
        },
      },
      {
        title: 'Log Details',
        table: {
          query: 'detailReturns.logs',
          params: { ddn: 'ddn', version: 'version' },
          columns: [
            { key: 'logNumber', header: 'Log Number' },
            { key: 'inputMark', header: 'Input Mark' },
            { key: 'billedMark', header: 'Billed Mark' },
            { key: 'nmvInd', header: 'No Mark Visible', format: 'yesno' },
            { key: 'species', header: 'Species' },
            { key: 'product', header: 'Product' },
            { key: 'grade', header: 'Grade' },
            { key: 'length', header: 'Length', format: 'number' },
            { key: 'top', header: 'Top', format: 'number' },
            { key: 'butt', header: 'Butt', format: 'number' },
            { key: 'netVolume', header: 'Net Volume', format: 'volume' },
            { key: 'defectCount', header: 'Defect Count', format: 'number' },
            { key: 'pieceCount', header: 'Piece Count', format: 'number' },
          ],
          rowLink: { screen: 'detail-log-defects', params: { logType: 'logType', logDtlId: 'logdtlId' } },
        },
      },
      {
        title: 'Segregations',
        table: {
          query: 'detailReturns.segregations',
          params: { ddn: 'ddn', version: 'version' },
          columns: [
            { key: 'species', header: 'Species' },
            { key: 'speciesDesc', header: 'Species Description' },
            { key: 'product', header: 'Product' },
            { key: 'grade', header: 'Grade' },
            { key: 'logCount', header: 'Log Count', format: 'number' },
            { key: 'volume', header: 'Volume', format: 'volume' },
          ],
        },
      },
      {
        title: 'Transaction History',
        table: {
          query: 'detailReturns.transactions',
          params: { ddn: 'ddn', version: 'version' },
          columns: [
            { key: 'eventSequence', header: 'Sequence Number' },
            { key: 'debitCreditType', header: 'Sign' },
            { key: 'txnTypeDesc', header: 'Type' },
            { key: 'dateSummarized', header: 'Date Summarized', format: 'date' },
            { key: 'sdn', header: 'Summary Document Number' },
            { key: 'dateIssued', header: 'Date Issued', format: 'date' },
            { key: 'volume', header: 'Volume', format: 'volume' },
            { key: 'value', header: 'Value', format: 'money' },
          ],
        },
      },
      {
        title: 'Version History',
        table: {
          query: 'detailReturns.versions',
          params: { ddn: 'ddn' },
          columns: [
            { key: 'version', header: 'Version Number' },
            { key: 'statusDesc', header: 'Status' },
            { key: 'activeInd', header: 'Active', format: 'yesno' },
            { key: 'eventTypeDesc', header: 'Event Type' },
            { key: 'entryUserid', header: 'Entered By' },
            { key: 'entryTimestamp', header: 'Entered', format: 'datetime' },
          ],
          rowLink: { screen: 'detail-return', params: { ddn: 'ddn', version: 'version' } },
        },
      },
    ],
    // Each proc reads only its own document type's table; the others print an empty template.
    reports: [
      {
        reportId: 'HBS2R552',
        label: 'Print Log Tally',
        showWhen: { field: 'docType', values: ['P'] },
        fileName: 'Log Tally {ddn} v{version} {timberMark} {scaleDate}',
        params: { PSRETURN_DETAILDOCUMENTNUMBER: 'ddn', PSRETURN_VERSIONNUMBER: 'version' },
      },
      {
        reportId: 'HBS2R602',
        label: 'Print Weigh Slip',
        showWhen: { field: 'docType', values: ['W'] },
        fileName: 'Weigh Slip {ddn} v{version} {timberMark} {scaleDate}',
        params: { WSRETURN_DETAILDOCUMENTNUMBER: 'ddn', WSRETURN_VERSIONNUMBER: 'version' },
      },
      {
        reportId: 'HBS2R652',
        label: 'Print Sample Tally',
        showWhen: { field: 'docType', values: ['S'] },
        fileName: 'Sample Tally {ddn} v{version} {timberMark} {scaleDate}',
        params: { SSRETURN_DETAILDOCUMENTNUMBER: 'ddn', SSRETURN_VERSIONNUMBER: 'version' },
      },
      {
        reportId: 'HBS3R755',
        label: 'Print Special Forest Product Tally',
        showWhen: { field: 'docType', values: ['F'] },
        fileName: 'SFP Tally {ddn} v{version} {timberMark} {scaleDate}',
        params: { PSRETURN_DETAILDOCUMENTNUMBER: 'ddn', PSRETURN_VERSIONNUMBER: 'version' },
      },
      {
        reportId: 'HBS2R702',
        label: 'Print Ledger Entry',
        showWhen: { field: 'docType', values: ['A', 'D'] },
        fileName: 'Ledger Entry {ddn} v{version} {timberMark} {scaleDate}',
        params: { LEDGERDTL_DOCUMENTNUMBER: 'ddn', LEDGERDTL_VERSIONNUMBER: 'version', LEDGERDTL_LEDGERTYPE: 'docType' },
      },
    ],
  },
  {
    kind: 'detail',
    id: 'detail-log-defects',
    legacy: 'P557 / P657',
    area: 'scale-returns',
    title: 'Log Defects',
    nav: false,
    capability: 'SCALE_RETURNS_VIEW',
    query: 'detailReturns.log',
    keys: ['logType', 'logDtlId'],
    sections: [
      {
        title: 'Log',
        fields: [
          { key: 'ddn', label: 'Detail Document Number' },
          { key: 'version', label: 'Version' },
          { key: 'logNumber', label: 'Log Number' },
          { key: 'inputMark', label: 'Beachcomb Mark' },
          { key: 'nmvInd', label: 'No Mark Visible', format: 'yesno' },
          { key: 'species', label: 'Species' },
          { key: 'grade', label: 'Grade' },
          { key: 'length', label: 'Length', format: 'number' },
          { key: 'top', label: 'Top', format: 'number' },
          { key: 'butt', label: 'Butt', format: 'number' },
          { key: 'netVolume', label: 'Net Volume', format: 'volume' },
        ],
      },
      {
        title: 'Defects',
        table: {
          query: 'detailReturns.logDefects',
          params: { logType: 'logType', logDtlId: 'logdtlId' },
          columns: [
            { key: 'defectNumber', header: 'Defect Number' },
            { key: 'defectType', header: 'Defect Code' },
            { key: 'volume', header: 'Defect Volume', format: 'volume' },
            { key: 'descriptor', header: 'Defect Description' },
          ],
        },
      },
    ],
  },
];
