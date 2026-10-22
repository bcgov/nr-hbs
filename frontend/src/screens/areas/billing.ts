import type { ROLE_TYPE } from '@/context/auth/types';

import type { ActionDef, BulkActionDef, ColumnDef, FieldDef, ScreenDef } from '../types';

/**
 * Billing area — legacy Billing tab (P800, /ivs/*).
 * Backend: backend/.../catalog/BillingCatalog.java (ids `billing.*`).
 * Mapping and not-ported logic: docs/areas/billing-and-admin.md.
 *
 * The P800 profile links (Statement Delivery / Mark Holder / Cruise Based
 * Billing profile search + add) belong to the Profiles area.
 */

const FREQUENCIES = [
  { value: 'MTH', label: 'Monthly' },
  { value: 'BWK', label: 'Bi-Weekly' },
  { value: 'DLY', label: 'Daily' },
];

/** P828 default: the year of (today - 60 days). */
const DEFAULT_SAMPLING_YEAR = String(new Date(Date.now() - 60 * 24 * 60 * 60 * 1000).getFullYear());

// ------------------------------------------------------------------ P811-P814 / P823-P824

const forceSummarizationCriteria: FieldDef[] = [
  { name: 'frequency', label: 'Frequency', type: 'select', options: FREQUENCIES },
  { name: 'clientNumber', label: 'Client No.', type: 'client', group: 'Client Location' },
  { name: 'clientLocnCode', label: 'Loc', maxLength: 2, group: 'Client Location' },
  {
    name: 'forceDate',
    label: 'Force date',
    type: 'date',
    required: true,
    helperText: 'Profiles active on this date are listed. Monthly ≤ 28 days, Bi-Weekly ≤ 14, Daily ≤ 1 day from today.',
  },
];

const forceSummarizationColumns: ColumnDef[] = [
  { key: 'clientNumber', header: 'Client Number', sortable: true },
  { key: 'clientLocnCode', header: 'Client Location' },
  { key: 'frequencyDescription', header: 'Frequency' },
  { key: 'forcedSummarizationDate', header: 'Forced Date', format: 'date' },
  { key: 'effectiveDate', header: 'Effective Date', format: 'date', sortable: true },
  { key: 'expiryDate', header: 'Expiry Date', format: 'date' },
  { key: 'forceDate', header: 'Update Forced Date?', format: 'date' },
  { key: 'defaultProfile', header: 'Default Profile', format: 'yesno' },
];

const forceSummarizationAction = (label: string): ActionDef => ({
  id: 'force-summarization-date',
  label,
  command: 'billing.markHolderProfile.forceSummarizationDate',
  capability: 'BILLING_FORCE',
  confirm:
    'Set the next summarization date of this Mark Holder Profile to the force date? ' +
    'Monthly profiles: within 28 days from today; Bi-Weekly: 14; Daily: 1.',
  params: { mhprofId: 'mhprofId', forceDate: 'forceDate', updateTimestamp: 'updateTimestamp' },
});

/** P811/P813 "For All Client With Custom Mark Holder Profile" / "Client Location" — the legacy bulk Save. */
const forceAllForm = (id: string, legacy: string, returnType: 'P' | 'W', title: string): ScreenDef => ({
  kind: 'form',
  id,
  legacy,
  area: 'billing',
  nav: false,
  title,
  capability: 'BILLING_FORCE',
  command: 'billing.forceSummarization.force',
  submitLabel: title,
  notes:
    '"For All Client With Custom Mark Holder Profile" also forces the default Mark Holder Profile. ' +
    'Monthly ≤ 28 days, Bi-Weekly ≤ 14, Daily ≤ 1 day from today.',
  fields: [
    { name: 'returnType', label: 'Return Type', type: 'select', options: [{ value: returnType, label: returnType === 'P' ? 'Piece Scale Tally' : 'Weight Slip' }], defaultValue: returnType, required: true, readOnlyOnEdit: true },
    { name: 'frequency', label: 'Frequency', type: 'select', options: FREQUENCIES, defaultValue: 'MTH', required: true },
    {
      name: 'selected',
      label: 'Profiles',
      type: 'radio',
      defaultValue: 'yes',
      required: true,
      options: [
        { value: 'yes', label: 'Client Location' },
        { value: 'no', label: 'For All Client With Custom Mark Holder Profile' },
      ],
      span: 4,
    },
    { name: 'clientNumber', label: 'Client No.', type: 'client', group: 'Client Location' },
    { name: 'clientLocnCode', label: 'Loc', maxLength: 2, group: 'Client Location' },
    { name: 'forceDate', label: 'Force date', type: 'date', required: true },
  ],
});

