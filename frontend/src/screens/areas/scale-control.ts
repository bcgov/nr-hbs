import type { FieldDef, ReportLinkDef, ScreenDef } from '../types';

/**
 * Scale Control (legacy tab P900) — Late Submissions (P901/P902), Scale Event
 * Anomalies (P911–P955) and Anomaly Assessment Windows (P908/P909).
 * Backend: catalog/ScaleControlCatalog.java. Transport / LDS screens are in
 * scale-control-transport.ts; change log / software use in scale-control-audit.ts.
 */

/**
 * Legacy P911 "Anomaly Type" select (radAnomalyType). The URL values are the
 * ones the Home "Recent Anomalies" links use (anomalyType=SCALING, …); the
 * backend maps them to SCALE_CONTROL_TYPE_CODE (+ SCALE_ANOMALY_TYPE_CODE for
 * the single gap / duplicate options).
 */
const ANOMALY_TYPES = [
  { value: 'SCALING_GAP', label: 'Scaling Event Gaps' },
  { value: 'SCALING_DUP', label: 'Scaling Event Duplicates' },
  { value: 'SCALING', label: 'Scaling Event Gaps and Duplicates' },
  { value: 'WEIGHING_GAP', label: 'Weighing Event Gaps' },
  { value: 'WEIGHING_DUP', label: 'Weighing Event Duplicates' },
  { value: 'WEIGHING', label: 'Weighing Event Gaps and Duplicates' },
  { value: 'ARRIVAL_GAP', label: 'Arrival Event Gaps' },
  { value: 'ARRIVAL_DUP', label: 'Arrival Event Duplicates' },
  { value: 'ARRIVAL', label: 'Arrival Event Gaps and Duplicates' },
  { value: 'ARR_DEP', label: 'Arrivals without matching Departures' },
  { value: 'DEP_ARR', label: 'Departures without matching Arrivals' },
  { value: 'WEIGH_SAMPLE', label: 'Weigh Slips without matching Samples' },
  { value: 'WEIGH_RED', label: 'Weigh Slips without matching Red Tag Scales' },
  { value: 'RED_WEIGH', label: 'Red Tag Scales without matching Weigh Slips' },
];

const ANOMALY_CRITERIA: FieldDef[] = [
  { name: 'status', label: 'Anomaly Status', type: 'select', codeList: 'codes.anomalyStatuses' },
  { name: 'anomalyType', label: 'Anomaly Type', type: 'select', required: true, options: ANOMALY_TYPES, span: 2 },
  { name: 'scaleDateFrom', label: 'From', type: 'date', group: 'Document Event Date [Scale Date]' },
  { name: 'scaleDateTo', label: 'To', type: 'date', group: 'Document Event Date [Scale Date]' },
  { name: 'assessedFrom', label: 'From', type: 'date', group: 'Original Assessment Date' },
  { name: 'assessedTo', label: 'To', type: 'date', group: 'Original Assessment Date' },
  { name: 'primaryLicence', label: 'Primary Scaler Licence', upper: true, maxLength: 4, group: 'Scaling Criteria' },
  { name: 'orgUnitNo', label: 'Sites in Region/District', type: 'select', codeList: 'codes.orgUnits', group: 'Scaling Criteria' },
  { name: 'scaleSite', label: 'Single Site', upper: true, maxLength: 4, group: 'Scaling Criteria' },
];

/** HBS2R9xx prompts (procs apply the scale-date range only when both ends are given). */
const ANOMALY_REPORT_PARAMS = {
  RB_ANOMALY_STATUS: 'status',
  RB_SCALED_FROM: 'scaleDateFrom',
  RB_SCALED_TO: 'scaleDateTo',
  RB_ASSESSED_FROM: 'assessedFrom',
  RB_ASSESSED_TO: 'assessedTo',
  RB_PRIMARY_LICENSE: 'primaryLicence',
  RB_SCALE_SITE: 'scaleSite',
  RB_DISTRICT_SCALED: 'orgUnitNo',
};

