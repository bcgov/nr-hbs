import type { ActionDef, FieldDef, ScreenDef } from '../types';

/**
 * Rating — legacy tab "Rating" (module plu, menu P199 "Rate Management Menu").
 * Backend: backend/.../catalog/RatingCatalog.java (ids rating.*).
 * Mapping + unported logic: docs/areas/rating-and-sampling.md.
 */

const RULE_RECORD: Record<string, string> = {
  overrideRatingRuleId: 'overrideRatingRuleId',
  ovrrdrtrlType: 'ovrrdrtrlType',
  overrideRuleCategoryCode: 'overrideRuleCategoryCode',
  timberMark: 'timberMark',
  processingEffectiveDate: 'processingEffectiveDate',
  processingExpiryDate: 'processingExpiryDate',
  scaleEffectiveDate: 'scaleEffectiveDate',
  scaleExpiryDate: 'scaleExpiryDate',
  ovrrdrtrlComment: 'ovrrdrtrlComment',
  overrideRateDate: 'overrideRateDate',
  entryUserid: 'entryUserid',
  entryTimestamp: 'entryTimestamp',
  fieldScaleDeckId: 'fieldScaleDeckId',
};

/** Header fields shared by P203 (date rule) and P204 (rate rule). */
const ruleHeaderFields = (): FieldDef[] => [
  { name: 'timberMark', label: 'Timber Mark', required: true, upper: true, maxLength: 6 },
  {
    name: 'fieldScaleDeckId',
    label: 'Field Scale Deck ID',
    upper: true,
    maxLength: 1,
    helperText: 'Leave blank for a regular rule; an upper case letter A–Z or a number 0–9 for a field scale deck rule.',
  },
  { name: 'scaleEffectiveDate', label: 'From Scale Date', type: 'date', required: true },
  { name: 'scaleExpiryDate', label: 'To Scale Date', type: 'date', required: true },
  { name: 'processingEffectiveDate', label: 'From Process Date', type: 'date', required: true },
  { name: 'processingExpiryDate', label: 'To Process Date', type: 'date', required: true },
];

const deactivateRule: ActionDef = {
  id: 'deactivate-rule',
  label: 'De-activate Rule',
  command: 'rating.overrideRule.deactivate',
  capability: 'RATE_ADMIN',
  params: RULE_RECORD,
  confirm: 'De-activate this override rule? (Only Active rules should be de-activated.)',
  danger: true,
};

const activateRule: ActionDef = {
  id: 'activate-rule',
  label: 'Activate Rule',
  command: 'rating.overrideRule.activate',
  capability: 'RATE_ADMIN',
  params: RULE_RECORD,
  confirm:
    'Activate this pending rule? Its scaling and processing intervals must not overlap another active rule for this timber mark (and field scale deck ID).',
};

const addOverrideRate: ActionDef = {
  id: 'add-override-rate',
  label: 'Save and Add More Rate Details',
  command: 'rating.overrideRate.create',
  capability: 'RATE_ADMIN',
  params: { overrideRatingRuleId: 'overrideRatingRuleId' },
  fields: [
    { name: 'scaleSpeciesCode', label: 'Species', type: 'select', codeList: 'codes.rating.species', helperText: 'Blank = All (Logs only)' },
    { name: 'scaleProductCode', label: 'Product', type: 'select', codeList: 'codes.rating.products', required: true },
    { name: 'scaleGradeCode', label: 'Grade', type: 'select', codeList: 'codes.rating.grades', helperText: 'Blank = All (Logs only)' },
    { name: 'overrideRate', label: 'Override Rate ($)', type: 'number', required: true, helperText: '> 0.00 and <= 9,999.99' },
  ],
};

const rateColumns = [
  { key: 'species', header: 'Species' },
  { key: 'product', header: 'Product' },
  { key: 'grade', header: 'Grade' },
  { key: 'overrideRate', header: 'Override Rate', format: 'money' as const },
];

