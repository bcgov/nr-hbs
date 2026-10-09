import type { ColumnDef, FieldDef, ScreenDef } from '../types';

/**
 * Scale Control (legacy tab P900) — Scale Return Change Log (P961/P962) and
 * Scale Site Software Use (P991/P992/P993).
 * Backend: catalog/ScaleControlAuditCatalog.java.
 */

const RETURN_FORMATS = [
  { value: 'SSR', label: 'Summary' },
  { value: 'DSR', label: 'Detail' },
];

const CHANGE_LOG_COLUMNS: ColumnDef[] = [
  { key: 'changeDate', header: 'Date/Time', format: 'datetime', sortable: true },
  { key: 'userId', header: 'User ID', sortable: true },
  { key: 'action', header: 'Action' },
  { key: 'field', header: 'Field', sortable: true },
  { key: 'previousValue', header: 'Previous Value' },
  { key: 'currentValue', header: 'Current Value' },
];

const SOFTWARE_CRITERIA: FieldDef[] = [
  { name: 'fromDate', label: 'Scale Date Range From', type: 'date', required: true, group: 'Scale Dates' },
  { name: 'toDate', label: 'To', type: 'date', required: true, group: 'Scale Dates' },
  { name: 'product', label: 'Product', type: 'select', codeList: 'codes.scaleControl.softwareProducts', group: 'Software' },
  { name: 'version', label: 'Version', maxLength: 4, group: 'Software' },
  { name: 'revision', label: 'Revision', maxLength: 4, group: 'Software' },
  { name: 'scaleSite', label: 'Scale Site No', maxLength: 4, upper: true, group: 'Scale Site' },
];

/** Report prompts shared by HBS3R992 / HBS3R993 (dates are yyyy-mm-dd). */
const SOFTWARE_REPORT_PARAMS = {
  scaleSite: 'scaleSite',
  product: 'product',
  version: 'version',
  revision: 'revision',
  from: 'fromDate',
  to: 'toDate',
};

