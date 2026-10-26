import type { ColumnDef, FieldDef, ScreenDef } from '@/screens/types';
import { SAMPLING_YEAR_OPTIONS } from '@/screens/years';

/**
 * Profiles area — legacy /cpm/* (P310–P385). Backend:
 * backend/.../catalog/ProfilesCatalog.java + ProfilesBillingCatalog.java.
 * Mapping and unported legacy logic: docs/areas/profiles.md.
 */

const SUBMISSION_TYPES = [
  { value: 'S', label: 'Summary' },
  { value: 'D', label: 'Detail' },
];

const MARK_HOLDER_RETURN_TYPES = [
  { value: 'P', label: 'Piece Scale' },
  { value: 'W', label: 'Weight Scale' },
];

const MARK_HOLDER_FREQUENCIES = [
  { value: 'DLY', label: 'Daily' },
  { value: 'BWK', label: '1-15,16-End of Month' },
  { value: 'MTH', label: 'Monthly' },
];

const POPULATION_FREQUENCIES = [
  { value: 'MTH', label: 'Monthly' },
  { value: 'BWK', label: 'Bi-Weekly' },
  { value: 'DLY', label: 'Daily' },
];

const DELIVERY_METHODS = [
  { value: 'P', label: 'Print' },
  { value: 'E', label: 'E-mail' },
  { value: 'F', label: 'FTP' },
];

/**
 * Audit columns, shown read-only. The update workflows re-read the row
 * server-side; `updateTimestamp` is sent back as the optimistic lock (the save
 * is refused when someone — or a legacy batch job — changed the record since
 * it was loaded).
 */
const AUDIT_FIELDS: FieldDef[] = [
  { name: 'entryUserid', label: 'Entered By', readOnlyOnEdit: true, group: 'Audit' },
  { name: 'entryTimestamp', label: 'Entered On', type: 'date', readOnlyOnEdit: true, group: 'Audit' },
  { name: 'updateTimestamp', label: 'Last Updated', readOnlyOnEdit: true, group: 'Audit' },
];

/** The lock value only (screens that show no other audit field). */
const LOCK_FIELD: FieldDef = { name: 'updateTimestamp', label: 'Last Updated', readOnlyOnEdit: true, group: 'Audit' };

const SCALE_SITE_COLUMNS: ColumnDef[] = [
  { key: 'scaleSiteId', header: 'Scale Site', sortable: true },
  { key: 'submissionType', header: 'Submission Type', sortable: true },
  { key: 'returnTypeDesc', header: 'Return Type', sortable: true },
  { key: 'effectiveDate', header: 'Effective Date', format: 'date', sortable: true },
  { key: 'expiryDate', header: 'Expiry Date', format: 'date', sortable: true },
  { key: 'clientNumber', header: 'Client Number', sortable: true },
  { key: 'clientLocnCode', header: 'Location' },
];

const POPULATION_COLUMNS: ColumnDef[] = [
  { key: 'populationNumber', header: 'Population', sortable: true },
  { key: 'samplingYear', header: 'Sampling Year', sortable: true },
  { key: 'effectiveDate', header: 'Effective Date', format: 'date', sortable: true },
  { key: 'expiryDate', header: 'Expiry Date', format: 'date', sortable: true },
  { key: 'frequencyDesc', header: 'Frequency' },
  { key: 'lastRatioStmtDate', header: 'Ratio Computation Period Start', format: 'date' },
  { key: 'nextRatioStmtDate', header: 'Ratio Computation Period End', format: 'date' },
];

const MARK_HOLDER_COLUMNS: ColumnDef[] = [
  { key: 'clientLoc', header: 'Client / Location', sortable: false },
  { key: 'returnTypeDesc', header: 'Return Type', sortable: true },
  { key: 'effectiveDate', header: 'Effective Date', format: 'date', sortable: true },
  { key: 'expiryDate', header: 'Expiry Date', format: 'date', sortable: true },
  { key: 'frequencyDesc', header: 'Summarization Frequency' },
  { key: 'forcedSummarizationDate', header: 'Next Scheduled Summarization', format: 'date' },
];

const DELIVERY_COLUMNS: ColumnDef[] = [
  { key: 'clientLoc', header: 'Client / Location' },
  { key: 'effectiveDate', header: 'Effective Date', format: 'date', sortable: true },
  { key: 'expiryDate', header: 'Expiry Date', format: 'date', sortable: true },
  { key: 'deliveryMethodDesc', header: 'Delivery Method' },
];

const CLIENT_CRITERIA: FieldDef[] = [
  { name: 'clientNumber', label: 'Client Number', type: 'client', maxLength: 8 },
  { name: 'clientLocnCode', label: 'Location', maxLength: 2 },
];

