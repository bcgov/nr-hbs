import type { ActionDef, ScreenDef } from '../types';

/**
 * Administration area — the part of legacy User Services (P009–P019) that
 * FAM does not replace: HBS data domains (districts / scale sites / client
 * locations a user's roles apply to). Enrolment, requests, approvals and the
 * public "Register" flow are handled by FAM and are not rebuilt.
 * Backend: backend/.../catalog/AdminCatalog.java (ids `admin.*`), commands
 * fenced by api/admin/UserDataDomainGuard. See docs/areas/billing-and-admin.md.
 */

const userParam = { userId: 'userId' };

const removeAction = (id: string, command: string, params: Record<string, string>): ActionDef => ({
  id,
  label: 'Remove',
  command,
  capability: 'USER_ADMIN',
  danger: true,
  confirm: 'Remove this data domain from the user? The change takes effect immediately.',
  params,
});

export const screens: ScreenDef[] = [
  {
    kind: 'search',
    id: 'user-data-domains',
    legacy: 'P015/P011',
    area: 'admin',
    title: 'User Data Domains',
    description:
      'Search for HBS users and maintain their Associated Districts, Associated Sites and Associated Locations.',
    capability: 'USER_ADMIN',
    query: 'admin.users.search',
    criteria: [
      { name: 'userId', label: 'User Id', upper: true, maxLength: 30, placeholder: 'IDIR\\USER or BCEID\\USER' },
      { name: 'lastName', label: 'Last Name', maxLength: 40 },
      { name: 'firstName', label: 'First Name', maxLength: 40 },
      { name: 'email', label: 'Email', maxLength: 128 },
      {
        name: 'userType',
        label: 'User Type',
        type: 'select',
        options: [
          { value: 'MOF', label: 'Ministry Users' },
          { value: 'CLI', label: 'Industry Users' },
          { value: 'SPC', label: 'Other Industry Users' },
        ],
      },
      {
        name: 'status',
        label: 'Status',
        type: 'select',
        options: [
          { value: 'ACT', label: 'Active' },
          { value: 'INA', label: 'Inactive' },
        ],
      },
      { name: 'clientNumber', label: 'Client No', type: 'client' },
      { name: 'clientLocnCode', label: 'Loc', maxLength: 2 },
      { name: 'orgUnit', label: 'Org Unit', codeList: 'codes.orgUnits' },
    ],
    columns: [
      { key: 'userId', header: 'User Id', sortable: true },
      { key: 'userName', header: 'User Name', sortable: true },
      { key: 'status', header: 'Status', format: 'status', sortable: true },
      { key: 'userType', header: 'User Type', sortable: true },
      { key: 'organization', header: 'Organization' },
      { key: 'subOrganization', header: 'Sub Organization' },
    ],
    rowLink: { screen: 'user-data-domain', params: userParam },
    notes:
      'User accounts and roles are managed in FAM. Client and Other Industry administrators see only users of their own client.',
  },
  {
    kind: 'detail',
    id: 'user-data-domain',
    legacy: 'P017/P018/P019',
    area: 'admin',
    nav: false,
    title: 'Harvest Billing System User Details',
    capability: 'USER_ADMIN',
    query: 'admin.users.detail',
    keys: ['userId'],
    sections: [
      {
        title: 'User',
        fields: [
          { key: 'userId', label: 'User Id' },
          { key: 'userName', label: 'User Name' },
          { key: 'userType', label: 'User Type' },
          { key: 'status', label: 'Status', format: 'status' },
          { key: 'organization', label: 'Organization' },
          { key: 'subOrganization', label: 'Sub Organization' },
          { key: 'email', label: 'Email Address' },
          { key: 'phone', label: 'Phone' },
        ],
      },
      {
        title: 'Associated Districts',
        table: {
          query: 'admin.userDistricts.list',
          params: userParam,
          columns: [
            { key: 'orgUnitName', header: 'District Name' },
            { key: 'orgUnitCode', header: 'Org. Unit' },
          ],
          rowLink: { screen: 'user-district-domains', params: userParam, label: 'Manage' },
        },
      },
      {
        title: 'Associated Sites',
        table: {
          query: 'admin.userSites.list',
          params: userParam,
          columns: [
            { key: 'orgUnitName', header: 'District Name' },
            { key: 'siteName', header: 'Scale Site Name' },
            { key: 'scaleSiteIdNmbr', header: 'Scale Site' },
            { key: 'siteOwner', header: 'Site Owner' },
          ],
          rowLink: { screen: 'user-site-domains', params: userParam, label: 'Manage' },
        },
      },
      {
        title: 'Associated Locations',
        table: {
          query: 'admin.userLocations.list',
          params: userParam,
          columns: [
            { key: 'clientLocnName', header: 'Location Name' },
            { key: 'clientNumber', header: 'Client' },
            { key: 'clientLocnCode', header: 'Code' },
          ],
          rowLink: { screen: 'user-location-domains', params: userParam, label: 'Manage' },
        },
      },
    ],
    actions: [
      {
        id: 'add-district',
        label: 'Add Associated District',
        command: 'admin.userDataDomains.addDistrict',
        capability: 'USER_ADMIN',
        params: userParam,
        fields: [{ name: 'orgUnitNo', label: 'District', codeList: 'codes.orgUnits', required: true }],
      },
      {
        id: 'add-site',
        label: 'Add Associated Site',
        command: 'admin.userDataDomains.addSite',
        capability: 'USER_ADMIN',
        params: userParam,
        fields: [{ name: 'scaleSiteIdNmbr', label: 'Scale Site', codeList: 'codes.admin.scaleSites', required: true }],
      },
      {
        id: 'add-location',
        label: 'Add Associated Location',
        command: 'admin.userDataDomains.addLocation',
        capability: 'USER_ADMIN',
        params: userParam,
        fields: [
          { name: 'clientNumber', label: 'Client No', type: 'client', helperText: 'Ignored for industry administrators (your own client is used).' },
          { name: 'clientLocnCode', label: 'Location Code', maxLength: 2, required: true },
        ],
      },
    ],
    notes:
      'Districts apply to HBS Scaling / Billing Administrators, Invoice Correction Approvers and Sample Plan Administrators; sites to Industry Site Administrators; locations to Industry Document Receivers. Select a row to remove domains.',
  },
  {
    kind: 'search',
    id: 'user-district-domains',
    legacy: 'P019',
    area: 'admin',
    nav: false,
    title: 'Update Associated Districts',
    capability: 'USER_ADMIN',
    query: 'admin.userDistricts.list',
    criteria: [{ name: 'userId', label: 'User Id', upper: true, required: true }],
    columns: [
      { key: 'orgUnitName', header: 'District Name', sortable: true },
      { key: 'orgUnitCode', header: 'Org. Unit' },
    ],
    rowActions: [
      removeAction('remove-district', 'admin.userDataDomains.removeDistrict', { userId: 'userId', orgUnitNo: 'orgUnitNo' }),
    ],
  },
  {
    kind: 'search',
    id: 'user-site-domains',
    legacy: 'P017/P018',
    area: 'admin',
    nav: false,
    title: 'Update Associated Sites',
    capability: 'USER_ADMIN',
    query: 'admin.userSites.list',
    criteria: [{ name: 'userId', label: 'User Id', upper: true, required: true }],
    columns: [
      { key: 'orgUnitName', header: 'District Name' },
      { key: 'siteName', header: 'Scale Site Name', sortable: true },
      { key: 'scaleSiteIdNmbr', header: 'Scale Site' },
      { key: 'siteOwner', header: 'Site Owner' },
    ],
    rowActions: [
      removeAction('remove-site', 'admin.userDataDomains.removeSite', {
        userId: 'userId',
        scaleSiteIdNmbr: 'scaleSiteIdNmbr',
      }),
    ],
  },
  {
    kind: 'search',
    id: 'user-location-domains',
    legacy: 'P017 (Associated Locations)',
    area: 'admin',
    nav: false,
    title: 'Update Associated Client Locations',
    capability: 'USER_ADMIN',
    query: 'admin.userLocations.list',
    criteria: [{ name: 'userId', label: 'User Id', upper: true, required: true }],
    columns: [
      { key: 'clientLocnName', header: 'Location Name' },
      { key: 'clientNumber', header: 'Client' },
      { key: 'clientLocnCode', header: 'Code', sortable: true },
    ],
    rowActions: [
      removeAction('remove-location', 'admin.userDataDomains.removeLocation', {
        userId: 'userId',
        clientNumber: 'clientNumber',
        clientLocnCode: 'clientLocnCode',
      }),
    ],
  },
];