const OVERRIDE_RULES_REPORT_PARAMS = {
  OVRRULES_TIMBER_MARK: 'timberMark',
  OVRRULES_FIELD_SCALE_DECKID: 'singleDeck',
  OVRRULES_RULE_STATUS: 'ruleStatus',
  OVRRULES_SCALEAFTER_DATE: 'scaledFrom',
  OVRRULES_SCALEBEFORE_DATE: 'scaledTo',
  OVRRULES_PROCESSAFTER_DATE: 'processedFrom',
  OVRRULES_PROCESSBEFORE_DATE: 'processedTo',
};

export const screens: ScreenDef[] = [
  // ── P200/P201 Search for Override Rules / List of Override Rules ──
  {
    kind: 'search',
    id: 'override-rules',
    legacy: 'P200/P201',
    area: 'rating',
    title: 'Search for Override Rules',
    navLabel: 'Override Rates',
    capability: 'RATING_MINISTRY_VIEW',
    query: 'rating.overrideRules.search',
    criteria: [
      { name: 'timberMark', label: 'Timber Mark', required: true, upper: true, maxLength: 6 },
      {
        name: 'ruleTypeFS',
        label: 'Rule Type',
        type: 'radio',
        required: true,
        defaultValue: 'regularRule',
        options: [
          { value: 'regularRule', label: 'Regular Rules' },
          { value: 'allDeckFS', label: 'Field Scale Deck Rules: Rules for All Decks' },
          { value: 'singleDeckFS', label: 'Field Scale Deck Rules: Rules for a Single Deck' },
        ],
      },
      { name: 'singleDeck', label: 'Field Deck ID', upper: true, maxLength: 1, helperText: 'Single deck only: A–Z or 0–9' },
      { name: 'scaledFrom', label: 'Scaled From', type: 'date', required: true },
      { name: 'scaledTo', label: 'Scaled To', type: 'date', required: true },
      { name: 'processedFrom', label: 'Processed From', type: 'date', required: true },
      { name: 'processedTo', label: 'Processed To', type: 'date', required: true },
      { name: 'ruleStatus', label: 'Rule Status', type: 'select', codeList: 'codes.rating.ruleStatuses' },
    ],
    columns: [
      { key: 'overrideRatingRuleId', header: 'Rule Number', sortable: true },
      { key: 'ruleType', header: 'Rule Type', sortable: true },
      { key: 'ruleStatus', header: 'Rule Status', sortable: true },
      { key: 'fieldScaleDeckId', header: 'Field Scale Deck ID' },
      { key: 'scaleEffectiveDate', header: 'From Scale Date', format: 'date', sortable: true },
      { key: 'scaleExpiryDate', header: 'To Scale Date', format: 'date' },
      { key: 'processingEffectiveDate', header: 'From Process Date', format: 'date', sortable: true },
      { key: 'processingExpiryDate', header: 'To Process Date', format: 'date' },
      { key: 'overrideRateDate', header: 'Override Rate Date', format: 'date' },
    ],
    rowLink: { screen: 'override-rule', params: { overrideRatingRuleId: 'overrideRatingRuleId' } },
    createLink: { screen: 'override-rate-rule-add', params: { timberMark: 'timberMark' }, label: 'Add New Rate Rule', capability: 'RATE_ADMIN' },
    reports: [{ reportId: 'HBS2R201', label: 'Print', params: OVERRIDE_RULES_REPORT_PARAMS }],
    notes:
      'Scale and process dates must be within the last 7 years. Date rules are added from "Add Override Date Rule". The printed report cannot select "all field scale decks" (it lists regular rules unless a single deck is entered).',
  },
  // ── P202 / P206 / P208 View Override Rate / Date Rule (and P207 for Pending rate rules) ──
  {
    kind: 'detail',
    id: 'override-rule',
    legacy: 'P202/P206/P207/P208',
    area: 'rating',
    title: 'View Override Rule',
    nav: false,
    capability: 'RATING_MINISTRY_VIEW',
    query: 'rating.overrideRules.detail',
    keys: ['overrideRatingRuleId'],
    sections: [
      {
        title: 'Override Rule',
        fields: [
          { key: 'timberMark', label: 'Timber Mark' },
          { key: 'scaleEffectiveDate', label: 'From Scale Date', format: 'date' },
          { key: 'scaleExpiryDate', label: 'To Scale Date', format: 'date' },
          { key: 'fieldScaleDeckId', label: 'Field Scale Deck ID' },
          { key: 'overrideRatingRuleId', label: 'Rule Number' },
          { key: 'ruleStatus', label: 'Rule Status' },
          { key: 'processingEffectiveDate', label: 'From Process Date', format: 'date' },
          { key: 'processingExpiryDate', label: 'To Process Date', format: 'date' },
          { key: 'ruleType', label: 'Rule Type' },
          { key: 'overrideRateDate', label: 'Override Rate Date', format: 'date' },
          { key: 'entryUserid', label: 'Created By' },
          { key: 'ovrrdrtrlComment', label: 'Reason' },
        ],
      },
      {
        title: 'Returns',
        fields: [
          { key: 'pieceReturnCount', label: 'Confirmed Returns ==> Piece Scale', format: 'number' },
          { key: 'weightReturnCount', label: 'Confirmed Returns ==> Weight Scale', format: 'number' },
          { key: 'pieceFutureReturnCount', label: 'Candidate Returns ==> Piece Scale', format: 'number' },
          { key: 'weightFutureReturnCount', label: 'Candidate Returns ==> Weight Scale', format: 'number' },
        ],
      },
      {
        title: 'Override Rate Details',
        table: {
          query: 'rating.overrideRates.list',
          params: { overrideRatingRuleId: 'overrideRatingRuleId' },
          columns: rateColumns,
          rowLink: { screen: 'override-rate', params: { overrideRateId: 'overrideRateId' } },
        },
      },
    ],
    actions: [addOverrideRate, activateRule, deactivateRule],
    notes:
      'Rate details can be added and the rule activated only while the rule is Pending (rate rules). De-activate applies to Active rules.',
  },
  // P204/P207 one override rate detail row (view → edit / delete).
  {
    kind: 'detail',
    id: 'override-rate',
    legacy: 'P204/P207',
    area: 'rating',
    title: 'Override Rate Detail',
    nav: false,
    capability: 'RATING_MINISTRY_VIEW',
    query: 'rating.overrideRates.list',
    keys: ['overrideRateId'],
    sections: [
      {
        title: 'Override Rate Detail',
        fields: [
          { key: 'overrideRatingRuleId', label: 'Rule Number' },
          { key: 'species', label: 'Species' },
          { key: 'product', label: 'Product' },
          { key: 'grade', label: 'Grade' },
          { key: 'overrideRate', label: 'Override Rate', format: 'money' },
          { key: 'entryUserid', label: 'Created By' },
        ],
      },
    ],
    editLink: { screen: 'override-rate-edit', params: { overrideRateId: 'overrideRateId' }, label: 'Update', capability: 'RATE_ADMIN' },
    actions: [
      {
        id: 'remove-override-rate',
        label: 'Delete',
        command: 'rating.overrideRate.remove',
        capability: 'RATE_ADMIN',
        params: { overrideRateId: 'overrideRateId' },
        confirm: 'Delete this override rate detail row?',
        danger: true,
        then: { screen: 'override-rule', params: { overrideRatingRuleId: 'overrideRatingRuleId' } },
      },
    ],
  },
  {
    kind: 'form',
    id: 'override-rate-edit',
    legacy: 'P204/P207',
    area: 'rating',
    title: 'Update Override Rate Detail',
    nav: false,
    capability: 'RATE_ADMIN',
    command: 'rating.overrideRate.update',
    loadQuery: 'rating.overrideRates.list',
    keys: ['overrideRateId'],
    fields: [
      { name: 'overrideRatingRuleId', label: 'Rule Number', readOnlyOnEdit: true },
      { name: 'scaleSpeciesCode', label: 'Species', type: 'select', codeList: 'codes.rating.species', helperText: 'Blank = All (Logs only)' },
      { name: 'scaleProductCode', label: 'Product', type: 'select', codeList: 'codes.rating.products', required: true },
      { name: 'scaleGradeCode', label: 'Grade', type: 'select', codeList: 'codes.rating.grades', helperText: 'Blank = All (Logs only)' },
      { name: 'overrideRate', label: 'Override Rate ($)', type: 'number', required: true },
      { name: 'entryUserid', label: 'Created By', readOnlyOnEdit: true },
      { name: 'entryTimestamp', label: 'Created On', type: 'date', readOnlyOnEdit: true },
    ],
    then: { screen: 'override-rule', params: { overrideRatingRuleId: 'overrideRatingRuleId' } },
    submitLabel: 'Save',
  },
  // ── P203 Add Override Date Rule ──
  {
    kind: 'form',
    id: 'override-date-rule-add',
    legacy: 'P203',
    area: 'rating',
    title: 'Add Override Date Rule',
    navLabel: 'Add Override Date Rule',
    capability: 'RATE_ADMIN',
    command: 'rating.overrideRule.createDateRule',
    fields: [
      ...ruleHeaderFields(),
      { name: 'overrideRateDate', label: 'Override Rate Date', type: 'date', required: true },
      { name: 'ovrrdrtrlComment', label: 'Reason', type: 'textarea', maxLength: 240, span: 4 },
    ],
    then: { screen: 'override-rule', params: { overrideRatingRuleId: 'overrideRatingRuleId' } },
    submitLabel: 'Activate Rule',
    notes:
      'The rule is saved as Active. Scale dates must be within the last 7 years; the From Process Date must be today or later; intervals must not overlap another active rule for the timber mark (checked by the legacy screen — not yet enforced here).',
  },
  // ── P204 Add Override Rate Rule (header; rate rows are added on the rule screen) ──
  {
    kind: 'form',
    id: 'override-rate-rule-add',
    legacy: 'P204',
    area: 'rating',
    title: 'Add Override Rate Rule',
    nav: false,
    capability: 'RATE_ADMIN',
    command: 'rating.overrideRule.createRateRule',
    fields: [...ruleHeaderFields(), { name: 'ovrrdrtrlComment', label: 'Reason', type: 'textarea', maxLength: 240, span: 4 }],
    then: { screen: 'override-rule', params: { overrideRatingRuleId: 'overrideRatingRuleId' } },
    submitLabel: 'Save and Add More Rate Details',
    notes: 'The rule is saved as Pending. Add at least one override rate detail, then Activate Rule.',
  },

  // ── P210/P211 District Default Rates ──
  {
    kind: 'search',
    id: 'district-default-rates',
    legacy: 'P210/P211',
    area: 'rating',
    title: 'Search for District Default Rates',
    navLabel: 'District Default Rates',
    capability: 'RATING_MINISTRY_VIEW',
    query: 'rating.districtRates.search',
    criteria: [
      { name: 'orgUnitNo', label: 'District', type: 'select', codeList: 'codes.rating.districts' },
      { name: 'appraisalMethod', label: 'Appraisal Method', type: 'select', codeList: 'codes.rating.appraisalMethods' },
      { name: 'effectiveFrom', label: 'Effective Date From', type: 'date', required: true },
      { name: 'effectiveTo', label: 'Effective Date To', type: 'date', required: true },
    ],
    columns: [
      { key: 'district', header: 'District', sortable: true },
      { key: 'appraisalMethod', header: 'Method' },
      { key: 'species', header: 'Species', sortable: true },
      { key: 'product', header: 'Product', sortable: true },
      { key: 'grade', header: 'Grade', sortable: true },
      { key: 'rate', header: 'Rate', format: 'money', sortable: true },
      { key: 'effectiveDate', header: 'Effective Date', format: 'date', sortable: true },
      { key: 'expiryDate', header: 'Expiry Date', format: 'date', sortable: true },
    ],
    rowLink: { screen: 'district-default-rate-edit', params: { distrtId: 'distrtId' } },
    createLink: {
      screen: 'district-default-rate-add',
      params: { orgUnitNo: 'orgUnitNo', appraisalMthdCode: 'appraisalMethod' },
      label: 'Add New Rate',
      capability: 'RATE_ADMIN',
    },
    reports: [
      {
        reportId: 'HBS3R211',
        label: 'Print',
        params: {
          PDDR_DISTRICT: 'orgUnitNo',
          PDDR_METHOD: 'appraisalMethod',
          PDDR_START_DATE: 'effectiveFrom',
          PDDR_END_DATE: 'effectiveTo',
          PDDR_ORDER_BY: '=',
        },
      },
    ],
  },
  // ── P212 Add Default Rate ──
  {
    kind: 'form',
    id: 'district-default-rate-add',
    legacy: 'P212',
    area: 'rating',
    title: 'Add New Default Rate',
    nav: false,
    capability: 'RATE_ADMIN',
    command: 'rating.districtRate.create',
    fields: [
      { name: 'orgUnitNo', label: 'District', type: 'select', codeList: 'codes.rating.districts', required: true },
      { name: 'appraisalMthdCode', label: 'Appraisal Method', type: 'select', codeList: 'codes.rating.appraisalMethods', required: true },
      { name: 'effectiveDate', label: 'Effective Date', type: 'date', required: true },
      { name: 'expiryDate', label: 'Expiry Date', type: 'date', required: true },
      { name: 'scaleSpeciesCode', label: 'Species', type: 'select', codeList: 'codes.rating.species', helperText: 'Blank = All' },
      { name: 'scaleProductCode', label: 'Product', type: 'select', codeList: 'codes.rating.products', required: true },
      { name: 'scaleGradeCode', label: 'Grade', type: 'select', codeList: 'codes.rating.grades', required: true },
      { name: 'rate', label: 'Rate ($)', type: 'number', required: true, helperText: '> 0.00 and <= 9,999.99' },
    ],
    then: { screen: 'district-default-rates', params: { orgUnitNo: 'orgUnitNo', appraisalMethod: 'appraisalMthdCode', effectiveFrom: 'effectiveDate', effectiveTo: 'expiryDate' } },
    submitLabel: 'Add Default Rate',
    notes:
      'Dates cannot predate today by more than 7 years and must not overlap an existing rate for the same district / method / species-product-grade (legacy checks — not yet enforced here).',
  },
  // ── P213 Update Default Rate ──
  {
    kind: 'form',
    id: 'district-default-rate-edit',
    legacy: 'P213',
    area: 'rating',
    title: 'Update Default Rate',
    nav: false,
    capability: 'RATING_MINISTRY_VIEW',
    command: 'rating.districtRate.update',
    loadQuery: 'rating.districtRates.detail',
    keys: ['distrtId'],
    fields: [
      { name: 'orgUnitNo', label: 'District', type: 'select', codeList: 'codes.rating.districts', readOnlyOnEdit: true },
      { name: 'appraisalMthdCode', label: 'Method', type: 'select', codeList: 'codes.rating.appraisalMethods', readOnlyOnEdit: true },
      { name: 'scaleSpeciesCode', label: 'Species', type: 'select', codeList: 'codes.rating.species', readOnlyOnEdit: true },
      { name: 'scaleProductCode', label: 'Product', type: 'select', codeList: 'codes.rating.products', readOnlyOnEdit: true },
      { name: 'scaleGradeCode', label: 'Grade', type: 'select', codeList: 'codes.rating.grades', readOnlyOnEdit: true },
      { name: 'rate', label: 'Rate ($)', type: 'number', required: true },
      { name: 'effectiveDate', label: 'Effective Date', type: 'date', required: true },
      { name: 'expiryDate', label: 'Expiry Date', type: 'date', required: true },
      { name: 'activeInd', label: 'Active', type: 'yesno', readOnlyOnEdit: true },
      { name: 'entryUserid', label: 'Created By', readOnlyOnEdit: true },
      { name: 'entryTimestamp', label: 'Created On', type: 'date', readOnlyOnEdit: true },
    ],
    submitLabel: 'Save',
    notes: 'Saving requires the Rate Administrator role. Dates on file older than 7 years cannot be changed.',
  },

  // ── P220/P221 Waste & Residue Rates ──
  {
    kind: 'search',
    id: 'waste-residue-rates',
    legacy: 'P220/P221',
    area: 'rating',
    title: 'Search for Waste & Residue Rates',
    navLabel: 'Waste & Residue Rates',
    capability: 'RATING_VIEW',
    query: 'rating.wasteRates.search',
    criteria: [
      { name: 'timberMark', label: 'Timber Mark', required: true, upper: true, maxLength: 6 },
      { name: 'cutBlock', label: 'Cut Block', upper: true, maxLength: 10, helperText: 'Optional; ALL for every cut block' },
      { name: 'scaleDateFrom', label: 'Scale Date From', type: 'date', required: true },
      { name: 'scaleDateTo', label: 'Scale Date To', type: 'date', required: true },
    ],
    columns: [
      { key: 'cutBlockId', header: 'Cut Block', sortable: true },
      { key: 'effectiveDate', header: 'Scale Date', format: 'date', sortable: true },
      { key: 'species', header: 'Species' },
      { key: 'grade', header: 'Grade' },
      { key: 'activeInd', header: 'Active?', format: 'yesno' },
      { key: 'rate', header: 'Rate', format: 'money', sortable: true },
      { key: 'rateSource', header: 'Rate Source' },
    ],
    rowLink: { screen: 'waste-residue-rate', params: { wstrtId: 'wstrtId' } },
    createLink: { screen: 'waste-residue-rate-add', params: { timberMark: 'timberMark' }, label: 'Add Rate', capability: 'WASTE_RATE_ADMIN' },
    reports: [
      {
        reportId: 'HBS3R221',
        label: 'Print',
        params: {
          PWRR_TIMBERMARK: 'timberMark',
          PWRR_CUTBLOCK: 'cutBlock',
          PWRR_EFFECTIVEONAFTER: 'scaleDateFrom',
          PWRR_EFFECTIVEONBEFORE: 'scaleDateTo',
        },
      },
    ],
  },
  // P223 view (Delete = soft de-activate).
  {
    kind: 'detail',
    id: 'waste-residue-rate',
    legacy: 'P223',
    area: 'rating',
    title: 'Waste & Residue Rate',
    nav: false,
    capability: 'RATING_VIEW',
    query: 'rating.wasteRates.detail',
    keys: ['wstrtId'],
    sections: [
      {
        title: 'Waste & Residue Rate',
        fields: [
          { key: 'timberMark', label: 'Timber Mark' },
          { key: 'cutBlockId', label: 'Cut Block' },
          { key: 'effectiveDate', label: 'Scale Date', format: 'date' },
          { key: 'species', label: 'Species' },
          { key: 'grade', label: 'Grade' },
          { key: 'rate', label: 'Rate', format: 'money' },
          { key: 'rateSource', label: 'Rate Source' },
          { key: 'activeInd', label: 'Active?', format: 'yesno' },
          { key: 'entryUserid', label: 'Created By' },
        ],
      },
    ],
    actions: [
      {
        id: 'delete-waste-rate',
        label: 'Delete',
        command: 'rating.wasteRate.deactivate',
        capability: 'WASTE_RATE_ADMIN',
        params: {
          wstrtId: 'wstrtId',
          timberMark: 'timberMark',
          cutBlockId: 'cutBlockId',
          scaleSpeciesCode: 'scaleSpeciesCode',
          scaleGradeCode: 'scaleGradeCode',
          avoidableInd: 'avoidableInd',
          rate: 'rate',
          effectiveDate: 'effectiveDate',
          expiryDate: 'expiryDate',
          entryUserid: 'entryUserid',
          entryTimestamp: 'entryTimestamp',
          wasteRateSourceCode: 'wasteRateSourceCode',
        },
        confirm: 'Delete (de-activate) this waste & residue rate? Inactive rates cannot be updated.',
        danger: true,
      },
    ],
    notes: 'Updating a waste & residue rate (P223 Save) is not yet available in this application; add a new rate and delete the old one.',
  },
  // ── P222 Add A Waste & Residue Rate ──
  {
    kind: 'form',
    id: 'waste-residue-rate-add',
    legacy: 'P222',
    area: 'rating',
    title: 'Add A Waste & Residue Rate',
    nav: false,
    capability: 'WASTE_RATE_ADMIN',
    command: 'rating.wasteRate.create',
    fields: [
      { name: 'timberMark', label: 'Timber Mark', required: true, upper: true, maxLength: 6 },
      { name: 'cutBlockId', label: 'Cut Block', required: true, upper: true, maxLength: 10 },
      { name: 'effectiveDate', label: 'Scale Date', type: 'date', required: true },
      { name: 'scaleSpeciesCode', label: 'Species', type: 'select', codeList: 'codes.rating.species', required: true },
      { name: 'scaleGradeCode', label: 'Grade', type: 'select', codeList: 'codes.rating.grades', required: true },
      { name: 'rate', label: 'Rate ($)', type: 'number', required: true, helperText: '> 0.00 and <= 999.99; see Compute Waste & Residue Average Rate' },
      { name: 'wasteRateSourceCode', label: 'Rate Source', type: 'select', codeList: 'codes.rating.wasteRateSources', required: true },
    ],
    then: { screen: 'waste-residue-rates', params: { timberMark: 'timberMark', cutBlock: 'cutBlockId', scaleDateFrom: 'effectiveDate', scaleDateTo: 'effectiveDate' } },
    submitLabel: 'Activate',
    notes:
      'The cut block must belong to the timber mark, the date cannot predate today by more than 7 years and duplicates are rejected by the legacy screen (not yet enforced here).',
  },

  // ── P230/P231 Stumpage Rate search is not carried over yet (HBS_RATES OUT-parameter procs). ──

  // ── P240/P241 Compute Waste & Residue Average Rate ──
  {
    kind: 'search',
    id: 'waste-residue-average-rate',
    legacy: 'P240/P241',
    area: 'rating',
    title: 'Compute Waste & Residue Average Rate',
    navLabel: 'Compute Waste & Residue Average Rate',
    capability: 'RATING_VIEW',
    query: 'rating.wasteAverageRate',
    criteria: [
      { name: 'timberMark', label: 'Timber Mark', required: true, upper: true, maxLength: 6 },
      { name: 'periodStart', label: 'Period Start Date', type: 'date', required: true },
      { name: 'periodEnd', label: 'Period End Date', type: 'date', required: true },
      { name: 'species', label: 'Species', type: 'select', codeList: 'codes.rating.coniferousSpecies', helperText: 'Blank = All Coniferous' },
      {
        name: 'grade',
        label: 'Grade',
        type: 'select',
        required: true,
        defaultValue: 'ALL',
        options: [
          { value: 'ALL', label: 'All Eligible' },
          { value: '3', label: '3' },
          { value: '6', label: '6' },
          { value: 'B', label: 'B' },
          { value: 'C', label: 'C' },
          { value: 'D', label: 'D' },
          { value: 'E', label: 'E' },
          { value: 'F', label: 'F' },
          { value: 'G', label: 'G' },
          { value: 'H', label: 'H' },
          { value: 'I', label: 'I' },
          { value: 'J', label: 'J' },
          { value: 'K', label: 'K' },
          { value: 'L', label: 'L' },
          { value: 'M', label: 'M' },
        ],
      },
    ],
    columns: [
      { key: 'averageRate', header: 'Average Rate ($/m3)', format: 'money' },
      { key: 'volumeBilled', header: 'Volume Billed', format: 'volume' },
      { key: 'harvestRecords', header: 'Harvest Records', format: 'number' },
    ],
    notes: 'Product: Logs. Volume-weighted average of billed royalty, reserve stumpage, bonus, development and silviculture levies; capped at $999.99.',
  },
];