// ------------------------------------------------------------------ P829 / P830

const finalBillCountColumns: ColumnDef[] = [
  { key: 'eligiblePsym', header: 'Eligible PSYMarks', format: 'number', sortable: true },
  { key: 'lastGenDate', header: 'Last Generated', format: 'date' },
  { key: 'total', header: 'Total', format: 'number' },
  { key: 'issued', header: 'Issued', format: 'number' },
  { key: 'ready', header: 'Ready', format: 'number' },
  { key: 'held', header: 'Held', format: 'number' },
  { key: 'error', header: 'Error', format: 'number' },
  { key: 'cancelled', header: 'Cancelled', format: 'number' },
  { key: 'deleted', header: 'Deleted', format: 'number' },
  { key: 'other', header: 'Other', format: 'number' },
  { key: 'eligiblePsymFlag', header: 'Eligible to Generate' },
];

/** P828Form.initForm: these admin roles start with every suppress flag set. */
const P828_ADMINS: ROLE_TYPE[] = ['HBS_BILL_ADMIN', 'HBS_SCALE_ADMIN', 'HBS_INV_CORR_APP', 'HBS_SMP_ADMIN'];

const suppressFields: FieldDef[] = [
  { name: 'suppressCompleted', label: 'Final Bills are completed', type: 'checkbox', group: 'Suppress output for Populations where:' },
  { name: 'suppressInProgress', label: 'Sampling Year in progress', type: 'checkbox', group: 'Suppress output for Populations where:' },
  {
    name: 'suppressNoEligible',
    label: 'No eligible PSYMarks billed',
    type: 'checkbox',
    defaultValue: 'Y',
    group: 'Suppress output for Populations where:',
  },
];

/**
 * P829/P830 Generate and Hold / Generate and Release (HBS3R830), offered on eligible ('*') rows only —
 * GenerateFinalBillCommand refuses any other row.
 */
const eligibleToGenerate = { field: 'eligiblePsymFlag', values: ['*'] };
const generateActions = (params: Record<string, string>): ActionDef[] => [
  {
    id: 'generate-hold',
    label: 'Generate and Hold',
    command: 'billing.finalBill.generateHold',
    capability: 'FINAL_BILL_GENERATE',
    confirm: 'Generate the final bills (status Held)?',
    params,
    showWhen: eligibleToGenerate,
  },
  {
    id: 'generate-release',
    label: 'Generate and Release',
    command: 'billing.finalBill.generateRelease',
    capability: 'FINAL_BILL_GENERATE',
    confirm: 'Generate the final bills (status Ready)?',
    params,
    showWhen: eligibleToGenerate,
  },
];

// ------------------------------------------------------------------ P831-P838 shared criteria

const tenureCriteria: FieldDef[] = [
  { name: 'timberMark', label: 'Timber Mark', upper: true, maxLength: 6, group: 'For This Timber Mark' },
  { name: 'markClientNumber', label: 'Client No.', type: 'client', group: 'For All Timber Marks Owned By' },
  { name: 'markClientLocnCode', label: 'Loc', maxLength: 2, group: 'For All Timber Marks Owned By' },
  { name: 'population', label: 'Population', upper: true, maxLength: 4, group: 'For This Population and Sampling Year' },
  { name: 'samplingYear', label: 'Sampling Year', type: 'number', maxLength: 4, group: 'For This Population and Sampling Year' },
  { name: 'popClientNumber', label: 'Client No.', type: 'client', group: 'For All Populations Owned By' },
  { name: 'popClientLocnCode', label: 'Loc', maxLength: 2, group: 'For All Populations Owned By' },
];

// ------------------------------------------------------------------ P841-P844 field scale tables

const deckParams = { timberMark: 'timberMark', fieldScaleDeckId: 'fieldScaleDeckId' };

/** P832 rows → the summary-return lock keys. */
const unissuedRowParams = { documentControlNumber: 'documentControlNumber', version: 'version', updateTimestamp: 'updateTimestamp' };