export const screens: ScreenDef[] = [
  {
    kind: 'search',
    id: 'change-log',
    legacy: 'P961/P962',
    area: 'scale-control',
    title: 'Search Scale Return Change Log',
    navLabel: 'Scale Return Change Log',
    capability: 'SCALE_CONTROL_MINISTRY_VIEW',
    query: 'scaleControl.changeLog.search',
    criteria: [
      { name: 'returnFormat', label: 'Return Format', type: 'radio', required: true, options: RETURN_FORMATS, defaultValue: 'SSR' },
      {
        name: 'fieldName',
        label: 'Changes To',
        type: 'radio',
        options: [
          { value: '', label: 'Any Field' },
          { value: 'Timber_Mark', label: 'Timber Mark' },
          { value: 'Scale_Date', label: 'Scale Date' },
          { value: 'Scale_Site', label: 'Scale Site' },
          { value: 'Pop_Strat_Year', label: 'Pop/Strat/Year' },
        ],
      },
      { name: 'fromDate', label: 'From Date', type: 'date', required: true, group: 'Change Date' },
      { name: 'toDate', label: 'To Date', type: 'date', required: true, group: 'Change Date' },
      {
        name: 'userId',
        label: 'User ID',
        upper: true,
        maxLength: 30,
        placeholder: 'IDIR\\USERID',
        helperText: 'Domain and user id, e.g. IDIR\\JSMITH',
      },
    ],
    columns: [
      { key: 'documentNumber', header: 'Document Number', sortable: true },
      { key: 'versionNumber', header: 'Version' },
      ...CHANGE_LOG_COLUMNS,
    ],
    rowLink: {
      screen: 'change-log-document',
      params: { returnFormat: 'returnFormat', documentNumber: 'documentNumber', versionNumber: 'versionNumber' },
    },
    reports: [
      {
        reportId: 'HBS2R962',
        label: 'Print',
        params: {
          AUDITLOG_RETURN_FORMAT: 'returnFormat',
          AUDITLOG_CHANGED_FIELD: 'fieldName',
          AUDITLOG_CHANGEDFROMDATE: 'fromDate',
          AUDITLOG_CHANGEDTODATE: 'toDate',
          AUDITLOG_USERID: 'userId',
        },
      },
    ],
  },
  {
    kind: 'search',
    id: 'change-log-document',
    legacy: 'P962',
    area: 'scale-control',
    title: 'Scale Return Change Log',
    nav: false,
    capability: 'SCALE_CONTROL_VIEW',
    description: 'Field-level change history of one scale return version (SDN / DDN).',
    query: 'scaleControl.changeLog.document',
    criteria: [
      { name: 'returnFormat', label: 'Return Format', type: 'select', required: true, options: RETURN_FORMATS },
      { name: 'documentNumber', label: 'Document Number', required: true, upper: true, maxLength: 13 },
      { name: 'versionNumber', label: 'Version', required: true, maxLength: 2 },
    ],
    columns: CHANGE_LOG_COLUMNS,
    reports: [
      {
        reportId: 'HBS2R962',
        label: 'Print',
        params: {
          AUDITLOG_RETURN_FORMAT: 'returnFormat',
          AUDITLOG_RETURN_DOCNUMBER: 'documentNumber',
          AUDITLOG_RETURN_VERSION: 'versionNumber',
        },
      },
    ],
  },
  {
    kind: 'search',
    id: 'software-use',
    legacy: 'P991/P993',
    area: 'scale-control',
    title: 'Search for Scale Site Software Use',
    navLabel: 'Scale Site Software Use',
    capability: 'SOFTWARE_USE_VIEW',
    description: 'Software Use List — returns submitted per scale site and software product, version and revision.',
    notes:
      'Software vendors only see their own products. Use "Software Anomalies" for returns submitted with unregistered or invalid software.',
    query: 'scaleControl.software.use',
    criteria: SOFTWARE_CRITERIA,
    columns: [
      { key: 'returnType', header: 'Return Type', sortable: true },
      { key: 'scaleSite', header: 'Scale Site', sortable: true },
      { key: 'returnCount', header: 'Return Count', format: 'number' },
      { key: 'softwareProduct', header: 'Software Product', sortable: true },
      { key: 'softwareVersion', header: 'Software Version' },
      { key: 'softwareRevision', header: 'Software Revision' },
    ],
    reports: [
      {
        reportId: 'HBS3R993',
        label: 'Print',
        params: {
          RB_SCALE_SITE: SOFTWARE_REPORT_PARAMS.scaleSite,
          RB_SOFTWAREPRODUCT: SOFTWARE_REPORT_PARAMS.product,
          RB_SOFTWAREVERSION: SOFTWARE_REPORT_PARAMS.version,
          RB_SOFTWAREREVISION: SOFTWARE_REPORT_PARAMS.revision,
          RB_AFTER_DATE: SOFTWARE_REPORT_PARAMS.from,
          RB_BEFORE_DATE: SOFTWARE_REPORT_PARAMS.to,
        },
      },
    ],
  },
  {
    kind: 'search',
    id: 'software-anomalies',
    legacy: 'P991/P992',
    area: 'scale-control',
    title: 'Software Anomalies',
    navLabel: 'Scale Site Software Anomalies',
    capability: 'SCALE_CONTROL_VIEW',
    description: 'Anomaly List — returns submitted with software that is not on file or not valid on the scale date.',
    query: 'scaleControl.software.anomalies',
    criteria: SOFTWARE_CRITERIA,
    columns: [
      { key: 'scaleSite', header: 'Scale Site', sortable: true },
      { key: 'scaleDate', header: 'Scale Date', format: 'date', sortable: true },
      { key: 'softwareVendor', header: 'Software Vendor' },
      { key: 'softwareProduct', header: 'Software Product', sortable: true },
      { key: 'softwareVersion', header: 'Software Version' },
      { key: 'softwareRevision', header: 'Software Revision' },
      { key: 'reason', header: 'Reason', sortable: true },
      { key: 'returnType', header: 'Return Type' },
      { key: 'documentControlNo', header: 'Document Control No' },
      { key: 'documentVersion', header: 'Document Version' },
    ],
    reports: [
      {
        reportId: 'HBS3R992',
        label: 'Print',
        params: {
          P_SCALESITE: SOFTWARE_REPORT_PARAMS.scaleSite,
          P_SOFTWAREPRODUCT: SOFTWARE_REPORT_PARAMS.product,
          P_SOFTWAREVERSION: SOFTWARE_REPORT_PARAMS.version,
          P_SOFTWAREREVISION: SOFTWARE_REPORT_PARAMS.revision,
          P_AFTER_DATE: SOFTWARE_REPORT_PARAMS.from,
          P_BEFORE_DATE: SOFTWARE_REPORT_PARAMS.to,
        },
      },
    ],
  },
];
