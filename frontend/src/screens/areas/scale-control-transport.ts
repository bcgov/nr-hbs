import type { ScreenDef } from '../types';

/**
 * Scale Control (legacy tab P900) — Timber Transport Events (P971/P972) and
 * the Load Description Slip registry (P975–P979, P981/P982).
 * Backend: ScaleControlTransportCatalog (scaleControl.transport.*, scaleControl.lds.*).
 */

const TRANSPORT_TYPES = [
  { value: 'CBD', label: 'Cut Block Departures' },
  { value: 'DEP', label: 'Departures To Other Sites' },
  { value: 'ARR', label: 'Arrivals From Other Sites' },
  { value: 'INT', label: 'Intra-Site Events' },
];

/** Prompts shared by HBS3R972/973/974/979 (legacy ReportingFactory2.getTimberTransportEventParameters). */
const transportPrompts = {
  RB_TIMBER_MARK: 'timberMark',
  RB_CUT_BLOCK_ID: 'cutBlock',
  RB_SCALE_SITE: 'scaleSite',
  RB_FROM_DATE: 'fromDate',
  RB_TO_DATE: 'toDate',
  RB_FROM_LOAD_NUMBER: 'loadNumberFrom',
  RB_TO_LOAD_NUMBER: 'loadNumberTo',
  RB_FROM_WEIGH_SLIP_NUMBER: 'weighSlipFrom',
  RB_TO_WEIGH_SLIP_NUMBER: 'weighSlipTo',
};