/**
 * P842–P844 bulk actions (P840Action.bulkRequestCommonAction): a row's check box was enabled
 * when the mark is in one of the user's associated districts and the row has issued tallies
 * (P842) or field-scale-flagged returns (P843/P844) — the queries' `selectable` column.
 */
const deckBulk = (docType: 'S' | 'W' | 'P', label: string, rowParams: Record<string, string>): BulkActionDef => ({
  id: `deck-bulk-${docType}`,
  label,
  command: 'billing.fieldScaledDecks.bulk',
  capability: 'FIELD_SCALED_DECKS',
  params: { docType: `=${docType}`, ...deckParams },
  rowParams,
  showWhen: { field: 'selectable', values: ['Y'] },
  danger: docType === 'S',
});

const statusCountColumns: ColumnDef[] = [
  { key: 'held', header: 'Held', format: 'number' },
  { key: 'ready', header: 'Ready', format: 'number' },
  { key: 'error', header: 'Error', format: 'number' },
  { key: 'other', header: 'Other', format: 'number' },
  { key: 'locked', header: 'Locked', format: 'number' },
  { key: 'issued', header: 'Issued', format: 'number' },
  { key: 'total', header: 'Total', format: 'number' },
  { key: 'cancelled', header: 'Cancelled', format: 'number' },
];

