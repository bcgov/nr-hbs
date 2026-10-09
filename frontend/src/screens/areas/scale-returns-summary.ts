import type { ActionDef, ColumnDef, FieldDef, Option, ReportLinkDef, ScreenDef } from '@/screens/types';

/**
 * Scale Returns - Summary (legacy /dac/summary/*). Backend:
 * backend/.../catalog/SummaryReturnsCatalog.java (ids `summaryReturns.*`).
 * Mapping of every legacy screen: docs/areas/summary-returns.md.
 *
 * Writes: the legacy add / update / workflow actions are Java business logic
 * (SummaryScaleReturnManagerBean / SummaryWorkflowManagerBean), not single
 * table-API proc calls, so the commands referenced below are RESERVED ids for
 * the summary-return workflow service still to be ported. Until it lands the
 * buttons report "unknown command" instead of writing anything.
 */

const PENDING_NOTE =
  'Saving and workflow actions for summary returns are being ported from the legacy Java ' +
  '(validation, versioning, pricing). Until then this screen is read-only in effect: ' +
  'submitting will report that the action is not yet available.';

const SCALE_TYPES: Option[] = [
  { value: 'P', label: 'Piece Scale' },
  { value: 'W', label: 'Weight Scale' },
  { value: 'S', label: 'Sample Scale' },
];

const YES_NO_ALL: Option[] = [
  { value: 'Y', label: 'Yes' },
  { value: 'N', label: 'No' },
];

/** Add/update "Return Type" drop-downs (P050/P053: PR,BC; P100/P103: PR,FB,NB; P150/P153: PR,CH). */
const PIECE_RETURN_TYPES: Option[] = [
  { value: 'PR', label: 'PR - Primary Scale' },
  { value: 'BC', label: 'BC - Beachcomb' },
];
const WEIGHT_RETURN_TYPES: Option[] = [
  { value: 'PR', label: 'PR - Primary Scale' },
  { value: 'FB', label: 'FB - Final Bill' },
  { value: 'NB', label: 'NB - Nil Bill' },
];
const SAMPLE_RETURN_TYPES: Option[] = [
  { value: 'PR', label: 'PR - Primary Scale' },
  { value: 'CH', label: 'CH - Check Scale' },
];

/** P051 / P101 / P151 result columns. */
const LIST_COLUMNS: ColumnDef[] = [
  { key: 'documentControlNumber', header: 'SDN', sortable: true },
  { key: 'version', header: 'Vers No', sortable: true },
  { key: 'status', header: 'Status', format: 'status', sortable: true },
  { key: 'categoryDesc', header: 'Return Category', sortable: true },
  { key: 'scaleSite', header: 'Scale Site', sortable: true },
  { key: 'scaleDate', header: 'Scale Date', format: 'date', sortable: true },
  { key: 'popStratumYear', header: 'Pop Stratum Year' },
  { key: 'timberMark', header: 'Timber Mark', sortable: true },
  { key: 'scalerLicence', header: 'Scaler Licence', sortable: true },
  { key: 'returnNumber', header: 'Return Number' },
  { key: 'generated', header: 'Generated Summary', format: 'yesno' },
];

const TO_DETAIL = {
  screen: 'summary-return',
  params: { documentControlNumber: 'documentControlNumber', version: 'version' },
};

/** HBS2R051 / HBS2R101 / HBS2R151 list reports (ReportingFactory2.createSummary*ScaleReturnListReport). */
const listReport = (reportId: string, label: string, extra: Record<string, string>): ReportLinkDef => ({
  reportId,
  label,
  params: {
    PSR_DOCUMENTCONTROLNUMBER: 'documentControlNumber',
    PSR_VERSIONSTATUSCODE: 'status',
    PSR_RETURNCATEGORYCODE: 'category',
    PSR_ERRORCODE: 'errorCode',
    PSR_SCALEDATEFROM: 'scaleDateFrom',
    PSR_SCALEDATETO: 'scaleDateTo',
    PSR_TIMBERMARK: 'timberMark',
    PSR_SCALESITE: 'scaleSite',
    PSR_DISTRICTSCALED: 'districtScaled',
    PSR_DISTRICTHARVESTED: 'districtHarvested',
    PSR_LICENCENUMBER: 'scalerLicence',
    PSR_RETURNNUMBER: 'returnNumber',
    PSR_ACTIVEVERSIONFLAG: 'activeOnly',
    PSR_GENERATEDFLAG: 'generated',
    PSR_SEARCHBYSITES: '=sitesinaregionordistrict',
    PSR_SEARCHBYMARKS: '=marksinaregionordistrict',
    PSR_ORDERBY: '=0',
    ...extra,
  },
});