export const screens: ScreenDef[] = [
  // ------------------------------------------------------------ P310 / P311
  {
    kind: 'search',
    id: 'client-profile-search',
    legacy: 'P310',
    area: 'profiles',
    title: 'Search for a Client Profile',
    navLabel: 'Client Profile',
    capability: 'PROFILES_VIEW',
    query: 'profiles.client.search',
    criteria: [
      { name: 'clientNumber', label: 'Client Number', type: 'client', maxLength: 8, required: true },
      { name: 'clientLocnCode', label: 'Location', maxLength: 2 },
    ],
    columns: [
      { key: 'clientNumber', header: 'Client Number', sortable: true },
      { key: 'clientLocnCode', header: 'Location', sortable: true },
      { key: 'clientName', header: 'Client Name' },
      { key: 'locationName', header: 'Location' },
      { key: 'city', header: 'City' },
    ],
    rowLink: { screen: 'client-profile', params: { clientNumber: 'clientNumber', clientLocnCode: 'clientLocnCode' } },
    notes: 'Industry users can only look up locations of their own client.',
  },
  {
    kind: 'detail',
    id: 'client-profile',
    legacy: 'P311',
    area: 'profiles',
    title: 'Client Profile',
    nav: false,
    capability: 'PROFILES_VIEW',
    query: 'profiles.client.detail',
    keys: ['clientNumber', 'clientLocnCode'],
    sections: [
      {
        title: 'Client Profile',
        fields: [
          { key: 'clientNumber', label: 'Client Number' },
          { key: 'clientLocnCode', label: 'Location' },
          { key: 'clientName', label: 'Client Name' },
          { key: 'clientType', label: 'Client Type' },
          { key: 'locationName', label: 'Location' },
          { key: 'address1', label: 'Address Line 1' },
          { key: 'address2', label: 'Address Line 2' },
          { key: 'address3', label: 'Address Line 3' },
          { key: 'city', label: 'City' },
          { key: 'province', label: 'Province' },
          { key: 'country', label: 'Country' },
          { key: 'postalCode', label: 'Postal Code' },
          { key: 'businessPhone', label: 'Business Phone' },
        ],
      },
      {
        title: 'Client Profile Links',
        fields: [
          { key: 'scaleSiteProfileCount', label: 'Scale Site Profiles', format: 'number' },
          { key: 'markHolderProfileCount', label: 'Mark Holder Profiles', format: 'number' },
          { key: 'populationProfileCount', label: 'Population Profiles', format: 'number' },
          { key: 'deliveryProfileCount', label: 'Delivery Profiles', format: 'number' },
        ],
      },
      {
        title: 'Scale Site Profiles',
        table: {
          query: 'profiles.scaleSites.byClient',
          params: { clientNumber: 'clientNumber', clientLocnCode: 'clientLocnCode' },
          columns: SCALE_SITE_COLUMNS,
          rowLink: { screen: 'scale-site-profile-update', params: { profileKey: 'profileKey' } },
        },
      },
      {
        title: 'Mark Holder Profiles',
        table: {
          query: 'profiles.markHolders.search',
          params: { clientNumber: 'clientNumber', clientLocnCode: 'clientLocnCode' },
          columns: MARK_HOLDER_COLUMNS,
          rowLink: { screen: 'mark-holder-profile', params: { mhprofId: 'mhprofId' } },
        },
      },
      {
        title: 'Population Profiles',
        table: {
          query: 'profiles.population.search',
          params: { clientNumber: 'clientNumber', clientLocnCode: 'clientLocnCode' },
          columns: POPULATION_COLUMNS,
          rowLink: { screen: 'population-profile', params: { popprofId: 'popprofId' } },
        },
      },
      {
        title: 'Delivery Profiles',
        table: {
          query: 'profiles.delivery.search',
          params: { clientNumber: 'clientNumber', clientLocnCode: 'clientLocnCode' },
          columns: DELIVERY_COLUMNS,
          rowLink: { screen: 'delivery-profile', params: { dlvprofId: 'dlvprofId' } },
        },
      },
    ],
  },

  // ------------------------------------------------------------ P320 – P323
  {
    kind: 'search',
    id: 'scale-site-profiles',
    legacy: 'P320/P321',
    area: 'profiles',
    title: 'Search for Scale Site Profiles',
    navLabel: 'Scale Site Profiles',
    capability: 'PROFILES_VIEW',
    query: 'profiles.scaleSites.search',
    criteria: [
      { name: 'scaleSiteId', label: 'Scale Site ID', upper: true, maxLength: 4, group: 'Scale Site' },
      { name: 'clientNumber', label: 'Client Number', type: 'client', maxLength: 8, group: 'Trading Partner' },
      { name: 'clientLocnCode', label: 'Location', maxLength: 2, group: 'Trading Partner' },
      { name: 'ownerClientNumber', label: 'Client Number', type: 'client', maxLength: 8, group: 'Site Owner' },
      { name: 'ownerClientLocnCode', label: 'Location', maxLength: 2, group: 'Site Owner' },
    ],
    requireOneOf: ['scaleSiteId', 'clientNumber', 'ownerClientNumber'],
    columns: SCALE_SITE_COLUMNS,
    rowLink: { screen: 'scale-site-profile-update', params: { profileKey: 'profileKey' } },
    createLink: {
      screen: 'scale-site-profile-add',
      params: { scaleSiteId: 'scaleSiteId' },
      label: 'Add',
      capability: 'SCALE_SITE_ADMIN',
    },
    reports: [
      {
        reportId: 'HBS2R321',
        label: 'Print',
        params: {
          RB_SELECTCOUNT: '=N',
          RB_CLIENT_NUMBER: 'clientNumber',
          RB_CLIENT_LOCN_CODE: 'clientLocnCode',
          RB_SCALE_SITE_ID_NMBR: 'scaleSiteId',
          RB_JOBNO: '=',
        },
      },
    ],
    notes:
      'Only summary (trading partner submitter) rows can be updated; detail rows from the scale site profile table are read-only.',
  },
  {
    kind: 'form',
    id: 'scale-site-profile-update',
    legacy: 'P322',
    area: 'profiles',
    title: 'Update Scale Site Profile',
    nav: false,
    capability: 'SCALE_SITE_ADMIN',
    command: 'profiles.scaleSites.update',
    loadQuery: 'profiles.scaleSites.record',
    keys: ['profileKey'],
    fields: [
      { name: 'scaleSiteId', label: 'Scale Site', readOnlyOnEdit: true },
      { name: 'docType', label: 'Submission Type', type: 'select', options: SUBMISSION_TYPES, readOnlyOnEdit: true },
      { name: 'returnType', label: 'Return Type', codeList: 'codes.returnTypes', readOnlyOnEdit: true },
      { name: 'effectiveDate', label: 'Effective Date', type: 'date', readOnlyOnEdit: true },
      { name: 'expiryDate', label: 'Expiry Date', type: 'date', required: true },
      { name: 'clientNumber', label: 'Client Number', readOnlyOnEdit: true },
      { name: 'clientLocnCode', label: 'Location', readOnlyOnEdit: true },
      { name: 'tpsId', label: 'Profile ID', readOnlyOnEdit: true, group: 'Audit' },
      { name: 'submitterType', label: 'Submitter Type', readOnlyOnEdit: true, group: 'Audit' },
      ...AUDIT_FIELDS,
    ],
    then: { screen: 'scale-site-profiles', params: { scaleSiteId: 'scaleSiteId' } },
    submitLabel: 'Submit',
    notes:
      'Expiry date cannot be before the effective date or overlap the next profile for the same site, client and return type. The scale site must be in one of your associated districts.',
  },
  {
    kind: 'form',
    id: 'scale-site-profile-add',
    legacy: 'P323',
    area: 'profiles',
    title: 'Add Scale Site Profile',
    nav: false,
    capability: 'SCALE_SITE_ADMIN',
    command: 'profiles.scaleSites.create',
    fields: [
      { name: 'scaleSiteId', label: 'Scale Site', upper: true, maxLength: 4, required: true },
      { name: 'docType', label: 'Submission Type', type: 'select', options: SUBMISSION_TYPES, required: true, defaultValue: 'S' },
      { name: 'returnType', label: 'Return Type', codeList: 'codes.returnTypes', required: true },
      { name: 'effectiveDate', label: 'Effective Date', type: 'date', required: true },
      { name: 'expiryDate', label: 'Expiry Date', type: 'date', required: true },
      { name: 'clientNumber', label: 'Client Number', type: 'client', maxLength: 8 },
      { name: 'clientLocnCode', label: 'Location', maxLength: 2 },
    ],
    then: { screen: 'scale-site-profiles', params: { scaleSiteId: 'scaleSiteId' } },
    submitLabel: 'Submit',
    notes:
      'Client Number and Location must be blank when inserting a detail record and are required (a trading partner) for a summary record. The scale site must be in one of your associated districts; the effective date cannot fall inside an existing profile and the expiry date must end before the next one.',
  },

  // ------------------------------------------------------------ P330 – P333
  {
    kind: 'search',
    id: 'population-profiles',
    legacy: 'P330/P331',
    area: 'profiles',
    title: 'Search for Population Profiles',
    navLabel: 'Population Profiles',
    capability: 'PROFILES_VIEW',
    query: 'profiles.population.search',
    criteria: [
      { name: 'populationNumber', label: 'Population', maxLength: 4, group: 'Population' },
      { name: 'clientNumber', label: 'Client Number', type: 'client', maxLength: 8, group: 'Population Owner' },
      { name: 'clientLocnCode', label: 'Location', maxLength: 2, group: 'Population Owner' },
    ],
    requireOneOf: ['populationNumber', 'clientNumber'],
    columns: POPULATION_COLUMNS,
    rowLink: { screen: 'population-profile', params: { popprofId: 'popprofId' } },
    createLink: {
      screen: 'population-profile-add',
      params: { populationNumber: 'populationNumber' },
      label: 'Add',
      capability: 'SAMPLING_ADMIN',
    },
    reports: [
      {
        reportId: 'HBS3R331',
        label: 'Print',
        params: {
          RB_SELECTCOUNT: '=N',
          RB_CLIENT_NUMBER: 'clientNumber',
          RB_CLIENT_LOCN_CODE: 'clientLocnCode',
          RB_POPULATION_NMBR: 'populationNumber',
          RB_JOBNO: '=',
        },
      },
    ],
  },
  {
    kind: 'detail',
    id: 'population-profile',
    legacy: 'P332',
    area: 'profiles',
    title: 'Population Profile',
    nav: false,
    capability: 'PROFILES_VIEW',
    query: 'profiles.population.record',
    keys: ['popprofId'],
    sections: [
      {
        title: 'Population Profile',
        fields: [
          { key: 'populationNumber', label: 'Population' },
          { key: 'samplingYear', label: 'Sampling Year' },
          { key: 'effectiveDate', label: 'Effective Date', format: 'date' },
          { key: 'expiryDate', label: 'Expiry Date', format: 'date' },
          { key: 'frequencyDesc', label: 'Frequency' },
          { key: 'lastRatioStmtDate', label: 'Ratio Computation Period Start', format: 'date' },
          { key: 'nextRatioStmtDate', label: 'Ratio Computation Period End', format: 'date' },
          { key: 'clientNumber', label: 'Client Number' },
          { key: 'clientLocnCode', label: 'Location' },
        ],
      },
    ],
    editLink: {
      screen: 'population-profile-update',
      params: { popprofId: 'popprofId' },
      label: 'Update',
      capability: 'SAMPLING_ADMIN',
    },
    actions: [
      {
        id: 'delete',
        label: 'Delete',
        command: 'profiles.population.delete',
        capability: 'SAMPLING_ADMIN',
        params: { popprofId: 'popprofId', updateTimestamp: 'updateTimestamp' },
        confirm: 'Delete this population profile?',
        danger: true,
        then: { screen: 'population-profiles', params: { populationNumber: 'populationNumber' } },
      },
    ],
  },
  {
    kind: 'form',
    id: 'population-profile-update',
    legacy: 'P332',
    area: 'profiles',
    title: 'Update Population Profile',
    nav: false,
    capability: 'SAMPLING_ADMIN',
    command: 'profiles.population.update',
    loadQuery: 'profiles.population.record',
    keys: ['popprofId'],
    fields: [
      { name: 'populationNumber', label: 'Population', readOnlyOnEdit: true },
      { name: 'samplingYear', label: 'Sampling Year', type: 'select', options: SAMPLING_YEAR_OPTIONS, readOnlyOnEdit: true },
      { name: 'effectiveDate', label: 'Effective Date', type: 'date', readOnlyOnEdit: true },
      {
        name: 'expiryDate',
        label: 'Expiry Date',
        type: 'date',
        required: true,
        helperText: 'Last day of a month, not before the ratio computation period end',
      },
      { name: 'frequency', label: 'Frequency', type: 'select', options: POPULATION_FREQUENCIES, readOnlyOnEdit: true },
      { name: 'lastRatioStmtDate', label: 'Ratio Computation Period Start', type: 'date', readOnlyOnEdit: true },
      { name: 'nextRatioStmtDate', label: 'Ratio Computation Period End', type: 'date', readOnlyOnEdit: true },
      { name: 'popprofId', label: 'Profile ID', readOnlyOnEdit: true, group: 'Audit' },
      LOCK_FIELD,
    ],
    then: { screen: 'population-profile', params: { popprofId: 'popprofId' } },
    submitLabel: 'Save',
    notes:
      'Only the expiry date can be changed. It must be the last day of a month, on or after the end of the current ratio computation period, and must not overlap another profile of the population.',
  },
  {
    kind: 'form',
    id: 'population-profile-add',
    legacy: 'P333',
    area: 'profiles',
    title: 'Add Population Profile',
    nav: false,
    capability: 'SAMPLING_ADMIN',
    command: 'profiles.population.create',
    fields: [
      { name: 'populationNumber', label: 'Population', maxLength: 4, required: true },
      { name: 'samplingYear', label: 'Sampling Year', type: 'select', options: SAMPLING_YEAR_OPTIONS, required: true },
      {
        name: 'effectiveDate',
        label: 'Effective Date',
        type: 'date',
        required: true,
        helperText: 'First day of a future month (2000–2099)',
      },
      { name: 'expiryDate', label: 'Expiry Date', type: 'date', required: true, helperText: 'Last day of a month' },
      { name: 'frequency', label: 'Frequency', type: 'select', options: POPULATION_FREQUENCIES, defaultValue: 'MTH', required: true },
    ],
    then: { screen: 'population-profile', params: { popprofId: 'popprofId' } },
    submitLabel: 'Save',
    notes:
      'The first ratio computation period ends on the last day of the effective month (Monthly), the 15th (Bi-Weekly) or the effective day (Daily). Profiles of the same population and year cannot overlap.',
  },

  // ------------------------------------------------------------ P340 – P344
  {
    kind: 'search',
    id: 'mark-holder-profiles',
    legacy: 'P340/P341',
    area: 'profiles',
    title: 'Search for Mark Holder Profiles',
    navLabel: 'Mark Holder Profiles',
    capability: 'PROFILES_VIEW',
    query: 'profiles.markHolders.search',
    criteria: [
      { name: 'timberMark', label: 'Timber Mark', upper: true, maxLength: 6, group: 'Timber Mark' },
      { name: 'clientNumber', label: 'Client Number', type: 'client', maxLength: 8, group: 'Mark Holder (Licensee)' },
      { name: 'clientLocnCode', label: 'Location', maxLength: 2, group: 'Mark Holder (Licensee)' },
    ],
    requireOneOf: ['timberMark', 'clientNumber'],
    columns: MARK_HOLDER_COLUMNS,
    rowLink: { screen: 'mark-holder-profile', params: { mhprofId: 'mhprofId' } },
    createLink: {
      screen: 'mark-holder-profile-add',
      params: { clientNumber: 'clientNumber', clientLocnCode: 'clientLocnCode' },
      label: 'Add',
      capability: 'PROFILE_BILLING_ADMIN',
    },
    reports: [
      {
        reportId: 'HBS3R341',
        label: 'Print',
        params: {
          RB_SELECTCOUNT: '=N',
          RB_CLIENT_NUMBER: 'clientNumber',
          RB_CLIENT_LOCN_CODE: 'clientLocnCode',
          RB_TIMBER_MARK: 'timberMark',
          RB_JOBNO: '=',
        },
      },
    ],
  },
  {
    kind: 'detail',
    id: 'mark-holder-profile',
    legacy: 'P342/P344',
    area: 'profiles',
    title: 'Mark Holder Profile',
    nav: false,
    capability: 'PROFILES_VIEW',
    query: 'profiles.markHolders.record',
    keys: ['mhprofId'],
    sections: [
      {
        title: 'Mark Holder Profile',
        fields: [
          { key: 'clientLoc', label: 'Client / Location' },
          { key: 'clientName', label: 'Client Name' },
          { key: 'returnTypeDesc', label: 'Return Type' },
          { key: 'effectiveDate', label: 'Effective Date', format: 'date' },
          { key: 'expiryDate', label: 'Expiry Date', format: 'date' },
          { key: 'frequencyDesc', label: 'Frequency' },
          { key: 'forcedSummarizationDate', label: 'Next Scheduled Summarization', format: 'date' },
        ],
      },
    ],
    editLink: {
      screen: 'mark-holder-profile-update',
      params: { mhprofId: 'mhprofId' },
      label: 'Update',
      capability: 'PROFILE_BILLING_ADMIN',
    },
    actions: [
      {
        id: 'delete',
        label: 'Delete',
        command: 'profiles.markHolders.delete',
        capability: 'PROFILE_BILLING_ADMIN',
        params: { mhprofId: 'mhprofId', updateTimestamp: 'updateTimestamp' },
        confirm: 'Are you sure you want to delete this Mark Holder Profile?',
        danger: true,
        then: {
          screen: 'mark-holder-profiles',
          params: { clientNumber: 'clientNumber', clientLocnCode: 'clientLocnCode' },
        },
      },
    ],
    notes: 'Only profiles with a future effective date may be updated or deleted.',
  },
  {
    kind: 'form',
    id: 'mark-holder-profile-update',
    legacy: 'P342',
    area: 'profiles',
    title: 'Update Mark Holder Profile',
    nav: false,
    capability: 'PROFILE_BILLING_ADMIN',
    command: 'profiles.markHolders.update',
    loadQuery: 'profiles.markHolders.record',
    keys: ['mhprofId'],
    fields: [
      { name: 'clientNumber', label: 'Client Number', readOnlyOnEdit: true },
      { name: 'clientLocnCode', label: 'Location', readOnlyOnEdit: true },
      { name: 'returnType', label: 'Return Type', type: 'select', options: MARK_HOLDER_RETURN_TYPES, readOnlyOnEdit: true },
      { name: 'effectiveDate', label: 'Effective Date', type: 'date', readOnlyOnEdit: true },
      { name: 'expiryDate', label: 'Expiry Date', type: 'date', required: true },
      { name: 'frequency', label: 'Frequency', type: 'select', options: MARK_HOLDER_FREQUENCIES, readOnlyOnEdit: true },
      { name: 'forcedSummarizationDate', label: 'Next Scheduled Summarization', type: 'date', readOnlyOnEdit: true },
      { name: 'mhprofId', label: 'Profile ID', readOnlyOnEdit: true, group: 'Audit' },
      ...AUDIT_FIELDS,
    ],
    then: { screen: 'mark-holder-profile', params: { mhprofId: 'mhprofId' } },
    submitLabel: 'Submit',
    notes:
      'Effective date must be a future date to allow updating of the expiry date. Expiry date must be the last day of the month, not before today, and must not overlap the next profile.',
  },
  {
    kind: 'form',
    id: 'mark-holder-profile-add',
    legacy: 'P343',
    area: 'profiles',
    title: 'Add Mark Holder Profile',
    nav: false,
    capability: 'PROFILE_BILLING_ADMIN',
    command: 'profiles.markHolders.create',
    fields: [
      { name: 'clientNumber', label: 'Client Number', type: 'client', maxLength: 8, required: true },
      { name: 'clientLocnCode', label: 'Location', maxLength: 2, required: true },
      { name: 'returnType', label: 'Return Type', type: 'select', options: MARK_HOLDER_RETURN_TYPES, defaultValue: 'W', required: true },
      { name: 'effectiveDate', label: 'Effective Date', type: 'date', required: true, helperText: 'First day of a future month' },
      { name: 'expiryDate', label: 'Expiry Date', type: 'date', required: true, helperText: 'Last day of the month' },
      { name: 'frequency', label: 'Frequency', type: 'select', options: MARK_HOLDER_FREQUENCIES, defaultValue: 'MTH', required: true },
    ],
    then: { screen: 'mark-holder-profile', params: { mhprofId: 'hbsMarkHolderProfileId' } },
    submitLabel: 'Submit',
  },

  // ------------------------------------------------------------ P360 – P365
  {
    kind: 'search',
    id: 'scaler-profile-search',
    legacy: 'P360',
    area: 'profiles',
    title: 'Search for Scaler Profile',
    navLabel: 'Scaler Profile',
    capability: 'SCALER_PROFILE_EDIT',
    query: 'profiles.scalers.search',
    criteria: [{ name: 'licenceNumber', label: 'Scaler Licence ID', upper: true, maxLength: 4, required: true }],
    columns: [
      { key: 'licenceNumber', header: 'Scaler Licence' },
      { key: 'clientName', header: 'Name' },
      { key: 'licenceStatus', header: 'Status' },
      { key: 'akExpiryDate', header: 'Authentication Key Expiry Scale Date', format: 'date' },
    ],
    rowLink: { screen: 'scaler-profile', params: { licenceNumber: 'licenceNumber' } },
    notes:
      'To issue or replace a scaler authentication key, use Profiles › Request Authentication Key. Scalers without an active Scaler Profile link should contact their District Scaling Supervisor.',
  },
  {
    kind: 'search',
    id: 'scaler-expiring-keys',
    legacy: 'P360/P364',
    area: 'profiles',
    title: 'Select Scaler to Update',
    navLabel: 'Scalers with Expiring Authentication Keys',
    capability: 'SCALE_SITE_ADMIN',
    query: 'profiles.scalers.expiring',
    criteria: [
      { name: 'expiryDate', label: 'Active Scalers with Authentication Keys Expiring Before', type: 'date', required: true },
      { name: 'orgUnitNo', label: 'In Organization Unit', codeList: 'codes.orgUnits' },
      { name: 'inMyOrgUnit', label: 'In my Organization Unit', type: 'checkbox', align: 'center' },
    ],
    columns: [
      { key: 'licenceNumber', header: 'Scaler License', sortable: true },
      { key: 'clientName', header: 'Name' },
      { key: 'userId', header: 'User ID' },
      { key: 'lastActive', header: 'Last Scale Date', format: 'date', sortable: true },
      { key: 'effectiveDate', header: 'Authentication Key Active Scale Date', format: 'date' },
      { key: 'expiryDate', header: 'Authentication Key Expiry Scale Date', format: 'date', sortable: true },
      { key: 'entryUserid', header: 'Entered By' },
      { key: 'updateUserid', header: 'Updated By' },
    ],
    rowLink: { screen: 'scaler-profile', params: { licenceNumber: 'licenceNumber' } },
    notes: 'Active Scalers have scaled at least one tally, sample or weight slip during past year.',
  },
  {
    kind: 'detail',
    id: 'scaler-profile',
    legacy: 'P362',
    area: 'profiles',
    title: 'Update Scaler Profile',
    nav: false,
    capability: 'SCALER_PROFILE_EDIT',
    query: 'profiles.scalers.detail',
    keys: ['licenceNumber'],
    // P361 "Edit Authentication Key" → P362 Submit / P363 (the bespoke key request page, which shows the new
    // key once); P002 offered "Download HBS Public Key" beside the Scaler Profile link.
    extraLinks: [
      {
        path: '/profiles/scaler-key-request',
        params: { licenceNumber: 'licenceNumber' },
        label: 'Edit Authentication Key',
        capability: 'SCALER_PROFILE_EDIT',
      },
      { path: '/scale-returns/public-key', params: {}, label: 'Download Harvest Billing System Public Key', capability: 'ANY_USER' },
    ],
    sections: [
      {
        title: 'Scaler',
        fields: [
          { key: 'licenceNumber', label: 'Scaler Licence' },
          { key: 'clientName', label: 'Name' },
          { key: 'userId', label: 'User ID' },
          { key: 'activeFrom', label: 'Active From Scale Date', format: 'date' },
          { key: 'activeTo', label: 'Active To Scale Date', format: 'date' },
        ],
      },
      {
        title: 'Scaler Profile History',
        table: {
          query: 'profiles.scalers.keyHistory',
          params: { licenceNumber: 'licenceNumber' },
          columns: [
            { key: 'userId', header: 'User ID' },
            { key: 'authenticationKey', header: 'Authentication Key' },
            { key: 'effectiveDate', header: 'Active From Scale Date', format: 'date' },
            { key: 'expiryDate', header: 'Active To Scale Date', format: 'date' },
            { key: 'entryUserid', header: 'Entered By' },
            { key: 'updateUserid', header: 'Updated By' },
          ],
        },
      },
    ],
    notes:
      'Edit Authentication Key requests a new key for this scaler (legacy P362 Submit / P363 confirmation). Keys are shown masked here; the new key is shown once, when it is issued.',
  },

  // ------------------------------------------------------------ P370 – P374
  {
    kind: 'search',
    id: 'delivery-profiles',
    legacy: 'P370/P371',
    area: 'profiles',
    title: 'Search for Statement Delivery Profiles',
    navLabel: 'Statement Delivery Profiles',
    capability: 'PROFILES_VIEW',
    query: 'profiles.delivery.search',
    criteria: [
      { name: 'clientNumber', label: 'Client Number', type: 'client', maxLength: 8, required: true },
      { name: 'clientLocnCode', label: 'Location', maxLength: 2 },
    ],
    columns: DELIVERY_COLUMNS,
    rowLink: { screen: 'delivery-profile', params: { dlvprofId: 'dlvprofId' } },
    createLink: {
      screen: 'delivery-profile-add',
      params: { clientNumber: 'clientNumber', clientLocnCode: 'clientLocnCode' },
      label: 'Add',
      capability: 'PROFILE_BILLING_ADMIN',
    },
    reports: [
      {
        reportId: 'HBS3R371',
        label: 'Print',
        params: {
          RB_SELECTCOUNT: '=N',
          RB_CLIENT_NUMBER: 'clientNumber',
          RB_CLIENT_LOCN_CODE: 'clientLocnCode',
          RB_JOBNO: '=',
        },
      },
    ],
  },
  {
    kind: 'detail',
    id: 'delivery-profile',
    legacy: 'P372/P374',
    area: 'profiles',
    title: 'Statement Delivery Profile',
    nav: false,
    capability: 'PROFILES_VIEW',
    query: 'profiles.delivery.record',
    keys: ['dlvprofId'],
    sections: [
      {
        title: 'Statement Delivery Profile',
        fields: [
          { key: 'clientLoc', label: 'Client / Location' },
          { key: 'clientName', label: 'Client Name' },
          { key: 'effectiveDate', label: 'Effective Date', format: 'date' },
          { key: 'expiryDate', label: 'Expiry Date', format: 'date' },
          { key: 'deliveryMethodDesc', label: 'Document Delivery Method' },
        ],
      },
    ],
    editLink: {
      screen: 'delivery-profile-update',
      params: { dlvprofId: 'dlvprofId' },
      label: 'Update',
      capability: 'PROFILE_BILLING_ADMIN',
    },
    actions: [
      {
        id: 'delete',
        label: 'Delete',
        command: 'profiles.delivery.delete',
        capability: 'PROFILE_BILLING_ADMIN',
        params: { dlvprofId: 'dlvprofId' },
        confirm: 'Confirm Delivery Profile Delete?',
        danger: true,
        then: {
          screen: 'delivery-profiles',
          params: { clientNumber: 'clientNumber', clientLocnCode: 'clientLocnCode' },
        },
      },
    ],
  },
  {
    kind: 'form',
    id: 'delivery-profile-update',
    legacy: 'P372',
    area: 'profiles',
    title: 'Update Statement Delivery Profile',
    nav: false,
    capability: 'PROFILE_BILLING_ADMIN',
    command: 'profiles.delivery.update',
    loadQuery: 'profiles.delivery.record',
    keys: ['dlvprofId'],
    fields: [
      { name: 'clientNumber', label: 'Client Number', readOnlyOnEdit: true },
      { name: 'clientLocnCode', label: 'Location', readOnlyOnEdit: true },
      { name: 'effectiveDate', label: 'Effective Date', type: 'date', required: true },
      { name: 'expiryDate', label: 'Expiry Date', type: 'date', required: true },
      { name: 'deliveryMethod', label: 'Document Delivery Method', type: 'select', options: DELIVERY_METHODS, required: true },
      { name: 'dlvprofId', label: 'Profile ID', readOnlyOnEdit: true, group: 'Audit' },
      ...AUDIT_FIELDS,
    ],
    then: { screen: 'delivery-profile', params: { dlvprofId: 'dlvprofId' } },
    submitLabel: 'Submit',
    notes:
      'E-mail and FTP delivery require at least one active user with the document receiver role for this client location.',
  },
  {
    kind: 'form',
    id: 'delivery-profile-add',
    legacy: 'P373',
    area: 'profiles',
    title: 'Add Statement Delivery Profile',
    nav: false,
    capability: 'PROFILE_BILLING_ADMIN',
    command: 'profiles.delivery.create',
    fields: [
      { name: 'clientNumber', label: 'Client Number', type: 'client', maxLength: 8, required: true },
      { name: 'clientLocnCode', label: 'Location', maxLength: 2, required: true },
      { name: 'effectiveDate', label: 'Effective Date', type: 'date', required: true, helperText: 'Must be a future date' },
      { name: 'expiryDate', label: 'Expiry Date', type: 'date', required: true },
      { name: 'deliveryMethod', label: 'Document Delivery Method', type: 'select', options: DELIVERY_METHODS, defaultValue: 'P', required: true },
    ],
    then: { screen: 'delivery-profile', params: { dlvprofId: 'hbsCliDelProfileId' } },
    submitLabel: 'Submit',
  },

  // ------------------------------------------------------------ P380 – P385
  {
    kind: 'search',
    id: 'cruise-based-profiles',
    legacy: 'P380/P381',
    area: 'profiles',
    title: 'Search for Cruise Based Billing Profiles',
    navLabel: 'Cruise Based Billing Profiles',
    capability: 'PROFILES_VIEW',
    query: 'profiles.cruiseBased.search',
    criteria: [
      { name: 'timberMark', label: 'Timber Mark', upper: true, maxLength: 6 },
      {
        name: 'locatedIn',
        label: 'Timber Marks Located In',
        codeList: 'codes.orgUnits',
        helperText: 'Ministry users only; lists marks with an incomplete profile or documents in error',
      },
    ],
    requireOneOf: ['timberMark', 'locatedIn'],
    columns: [
      { key: 'timberMark', header: 'Timber Mark', sortable: true },
      { key: 'effectiveDate', header: 'Effective Date', format: 'date', sortable: true },
      { key: 'expiryDate', header: 'Expiry Date', format: 'date' },
      { key: 'ttlMerchntblArea', header: 'Total Merchantable Area (Ha)', format: 'number' },
      { key: 'netCruiseVol', header: 'Net Cruise Volume', format: 'volume' },
      { key: 'intDecVol', header: 'Deciduous Volume', format: 'volume' },
      { key: 'totalVol', header: 'Total Cruise Volume', format: 'volume' },
      { key: 'm3PerHa', header: 'Appraised m3/Ha', format: 'number' },
      { key: 'profileM3Display', header: 'Profile m3/Ha' },
      { key: 'errorCount', header: 'Documents in Error', format: 'number', sortable: true },
    ],
    rowLink: {
      screen: 'cruise-based-profile',
      params: { timberMark: 'timberMark', effectiveDate: 'effectiveDateKey' },
    },
  },
  {
    kind: 'detail',
    id: 'cruise-based-profile',
    legacy: 'P385/P383',
    area: 'profiles',
    title: 'View Cruise Based Billing Profile',
    nav: false,
    capability: 'PROFILES_VIEW',
    query: 'profiles.cruiseBased.detail',
    keys: ['timberMark', 'effectiveDate'],
    sections: [
      {
        title: 'Timber Mark Information',
        fields: [
          { key: 'timberMark', label: 'Timber Mark' },
          { key: 'cuttingPermitId', label: 'Cutting Permit' },
          { key: 'saleMethodCode', label: 'Sale Method' },
          { key: 'saleMethodDescription', label: 'Sale Method Description' },
          { key: 'markStatus', label: 'Status' },
          { key: 'markStatusDesc', label: 'Status Description' },
          { key: 'catastrophicInd', label: 'Catastrophic', format: 'yesno' },
          { key: 'adminOrg', label: 'Administrative Organization' },
          { key: 'ttlMerchntblArea', label: 'Total Merchantable Area in Forest Tenure Administration (Ha)', format: 'number' },
          { key: 'cruiseBasedInd', label: 'Cruise Based', format: 'yesno' },
        ],
      },
      {
        title: 'Appraisal Information',
        fields: [
          { key: 'appraisalStatus', label: 'Status' },
          { key: 'effectiveDate', label: 'Effective', format: 'date' },
          { key: 'rateCalcMethod', label: 'Rate Calculation' },
          { key: 'appraisalMethod', label: 'Method' },
          { key: 'expiryDate', label: 'Expiry', format: 'date' },
          { key: 'adjustQuarterlyInd', label: 'Adjusted Quarterly', format: 'yesno' },
          { key: 'ttlMerchntblArea', label: 'Total Merchantable Area', format: 'number' },
          { key: 'othersTiedTo', label: 'Tied To' },
          { key: 'conifStandRateElig', label: 'Coniferous Stand Rate Eligibility' },
          { key: 'decidStandRateElig', label: 'Deciduous Stand Rate Eligibility' },
        ],
      },
      {
        title: 'Cruise Based Billing Profile',
        fields: [
          { key: 'statusMessage', label: 'Status' },
          { key: 'cbProfileM3', label: 'Profile Total m3 / Ha', format: 'number' },
          { key: 'm3PerHa', label: 'Appraised m3 / Ha', format: 'number' },
        ],
        table: {
          query: 'profiles.cruiseBased.ratios',
          params: { timberMark: 'timberMark', effectiveDate: 'effectiveDateKey' },
          columns: [
            { key: 'speciesCode', header: 'Species' },
            { key: 'gradeCode', header: 'Billing Code' },
            { key: 'volumePerHectare', header: 'm3 / Ha', format: 'number' },
            { key: 'speciesVolumePerHectare', header: 'Species m3 / Ha', format: 'number' },
          ],
        },
      },
    ],
    editLink: {
      screen: 'cruise-based-ratios',
      params: { timberMark: 'timberMark', effectiveDate: 'effectiveDateKey' },
      label: 'Create/Update',
      capability: 'PROFILE_BILLING_ADMIN',
    },
    notes:
      'Appraised timber profile and stumpage rates for this mark are shown on the Timber Mark Information query.',
  },
  {
    kind: 'search',
    id: 'cruise-based-ratios',
    legacy: 'P383',
    area: 'profiles',
    title: 'Update Cruise Based Billing Profile',
    nav: false,
    capability: 'PROFILE_BILLING_ADMIN',
    query: 'profiles.cruiseBased.ratios',
    autoSearch: true,
    criteria: [
      { name: 'timberMark', label: 'Timber Mark', upper: true, maxLength: 6, required: true },
      { name: 'effectiveDate', label: 'Appraisal Effective Date', type: 'date', required: true },
    ],
    columns: [
      { key: 'speciesCode', header: 'Species', sortable: true },
      { key: 'gradeCode', header: 'Billing Code', sortable: true },
      { key: 'volumePerHectare', header: 'm3 / Ha', format: 'number' },
      { key: 'speciesVolumePerHectare', header: 'Species m3 / Ha', format: 'number' },
    ],
    rowLink: {
      screen: 'cruise-based-ratio-update',
      params: {
        timberMark: 'timberMark',
        effectiveDate: 'effectiveDateKey',
        speciesCode: 'speciesCode',
        gradeCode: 'gradeCode',
      },
    },
    createLink: {
      screen: 'cruise-based-ratio-add',
      params: { timberMark: 'timberMark', effectiveDate: 'effectiveDate' },
      label: 'Add',
      capability: 'PROFILE_BILLING_ADMIN',
    },
    rowActions: [
      {
        id: 'delete',
        label: 'Delete',
        command: 'profiles.cruiseBased.ratio.delete',
        capability: 'PROFILE_BILLING_ADMIN',
        params: {
          timberMark: 'timberMark',
          effectiveDate: 'effectiveDateKey',
          speciesCode: 'speciesCode',
          gradeCode: 'gradeCode',
        },
        confirm: 'Delete this species / billing code row?',
        danger: true,
      },
    ],
    notes:
      'm3 / Ha must be greater than 0 and less than 1000; species and billing code must be a valid combination on the appraisal worksheet, with no duplicates. Interior appraisals must use grade 7 or 8.',
  },
  {
    kind: 'form',
    id: 'cruise-based-ratio-add',
    legacy: 'P383',
    area: 'profiles',
    title: 'Add Cruise Based Billing Profile Row',
    nav: false,
    capability: 'PROFILE_BILLING_ADMIN',
    command: 'profiles.cruiseBased.ratio.create',
    fields: [
      { name: 'timberMark', label: 'Timber Mark', upper: true, maxLength: 6, required: true },
      { name: 'effectiveDate', label: 'Appraisal Effective Date', type: 'date', required: true },
      { name: 'speciesCode', label: 'Species', upper: true, maxLength: 2, required: true },
      { name: 'gradeCode', label: 'Billing Code', upper: true, maxLength: 1, required: true },
      { name: 'volumePerHectare', label: 'm3 / Ha', type: 'number', required: true },
    ],
    then: { screen: 'cruise-based-ratios', params: { timberMark: 'timberMark', effectiveDate: 'effectiveDate' } },
  },
  {
    kind: 'form',
    id: 'cruise-based-ratio-update',
    legacy: 'P383',
    area: 'profiles',
    title: 'Update Cruise Based Billing Profile Row',
    nav: false,
    capability: 'PROFILE_BILLING_ADMIN',
    command: 'profiles.cruiseBased.ratio.update',
    loadQuery: 'profiles.cruiseBased.ratioRecord',
    keys: ['timberMark', 'effectiveDate', 'speciesCode', 'gradeCode'],
    fields: [
      { name: 'timberMark', label: 'Timber Mark', readOnlyOnEdit: true },
      { name: 'effectiveDate', label: 'Appraisal Effective Date', type: 'date', readOnlyOnEdit: true },
      { name: 'speciesCode', label: 'Species', readOnlyOnEdit: true },
      { name: 'gradeCode', label: 'Billing Code', readOnlyOnEdit: true },
      { name: 'volumePerHectare', label: 'm3 / Ha', type: 'number', required: true },
      ...AUDIT_FIELDS,
    ],
    then: { screen: 'cruise-based-ratios', params: { timberMark: 'timberMark', effectiveDate: 'effectiveDate' } },
  },
];
