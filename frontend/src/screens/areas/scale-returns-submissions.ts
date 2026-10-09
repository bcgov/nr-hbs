import type { ColumnDef, FieldDef, ScreenDef } from '../types';

/**
 * Submissions & batch tracking (Scale Returns area):
 * - P041/P032 Search for Detail Scale Transmissions (XML)   → xml-transmissions
 * - P024/P027 Search for Submitted Detail Batches (XML)       → detail-batches
 * - P280–P283 Paper batch slips (generate / search / update)   → batch-slip*
 * Backend: SubmissionsCatalog.java, SubmissionsBatchSlipCatalog.java.
 */

/** Local yyyy-mm-dd, `days` from today (legacy WORKBENCH_DATE_RANGE = -30). */
const isoDaysFromToday = (days: number): string => {
  const d = new Date();
  d.setDate(d.getDate() + days);
  const mm = String(d.getMonth() + 1).padStart(2, '0');
  const dd = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${mm}-${dd}`;
};
const TODAY = isoDaysFromToday(0);
const THIRTY_DAYS_AGO = isoDaysFromToday(-30);

// ── P041 / P032 ────────────────────────────────────────────────────────────

const transmissionColumns: ColumnDef[] = [
  { key: 'receivedTimestamp', header: 'Date / Time Received', format: 'datetime', sortable: true },
  { key: 'transmissionId', header: 'Xmit Id', sortable: true },
  { key: 'fileName', header: 'File Name', sortable: true },
  { key: 'batchCount', header: 'Batch Count', format: 'number', sortable: true },
  { key: 'recordCount', header: 'Record Count', format: 'number', sortable: true },
  { key: 'transmissionType', header: 'Type' },
  { key: 'stepDescription', header: 'Step', sortable: true },
  { key: 'statusCode', header: 'Status', format: 'status', sortable: true },
  { key: 'reason', header: 'Reason' },
];

const batchColumns: ColumnDef[] = [
  { key: 'batchId', header: 'Batch Id', sortable: true },
  { key: 'batchReceived', header: 'Batch Received', format: 'datetime', sortable: true },
  { key: 'transmissionId', header: 'Xmit ID', sortable: true },
  { key: 'docType', header: 'Doc Type', sortable: true },
  { key: 'scaleSiteNo', header: 'Scale Site', sortable: true },
  { key: 'returnCount', header: 'Return Count', format: 'number', sortable: true },
  { key: 'rejectedCount', header: 'Rejected', format: 'number', sortable: true },
  { key: 'inErrorCount', header: 'In Error', format: 'number', sortable: true },
  { key: 'readyCount', header: 'Ready', format: 'number', sortable: true },
  { key: 'otherCount', header: 'Other', format: 'number', sortable: true },
  { key: 'stepDescription', header: 'Batch Step' },
  { key: 'statusCode', header: 'Status', format: 'status' },
  { key: 'reason', header: 'Reason' },
];

const RETURN_TYPES = [
  { value: 'P', label: 'Piece' },
  { value: 'W', label: 'Weight' },
  { value: 'S', label: 'Sample' },
  { value: 'A', label: 'Arrival Ledger' },
  { value: 'D', label: 'Departure Ledger' },
  { value: 'F', label: 'SFP Tally' },
  { value: 'ALL', label: 'All' },
];

// ── P280 / P283 batch slip fields ─────────────────────────────────────────

const slipFields: FieldDef[] = [
  { name: 'scaleSiteNo', label: 'Scale Site', required: true, upper: true, maxLength: 4 },
  { name: 'scaledFrom', label: 'Scaled From', type: 'date', required: true },
  { name: 'scaledTo', label: 'Scaled To', type: 'date', required: true },
  { name: 'orgUnitNo', label: 'Region', type: 'select', codeList: 'codes.submissions.regions', required: true },
  {
    name: 'senderUserid',
    label: "Sender's HBS User Id",
    required: true,
    upper: true,
    maxLength: 30,
    placeholder: 'IDIR\\USERID',
    helperText: 'Domain and user id, e.g. IDIR\\JSMITH',
  },
  { name: 'hdqSentDate', label: 'Date Sent to HDQ', type: 'date', required: true },
  { name: 'documentCount', label: 'Document Count', type: 'number', required: true, maxLength: 5 },
  { name: 'paperBatchComment', label: 'Instruction/notes', type: 'textarea', maxLength: 255, span: 4 },
];

/** Every HBS_STORE_HBS_PAPER_BATCH argument, taken from the P283 record. */
const slipStoreParams: Record<string, string> = {
  batchId: 'batchId',
  scaleSiteNo: 'scaleSiteNo',
  dtlDocBatchId: 'dtlDocBatchId',
  returnType: 'returnType',
  scaledFrom: 'scaledFrom',
  scaledTo: 'scaledTo',
  documentCount: 'documentCount',
  hdqSentDate: 'hdqSentDate',
  senderUserid: 'senderUserid',
  paperBatchComment: 'paperBatchComment',
  orgUnitNo: 'orgUnitNo',
  entryUserid: 'entryUserid',
  entryTimestamp: 'entryTimestamp',
};

export const screens: ScreenDef[] = [
  {
    kind: 'search',
    id: 'xml-transmissions',
    legacy: 'P041/P032',
    area: 'scale-returns',
    title: 'Search for Detail Scale Transmissions',
    navLabel: 'Detail Scale Transmissions: XML',
    description: 'List of Received Detail Scale Transmissions (XML).',
    capability: 'SCALE_RETURNS_VIEW',
    query: 'submissions.transmissions',
    autoSearch: true,
    notes:
      'Defaults to transmissions received in the last 30 days. Industry users only see transmissions for their own client. ' +
      'When searching by Transmission ID, clear the received dates if the file is older than the date range.',
    criteria: [
      { name: 'transmissionId', label: 'Transmission ID', type: 'number', maxLength: 8, group: 'Specific Transmission ID' },
      {
        name: 'transmissionType',
        label: 'Transmission Type',
        type: 'select',
        options: [
          { value: 'N', label: 'Production' },
          { value: 'Y', label: 'Test' },
        ],
        group: 'General Criteria',
      },
      { name: 'transmissionStep', label: 'Transmission Step', type: 'select', codeList: 'codes.xmlTransSteps', group: 'General Criteria' },
      { name: 'processingStatus', label: 'Processing Status', type: 'select', codeList: 'codes.processingStatuses', group: 'General Criteria' },
      {
        name: 'excludeCheck',
        label: 'Exclude Transmissions without Batches',
        type: 'checkbox',
        defaultValue: 'Y',
        group: 'General Criteria',
      },
      { name: 'receivedFrom', label: 'From', type: 'date', defaultValue: THIRTY_DAYS_AGO, group: 'Transmissions Received Date' },
      { name: 'receivedTo', label: 'To', type: 'date', defaultValue: TODAY, group: 'Transmissions Received Date' },
      { name: 'orgUnitNo', label: 'Sites in Region/District', type: 'select', codeList: 'codes.orgUnits', group: 'Scale Site' },
      { name: 'scaleSiteNo', label: 'Single Site', upper: true, maxLength: 4, group: 'Scale Site' },
      { name: 'clientNumber', label: 'XML Transmission Creator', type: 'client', maxLength: 8, group: 'Submitter' },
      { name: 'clientLocnCode', label: 'Location', maxLength: 2, group: 'Submitter' },
      { name: 'inputUserId', label: 'Input User ID', upper: true, maxLength: 30, group: 'Submitter' },
    ],
    requireOneOf: ['transmissionId', 'receivedFrom'],
    columns: transmissionColumns,
    rowLink: { screen: 'xml-transmission-detail', params: { transmissionId: 'transmissionId' } },
  },
  {
    kind: 'detail',
    id: 'xml-transmission-detail',
    legacy: 'P032',
    area: 'scale-returns',
    title: 'Received Transmission (XML)',
    capability: 'SCALE_RETURNS_VIEW',
    nav: false,
    query: 'submissions.transmission',
    keys: ['transmissionId'],
    reports: [
      {
        reportId: 'HBS2R032',
        label: 'Transmission Detail List',
        params: { RB_SELECTCOUNT: '=N', RB_TRANSMISSION_ID: 'transmissionId', RB_BATCH_ID: '=' },
      },
      {
        reportId: 'HBS2R033',
        label: 'Transmission Detail File List',
        params: { RB_SELECTCOUNT: '=N', RB_TRANSMISSION_ID: 'transmissionId' },
      },
    ],
    sections: [
      {
        title: 'Transmission',
        fields: [
          { key: 'transmissionId', label: 'Xmit Id' },
          { key: 'receivedTimestamp', label: 'Date / Time Received', format: 'datetime' },
          { key: 'fileName', label: 'File Name' },
          { key: 'fileDatetime', label: 'File Date', format: 'datetime' },
          { key: 'transmissionType', label: 'Type' },
          { key: 'stepDescription', label: 'Step' },
          { key: 'statusDescription', label: 'Status' },
          { key: 'reason', label: 'Reason' },
          { key: 'batchCount', label: 'Batch Count', format: 'number' },
          { key: 'recordCount', label: 'Record Count', format: 'number' },
          { key: 'clientNumber', label: 'XML Transmission Creator' },
          { key: 'clientLocnCode', label: 'Location' },
          { key: 'inputUserid', label: 'Input User ID' },
          { key: 'softwareProduct', label: 'Software Product' },
          { key: 'softwareVersion', label: 'Software Version' },
          { key: 'softwareRevision', label: 'Software Revision' },
        ],
      },
      {
        title: 'Batches',
        table: {
          query: 'submissions.transmissionBatches',
          params: { transmissionId: 'transmissionId' },
          columns: batchColumns.filter((c) => c.key !== 'transmissionId').map((c) => ({ ...c, sortable: false })),
          rowLink: { screen: 'detail-batch', params: { batchId: 'batchId' } },
        },
      },
      {
        title: 'Transmission Errors',
        table: {
          query: 'submissions.transmissionErrors',
          params: { transmissionId: 'transmissionId' },
          columns: [
            { key: 'categoryCode', header: 'Category' },
            { key: 'messageCode', header: 'Message' },
            { key: 'description', header: 'Description' },
            { key: 'errorTimestamp', header: 'Error Date', format: 'datetime' },
          ],
        },
      },
    ],
  },

  // ── P024 / P027 ──────────────────────────────────────────────────────────
  {
    kind: 'search',
    id: 'detail-batches',
    legacy: 'P024/P027',
    area: 'scale-returns',
    title: 'Search for Submitted Detail Batches',
    navLabel: 'Submitted Batches: Detail',
    description: 'List of Submitted Detail Scale Data Batches (XML).',
    capability: 'SCALE_RETURNS_VIEW',
    query: 'submissions.batches',
    notes:
      'Defaults to batches received in the last 30 days. Industry users only see batches they submitted or that were scaled at their sites.',
    criteria: [
      { name: 'returnType', label: 'Return Type', type: 'radio', options: RETURN_TYPES, defaultValue: 'ALL', required: true, group: 'General Criteria' },
      { name: 'receivedFrom', label: 'From', type: 'date', required: true, defaultValue: THIRTY_DAYS_AGO, group: 'Batch Received Date' },
      { name: 'receivedTo', label: 'To', type: 'date', required: true, defaultValue: TODAY, group: 'Batch Received Date' },
      {
        name: 'siteSearch',
        label: 'Scale Site',
        type: 'radio',
        defaultValue: 'allsites',
        options: [
          { value: 'allsites', label: 'All Sites' },
          { value: 'sitesinregionordistrict', label: 'Sites in Region/District' },
          { value: 'singlesite', label: 'Single Site' },
        ],
        group: 'Scale Site',
      },
      { name: 'orgUnitNo', label: 'Region/District', type: 'select', codeList: 'codes.orgUnits', group: 'Scale Site' },
      { name: 'scaleSiteNo', label: 'Single Site', upper: true, maxLength: 4, group: 'Scale Site' },
      {
        name: 'submitter',
        label: 'Submitter',
        type: 'radio',
        defaultValue: 'all',
        options: [
          { value: 'all', label: 'All' },
          { value: 'creator', label: 'XML Transmission Creator' },
          { value: 'user', label: 'Input User ID' },
        ],
        group: 'Submitter',
      },
      { name: 'clientNumber', label: 'XML Transmission Creator', type: 'client', maxLength: 8, group: 'Submitter' },
      { name: 'clientLocnCode', label: 'Location', maxLength: 2, group: 'Submitter' },
      { name: 'inputUserId', label: 'Input User ID', upper: true, maxLength: 30, group: 'Submitter' },
      {
        name: 'batchType',
        label: 'Batches',
        type: 'radio',
        defaultValue: 'allbatches',
        options: [
          { value: 'allbatches', label: 'All Batches' },
          { value: 'withrejecteddocuments', label: 'With Rejected Documents' },
          { value: 'rejectedbatches', label: 'Rejected Batches' },
          { value: 'withnorejecteddocuments', label: 'With No Rejected Documents' },
        ],
        group: 'Batches',
      },
      { name: 'transmissionId', label: 'Transmission Id', type: 'number', maxLength: 8, group: 'Batches' },
    ],
    columns: batchColumns,
    rowLink: { screen: 'detail-batch', params: { batchId: 'batchId' } },
    reports: [
      {
        reportId: 'HBS2R027',
        label: 'Print',
        params: {
          RB_SELECTCOUNT: '=N',
          RB_RETURN_TYPE_CODE: 'returnType',
          RB_TRADING_PARTNER: 'clientNumber',
          RB_REGDIST: 'orgUnitNo',
          RB_SCALE_SITE: 'scaleSiteNo',
          RB_SEARCH_BY_SITES: 'siteSearch',
          RB_SEARCH_BY_MARKS: 'submitter',
          RB_USER_ID: 'inputUserId',
          RB_AFTER_DATE: 'receivedFrom',
          RB_BEFORE_DATE: 'receivedTo',
          RB_TRANSMISSION_ID: 'transmissionId',
          RB_BATCH_TYPE: 'batchType',
        },
      },
    ],
  },
  {
    kind: 'detail',
    id: 'detail-batch',
    legacy: 'P027',
    area: 'scale-returns',
    title: 'Submitted Detail Scale Data Batch (XML)',
    capability: 'SCALE_RETURNS_VIEW',
    nav: false,
    query: 'submissions.batch',
    keys: ['batchId'],
    // P027 In Error / Ready / Other links → detail document list (P551; ledgers P701).
    editLink: {
      screen: 'detail-workbench',
      label: 'View Documents',
      params: { batchId: 'batchId', returnType: 'docType' },
      capability: 'SCALE_RETURNS_VIEW',
    },
    reports: [
      {
        reportId: 'HBS2R032',
        label: 'Rejected Documents',
        params: { RB_SELECTCOUNT: '=N', RB_TRANSMISSION_ID: 'transmissionId', RB_BATCH_ID: 'batchId' },
      },
    ],
    sections: [
      {
        title: 'Batch',
        fields: [
          { key: 'batchId', label: 'Batch Id' },
          { key: 'batchReceived', label: 'Batch Received', format: 'datetime' },
          { key: 'transmissionId', label: 'Xmit ID' },
          { key: 'fileName', label: 'File Name' },
          { key: 'docType', label: 'Doc Type' },
          { key: 'docTypeDescription', label: 'Doc Type Description' },
          { key: 'scaleSiteNo', label: 'Scale Site' },
          { key: 'orgUnitName', label: 'District' },
          { key: 'submitterBatchId', label: 'Submitter Batch Id' },
          { key: 'fromScaleDate', label: 'From Scale Date', format: 'date' },
          { key: 'toScaleDate', label: 'To Scale Date', format: 'date' },
          { key: 'clientNumber', label: 'XML Transmission Creator' },
          { key: 'clientLocnCode', label: 'Location' },
          { key: 'inputUserid', label: 'Input User ID' },
        ],
      },
      {
        title: 'Documents',
        fields: [
          { key: 'returnCount', label: 'Return Count', format: 'number' },
          { key: 'rejectedCount', label: 'Rejected', format: 'number' },
          { key: 'inErrorCount', label: 'In Error', format: 'number' },
          { key: 'readyCount', label: 'Ready', format: 'number' },
          { key: 'otherCount', label: 'Other', format: 'number' },
          { key: 'stepDescription', label: 'Batch Step' },
          { key: 'statusDescription', label: 'Status' },
          { key: 'reason', label: 'Reason' },
        ],
      },
      {
        title: 'Batch Errors',
        table: {
          query: 'submissions.batchErrors',
          params: { batchId: 'batchId' },
          columns: [
            { key: 'categoryCode', header: 'Category' },
            { key: 'messageCode', header: 'Message' },
            { key: 'description', header: 'Description' },
            { key: 'errorTimestamp', header: 'Error Date', format: 'datetime' },
          ],
        },
      },
    ],
  },

  // ── P281 / P282 / P283 / P280 paper batch slips ──────────────────────────
  {
    kind: 'search',
    id: 'batch-slips',
    legacy: 'P281/P282',
    area: 'scale-returns',
    title: 'Search for Batch Slip',
    description: 'List of Batch Slips (paper batch tracking).',
    capability: 'MINISTRY',
    // Removed from the P002 menu in 2010 (ticket 8835); reachable by URL only.
    nav: false,
    query: 'submissions.batchSlips',
    criteria: [
      { name: 'batchId', label: 'Batch Slip Id', type: 'number', maxLength: 19 },
      { name: 'scaleSiteNo', label: 'Scale Site', upper: true, maxLength: 4 },
      { name: 'createdFrom', label: 'Batch Creation Date From', type: 'date' },
      { name: 'createdTo', label: 'Batch Creation Date To', type: 'date' },
    ],
    requireOneOf: ['batchId', 'createdFrom'],
    columns: [
      { key: 'batchId', header: 'Batch Slip Id', sortable: true },
      { key: 'entryTimestamp', header: 'Batch Slip Creation Date', format: 'date', sortable: true },
      { key: 'entryUserid', header: 'Batch Slip Creator' },
      { key: 'statusDescription', header: 'Batch Slip Status', sortable: true },
      { key: 'scaleSiteNo', header: 'Scale Site', sortable: true },
      { key: 'scaledFrom', header: 'From Scale Date', format: 'date', sortable: true },
      { key: 'scaledTo', header: 'To Scale Date', format: 'date', sortable: true },
      { key: 'regionName', header: 'Region' },
      { key: 'documentCount', header: 'Document Count', format: 'number' },
    ],
    rowLink: { screen: 'batch-slip', params: { batchId: 'batchId' } },
    createLink: { screen: 'batch-slip-add', label: 'Generate a Batch Slip', params: {}, capability: 'BATCH_SLIP_EDIT' },
  },
  {
    kind: 'detail',
    id: 'batch-slip',
    legacy: 'P283',
    area: 'scale-returns',
    title: 'Batch Slip',
    capability: 'MINISTRY',
    nav: false,
    query: 'submissions.batchSlip',
    keys: ['batchId'],
    editLink: { screen: 'batch-slip-update', label: 'Update a Batch Slip', params: { batchId: 'batchId' }, capability: 'BATCH_SLIP_EDIT' },
    actions: [
      {
        id: 'cancel',
        label: 'Cancel',
        command: 'batchSlip.cancel',
        capability: 'BATCH_SLIP_EDIT',
        params: slipStoreParams,
        confirm: 'Cancel this batch slip? Its status becomes Cleared and it can no longer be updated. (Pending batch slips only.)',
        danger: true,
      },
      {
        id: 'confirm',
        label: 'Confirm',
        command: 'batchSlip.confirm',
        capability: 'BATCH_SLIP_EDIT',
        params: slipStoreParams,
        confirm:
          'Mark this batch slip Matched? Only confirm a Pending slip whose Documents Received equals its Document Count.',
      },
    ],
    sections: [
      {
        title: 'Batch Slip',
        fields: [
          { key: 'batchId', label: 'Batch Slip Id' },
          { key: 'entryTimestamp', label: 'Batch Slip Creation Date', format: 'date' },
          { key: 'entryUserid', label: 'Batch Slip Creator' },
          { key: 'statusDescription', label: 'Batch Slip Status' },
          { key: 'scaleSiteNo', label: 'Scale Site' },
          { key: 'scaledFrom', label: 'Scaled From', format: 'date' },
          { key: 'scaledTo', label: 'Scaled To', format: 'date' },
          { key: 'regionName', label: 'Region' },
          { key: 'senderUserid', label: "Sender's HBS User Id" },
          { key: 'hdqSentDate', label: 'Date Sent to HDQ', format: 'date' },
          { key: 'documentCount', label: 'Document Count', format: 'number' },
          { key: 'documentsReceived', label: 'Documents Received', format: 'number' },
          { key: 'paperBatchComment', label: 'Instruction/notes' },
          { key: 'dtlDocBatchId', label: 'Matched Detail Batch Id' },
          { key: 'updateUserid', label: 'Last Updated By' },
          { key: 'updateTimestamp', label: 'Last Updated', format: 'datetime' },
        ],
      },
    ],
  },
  {
    kind: 'form',
    id: 'batch-slip-add',
    legacy: 'P280',
    area: 'scale-returns',
    title: 'Generate a Batch Slip',
    capability: 'BATCH_SLIP_EDIT',
    nav: false,
    command: 'batchSlip.create',
    fields: slipFields,
    then: { screen: 'batch-slip', params: { batchId: 'hbsPaperBatchId' } },
  },
  {
    kind: 'form',
    id: 'batch-slip-update',
    legacy: 'P283',
    area: 'scale-returns',
    title: 'Update a Batch Slip',
    capability: 'BATCH_SLIP_EDIT',
    nav: false,
    notes: 'Scale site, dates, region and sender may only be changed while the batch slip is Pending.',
    command: 'batchSlip.update',
    loadQuery: 'submissions.batchSlip',
    keys: ['batchId'],
    fields: [
      { name: 'batchId', label: 'Batch Slip Id', readOnlyOnEdit: true },
      { name: 'statusCode', label: 'Batch Slip Status', type: 'select', codeList: 'codes.submissions.paperBatchStatuses', readOnlyOnEdit: true },
      { name: 'entryUserid', label: 'Batch Slip Creator', readOnlyOnEdit: true },
      { name: 'entryTimestamp', label: 'Batch Slip Creation Date', type: 'date', readOnlyOnEdit: true },
      { name: 'dtlDocBatchId', label: 'Matched Detail Batch Id', readOnlyOnEdit: true },
      { name: 'returnType', label: 'Return Type', readOnlyOnEdit: true },
      ...slipFields,
    ],
    then: { screen: 'batch-slip', params: { batchId: 'batchId' } },
  },
];