const SEARCH_CRITERIA: FieldDef[] = [
  { name: 'returnType', label: 'Scale Type', type: 'radio', options: SCALE_TYPES, required: true, defaultValue: 'P', group: 'General Criteria' },
  { name: 'documentControlNumber', label: 'Document Control No', type: 'number', maxLength: 10, group: 'General Criteria' },
  { name: 'status', label: 'Version Status', codeList: 'codes.versionStates', group: 'General Criteria' },
  { name: 'generated', label: 'Generated Summary', type: 'select', options: YES_NO_ALL, group: 'General Criteria' },
  { name: 'category', label: 'Return Category', codeList: 'codes.summaryReturns.categories', group: 'General Criteria' },
  { name: 'errorCode', label: 'Error Code', upper: true, maxLength: 6, group: 'General Criteria' },
  {
    name: 'excludeErrorCode',
    label: 'Invert Selection (i.e. all errors except that specified)',
    upper: true,
    maxLength: 6,
    helperText: 'Error code to exclude',
    group: 'General Criteria',
  },
  { name: 'pssmryType', label: 'Piece Summary Type', codeList: 'codes.summaryReturns.pieceSummaryTypes', group: 'General Criteria' },
  { name: 'activeOnly', label: 'Active Versions Only', type: 'yesno', defaultValue: 'Y', group: 'General Criteria' },
  { name: 'scaleDateFrom', label: 'Scale Date From', type: 'date', group: 'Date Interval' },
  { name: 'scaleDateTo', label: 'Scale Date To', type: 'date', group: 'Date Interval' },
  { name: 'issueDateFrom', label: 'Issue Date From', type: 'date', group: 'Date Interval' },
  { name: 'issueDateTo', label: 'Issue Date To', type: 'date', group: 'Date Interval' },
  { name: 'districtScaled', label: 'Sites in Region/District', codeList: 'codes.orgUnits', group: 'Location or Scaling Criteria' },
  { name: 'scaleSite', label: 'Single Site', upper: true, maxLength: 4, group: 'Location or Scaling Criteria' },
  { name: 'districtHarvested', label: 'Marks in Region/District', codeList: 'codes.orgUnits', group: 'Location or Scaling Criteria' },
  { name: 'timberMark', label: 'Single Mark', upper: true, maxLength: 6, group: 'Location or Scaling Criteria' },
  { name: 'scalerLicence', label: 'Primary Scaler Licence', upper: true, maxLength: 4, group: 'Location or Scaling Criteria' },
  { name: 'returnNumber', label: 'Return Number', maxLength: 4, group: 'Location or Scaling Criteria' },
  { name: 'fieldScaleDeckId', label: 'Field Deck Id', upper: true, maxLength: 1, group: 'Location or Scaling Criteria' },
  { name: 'invoiceNumber', label: 'Statement No', upper: true, maxLength: 7, group: 'Location or Scaling Criteria' },
  { name: 'population', label: 'Population', upper: true, maxLength: 4, group: 'Weight and Sample Scale Only - Population/Stratum/Year' },
  { name: 'stratum', label: 'Stratum', upper: true, maxLength: 2, group: 'Weight and Sample Scale Only - Population/Stratum/Year' },
  { name: 'samplingYear', label: 'Year', type: 'number', maxLength: 4, group: 'Weight and Sample Scale Only - Population/Stratum/Year' },
  { name: 'markHolderClient', label: 'Mark Holder', type: 'client', group: 'Client Association - Client No' },
  { name: 'siteOwnerClient', label: 'Site Owner', type: 'client', group: 'Client Association - Client No' },
  { name: 'stratumOwnerClient', label: 'Stratum Owner', type: 'client', group: 'Client Association - Client No' },
  { name: 'billToClient', label: 'Bill To', type: 'client', group: 'Client Association - Client No' },
  { name: 'copyToClient', label: 'Copy To', type: 'client', group: 'Client Association - Client No' },
];