export const screens: ScreenDef[] = [
  // ============================================================ Process Scheduling (HBS_SUPER_MGR)
  {
    kind: 'search',
    id: 'force-piece-summarization',
    legacy: 'P811/P812',
    area: 'billing',
    title: 'Piece Scale Tally Summarization',
    navLabel: 'Force Piece Scale Tally Summarization',
    description: 'Force Next Piece Scale Tally Summarization Date on Mark Holder Profiles.',
    capability: 'BILLING_FORCE',
    query: 'billing.forceSummarization.pieceProfiles',
    criteria: forceSummarizationCriteria,
    columns: forceSummarizationColumns,
    rowActions: [forceSummarizationAction('Force Next Piece Scale Tally Summarization Date')],
    createLink: { screen: 'force-piece-summarization-all', label: 'Force for a Client / All Clients', params: {}, capability: 'BILLING_FORCE' },
  },
  forceAllForm('force-piece-summarization-all', 'P811/P812', 'P', 'Force Next Piece Scale Tally Summarization Date'),
  {
    kind: 'search',
    id: 'force-weigh-slip-summarization',
    legacy: 'P813/P814',
    area: 'billing',
    title: 'Weight Slip Summarization',
    navLabel: 'Force Weigh Slip Summarization',
    description: 'Force Next Weight Slip Summarization Date on Mark Holder Profiles.',
    capability: 'BILLING_FORCE',
    query: 'billing.forceSummarization.weighSlipProfiles',
    criteria: forceSummarizationCriteria,
    columns: forceSummarizationColumns,
    rowActions: [forceSummarizationAction('Force Next Weight Slip Summarization Date')],
    createLink: { screen: 'force-weigh-slip-summarization-all', label: 'Force for a Client / All Clients', params: {}, capability: 'BILLING_FORCE' },
  },
  forceAllForm('force-weigh-slip-summarization-all', 'P813/P814', 'W', 'Force Next Weight Slip Summarization Date'),
  {
    kind: 'search',
    id: 'force-ratio-computation',
    legacy: 'P823/P824',
    area: 'billing',
    title: 'Force Ratio Computation',
    description: 'Force Next Ratio Computation Date on Population Profiles.',
    capability: 'BILLING_FORCE',
    query: 'billing.forceRatioComputation.profiles',
    criteria: [
      { name: 'population', label: 'Population', upper: true, maxLength: 4, group: 'For A Single Custom Profile' },
      { name: 'samplingYear', label: 'Sampling Year', type: 'number', maxLength: 4, group: 'For A Single Custom Profile' },
      { name: 'effectiveDate', label: 'Effective Date', type: 'date', group: 'For A Single Custom Profile' },
      { name: 'globalProfile', label: 'For The Global Profile', type: 'checkbox' },
      { name: 'forceDate', label: 'Force date', type: 'date', required: true },
    ],
    columns: [
      { key: 'populationNumber', header: 'Population', sortable: true },
      { key: 'samplingYear', header: 'Sampling Year', sortable: true },
      { key: 'effectiveDate', header: 'Effective Date', format: 'date', sortable: true },
      { key: 'expiryDate', header: 'Expiry Date', format: 'date' },
      { key: 'frequencyDescription', header: 'Frequency' },
      { key: 'lastRatioStmtDate', header: 'Last Ratio Statement', format: 'date' },
      { key: 'nextRatioStmtDate', header: 'Next Ratio Statement', format: 'date' },
      { key: 'forceDate', header: 'Force Date', format: 'date' },
      { key: 'globalProfile', header: 'Global Profile', format: 'yesno' },
    ],
    rowActions: [
      {
        id: 'force-ratio-computation-date',
        label: 'Force Next Ratio Computation Date',
        command: 'billing.populationProfile.forceRatioComputationDate',
        capability: 'BILLING_FORCE',
        confirm:
          'Set the next ratio computation date of this profile to the force date? It must lie within the ratio computation period.',
        params: { popprofId: 'popprofId', forceDate: 'forceDate', updateTimestamp: 'updateTimestamp' },
      },
    ],
  },

  // ============================================================ Weight Scale Final Billing
  {
    kind: 'search',
    id: 'final-bill-status',
    legacy: 'P828/P829',
    area: 'billing',
    title: 'Final Bill Status Summary',
    description: 'Configure Final Bill Status Summary — weight-scale final bills by population.',
    capability: 'BILLING_VIEW',
    query: 'billing.finalBillStatus.populations',
    criteria: [
      { name: 'samplingYear', label: 'Sampling Year', type: 'number', maxLength: 4, required: true, defaultValue: DEFAULT_SAMPLING_YEAR },
      // P828 population selection (HBS3R829 FB_RADIO_SELECTED); role defaults from P828Form.initForm.
      {
        name: 'districtScope',
        label: 'Populations',
        type: 'select',
        defaultValue: 'ALL',
        options: [
          { value: 'ALL', label: 'All Populations' },
          { value: 'DISTRICT', label: 'Populations in Region/District' },
          { value: 'ASSOCIATED', label: 'Populations in Associated Districts' },
          { value: 'SINGLE', label: 'Single Population' },
          { value: 'OWNEDBY', label: 'Population Owned By' },
        ],
        roleDefaults: [
          { roles: P828_ADMINS, value: 'ASSOCIATED' },
          { roles: ['HBS_CLI_SITE_ADMIN'], value: 'OWNEDBY' },
        ],
      },
      { name: 'orgUnit', label: 'Populations in Region/District', codeList: 'codes.orgUnits' },
      { name: 'population', label: 'Single Population', upper: true, maxLength: 4 },
      { name: 'clientNumber', label: 'Population Owned By — Client No.', type: 'client' },
      { name: 'clientLocnCode', label: 'Loc', maxLength: 2 },
      { name: 'expiringBefore', label: 'Expiring Before', type: 'date', helperText: 'Leave blank to list all' },
      ...suppressFields.map((f) =>
        f.name === 'suppressNoEligible' ? f : { ...f, roleDefaults: [{ roles: P828_ADMINS, value: 'Y' }] },
      ),
    ],
    reports: [
      {
        reportId: 'HBS3R829',
        label: 'Print',
        params: {
          FB_SAMPLING_YEAR: 'samplingYear',
          FB_RADIO_SELECTED: 'districtScope',
          FB_ORG_UNIT_NO: 'orgUnit',
          FB_POPULATION_NMBR: 'population',
          FB_CLIENT_NUMBER: 'clientNumber',
          FB_CLIENT_LOCN_CODE: 'clientLocnCode',
          FB_EXPIRING_BEFORE: 'expiringBefore',
          FB_BILLS_COMPLETED_FLAG: 'suppressCompleted',
          FB_YEAR_IN_PROGRESS_FLAG: 'suppressInProgress',
          FB_NO_ELIGIBLE_BILLS_FLAG: 'suppressNoEligible',
          FB_STRATUM_FLAG: '=N',
        },
      },
    ],
    columns: [
      { key: 'populationNumber', header: 'Pop No', sortable: true },
      { key: 'popExpiry', header: 'Pop Expiry', format: 'date', sortable: true },
      ...finalBillCountColumns,
    ],
    rowLink: {
      screen: 'final-bill-status-strata',
      params: { samplingYear: 'samplingYear', population: 'populationNumber' },
    },
    rowActions: generateActions({ samplingYear: 'samplingYear', population: 'populationNumber' }),
    notes:
      'Industry users see only populations owned by their client. Generate and Hold / Generate and Release create the final bills of a population marked Eligible to Generate (*).',
  },
  {
    kind: 'search',
    id: 'final-bill-status-strata',
    legacy: 'P830',
    area: 'billing',
    nav: false,
    title: 'Final Bill Status Summary (Single Population w/Stratum List)',
    capability: 'BILLING_VIEW',
    query: 'billing.finalBillStatus.strata',
    criteria: [
      { name: 'samplingYear', label: 'Sampling Year', type: 'number', maxLength: 4, required: true },
      { name: 'population', label: 'Population', upper: true, maxLength: 4, required: true },
      { name: 'expiringBefore', label: 'Expiring Before', type: 'date' },
      ...suppressFields,
    ],
    columns: [
      { key: 'stratumNumber', header: 'Stratum No', sortable: true },
      { key: 'popExpiry', header: 'Stratum Expiry', format: 'date' },
      ...finalBillCountColumns,
    ],
    rowActions: generateActions({ samplingYear: 'samplingYear', population: 'populationNumber', stratum: 'stratumNumber' }),
    rowLink: {
      screen: 'unissued-final-bills',
      label: 'Unissued final bills',
      params: { population: 'populationNumber', stratum: 'stratumNumber', samplingYear: 'samplingYear' },
    },
  },
  {
    kind: 'search',
    id: 'unissued-final-bills',
    legacy: 'P831/P832',
    area: 'billing',
    title: 'Search For Unissued Final Bills',
    description: 'List of Unissued Final Bills (weight-scale final-bill summary returns in Ready, Held or Error).',
    capability: 'FINAL_BILL_ADMIN',
    query: 'billing.unissuedFinalBills.search',
    criteria: [
      ...tenureCriteria,
      { name: 'stratum', label: 'Stratum', upper: true, maxLength: 2 },
      {
        name: 'versionStatus',
        label: 'Status',
        type: 'select',
        options: [
          { value: 'RDY', label: 'Ready' },
          { value: 'HLD', label: 'Held' },
          { value: 'ERR', label: 'Error' },
        ],
      },
    ],
    columns: [
      { key: 'documentControlNumber', header: 'Document', sortable: true },
      { key: 'scaleDate', header: 'Scale Date', format: 'date', sortable: true },
      { key: 'status', header: 'Status', format: 'status' },
      { key: 'scaleSite', header: 'Site' },
      { key: 'popStrYear', header: 'Pop/Str/Year' },
      { key: 'timberMark', header: 'TimberMark', sortable: true },
      { key: 'invoiceTo', header: 'Invoice To' },
      { key: 'copyTo', header: 'Copy To' },
      { key: 'estimatedVolume', header: 'Estimated Volume', format: 'volume' },
      { key: 'estimatedValue', header: 'Estimated Value', format: 'money' },
    ],
    rowLink: {
      screen: 'summary-return',
      params: { documentControlNumber: 'documentControlNumber', version: 'version' },
    },
    reports: [
      {
        reportId: 'HBS3R832',
        label: 'Print List',
        params: {
          PARAM_SELECTCOUNT: '=N',
          PARAM_TIMBERMARK: 'timberMark',
          PARAM_POPULATION: 'population',
          PARAM_STRATUM: 'stratum',
          PARAM_VERSIONSTATUS: 'versionStatus',
          PARAM_SAMPLINGYEAR: 'samplingYear',
          PARAM_TMOWNEDBYCLINUM: 'markClientNumber',
          PARAM_TMOWNEDBYCLILOC: 'markClientLocnCode',
          PARAM_POPOWNEDBYCLINUM: 'popClientNumber',
          PARAM_POPOWNEDBYCLILOC: 'popClientLocnCode',
        },
      },
    ],
    // P832 Hold Selected / Release Selected (P831Action ACTION_HOLD / ACTION_RELEASE): legacy acted on
    // checked rows listed as RDY/ERR (hold) or HLD (release) and skipped the others.
    bulkActions: [
      {
        id: 'hold-selected',
        label: 'Hold Selected',
        command: 'billing.unissuedFinalBills.hold',
        capability: 'FINAL_BILL_ADMIN',
        rowParams: unissuedRowParams,
        showWhen: { field: 'versionStateCode', values: ['RDY', 'ERR'] },
      },
      {
        id: 'release-selected',
        label: 'Release Selected',
        command: 'billing.unissuedFinalBills.release',
        capability: 'FINAL_BILL_ADMIN',
        rowParams: unissuedRowParams,
        showWhen: { field: 'versionStateCode', values: ['HLD'] },
      },
    ],
  },

  // ============================================================ Non-Renewable Tenures
  {
    kind: 'search',
    id: 'final-bill-tenures',
    legacy: 'P833/P834',
    area: 'billing',
    title: 'Search For Non-Renewable Tenures That Receive Final Bills',
    navLabel: 'Non-Renewable Tenures That Receive Final Bills',
    capability: 'FINAL_BILL_ADMIN',
    query: 'billing.finalBillTenures.search',
    criteria: tenureCriteria,
    columns: [
      { key: 'populationNumber', header: 'Population', sortable: true },
      { key: 'samplingYear', header: 'Sampling Year', sortable: true },
      { key: 'timberMark', header: 'Timber Mark', sortable: true },
      { key: 'fileTypeCode', header: 'File Type' },
    ],
    rowLink: {
      screen: 'final-bill-tenure',
      params: { population: 'populationNumber', samplingYear: 'samplingYear', timberMark: 'timberMark' },
    },
    createLink: {
      screen: 'final-bill-tenure-add',
      label: 'Add',
      capability: 'FINAL_BILL_ADMIN',
      params: { populationNumber: 'population', samplingYear: 'samplingYear', timberMark: 'timberMark' },
    },
    reports: [
      {
        reportId: 'HBS3R834',
        label: 'Print',
        params: {
          RB_TIMBERMARK: 'timberMark',
          RB_POPULATION: 'population',
          RB_SAMPLINGYEAR: 'samplingYear',
          RB_TMK_CLIENT_NUMBER: 'markClientNumber',
          RB_TMK_LOC_CODE: 'markClientLocnCode',
          RB_POP_CLIENT_NUMBER: 'popClientNumber',
          RB_POP_LOC_CODE: 'popClientLocnCode',
        },
      },
    ],
  },
  {
    kind: 'form',
    id: 'final-bill-tenure-add',
    legacy: 'P835',
    area: 'billing',
    nav: false,
    title: 'Add a Non-Renewable Tenure That Receives Final Bills',
    capability: 'FINAL_BILL_ADMIN',
    command: 'billing.finalBillTenure.create',
    fields: [
      { name: 'populationNumber', label: 'Population', upper: true, maxLength: 4, required: true },
      { name: 'samplingYear', label: 'Sampling Year', type: 'number', maxLength: 4, required: true },
      { name: 'timberMark', label: 'Timber Mark', upper: true, maxLength: 6, required: true },
    ],
    submitLabel: 'Save',
    then: {
      screen: 'final-bill-tenures',
      params: { population: 'populationNumber', samplingYear: 'samplingYear' },
    },
    notes:
      'The mark must be non-renewable, the population must not use Default ratios, and no invoices or statements may exist for the Pop/Yr/Mark.',
  },
  {
    kind: 'detail',
    id: 'final-bill-tenure',
    legacy: 'P836',
    area: 'billing',
    nav: false,
    title: 'Update a Non-Renewable Tenure That Receives Final Bills',
    capability: 'FINAL_BILL_ADMIN',
    query: 'billing.finalBillTenures.detail',
    keys: ['population', 'samplingYear', 'timberMark'],
    sections: [
      {
        title: 'Non-Renewable Tenure',
        fields: [
          { key: 'populationNumber', label: 'Population' },
          { key: 'samplingYear', label: 'Sampling Year' },
          { key: 'timberMark', label: 'Timber Mark' },
          { key: 'fileTypeCode', label: 'File Type' },
          { key: 'markClientNumber', label: 'Timber Mark Client' },
          { key: 'markClientLocnCode', label: 'Timber Mark Location' },
          { key: 'popClientNumber', label: 'Population Owner' },
          { key: 'popClientLocnCode', label: 'Population Owner Location' },
          { key: 'entryUserid', label: 'Entered By' },
          { key: 'entryTimestamp', label: 'Entered', format: 'datetime' },
        ],
      },
    ],
    actions: [
      {
        id: 'delete-final-bill-tenure',
        label: 'Delete',
        command: 'billing.finalBillTenure.remove',
        capability: 'FINAL_BILL_ADMIN',
        danger: true,
        confirm:
          'Remove this mark from final billing for this Pop/Yr? Not allowed when invoices or statements exist for the Pop/Yr/Mark.',
        params: {
          populationNumber: 'populationNumber',
          samplingYear: 'samplingYear',
          timberMark: 'timberMark',
          updateTimestamp: 'updateTimestamp',
        },
        then: { screen: 'final-bill-tenures', params: { population: 'populationNumber', samplingYear: 'samplingYear' } },
      },
    ],
    editLink: {
      screen: 'final-bill-tenure-edit',
      label: 'Update',
      capability: 'FINAL_BILL_ADMIN',
      params: { population: 'populationNumber', samplingYear: 'samplingYear', timberMark: 'timberMark' },
    },
  },
  {
    kind: 'form',
    id: 'final-bill-tenure-edit',
    legacy: 'P836',
    area: 'billing',
    nav: false,
    title: 'Update a Non-Renewable Tenure That Receives Final Bills',
    capability: 'FINAL_BILL_ADMIN',
    command: 'billing.finalBillTenure.update',
    loadQuery: 'billing.finalBillTenures.detail',
    keys: ['population', 'samplingYear', 'timberMark'],
    fields: [
      { name: 'opu', label: 'Current Population', readOnlyOnEdit: true, group: 'Current election' },
      { name: 'osy', label: 'Current Sampling Year', readOnlyOnEdit: true, group: 'Current election' },
      { name: 'otm', label: 'Current Timber Mark', readOnlyOnEdit: true, group: 'Current election' },
      { name: 'updateTimestamp', label: 'Last Updated', readOnlyOnEdit: true, group: 'Current election' },
      { name: 'populationNumber', label: 'Population', upper: true, maxLength: 4, required: true, group: 'New values' },
      { name: 'samplingYear', label: 'Sampling Year', type: 'number', maxLength: 4, required: true, group: 'New values' },
      { name: 'timberMark', label: 'Timber Mark', upper: true, maxLength: 6, required: true, group: 'New values' },
    ],
    submitLabel: 'Save',
    then: {
      screen: 'final-bill-tenure',
      params: { population: 'populationNumber', samplingYear: 'samplingYear', timberMark: 'timberMark' },
    },
    notes:
      'Changing the population, year or mark replaces the election. Not allowed when invoices or statements exist for the old or the new Pop/Yr/Mark.',
  },
  {
    kind: 'search',
    id: 'frozen-ratio-tenures',
    legacy: 'P837/P838',
    area: 'billing',
    title: 'Search For Non-Renewable Tenures Not Receiving Final Bills',
    navLabel: 'Non-Renewable Tenures Not Receiving Final Bills',
    capability: 'FINAL_BILL_ADMIN',
    query: 'billing.frozenRatioTenures.search',
    criteria: tenureCriteria,
    columns: [
      { key: 'populationNumber', header: 'Population', sortable: true },
      { key: 'stratumNumber', header: 'Stratum' },
      { key: 'samplingYear', header: 'Sampling Year' },
      { key: 'populationOwner', header: 'Population Owner' },
      { key: 'timberMark', header: 'Timber Mark', sortable: true },
      { key: 'licensee', header: 'Licensee' },
      { key: 'fileTypeCode', header: 'File Type' },
      { key: 'statementNumber', header: 'Ratio Report Number' },
    ],
  },

  // ============================================================ Field Scaled Decks
  {
    kind: 'search',
    id: 'field-scaled-decks',
    legacy: 'P840',
    area: 'billing',
    title: 'Search for Scale Returns From Field Scaled Decks',
    navLabel: 'Field Scaled Decks',
    capability: 'FIELD_SCALED_DECKS',
    query: 'billing.fieldScaledDecks.mark',
    criteria: [
      { name: 'timberMark', label: 'Timber Mark', upper: true, maxLength: 6, required: true },
      {
        name: 'fieldScaleDeckId',
        label: 'Field Scale Deck ID',
        upper: true,
        maxLength: 1,
        required: true,
        defaultValue: '*',
        helperText: '* = all decks',
      },
    ],
    columns: [
      { key: 'timberMark', header: 'Timber Mark' },
      { key: 'fieldScaleDeckLabel', header: 'Field Scale Deck ID' },
      { key: 'fileTypeCode', header: 'File Type' },
      { key: 'licensee', header: 'Licensee' },
    ],
    rowLink: { screen: 'field-scale-overview', params: deckParams },
  },
  {
    kind: 'detail',
    id: 'field-scale-overview',
    legacy: 'P841/P842/P843/P844',
    area: 'billing',
    nav: false,
    title: 'Field Scale Overview',
    capability: 'FIELD_SCALED_DECKS',
    query: 'billing.fieldScaledDecks.mark',
    keys: ['timberMark', 'fieldScaleDeckId'],
    sections: [
      {
        title: 'Selected Search Criteria',
        fields: [
          { key: 'timberMark', label: 'Timber Mark' },
          { key: 'fieldScaleDeckLabel', label: 'Field Scale Deck ID' },
          { key: 'fileTypeCode', label: 'File Type' },
          { key: 'licensee', label: 'Licensee' },
        ],
      },
      {
        title: 'Overview',
        table: {
          query: 'billing.fieldScaledDecks.overview',
          params: deckParams,
          columns: [
            { key: 'docType', header: '' },
            { key: 'detailReturns', header: 'Detail Returns', format: 'number' },
            { key: 'summaryReturns', header: 'Summary Returns', format: 'number' },
            { key: 'issuedFraction', header: 'Issued Fraction (%)', format: 'number' },
            { key: 'earliestScaleDate', header: 'Earliest Scale Date', format: 'date' },
            { key: 'latestScaleDate', header: 'Latest Scale Date', format: 'date' },
            { key: 'scaledVolume', header: 'Scaled Volume', format: 'volume' },
            { key: 'estimatedVolume', header: 'Estimated Volume', format: 'volume' },
            { key: 'strataCount', header: 'Strata Count', format: 'number' },
          ],
        },
      },
      {
        title: 'Override Rules',
        table: {
          query: 'billing.fieldScaledDecks.overrideRules',
          params: deckParams,
          columns: [
            { key: 'inactive', header: 'Inactive', format: 'number' },
            { key: 'pending', header: 'Pending', format: 'number' },
            { key: 'past', header: 'Past', format: 'number' },
            { key: 'currentCount', header: 'Current', format: 'number' },
            { key: 'future', header: 'Future', format: 'number' },
          ],
        },
      },
      {
        title: 'Sample Tallies From Field Scaled Decks',
        table: {
          query: 'billing.fieldScaledDecks.sampleTallies',
          params: deckParams,
          columns: [
            { key: 'psy', header: 'Pop/Strat/Year' },
            { key: 'ytdRatio', header: 'YTD Ratio', format: 'number' },
            { key: 'ratioSource', header: 'Source' },
            ...statusCountColumns,
          ],
          bulkActions: [deckBulk('S', 'Cancel Issued Sample Tallies in Selected Strata', { psy: 'psy' })],
        },
      },
      {
        title: 'Weigh Slips From Field Scaled Decks',
        table: {
          query: 'billing.fieldScaledDecks.weighSlips',
          params: deckParams,
          columns: [
            { key: 'psy', header: 'Pop/Strat/Year' },
            { key: 'ytdRatio', header: 'YTD Ratio', format: 'number' },
            { key: 'ratioSource', header: 'Source' },
            { key: 'fsFlag', header: 'FS Flag', format: 'number' },
            ...statusCountColumns,
          ],
          bulkActions: [deckBulk('W', 'Clear Field Scale Flag for Weigh Slips in Selected Strata', { psy: 'psy' })],
        },
      },
      {
        title: 'Red Tags',
        table: {
          query: 'billing.fieldScaledDecks.redTags',
          params: deckParams,
          columns: [
            { key: 'eventType', header: '' },
            { key: 'nti', header: 'NTI', format: 'number' },
            { key: 'held', header: 'Held', format: 'number' },
            { key: 'error', header: 'Error', format: 'number' },
            { key: 'other', header: 'Other', format: 'number' },
            { key: 'total', header: 'Total', format: 'number' },
          ],
        },
      },
      {
        title: 'Log Tallies From Field Scaled Decks',
        table: {
          query: 'billing.fieldScaledDecks.logTallies',
          params: deckParams,
          columns: [
            { key: 'eventType', header: 'Event Type' },
            { key: 'fsFlag', header: 'FS Flag', format: 'number' },
            ...statusCountColumns,
          ],
          bulkActions: [deckBulk('P', 'Clear Field Scale Flag for Selected Log Tallies', { eventCode: 'eventCode' })],
        },
      },
    ],
  },
];