const anomalyReport = (reportId: string, label: string): ReportLinkDef => ({
  reportId,
  label,
  params: ANOMALY_REPORT_PARAMS,
});

export const screens: ScreenDef[] = [
  // ---- P901 / P902 Late Submissions ----------------------------------------
  {
    kind: 'search',
    id: 'late-submissions',
    legacy: 'P901/P902',
    area: 'scale-control',
    title: 'Search for Late Submissions',
    navLabel: 'Late Submissions',
    description: 'Detail scale returns received more than the given number of days after their scale date.',
    capability: 'SCALE_CONTROL_VIEW',
    query: 'scaleControl.lateSubmissions.search',
    notes:
      'Industry users see returns for scale sites owned by their client. The legacy "Send XML" request is replaced by Export CSV.',
    criteria: [
      {
        name: 'returnType',
        label: 'Return Type',
        type: 'radio',
        defaultValue: 'ALL',
        options: [
          { value: 'P', label: 'Log Tallies' },
          { value: 'W', label: 'Weigh Slips' },
          { value: 'S', label: 'Sample Tallies' },
          { value: 'ALL', label: 'All' },
        ],
        span: 4,
      },
      { name: 'scaleDateFrom', label: 'From', type: 'date', required: true, group: 'Scale Date' },
      { name: 'scaleDateTo', label: 'To', type: 'date', required: true, group: 'Scale Date' },
      {
        name: 'daysLateLimit',
        label: 'Received More Than (Days After Scale Date)',
        type: 'number',
        required: true,
        defaultValue: '14',
        maxLength: 3,
        helperText: 'Between 1 and 731',
        span: 2,
      },
      { name: 'orgUnitNo', label: 'Sites in Region/District', type: 'select', codeList: 'codes.orgUnits', group: 'Scale Site' },
      { name: 'scaleSite', label: 'Single Site', upper: true, maxLength: 4, group: 'Scale Site' },
    ],
    columns: [
      { key: 'detailDocumentNumber', header: 'DDN', sortable: true },
      { key: 'docVersion', header: 'Doc Ver' },
      { key: 'docType', header: 'Doc Type' },
      { key: 'eventType', header: 'Event Type' },
      { key: 'versionStatus', header: 'Ver Status' },
      { key: 'scaleSite', header: 'Scale Site', sortable: true },
      { key: 'weighSlipNumber', header: 'Weigh Slip Number' },
      { key: 'popStrYr', header: 'Pop/Str/Yr' },
      { key: 'scalerLicence', header: 'Scaler Licence' },
      { key: 'returnNumber', header: 'Return' },
      { key: 'scaleDate', header: 'Scale Date', format: 'date', sortable: true },
      { key: 'dueDate', header: 'Due Date', format: 'date' },
      { key: 'dateReceived', header: 'Date Received', format: 'date', sortable: true },
      { key: 'daysLate', header: 'Days Late', format: 'number', sortable: true },
    ],
    reports: [
      {
        reportId: 'HBS3R902',
        label: 'Print',
        params: {
          PLLS_SCALESITE: 'scaleSite',
          PLLS_SCALEDONAFTER: 'scaleDateFrom',
          PLLS_SCALEDONBEFORE: 'scaleDateTo',
          PLLS_RADRETURNTYPE: 'returnType',
          PLLS_DAYSLATELIMIT: 'daysLateLimit',
          PLLS_SEARCHBYSITES: '=sitesinregionordistrict',
          PLLS_DISTRICTSCALED: 'orgUnitNo',
        },
      },
    ],
  },

  // ---- P911 / P912–P953 Scale Event Anomalies -------------------------------
  {
    kind: 'search',
    id: 'scale-anomalies',
    legacy: 'P911/P912/P914/P922/P932/P933/P942/P952/P953',
    area: 'scale-control',
    title: 'Search for Scale Event Anomalies',
    navLabel: 'Scale Event Anomalies',
    description: 'Gaps, duplicates and mismatches found by the nightly scale anomaly assessment (SCALE_ANOMALY_LOG).',
    capability: 'SCALE_CONTROL_VIEW',
    query: 'scaleControl.anomalies.search',
    notes:
      'Industry users see anomalies at scale sites owned by their client. Open a row to see the anomaly details and who cleared it.',
    criteria: ANOMALY_CRITERIA,
    columns: [
      { key: 'scaleSite', header: 'Scale Site', sortable: true },
      { key: 'docType', header: 'Doc Type' },
      { key: 'eventType', header: 'Event Type' },
      { key: 'primaryLicence', header: 'Primary Scaler', sortable: true },
      { key: 'returnNumber', header: 'Return Number', sortable: true },
      { key: 'weighSlipNumber', header: 'Weigh Slip', sortable: true },
      { key: 'loadArrivalNumber', header: 'Load Arrival' },
      { key: 'loadDepartureNumber', header: 'Departure Number' },
      { key: 'eventDate', header: 'Scale Date', format: 'date', sortable: true },
      { key: 'timberMark', header: 'Timber Mark' },
      { key: 'ldsNumber', header: 'LDS' },
      { key: 'otherSite', header: 'Originating / Destination Site' },
      { key: 'anomalyType', header: 'Anomaly Type' },
      { key: 'missingRange', header: 'Missing' },
      { key: 'anomalyStatus', header: 'Anomaly Status', sortable: true },
      { key: 'originalAssessment', header: 'Original Assessment', format: 'datetime', sortable: true },
    ],
    rowLink: { screen: 'scale-anomaly-detail', params: { anomalyId: 'anomalyId' } },
    createLink: { screen: 'anomaly-windows-view', params: {}, label: 'View Assessment Windows', capability: 'SCALE_CONTROL_VIEW' },
    reports: [
      anomalyReport('HBS2R912', 'Print Scaling Event Gaps and Duplicates'),
      anomalyReport('HBS2R914', 'Print Weighing Event Gaps and Duplicates'),
      anomalyReport('HBS2R922', 'Print Arrival Event Gaps and Duplicates'),
      anomalyReport('HBS2R932', 'Print Arrivals without matching Departures'),
      anomalyReport('HBS2R933', 'Print Departures without matching Arrivals'),
      anomalyReport('HBS2R942', 'Print Weigh Slips without matching Samples'),
      anomalyReport('HBS2R952', 'Print Weigh Slips without matching Red Tag Scales'),
      anomalyReport('HBS2R953', 'Print Red Tag Scales without matching Weigh Slips'),
    ],
  },
  {
    kind: 'detail',
    id: 'scale-anomaly-detail',
    legacy: 'P954/P955',
    area: 'scale-control',
    title: 'Scale Event Anomaly',
    nav: false,
    capability: 'SCALE_CONTROL_VIEW',
    query: 'scaleControl.anomalies.detail',
    keys: ['anomalyId'],
    notes:
      'Clearing an anomaly (legacy P954, HBS_SCALE_ADMIN) is not yet available here; it needs a dedicated service (see docs/areas/scale-control.md).',
    sections: [
      {
        title: 'Anomaly',
        fields: [
          { key: 'scaleControlType', label: 'Scale Control Type' },
          { key: 'anomalyType', label: 'Anomaly Type' },
          { key: 'anomalyStatus', label: 'Anomaly Status' },
          { key: 'missingRange', label: 'Missing' },
          { key: 'originalAssessment', label: 'Original Assessment', format: 'datetime' },
          { key: 'assessmentStatus', label: 'Assessment Status' },
        ],
      },
      {
        title: 'Document',
        fields: [
          { key: 'docType', label: 'Return Type' },
          { key: 'eventType', label: 'Event Type' },
          { key: 'detailDocumentNumber', label: 'DDN' },
          { key: 'docVersion', label: 'Doc Ver' },
          { key: 'primaryLicence', label: 'Primary Scaler' },
          { key: 'returnNumber', label: 'Return Number' },
          { key: 'weighSlipNumber', label: 'Weigh Slip Number' },
          { key: 'eventDate', label: 'Event Date', format: 'date' },
          { key: 'scaleSite', label: 'Scale Site' },
          { key: 'siteName', label: 'Site Name' },
          { key: 'loadArrivalNumber', label: 'Load Arrival Number' },
          { key: 'loadDepartureNumber', label: 'Load Departure Number' },
          { key: 'timberMark', label: 'Timber Mark' },
          { key: 'timberBrand', label: 'Timber Brand' },
          { key: 'popStrYr', label: 'Pop/Strat/Year' },
          { key: 'ldsNumber', label: 'LDS' },
          { key: 'transportId', label: 'Transport ID' },
          { key: 'otherSite', label: 'Originating / Destination Site' },
          { key: 'logCount', label: 'Log Count', format: 'number' },
          { key: 'sampleWeight', label: 'Sample Weight', format: 'number' },
        ],
      },
      {
        title: 'User Cleared',
        fields: [
          { key: 'clearedBy', label: 'Cleared By' },
          { key: 'dateCleared', label: 'Date Cleared', format: 'date' },
          { key: 'clearedComment', label: 'Comment' },
        ],
      },
    ],
  },

  // ---- P908 / P909 Anomaly Assessment Windows -------------------------------
  {
    kind: 'search',
    id: 'anomaly-windows-view',
    legacy: 'P908',
    area: 'scale-control',
    title: 'Anomaly Assessment Windows',
    nav: false,
    capability: 'SCALE_CONTROL_VIEW',
    query: 'scaleControl.anomalyWindows.list',
    autoSearch: true,
    criteria: [],
    columns: [
      { key: 'scaleControlType', header: 'Scale Control Type' },
      { key: 'assessmentBegin', header: 'From Days', format: 'number' },
      { key: 'assessmentEnd', header: 'To Days', format: 'number' },
      { key: 'assessmentStatus', header: 'Status' },
      { key: 'lastRun', header: 'Last Run', format: 'datetime' },
      { key: 'fromDate', header: 'From Date', format: 'date' },
      { key: 'toDate', header: 'To Date', format: 'date' },
    ],
    rowLink: { screen: 'anomaly-windows', params: { scaleControlTypeCode: 'scaleControlTypeCode' } },
  },
  {
    kind: 'form',
    id: 'anomaly-windows',
    legacy: 'P908/P909',
    area: 'scale-control',
    title: 'Anomaly Assessment Windows',
    nav: false,
    capability: 'PRODUCTION_CONTROL',
    description: 'Update the window span (From Days / To Days) of one scale control type.',
    notes:
      'From Days must be greater than To Days. Windows can only be changed when the status is Resolution complete or Adjustment complete. To change another control type, open it from Anomaly Assessment Windows.',
    command: 'scaleControl.anomalyWindows.update',
    loadQuery: 'scaleControl.anomalyWindows.detail',
    keys: [],
    submitLabel: 'Update Window Spans',
    then: { screen: 'anomaly-windows-view', params: {} },
    fields: [
      {
        name: 'scaleControlTypeCode',
        label: 'Scale Control Type',
        type: 'select',
        codeList: 'codes.scaleControl.controlTypes',
        required: true,
        readOnlyOnEdit: true,
        span: 2,
      },
      {
        name: 'anomalyAssessmentStatusCode',
        label: 'Status',
        type: 'select',
        codeList: 'codes.scaleControl.assessmentStatuses',
        required: true,
        readOnlyOnEdit: true,
      },
      { name: 'assessmentBegin', label: 'From Days', type: 'number', required: true, maxLength: 4, group: 'Window' },
      { name: 'assessmentEnd', label: 'To Days', type: 'number', required: true, maxLength: 4, group: 'Window' },
      { name: 'presentationSequence', label: 'Presentation Sequence', type: 'number', required: true, readOnlyOnEdit: true, group: 'Batch' },
      { name: 'previousAnomalyBatchId', label: 'Previous Batch', type: 'number', required: true, readOnlyOnEdit: true, group: 'Batch' },
      { name: 'currentAnomalyBatchId', label: 'Current Batch', type: 'number', required: true, readOnlyOnEdit: true, group: 'Batch' },
    ],
  },
];