const action = (
  id: string,
  label: string,
  capability: ActionDef['capability'],
  confirm: string,
  extra: Partial<ActionDef> = {},
): ActionDef => ({
  id,
  label,
  command: `summaryReturns.${id}`,
  capability,
  params: { documentControlNumber: 'documentControlNumber', version: 'version', returnType: 'returnType' },
  confirm,
  ...extra,
});

/** P052/P102/P152 buttons (SummaryReturnFieldsTag); state rules are enforced by the pending service. */
const DETAIL_ACTIONS: ActionDef[] = [
  action('release', 'Release', 'SUMMARY_RETURN_EDIT', 'Release this summary return for billing?'),
  action('hold', 'Hold', 'SUMMARY_RETURN_EDIT', 'Hold this summary return? A new held version is created.'),
  action('discard', 'Discard', 'SUMMARY_RETURN_EDIT', 'Confirm Discard', { danger: true }),
  action('cancel', 'Cancel', 'SUMMARY_RETURN_EDIT', 'Confirm Cancel', { danger: true }),
  action('cancelReplaceWithChanges', 'Cancel & Replace With Changes', 'SUMMARY_RETURN_EDIT', 'Confirm Cancel & Replace With Changes'),
  action('cancelReplaceWithoutChanges', 'Cancel & Replace Without Changes', 'SUMMARY_RETURN_EDIT', 'Confirm Cancel & Replace Without Changes'),
  action('replaceWithChanges', 'Replace With Changes', 'SUMMARY_RETURN_EDIT', 'Confirm Replace With Changes'),
  action('replaceWithoutChanges', 'Replace Without Changes', 'SUMMARY_RETURN_EDIT', 'Confirm Replace Without Changes'),
  action('requestRateCorrection', 'Request Rate Correction', 'SUMMARY_RETURN_ADMIN', 'Confirm Request Rate Correction'),
  action('approveRateCorrection', 'Approve Rate Correction', 'DETAIL_RETURN_APPROVE', 'Confirm Approve Rate Correction'),
  action('rejectRateCorrection', 'Reject Rate Correction', 'DETAIL_RETURN_APPROVE', 'Confirm Reject Rate Correction', { danger: true }),
  action('requeueTxn', 'Re-queue Txn', 'SUMMARY_RETURN_EDIT', 'Re-queue the failed transaction (Sample Scale, Failed Reconciliation only).', {
    fields: [{ name: 'trxId', label: 'Transaction Id', type: 'number', required: true }],
  }),
];

/** Fields shared by the add/update forms; names equal the summaryReturns.detail aliases. */
const pieceFields = (): FieldDef[] => [
  { name: 'pssmryType', label: 'Return Type', codeList: 'codes.summaryReturns.pieceSummaryTypes', defaultValue: 'SMRYDOC', required: true, helperText: 'SMRYDOC = submitted summary; AREAEST / CRSBSDEST / FLDSCLEST / WSTRESEST / OTVOLEST = estimates' },
  { name: 'category', label: 'Return Category', type: 'select', options: PIECE_RETURN_TYPES, helperText: 'Forced to CR / FI / WA / OT for estimates' },
  { name: 'scalerLicence', label: 'Scaler Licence', upper: true, maxLength: 4 },
  { name: 'timberMark', label: 'Timber Mark', upper: true, maxLength: 6, required: true },
  { name: 'numberOfChains', label: 'Chains', maxLength: 14 },
  { name: 'cutBlockId', label: 'Cut Block Id', upper: true, maxLength: 10 },
  { name: 'returnNumber', label: 'Return Number', maxLength: 4 },
  { name: 'billToClientNumber', label: 'Bill To', type: 'client' },
  { name: 'billToClientLocn', label: 'Bill To Location', maxLength: 2 },
  { name: 'campBoomNumber', label: 'Camp Boom', maxLength: 18 },
  { name: 'scaleSite', label: 'Scale Site', upper: true, maxLength: 4 },
  { name: 'copyToClientNumber', label: 'Copy To', type: 'client' },
  { name: 'copyToClientLocn', label: 'Copy To Location', maxLength: 2 },
  { name: 'numberOfSections', label: 'Sections', type: 'number', maxLength: 4 },
  { name: 'scaleDate', label: 'Scale Date', type: 'date', required: true, helperText: 'Area based: Harvest Month End Date (Enter Last Day of Month or Appraisal Expiry Date)' },
  { name: 'logCount', label: 'Log Count', type: 'number', maxLength: 6 },
  { name: 'placeOfScale', label: 'Place of Scale', maxLength: 40, span: 2 },
  { name: 'fieldScaleDeckId', label: 'Field Deck Id', upper: true, maxLength: 1 },
  { name: 'areaCut', label: 'Area Cut', type: 'number', maxLength: 8, helperText: 'Enter Hectares Cut during Harvest Month' },
  { name: 'batchSlipId', label: 'Batch Slip Id', maxLength: 18 },
  { name: 'commentText', label: 'Comment', type: 'textarea', maxLength: 240, span: 4 },
];