export const screens: ScreenDef[] = [
  // P971 / P972 — Search for Timber Transport Events / List of <type>
  {
    kind: 'search',
    id: 'transport-events',
    legacy: 'P971/P972',
    area: 'scale-control',
    title: 'Search for Timber Transport Events',
    capability: 'SCALE_CONTROL_MINISTRY_VIEW',
    query: 'scaleControl.transport.search',
    notes:
      'Only active versions will be returned. Originating Site is only permitted with Arrivals From Other Sites, ' +
      'Destination Site only with Departures To Other Sites; Transport ID and LDS Number are not used for Intra-Site Events. ' +
      'Use * as a wildcard in Cut Block, Transport ID and LDS Number. The legacy "Send XML" request is replaced by Export CSV.',
    criteria: [
      { name: 'transportType', label: 'Timber Transport Type', type: 'radio', options: TRANSPORT_TYPES, defaultValue: 'CBD', required: true, span: 4 },
      { name: 'timberMark', label: 'Timber Mark', upper: true, maxLength: 6, group: 'Source/Destination' },
      { name: 'cutBlock', label: 'Cut Block', maxLength: 10, helperText: 'use * as wildcard', group: 'Source/Destination' },
      { name: 'scaleSite', label: 'Scale Site', maxLength: 4, group: 'Source/Destination' },
      { name: 'originatingSite', label: 'Originating Site', maxLength: 4, group: 'Source/Destination' },
      { name: 'destinationSite', label: 'Destination Site', maxLength: 4, group: 'Source/Destination' },
      { name: 'fromDate', label: 'From Date', type: 'date', required: true, group: 'Date Range' },
      { name: 'toDate', label: 'To Date', type: 'date', required: true, group: 'Date Range' },
      { name: 'loadNumberFrom', label: 'Load Number From', type: 'number', maxLength: 10, group: 'Range of Numbers' },
      { name: 'loadNumberTo', label: 'Load Number To', type: 'number', maxLength: 10, group: 'Range of Numbers' },
      { name: 'weighSlipFrom', label: 'Weigh Slip From', type: 'number', maxLength: 10, group: 'Range of Numbers' },
      { name: 'weighSlipTo', label: 'Weigh Slip To', type: 'number', maxLength: 10, group: 'Range of Numbers' },
      { name: 'transportId', label: 'Transport ID', maxLength: 16, helperText: 'use * as wildcard', group: 'Range of Numbers' },
      { name: 'ldsNumber', label: 'LDS Number', maxLength: 10, helperText: 'use * as wildcard', group: 'Range of Numbers' },
    ],
    columns: [
      { key: 'detailDocumentNumber', header: 'DDN', sortable: true },
      { key: 'docVersion', header: 'DDV' },
      { key: 'docType', header: 'Doc Type' },
      { key: 'scaleEventTypeCode', header: 'Event Type' },
      { key: 'versionState', header: 'Status' },
      { key: 'timberMark', header: 'Timber Mark', sortable: true },
      { key: 'cutBlockId', header: 'Cut Block' },
      { key: 'fromScaleSite', header: 'Scale Site (from)' },
      { key: 'transportId', header: 'Transport ID' },
      { key: 'ldsNumber', header: 'LDS No' },
      { key: 'scaleSite', header: 'Scale Site', sortable: true },
      { key: 'destinationSite', header: 'Destination Site' },
      { key: 'eventDate', header: 'Arrival/Departure or Scale Date', format: 'date', sortable: true },
      { key: 'lanOrWsn', header: 'LAN/LDN or WSN', sortable: true },
      { key: 'scalerReturnOrPsy', header: 'Scaler/Return or Pop/Str/Yr' },
      { key: 'kgOrM3', header: 'M3 or Kg' },
    ],
    reports: [
      {
        reportId: 'HBS3R972',
        label: 'Print Cut Block Departures',
        params: { ...transportPrompts, RB_TRANSPORT_ID: 'transportId', RB_LDS_NUMBER: 'ldsNumber' },
      },
      {
        reportId: 'HBS3R974',
        label: 'Print Departures To Other Sites',
        params: {
          ...transportPrompts,
          RB_DESTINATION_SITE: 'destinationSite',
          RB_TRANSPORT_ID: 'transportId',
          RB_LDS_NUMBER: 'ldsNumber',
        },
      },
      {
        reportId: 'HBS3R973',
        label: 'Print Arrivals From Other Sites',
        params: {
          ...transportPrompts,
          RB_ORIGINATING_SITE: 'originatingSite',
          RB_TRANSPORT_ID: 'transportId',
          RB_LDS_NUMBER: 'ldsNumber',
        },
      },
      { reportId: 'HBS3R979', label: 'Print Intra-Site Events', params: transportPrompts },
    ],
  },

  // P975 / P976 — Search for LDS Registry Entries / List of Pre-Registered LDS Numbers
  {
    kind: 'search',
    id: 'lds-registry',
    legacy: 'P975/P976',
    area: 'scale-control',
    title: 'Search for LDS Registry Entries',
    capability: 'SCALE_CONTROL_MINISTRY_VIEW',
    query: 'scaleControl.lds.search',
    requireOneOf: ['timberMark', 'cutBlock', 'clientNumber', 'licence', 'district'],
    criteria: [
      { name: 'timberMark', label: 'Timber Mark', upper: true, maxLength: 6 },
      { name: 'cutBlock', label: 'Cut Block', maxLength: 10 },
      { name: 'clientNumber', label: 'Client', maxLength: 8 },
      { name: 'licence', label: 'Licence', upper: true, maxLength: 10 },
      { name: 'district', label: 'District', type: 'select', codeList: 'codes.orgUnits' },
    ],
    columns: [
      { key: 'timberMark', header: 'Timber Mark', sortable: true },
      { key: 'cutBlockId', header: 'Cut Block', sortable: true },
      { key: 'startNumber', header: 'Starting No', sortable: true },
      { key: 'endNumber', header: 'Ending No' },
      { key: 'dateRegistered', header: 'Date Registered', format: 'date', sortable: true },
      { key: 'district', header: 'District' },
      { key: 'clientNumber', header: 'Client' },
      { key: 'clientLocnCode', header: 'Loc' },
      { key: 'licence', header: 'Licence' },
      { key: 'permit', header: 'Permit' },
    ],
    rowLink: { screen: 'lds-registry-detail', params: { ldsregId: 'ldsregId' } },
    createLink: { screen: 'lds-registry-add', params: {}, label: 'Add', capability: 'SCALE_CONTROL_ADMIN' },
    reports: [
      {
        reportId: 'HBS3R976',
        label: 'Print',
        params: {
          RB_TIMBER_MARK: 'timberMark',
          RB_CUT_BLOCK: 'cutBlock',
          RB_LICENSE: 'licence',
          RB_DISTRICT: 'district',
          RB_CLIENT: 'clientNumber',
        },
      },
    ],
  },

  // P978 (read-only part) / P979 — LDS Registry Entry with Update and Delete
  {
    kind: 'detail',
    id: 'lds-registry-detail',
    legacy: 'P978/P979',
    area: 'scale-control',
    title: 'LDS Registry Entry',
    capability: 'SCALE_CONTROL_MINISTRY_VIEW',
    nav: false,
    query: 'scaleControl.lds.entry',
    keys: ['ldsregId'],
    sections: [
      {
        title: 'LDS Registry Entry',
        fields: [
          { key: 'timberMark', label: 'Timber Mark' },
          { key: 'cutBlockId', label: 'Cut Block' },
          { key: 'startNumber', label: 'Starting No' },
          { key: 'endNumber', label: 'Ending No' },
          { key: 'dateRegistered', label: 'Date Registered', format: 'date' },
          { key: 'district', label: 'District' },
          { key: 'clientNumber', label: 'Client' },
          { key: 'clientLocnCode', label: 'Loc' },
          { key: 'licence', label: 'Licence' },
          { key: 'permit', label: 'Permit' },
        ],
      },
    ],
    editLink: {
      screen: 'lds-registry-edit',
      label: 'Update',
      capability: 'SCALE_CONTROL_ADMIN',
      params: { ldsregId: 'ldsregId', entryUserid: 'entryUserid', entryTimestamp: 'entryTimestamp' },
    },
    actions: [
      {
        id: 'delete',
        label: 'Delete',
        command: 'scaleControl.lds.delete',
        capability: 'SCALE_CONTROL_ADMIN',
        params: { ldsregId: 'ldsregId' },
        confirm: 'Confirm Delete LDS Registry Entry: are you sure you want to delete this LDS registry entry?',
        danger: true,
        then: { screen: 'lds-registry', params: { timberMark: 'timberMark', cutBlock: 'cutBlockId' } },
      },
    ],
  },

  // P977 — Add A LDS Registry Entry
  {
    kind: 'form',
    id: 'lds-registry-add',
    legacy: 'P977',
    area: 'scale-control',
    title: 'Add A LDS Registry Entry',
    capability: 'SCALE_CONTROL_ADMIN',
    nav: false,
    command: 'scaleControl.lds.create',
    fields: [
      { name: 'timberMark', label: 'Timber Mark', required: true, upper: true, maxLength: 6 },
      { name: 'cutBlockId', label: 'Cut Block', required: true, maxLength: 10 },
      { name: 'startNumber', label: 'Starting No', type: 'number', required: true, maxLength: 10 },
      { name: 'endNumber', label: 'Ending No', type: 'number', required: true, maxLength: 10 },
    ],
    then: { screen: 'lds-registry-detail', params: { ldsregId: 'loadDescSlipRegistryId' } },
  },

  // P978 — Update A LDS Registry Entry
  {
    kind: 'form',
    id: 'lds-registry-edit',
    legacy: 'P978',
    area: 'scale-control',
    title: 'Update A LDS Registry Entry',
    capability: 'SCALE_CONTROL_ADMIN',
    nav: false,
    command: 'scaleControl.lds.update',
    loadQuery: 'scaleControl.lds.entry',
    keys: ['ldsregId'],
    fields: [
      { name: 'timberMark', label: 'Timber Mark', required: true, upper: true, maxLength: 6 },
      { name: 'cutBlockId', label: 'Cut Block', required: true, maxLength: 10 },
      { name: 'startNumber', label: 'Starting No', type: 'number', required: true, maxLength: 10 },
      { name: 'endNumber', label: 'Ending No', type: 'number', required: true, maxLength: 10 },
    ],
    then: { screen: 'lds-registry', params: { timberMark: 'timberMark', cutBlock: 'cutBlockId' } },
  },

  // P981 / P982 — Search for LDS Registry Violations / List of LDS Registry Violations
  {
    kind: 'search',
    id: 'lds-violations',
    legacy: 'P981/P982',
    area: 'scale-control',
    title: 'Search for LDS Registry Violations',
    capability: 'SCALE_CONTROL_MINISTRY_VIEW',
    query: 'scaleControl.lds.violations',
    criteria: [
      { name: 'timberMark', label: 'Timber Mark', required: true, upper: true, maxLength: 6, group: 'Source' },
      { name: 'cutBlock', label: 'Cut Block', required: true, maxLength: 10, group: 'Source' },
      { name: 'arrivalDateFrom', label: 'From', type: 'date', required: true, group: 'Arrival Date Range' },
      { name: 'arrivalDateTo', label: 'To', type: 'date', required: true, group: 'Arrival Date Range' },
    ],
    columns: [
      { key: 'timberMark', header: 'Timber Mark' },
      { key: 'cutBlock', header: 'Cut Block' },
      { key: 'transportIdentifier', header: 'Transport ID' },
      { key: 'ldsNumber', header: 'LDS No', sortable: true },
      { key: 'arrivalDate', header: 'Arrival Date', format: 'date', sortable: true },
      { key: 'scaleSite', header: 'Scale Site', sortable: true },
      { key: 'loadArrivalNumber', header: 'Load Arrival No' },
      { key: 'returnType', header: 'Return Type' },
      { key: 'scaleDate', header: 'Scale Date', format: 'date' },
      { key: 'detailDocumentNumber', header: 'Detail Document No', sortable: true },
      { key: 'anomaly', header: 'Anomaly' },
      { key: 'assignedLdsRange', header: 'Assigned LDS Range' },
    ],
    reports: [
      {
        reportId: 'HBS3R982',
        label: 'Print',
        params: {
          PARAM_TIMBERMARK: 'timberMark',
          PARAM_CUTBLOCK: 'cutBlock',
          PARAM_RECEIVEDONAFTER: 'arrivalDateFrom',
          PARAM_RECEIVEDONBEFORE: 'arrivalDateTo',
        },
      },
    ],
  },
];