const weightFields = (): FieldDef[] => [
  { name: 'category', label: 'Return Type', type: 'select', options: WEIGHT_RETURN_TYPES, required: true },
  { name: 'populationNumber', label: 'Population', upper: true, maxLength: 4, required: true },
  { name: 'stratumNumber', label: 'Stratum', upper: true, maxLength: 2, required: true },
  { name: 'samplingYear', label: 'Year', type: 'number', maxLength: 4, required: true },
  { name: 'timberMark', label: 'Timber Mark', upper: true, maxLength: 6, required: true },
  { name: 'scaleSite', label: 'Scale Site', upper: true, maxLength: 4, required: true },
  { name: 'billToClientNumber', label: 'Bill To', type: 'client' },
  { name: 'billToClientLocn', label: 'Bill To Location', maxLength: 2 },
  { name: 'scaleDate', label: 'Scale Date', type: 'date', required: true },
  { name: 'copyToClientNumber', label: 'Copy To', type: 'client' },
  { name: 'copyToClientLocn', label: 'Copy To Location', maxLength: 2 },
];

const sampleFields = (): FieldDef[] => [
  { name: 'category', label: 'Return Type', type: 'select', options: SAMPLE_RETURN_TYPES, required: true },
  { name: 'populationNumber', label: 'Population', upper: true, maxLength: 4, required: true },
  { name: 'stratumNumber', label: 'Stratum', upper: true, maxLength: 2, required: true },
  { name: 'samplingYear', label: 'Year', type: 'number', maxLength: 4, required: true },
  { name: 'timberMark', label: 'Timber Mark', upper: true, maxLength: 6, required: true },
  { name: 'scalerLicence', label: 'Scaler Licence', upper: true, maxLength: 4, required: true },
  { name: 'loadArrivalNumber', label: 'Load Arrival No', maxLength: 10 },
  { name: 'netWeight', label: 'Sample Weight', type: 'number', maxLength: 6, required: true },
  { name: 'returnNumber', label: 'Return Number', maxLength: 4, required: true },
  { name: 'logCount', label: 'Log Count', type: 'number', maxLength: 6, required: true },
  { name: 'scaleSite', label: 'Scale Site', upper: true, maxLength: 4, required: true },
  { name: 'weighSlipNumber', label: 'Weigh Slip', maxLength: 10 },
  { name: 'scaleDate', label: 'Scale Date', type: 'date', required: true },
];

const grouped = (group: string, fields: FieldDef[]): FieldDef[] => fields.map((f) => ({ ...f, group }));

export const screens: ScreenDef[] = [
  // ── P045 → P051 / P101 / P151 (Home dashboard deep links: returnType, status, generated) ──
  {
    kind: 'search',
    id: 'summary-returns',
    legacy: 'P045/P051/P101/P151',
    area: 'scale-returns',
    title: 'Search for Summary Scale Returns',
    navLabel: 'Summary Returns',
    description: 'Click on the Document Control No to view a single Summary Scale Return.',
    capability: 'SCALE_RETURNS_VIEW',
    query: 'summaryReturns.search',
    criteria: SEARCH_CRITERIA,
    requireOneOf: [
      'documentControlNumber',
      'status',
      'errorCode',
      'excludeErrorCode',
      'scaleDateFrom',
      'issueDateFrom',
      'timberMark',
      'scaleSite',
      'scalerLicence',
      'invoiceNumber',
      'population',
      'markHolderClient',
      'siteOwnerClient',
      'stratumOwnerClient',
      'billToClient',
      'copyToClient',
    ],
    columns: LIST_COLUMNS,
    rowLink: TO_DETAIL,
    createLink: {
      screen: 'summary-return-add-piece',
      params: {},
      label: 'Add Piece Scale Summary Return',
      capability: 'SUMMARY_RETURN_EDIT',
    },
    reports: [
      listReport('HBS2R051', 'Print (Piece)', {
        PSR_INVOICENUMBER: 'invoiceNumber',
        PSR_BILLINGDATEFROM: 'issueDateFrom',
        PSR_BILLINGDATETO: 'issueDateTo',
        PSR_FIELDSCALEDECKID: 'fieldScaleDeckId',
        PSR_PSSMRY: 'pssmryType',
      }),
      listReport('HBS2R101', 'Print (Weight)', {
        PSR_INVOICENUMBER: 'invoiceNumber',
        PSR_BILLINGDATEFROM: 'issueDateFrom',
        PSR_BILLINGDATETO: 'issueDateTo',
        PSR_FIELDSCALEDECKID: 'fieldScaleDeckId',
        PSR_POPULATION: 'population',
        PSR_STRATUM: 'stratum',
        PSR_SAMPLINGYEAR: 'samplingYear',
      }),
      listReport('HBS2R151', 'Print (Sample)', {
        PSR_STATEMENTNUMBER: 'invoiceNumber',
        PSR_COMPILEDDATEFROM: 'issueDateFrom',
        PSR_COMPILEDDATETO: 'issueDateTo',
        PSR_POPULATION: 'population',
        PSR_STRATUM: 'stratum',
        PSR_SAMPLINGYEAR: 'samplingYear',
      }),
    ],
    notes:
      'Returns whose active version has a scale date before January 1 of seven years ago are not listed ' +
      '(legacy seven-year limit). Industry users only see returns where their client is the Bill To, ' +
      'Copy To, mark holder, site owner or stratum owner.',
  },

  // ── P042 single return ──────────────────────────────────────────────
  {
    kind: 'search',
    id: 'summary-return-find',
    legacy: 'P042',
    area: 'scale-returns',
    title: 'Search for Summary Scale Return',
    navLabel: 'Single Summary Return',
    description: 'Please enter one of the Summary Scale Return Selection Criteria.',
    capability: 'SCALE_RETURNS_VIEW',
    query: 'summaryReturns.find',
    criteria: [
      { name: 'documentControlNumber', label: 'Summary Document Number', type: 'number', maxLength: 10, group: 'Specific Document Identifier' },
      { name: 'invoiceNumber', label: 'Statement No', upper: true, maxLength: 7, group: 'Specific Document Identifier' },
      { name: 'activeOnly', label: 'Active Version Only', type: 'yesno', defaultValue: 'Y', group: 'Specific Document Identifier' },
    ],
    requireOneOf: ['documentControlNumber', 'invoiceNumber'],
    columns: [
      { key: 'documentControlNumber', header: 'SDN', sortable: true },
      { key: 'version', header: 'Vers No', sortable: true },
      { key: 'returnTypeDesc', header: 'Scale Type' },
      { key: 'status', header: 'Status', format: 'status' },
      { key: 'categoryDesc', header: 'Return Category' },
      { key: 'scaleSite', header: 'Scale Site' },
      { key: 'scaleDate', header: 'Scale Date', format: 'date' },
      { key: 'timberMark', header: 'Timber Mark' },
      { key: 'generated', header: 'Generated Summary', format: 'yesno' },
      { key: 'withinLimit', header: 'Available', format: 'yesno' },
    ],
    rowLink: TO_DETAIL,
    notes:
      'Available = No means the document is older than the seven-year limit (legacy P174 "Summary Document Unavailable"); it cannot be opened.',
  },

  // ── P052 / P102 / P152 view (+ P055/P056/P105/P106/P107/P155/P156 sub-pages) ──
  {
    kind: 'detail',
    id: 'summary-return',
    legacy: 'P052/P102/P152',
    area: 'scale-returns',
    title: 'Summary Scale Return',
    nav: false,
    capability: 'SCALE_RETURNS_VIEW',
    query: 'summaryReturns.detail',
    keys: ['documentControlNumber', 'version'],
    notes: PENDING_NOTE,
    editLink: {
      screen: 'summary-return-update',
      params: { documentControlNumber: 'documentControlNumber', version: 'version' },
      label: 'Update',
      capability: 'SUMMARY_RETURN_EDIT',
    },
    actions: DETAIL_ACTIONS,
    reports: [
      { reportId: 'HBS2R052', label: 'Print (Piece)', params: { PSRETURN_DOCUMENTNUMBER: 'documentControlNumber', PSRETURN_VERSIONNUMBER: 'version' } },
      { reportId: 'HBS2R102', label: 'Print (Weight)', params: { WSRETURN_DOCUMENTNUMBER: 'documentControlNumber', WSRETURN_VERSIONNUMBER: 'version' } },
      { reportId: 'HBS2R152', label: 'Print (Sample)', params: { SSRETURN_DOCUMENTNUMBER: 'documentControlNumber', SSRETURN_VERSIONNUMBER: 'version' } },
    ],
    sections: [
      {
        title: 'Scale Return',
        fields: [
          { key: 'documentControlNumber', label: 'SDN' },
          { key: 'returnStateDesc', label: 'State' },
          { key: 'version', label: 'Version' },
          { key: 'statusDesc', label: 'Status' },
          { key: 'returnTypeDesc', label: 'Scale Type' },
          { key: 'generated', label: 'Generated Summary', format: 'yesno' },
          { key: 'activeVersionInd', label: 'Active Version', format: 'yesno' },
          { key: 'categoryDesc', label: 'Return Category' },
          { key: 'pssmryTypeDesc', label: 'Return Type' },
          { key: 'timberMark', label: 'Timber Mark' },
          { key: 'scalerLicence', label: 'Scaler Licence' },
          { key: 'returnNumber', label: 'Return Number' },
          { key: 'scaleSite', label: 'Scale Site' },
          { key: 'scaleDate', label: 'Scale Date', format: 'date' },
          { key: 'billToClient', label: 'Bill To' },
          { key: 'billToClientLocn', label: 'Bill To Location' },
          { key: 'copyToClient', label: 'Copy To' },
          { key: 'copyToClientLocn', label: 'Copy To Location' },
          { key: 'fieldScaleDeckId', label: 'Field Deck Id' },
          { key: 'updateUserid', label: 'Last Updated By' },
          { key: 'updateTimestamp', label: 'Last Updated', format: 'datetime' },
        ],
      },
      {
        title: 'Piece Scale',
        fields: [
          { key: 'logCount', label: 'Log Count', format: 'number' },
          { key: 'numberOfChains', label: 'Chains' },
          { key: 'campBoomNumber', label: 'Camp Boom' },
          { key: 'numberOfSections', label: 'Sections', format: 'number' },
          { key: 'placeOfScale', label: 'Place of Scale' },
          { key: 'cutBlockId', label: 'Cut Block Id' },
          { key: 'areaCut', label: 'Area Cut', format: 'number' },
          { key: 'commentText', label: 'Comment' },
        ],
      },
      {
        title: 'Weight / Sample Scale',
        fields: [
          { key: 'popStratumYear', label: 'Pop/Strat/Year' },
          { key: 'totalLoads', label: 'Total Loads', format: 'number' },
          { key: 'totalWeight', label: 'Total Weight', format: 'number' },
          { key: 'sampleVolume', label: 'Sample Volume', format: 'volume' },
          { key: 'loadArrivalNumber', label: 'Load Arrival No' },
          { key: 'netWeight', label: 'Sample Weight', format: 'number' },
          { key: 'weighSlipNumber', label: 'Weigh Slip' },
        ],
      },
      {
        title: 'Edit Errors',
        table: {
          query: 'summaryReturns.versionErrors',
          params: { documentControlNumber: 'documentControlNumber', version: 'version' },
          columns: [
            { key: 'errorCode', header: 'Error Code' },
            { key: 'errorDesc', header: 'Description' },
            { key: 'responsibility', header: 'Responsibility' },
          ],
        },
      },
      {
        title: 'Transactions',
        table: {
          query: 'summaryReturns.transactions',
          params: { documentControlNumber: 'documentControlNumber', version: 'version' },
          columns: [
            { key: 'txnSeq', header: 'Txn Seq' },
            { key: 'drCr', header: 'DR/CR' },
            { key: 'txnTypeDesc', header: 'Txn Type' },
            { key: 'txnStatus', header: 'Txn Status' },
            { key: 'overrideRatingRuleId', header: 'Override' },
            { key: 'rateDate', header: 'Rate Date', format: 'date' },
            { key: 'dateSent', header: 'Date Sent', format: 'date' },
            { key: 'invoiceNumber', header: 'Invoice / Statement Number' },
            { key: 'ratioStatementNumber', header: 'Ratio Statement' },
            { key: 'issueDate', header: 'Issue Date', format: 'date' },
            { key: 'volume', header: 'Volume', format: 'volume' },
            { key: 'value', header: 'Value', format: 'money' },
            { key: 'weight', header: 'Weight', format: 'number' },
          ],
        },
      },
      {
        title: 'Segregations',
        table: {
          query: 'summaryReturns.segregations',
          params: { documentControlNumber: 'documentControlNumber', version: 'version' },
          columns: [
            { key: 'txnSeq', header: 'Txn Seq' },
            { key: 'txnType', header: 'Txn Type' },
            { key: 'inputMark', header: 'Input Mark' },
            { key: 'billedMark', header: 'Billed Mark' },
            { key: 'species', header: 'Species' },
            { key: 'product', header: 'Product' },
            { key: 'grade', header: 'Grade' },
            { key: 'nmvInd', header: 'NMV', format: 'yesno' },
            { key: 'sb4Ind', header: 'SB4', format: 'yesno' },
            { key: 'avoidableInd', header: 'Avoidable', format: 'yesno' },
            { key: 'pieces', header: 'Pieces', format: 'number' },
            { key: 'ratio', header: 'Ratio', format: 'number' },
            { key: 'rate', header: 'Rate', format: 'money' },
            { key: 'rateSource', header: 'Rate Source' },
            { key: 'volume', header: 'Volume', format: 'volume' },
            { key: 'value', header: 'Value', format: 'money' },
          ],
        },
      },
      {
        title: 'Notations',
        table: {
          query: 'summaryReturns.notations',
          params: { documentControlNumber: 'documentControlNumber', version: 'version' },
          columns: [
            { key: 'notationNumber', header: 'Notation' },
            { key: 'text', header: 'Text' },
          ],
        },
      },
      {
        title: 'Daily Load Summaries',
        table: {
          query: 'summaryReturns.dailyLoads',
          params: { documentControlNumber: 'documentControlNumber', version: 'version' },
          columns: [
            { key: 'day', header: 'Day' },
            { key: 'loads', header: 'Loads', format: 'number' },
            { key: 'weight', header: 'Weight', format: 'number' },
          ],
        },
      },
      {
        title: 'Details (Generated Summary)',
        table: {
          query: 'summaryReturns.detailTransactions',
          params: { documentControlNumber: 'documentControlNumber', version: 'version' },
          columns: [
            { key: 'detailDocType', header: 'Document Type' },
            { key: 'detailDocumentNumber', header: 'Detail Document Number' },
            { key: 'detailDocumentVersion', header: 'Version' },
            { key: 'eventSequence', header: 'Txn Seq' },
            { key: 'drCr', header: 'DR/CR' },
            { key: 'txnType', header: 'Txn Type' },
            { key: 'txnStatus', header: 'Txn Status' },
          ],
        },
      },
      {
        title: 'Versions',
        table: {
          query: 'summaryReturns.versions',
          params: { documentControlNumber: 'documentControlNumber' },
          columns: [
            { key: 'version', header: 'Vers No' },
            { key: 'statusDesc', header: 'Status' },
            { key: 'activeVersionInd', header: 'Active', format: 'yesno' },
            { key: 'scaleDate', header: 'Scale Date', format: 'date' },
            { key: 'categoryDesc', header: 'Return Category' },
          ],
          rowLink: TO_DETAIL,
        },
      },
      {
        title: 'Change Log',
        table: {
          query: 'summaryReturns.detail',
          params: { documentControlNumber: 'documentControlNumber', version: 'version' },
          columns: [
            { key: 'documentControlNumber', header: 'SDN' },
            { key: 'version', header: 'Version' },
            { key: 'statusDesc', header: 'Status' },
          ],
          rowLink: {
            screen: 'change-log-document',
            params: { returnFormat: '=SSR', documentNumber: 'documentControlNumber', versionNumber: 'version' },
          },
        },
      },
    ],
  },

  // ── P050 add piece / volume estimate / area based estimate ──────────────
  {
    kind: 'form',
    id: 'summary-return-add-piece',
    legacy: 'P050',
    area: 'scale-returns',
    title: 'Add A Piece Scale Submitted Summary Return',
    navLabel: 'Add Summary Return: Piece / Estimates',
    nav: true,
    description:
      'Also used for Cruise Based Billing: Area (Return Type AREAEST) and Volume Estimate Returns: Cruise, Field, Waste, Other. ' +
      'New returns start as State "Original In Progress", Version 1, Status "Incomplete".',
    capability: 'SUMMARY_RETURN_EDIT',
    command: 'summaryReturns.addPiece',
    fields: grouped('Piece Scale Summary Return', pieceFields()),
    submitLabel: 'Add',
    then: TO_DETAIL,
    notes: PENDING_NOTE,
  },
  // ── P100 add weight ─────────────────────────────────────────────────
  {
    kind: 'form',
    id: 'summary-return-add-weight',
    legacy: 'P100',
    area: 'scale-returns',
    title: 'Weight Scale Summary Return',
    navLabel: 'Add Summary Return: Weight',
    nav: true,
    capability: 'SUMMARY_RETURN_EDIT',
    command: 'summaryReturns.addWeight',
    fields: grouped('Weight Scale Summary Return', weightFields()),
    submitLabel: 'Add',
    then: TO_DETAIL,
    notes: PENDING_NOTE,
  },
  // ── P150 add sample ─────────────────────────────────────────────────
  {
    kind: 'form',
    id: 'summary-return-add-sample',
    legacy: 'P150',
    area: 'scale-returns',
    title: 'Add A Sample Scale Submitted Summary Return',
    navLabel: 'Add Summary Return: Sample',
    nav: true,
    capability: 'SUMMARY_RETURN_EDIT',
    command: 'summaryReturns.addSample',
    fields: grouped('Sample Scale Summary Return', sampleFields()),
    submitLabel: 'Add',
    then: TO_DETAIL,
    notes: PENDING_NOTE,
  },
  // ── P053 / P103 / P153 update (one form; fill the section for the return's scale type) ──
  {
    kind: 'form',
    id: 'summary-return-update',
    legacy: 'P053/P103/P153',
    area: 'scale-returns',
    title: 'Update Summary Scale Return',
    nav: false,
    description: 'Only the section matching the return\'s Scale Type applies. Generated summaries are never editable.',
    capability: 'SUMMARY_RETURN_EDIT',
    command: 'summaryReturns.update',
    loadQuery: 'summaryReturns.detail',
    keys: ['documentControlNumber', 'version'],
    fields: [
      { name: 'documentControlNumber', label: 'SDN', readOnlyOnEdit: true, group: 'Scale Return' },
      { name: 'version', label: 'Version No', readOnlyOnEdit: true, group: 'Scale Return' },
      { name: 'returnType', label: 'Scale Type', readOnlyOnEdit: true, group: 'Scale Return' },
      ...grouped('Piece Scale (P053)', pieceFields().map((f) => ({ ...f, required: false }))),
      ...grouped(
        'Weight Scale (P103)',
        weightFields()
          .filter((f) => ['populationNumber', 'stratumNumber', 'samplingYear'].includes(f.name))
          .map((f) => ({ ...f, required: false })),
      ),
      ...grouped(
        'Sample Scale (P153)',
        sampleFields()
          .filter((f) => ['loadArrivalNumber', 'netWeight', 'weighSlipNumber'].includes(f.name))
          .map((f) => ({ ...f, required: false })),
      ),
    ],
    submitLabel: 'Save',
    then: TO_DETAIL,
    notes: PENDING_NOTE,
  },
];
