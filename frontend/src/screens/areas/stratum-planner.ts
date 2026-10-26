import type { ActionDef, ColumnDef, FieldDef, ScreenDef } from '../types';
import { SAMPLING_YEAR_OPTIONS } from '@/screens/years';

/**
 * Stratum Planner — legacy tab "Stratum Planner" (module smp, menu P850
 * "Stratum Advisor Menu").
 * Backend: reads in backend/.../catalog/SamplingCatalog.java (ids sampling.*);
 * the plan workflow (add / Save & Calculate / propose / approve / reject /
 * delete / copy / create from actuals, stratum plans, default ratios) in
 * backend/.../sampling/SamplingCommands.java (ids sampling.plan.*,
 * sampling.stratumPlan.*, sampling.stratumComposition.*).
 * Mapping: docs/areas/rating-and-sampling.md.
 */

// ── Shared field sets (legacy labels from P891 / P896 / P897 / P886 / P885) ──

/** P891 / P896 / P897 plan header: population, year, plan, owner, district, period. */
const planHeaderFields = (): FieldDef[] => [
  { name: 'population', label: 'Population', required: true, maxLength: 4, helperText: '4 digits' },
  { name: 'samplingYear', label: 'Year', type: 'select', options: SAMPLING_YEAR_OPTIONS, required: true },
  { name: 'planAbbreviation', label: 'Plan Abbreviation', required: true, upper: true, maxLength: 2 },
  { name: 'planName', label: 'Plan Name', required: true, maxLength: 40, span: 2 },
  { name: 'clientNumber', label: 'Client Number', type: 'client', required: true, maxLength: 8 },
  { name: 'clientLocnCode', label: 'Location', required: true, upper: true, maxLength: 2 },
  { name: 'orgUnitNo', label: 'District', type: 'select', codeList: 'codes.rating.districts', required: true },
  { name: 'effectiveDate', label: 'Effective Date', type: 'date', required: true, helperText: 'First day of a month' },
  { name: 'expiryDate', label: 'Expiry Date', type: 'date', required: true, helperText: 'Last day of a month' },
];

/** P892 "Confirm Add Population": the population / year is created when it does not exist yet. */
const confirmPopulationField: FieldDef = {
  name: 'confirmNewPopulation',
  label: 'Create the population if it does not exist',
  type: 'checkbox',
  helperText: 'Legacy P892: confirm creating a new population for this population number and year.',
  span: 4,
};

/** P896 / P897 options applied to the copied strata. */
const copyOptionFields: FieldDef[] = [
  { name: 'usePlanEffectiveDate', label: 'Use the plan Effective Date for all strata', type: 'checkbox', span: 2 },
  { name: 'usePlanExpiryDate', label: 'Use the plan Expiry Date for all strata', type: 'checkbox', span: 2 },
  { name: 'usePlanClient', label: 'Use the plan Client Number / Location for all strata', type: 'checkbox', span: 2 },
  { name: 'deleteUnusedStrata', label: 'Delete unused strata', type: 'checkbox', span: 2 },
];

/** P886 Add / Edit Stratum Plan. */
const stratumFields = (): FieldDef[] => [
  { name: 'stratumNumber', label: 'Stratum Number', required: true, maxLength: 2 },
  { name: 'stratumName', label: 'Stratum Name', required: true, maxLength: 20 },
  { name: 'clientNumber', label: 'Stratum Owner Client', type: 'client', required: true, maxLength: 8 },
  { name: 'clientLocnCode', label: 'Location', required: true, upper: true, maxLength: 2 },
  { name: 'effectiveDate', label: 'Stratum Effective Date', type: 'date', required: true, helperText: 'First day of a month' },
  { name: 'expiryDate', label: 'Stratum Expiry Date', type: 'date', required: true, helperText: 'Last day of a month' },
  { name: 'scaleProductCode', label: 'Product Schedule', type: 'select', codeList: 'codes.sampling.productSchedules', required: true },
  {
    name: 'gradeScheduleCode',
    label: 'Grade Schedule',
    type: 'select',
    options: [
      { value: 'C', label: 'Coast' },
      { value: 'I', label: 'Interior' },
    ],
    helperText: 'Blank = the district region default (coast regions: Coast)',
  },
  { name: 'stratumDesc', label: 'Stratum Description', type: 'textarea', maxLength: 240, span: 4 },
  { name: 'timberSourceDesc', label: 'Timber Source Description', type: 'textarea', maxLength: 240, span: 4 },
  { name: 'speciesGradeProfileDesc', label: 'Species / Grade Profile Description', type: 'textarea', maxLength: 240, span: 4 },
];

/** P885 one default-ratio row. */
const compositionFields = (): FieldDef[] => [
  { name: 'scaleSpeciesCode', label: 'Species', type: 'select', codeList: 'codes.rating.species', required: true },
  { name: 'scaleGradeCode', label: 'Grade', type: 'select', codeList: 'codes.rating.grades', helperText: 'Blank = no grade' },
  { name: 'defaultRatio', label: 'Ratio (m3/tonne)', type: 'number', required: true, helperText: '> 0 and < 10, up to 5 decimals; the stratum total must stay < 10' },
];

const PLAN_LOCK = { planId: 'planId', updateTimestamp: 'updateTimestamp' };

/** P883 functions (SamplePlanFunctionsTag): the backend re-checks status and district / owner. */
const planActions: ActionDef[] = [
  {
    id: 'propose-plan',
    label: 'Propose',
    command: 'sampling.plan.propose',
    showWhen: { field: 'samplePlanStatusCode', values: ['PRO'] },
    capability: 'SAMPLING_ADMIN',
    params: PLAN_LOCK,
    confirm: 'Confirm Sampling Plan: propose this In Progress plan for approval?',
  },
  {
    id: 'approve-plan',
    label: 'Approve',
    command: 'sampling.plan.approve',
    showWhen: { field: 'samplePlanStatusCode', values: ['AWP'] },
    capability: 'SAMPLING_ADMIN',
    params: PLAN_LOCK,
    confirm:
      'Confirm Sampling Plan: approve this plan? It becomes the Active plan of the population and year; the current Active plan is superseded and its strata no longer in this plan are removed.',
  },
  {
    id: 'reject-plan',
    label: 'Reject',
    command: 'sampling.plan.reject',
    showWhen: { field: 'samplePlanStatusCode', values: ['AWP'] },
    capability: 'SAMPLING_ADMIN',
    params: PLAN_LOCK,
    confirm: 'Confirm Sampling Plan: reject this plan?',
    danger: true,
  },
  {
    id: 'delete-plan',
    label: 'Delete',
    command: 'sampling.plan.delete',
    showWhen: { field: 'samplePlanStatusCode', values: ['PRO'] },
    capability: 'SAMPLING_ADMIN',
    params: PLAN_LOCK,
    confirm: 'Confirm Sampling Plan: delete this In Progress plan?',
    danger: true,
  },
  {
    id: 'add-stratum',
    label: 'Add Stratum',
    command: 'sampling.stratumPlan.create',
    showWhen: { field: 'samplePlanStatusCode', values: ['PRO'] },
    capability: 'SAMPLING_ADMIN',
    params: { planId: 'planId', planUpdateTimestamp: 'updateTimestamp' },
    fields: stratumFields(),
    then: { screen: 'stratum-plan', params: { stratumPlanId: 'stratumPlanId' } },
  },
  {
    id: 'copy-plan',
    label: 'Copy',
    command: 'sampling.plan.copy',
    capability: 'SAMPLING_ADMIN',
    params: { sourcePlanId: 'planId' },
    fields: [...planHeaderFields(), ...copyOptionFields, confirmPopulationField],
    then: { screen: 'sampling-plan', params: { planId: 'planId' } },
  },
  {
    id: 'create-from-actuals',
    label: 'Create New Plan From Actuals',
    command: 'sampling.plan.createFromActuals',
    showWhen: { field: 'samplePlanStatusCode', values: ['ACT'] },
    capability: 'SAMPLING_ADMIN',
    params: { sourcePlanId: 'planId' },
    fields: [...planHeaderFields(), ...copyOptionFields, confirmPopulationField],
    then: { screen: 'sampling-plan', params: { planId: 'planId' } },
  },
];

const statColumns: ColumnDef[] = [
  { key: 'scaleSpeciesCode', header: 'Species' },
  { key: 'scaleProductCode', header: 'Product' },
  { key: 'scaleGradeCode', header: 'Grade' },
  { key: 'sampleLoads', header: 'Sample Loads', format: 'number' },
  { key: 'sampleVolume', header: 'Sample Volume', format: 'volume' },
  { key: 'volumeFraction', header: 'Fraction (%)', format: 'number' },
  { key: 'ratio', header: 'Ratio', format: 'number' },
  { key: 'billedVolume', header: 'Billed Volume', format: 'volume' },
  { key: 'precision', header: 'Precision', format: 'number' },
];

const statTypeField = {
  name: 'type',
  label: 'List',
  type: 'radio' as const,
  required: true,
  defaultValue: 'SPC',
  options: [
    { value: 'SPC', label: 'List of Species' },
    { value: 'GRD', label: 'List of Grades' },
    { value: 'SGR', label: 'List of Segregations' },
  ],
};

/** P866–P868 search screen, reached from P865 with the stratum keys as URL criteria. */
const STRATUM_STATS_PATH = '/stratum-planner/sampling-stratum-stats';
const STRATUM_KEYS = { population: 'populationNumber', samplingYear: 'samplingYear', stratumNumber: 'stratumNumber' };
const STRATUM_RECORD_PARAMS = STRATUM_KEYS;

/** P870 category rows (categorizationType.P/T/S/N). */
const CATEGORY_COLUMNS: ColumnDef[] = [
  { key: 'numberLoads', header: 'Loads', format: 'number' },
  { key: 'category', header: 'Category' },
  { key: 'predominant', header: 'Predom' },
  { key: 'predominantFraction', header: 'Fraction', format: 'number' },
  { key: 'firstMajor', header: '1st Major' },
  { key: 'firstMajorFraction', header: 'Fraction', format: 'number' },
  { key: 'secondMajor', header: '2nd Major' },
  { key: 'secondMajorFraction', header: 'Fraction', format: 'number' },
];

/** P871–P873 "Sample Data" columns. */
const SAMPLE_DATA_COLUMNS: ColumnDef[] = [
  { key: 'sampleVolume', header: 'Volume', format: 'volume' },
  { key: 'volumeFraction', header: 'Fraction (%)', format: 'number' },
  { key: 'ratio', header: 'Ratio', format: 'number' },
];

export const screens: ScreenDef[] = [
  // ── P854/P880 Search For Sampling Plans / List of Sampling Plans ──
  // Home deep links: ?planStatus=ACT|AWP|PRO|REJ&currentYear=Y&districtScope=ASSOCIATED
  {
    kind: 'search',
    id: 'sampling-plans',
    legacy: 'P854/P880',
    area: 'stratum-planner',
    title: 'Search For Sampling Plans',
    navLabel: 'Sampling Plans',
    capability: 'SAMPLING_VIEW',
    query: 'sampling.plans.search',
    criteria: [
      { name: 'population', label: 'Population', maxLength: 4 },
      { name: 'samplingYear', type: 'select', options: SAMPLING_YEAR_OPTIONS, label: 'Sampling Year' },
      { name: 'effectiveOnOrBefore', label: 'Effective On or Before', type: 'date' },
      { name: 'expiringOnOrAfter', label: 'Expiring On or After', type: 'date' },
      { name: 'planStatus', label: 'Plan Status', type: 'select', codeList: 'codes.sampling.planStatuses' },
      {
        name: 'currentYear',
        label: 'In effect today',
        type: 'checkbox',
        helperText: 'Plans whose effective period includes today',
      },
      {
        name: 'districtScope',
        label: 'Population Location',
        type: 'radio',
        defaultValue: 'ALL',
        options: [
          { value: 'ALL', label: 'In All Regions/Districts' },
          { value: 'DISTRICT', label: 'In Region/District' },
          { value: 'ASSOCIATED', label: 'In Associated Districts' },
        ],
        span: 2,
      },
      { name: 'orgUnitNo', label: 'Region/District', type: 'select', codeList: 'codes.orgUnits' },
      { name: 'clientNumber', label: 'Population Owner Client', type: 'client', maxLength: 8 },
      { name: 'clientLocnCode', label: 'Location', maxLength: 2 },
    ],
    requireOneOf: [
      'population',
      'samplingYear',
      'effectiveOnOrBefore',
      'expiringOnOrAfter',
      'planStatus',
      'orgUnitNo',
      'clientNumber',
    ],
    columns: [
      { key: 'populationNumber', header: 'Population', sortable: true },
      { key: 'samplingYear', header: 'Year', sortable: true },
      { key: 'district', header: 'District', sortable: true },
      { key: 'effectiveDate', header: 'Effective', format: 'date', sortable: true },
      { key: 'expiryDate', header: 'Expiry', format: 'date', sortable: true },
      { key: 'clientNumber', header: 'Population Owner' },
      { key: 'clientLocnCode', header: 'Location' },
      { key: 'planAbbreviation', header: 'Plan' },
      { key: 'planName', header: 'Plan Name' },
      { key: 'planStatus', header: 'Plan Status', sortable: true },
      { key: 'ratioType', header: 'Ratio Type' },
    ],
    rowLink: { screen: 'sampling-plan', params: { planId: 'planId' } },
    notes:
      'Industry users see Active plans plus their own plans in any status. "In Associated Districts" is not yet applied as a filter (all districts are listed); choose a Region/District to narrow the list.',
  },
  // ── P883 View Sampling Plan ──
  {
    kind: 'detail',
    id: 'sampling-plan',
    legacy: 'P883',
    area: 'stratum-planner',
    title: 'View Sampling Plan',
    nav: false,
    capability: 'SAMPLING_VIEW',
    query: 'sampling.plans.detail',
    keys: ['planId'],
    sections: [
      {
        title: 'Sampling Plan',
        fields: [
          { key: 'populationNumber', label: 'Population' },
          { key: 'samplingYear', label: 'Year' },
          { key: 'district', label: 'District' },
          { key: 'effectiveDate', label: 'Effective', format: 'date' },
          { key: 'expiryDate', label: 'Expiry', format: 'date' },
          { key: 'clientNumber', label: 'Population Owner' },
          { key: 'clientLocnCode', label: 'Location' },
          { key: 'planAbbreviation', label: 'Plan' },
          { key: 'planName', label: 'Plan Name' },
          { key: 'planStatus', label: 'Status' },
          { key: 'ratioType', label: 'Ratio Type' },
          { key: 'precisionLimit', label: 'Precision Limit' },
          { key: 'sampleFloor', label: 'Sample Floor' },
          { key: 'estimateMethod', label: 'Estimate Method' },
          { key: 'constraintMethod', label: 'Constraint Method' },
          { key: 'optimumPrecisionCalc', label: 'Optimum Precision', format: 'number' },
          { key: 'targetPrecisionCalc', label: 'Target Precision', format: 'number' },
        ],
      },
      {
        title: 'Stratum Plans',
        table: {
          query: 'sampling.stratumPlans.list',
          params: { planId: 'planId' },
          columns: [
            { key: 'stratumNumber', header: 'Number' },
            { key: 'stratumName', header: 'Name' },
            { key: 'segs', header: 'Segregations', format: 'number' },
            { key: 'estimatedVolume', header: 'Estimated Volume', format: 'volume' },
            { key: 'estimatedLoads', header: 'Estimated Loads', format: 'number' },
            { key: 'estimatedLoadSize', header: 'Estimated Size', format: 'number' },
            { key: 'estimatedStandardDeviation', header: 'St Dev', format: 'number' },
            { key: 'optimumSampleCalc', header: 'Optimum Samp', format: 'number' },
            { key: 'optimumPrecisionCalc', header: 'Optimum Prec', format: 'number' },
            { key: 'optimumFrequencyCalc', header: 'Optimum Freq', format: 'number' },
            { key: 'sampleCountConstraint', header: 'Constraint Samp', format: 'number' },
            { key: 'precisionConstraint', header: 'Constraint Prec', format: 'number' },
            { key: 'targetSampleCalc', header: 'Target Samp', format: 'number' },
            { key: 'targetPrecisionCalc', header: 'Target Prec', format: 'number' },
            { key: 'targetFrequencyCalc', header: 'Target Freq', format: 'number' },
          ],
          rowLink: { screen: 'stratum-plan', params: { stratumPlanId: 'stratumPlanId' } },
        },
      },
      {
        title: 'Sampling Plan Comparison',
        table: {
          query: 'sampling.planComparison.list',
          params: { planId: 'planId' },
          columns: [
            { key: 'stratumNumber', header: 'Stratum Number' },
            { key: 'stratumName', header: 'Name' },
            { key: 'sampleLoadsPlan', header: 'Loads Plan', format: 'number' },
            { key: 'sampleLoadsActual', header: 'Loads Act.', format: 'number' },
            { key: 'loadSizePlan', header: 'Load Size Plan', format: 'number' },
            { key: 'loadSizeActual', header: 'Load Size Act.', format: 'number' },
            { key: 'stDevPlan', header: 'St. Dev Plan', format: 'number' },
            { key: 'stDevActual', header: 'St. Dev Act.', format: 'number' },
            { key: 'frequencyPlan', header: 'Frequency Plan', format: 'number' },
            { key: 'frequencyActual', header: 'Frequency Act.', format: 'number' },
            { key: 'billedLoadsPlan', header: 'Billed Loads Plan', format: 'number' },
            { key: 'billedLoadsActual', header: 'Billed Loads Act.', format: 'number' },
            { key: 'volumePlan', header: 'Volume Plan', format: 'volume' },
            { key: 'volumeActual', header: 'Volume Act.', format: 'volume' },
            { key: 'precisionPlan', header: 'Precision Plan', format: 'number' },
            { key: 'precisionActual', header: 'Precision Act.', format: 'number' },
          ],
        },
      },
    ],
    reports: [
      { reportId: 'HBS3R888', label: 'Print Sample Plan Report', params: { SP_SAMPLEPLANID: 'planId' } },
      {
        reportId: 'HBS3R889',
        label: 'Print Stratum Description Report',
        params: { SP_SAMPLEPLANID: 'planId', SP_STRATUMNO: '=' },
      },
    ],
    editLink: { screen: 'sampling-plan-edit', params: { planId: 'planId' }, label: 'Edit', capability: 'SAMPLING_ADMIN' },
    actions: planActions,
    notes:
      'Edit, Add Stratum, Propose and Delete apply to In Progress plans (district administrators and the owning client\'s Stratum Planner administrators); Approve and Reject to Awaiting Approval plans (ministry district administrators); Create New Plan From Actuals to Active plans. Adding or changing a stratum clears the plan precisions: Save & Calculate before proposing. The comparison applies to Active, sample-based plans; reports apply to Active plans.',
  },
  // ── P891 Add Sampling Plan (+ P892 Confirm Add Population) ──
  {
    kind: 'form',
    id: 'sampling-plan-add',
    legacy: 'P891/P892',
    area: 'stratum-planner',
    title: 'Add Sampling Plan',
    navLabel: 'Add Sampling Plan',
    capability: 'SAMPLING_ADMIN',
    command: 'sampling.plan.create',
    fields: [
      ...planHeaderFields(),
      {
        name: 'ratioTypeCode',
        label: 'Ratio Type',
        type: 'radio',
        required: true,
        defaultValue: 'S',
        options: [
          { value: 'S', label: 'Sample' },
          { value: 'D', label: 'Default' },
        ],
        span: 2,
      },
      { name: 'precisionLimit', label: 'Precision Limit (%)', type: 'number', defaultValue: '1.0', helperText: '0.5 to 5.0 (Sample ratio type)' },
      { name: 'sampleFloor', label: 'Sample Floor', type: 'number', defaultValue: '10', helperText: '2 or more (Sample ratio type)' },
      confirmPopulationField,
    ],
    then: { screen: 'sampling-plan', params: { planId: 'planId' } },
    submitLabel: 'Save',
    notes:
      'The plan is saved In Progress (estimate method "Enter Volume and Loads", constraint "Constrain Precision"); add its strata from the plan. A population\'s ratio type cannot change once a plan has been approved.',
  },
  // ── P881 Edit Sampling Plan ("Save & Calculate") ──
  {
    kind: 'form',
    id: 'sampling-plan-edit',
    legacy: 'P881',
    area: 'stratum-planner',
    title: 'Edit Sampling Plan',
    nav: false,
    capability: 'SAMPLING_ADMIN',
    command: 'sampling.plan.calculate',
    loadQuery: 'sampling.plans.edit',
    keys: ['planId'],
    fields: [
      { name: 'populationNumber', label: 'Population', readOnlyOnEdit: true },
      { name: 'samplingYear', label: 'Year', type: 'select', options: SAMPLING_YEAR_OPTIONS, readOnlyOnEdit: true },
      { name: 'planAbbreviation', label: 'Plan Abbreviation', readOnlyOnEdit: true },
      { name: 'planName', label: 'Plan Name', required: true, maxLength: 40 },
      { name: 'clientNumber', label: 'Client Number', type: 'client', required: true, maxLength: 8 },
      { name: 'clientLocnCode', label: 'Location', required: true, upper: true, maxLength: 2 },
      { name: 'orgUnitNo', label: 'District', type: 'select', codeList: 'codes.rating.districts', required: true },
      { name: 'effectiveDate', label: 'Effective Date', type: 'date', required: true },
      { name: 'expiryDate', label: 'Expiry Date', type: 'date', required: true },
      { name: 'precisionLimit', label: 'Precision Limit (%)', type: 'number', helperText: '0.5 to 5.0 (Sample ratio type)' },
      { name: 'sampleFloor', label: 'Sample Floor', type: 'number', helperText: '2 or more (Sample ratio type)' },
      {
        name: 'estimateType',
        label: 'Estimate Method',
        type: 'radio',
        required: true,
        options: [
          { value: 'VL', label: 'Enter Volume and Loads' },
          { value: 'VS', label: 'Enter Volume and Size' },
          { value: 'LS', label: 'Enter Loads and Size' },
        ],
        span: 2,
      },
      {
        name: 'constraintType',
        label: 'Constraint Method',
        type: 'radio',
        required: true,
        options: [
          { value: 'S', label: 'Constrain Samples' },
          { value: 'P', label: 'Constrain Precision' },
        ],
        span: 2,
      },
      { name: 'updateTimestamp', label: 'Last Updated', readOnlyOnEdit: true },
    ],
    then: { screen: 'sampling-plan', params: { planId: 'planId' } },
    submitLabel: 'Save & Calculate',
    notes:
      'Saves the plan and recalculates every stratum\'s optimum / target samples, precision and frequency from its estimates (enter a stratum\'s estimates from the stratum plan). In Progress plans only.',
  },
  // ── P888 View Stratum Plan Details (+ P884 View Default Ratios) ──
  {
    kind: 'detail',
    id: 'stratum-plan',
    legacy: 'P888/P884',
    area: 'stratum-planner',
    title: 'View Stratum Plan Details',
    nav: false,
    capability: 'SAMPLING_VIEW',
    query: 'sampling.stratumPlans.detail',
    keys: ['stratumPlanId'],
    sections: [
      {
        title: 'Stratum Plan',
        fields: [
          { key: 'populationNumber', label: 'Population' },
          { key: 'samplingYear', label: 'Year' },
          { key: 'planAbbreviation', label: 'Plan' },
          { key: 'stratumNumber', label: 'Stratum Number' },
          { key: 'stratumName', label: 'Stratum Name' },
          { key: 'effectiveDate', label: 'Stratum Effective Date', format: 'date' },
          { key: 'expiryDate', label: 'Stratum Expiry Date', format: 'date' },
          { key: 'clientNumber', label: 'Stratum Owner Client' },
          { key: 'clientLocnCode', label: 'Location' },
          { key: 'stratumDesc', label: 'Stratum Description' },
          { key: 'timberSourceDesc', label: 'Timber Source Description' },
          { key: 'speciesGradeProfileDesc', label: 'Species / Grade Profile Description' },
          { key: 'productSchedule', label: 'Product Schedule' },
          { key: 'gradeSchedule', label: 'Grade Schedule' },
          { key: 'estimatedVolume', label: 'Volume', format: 'volume' },
          { key: 'estimatedLoads', label: 'Loads', format: 'number' },
          { key: 'estimatedLoadSize', label: 'Size', format: 'number' },
        ],
      },
      {
        title: 'Default Ratios',
        table: {
          query: 'sampling.stratumComposition.list',
          params: { stratumPlanId: 'stratumPlanId' },
          columns: [
            { key: 'scaleSpeciesCode', header: 'Species' },
            { key: 'scaleGradeCode', header: 'Grade' },
            { key: 'defaultRatio', header: 'Ratio', format: 'number' },
            { key: 'fraction', header: 'Fraction (%)', format: 'number' },
          ],
          rowLink: {
            screen: 'stratum-composition',
            params: { stratumPlanId: 'stratumPlanId', scaleSpeciesCode: 'scaleSpeciesCode', scaleGradeCode: 'scaleGradeCode' },
          },
        },
      },
    ],
    reports: [
      {
        reportId: 'HBS3R889',
        label: 'Print Stratum Description Report',
        params: { SP_SAMPLEPLANID: 'planId', SP_STRATUMNO: 'stratumNumber' },
      },
    ],
    editLink: { screen: 'stratum-plan-edit', params: { stratumPlanId: 'stratumPlanId' }, label: 'Edit', capability: 'SAMPLING_ADMIN' },
    actions: [
      {
        id: 'stratum-estimates',
        label: 'Estimates',
        command: 'sampling.stratumPlan.estimates',
        capability: 'SAMPLING_ADMIN',
        params: { stratumPlanId: 'stratumPlanId', updateTimestamp: 'updateTimestamp' },
        fields: [
          { name: 'estimatedVolume', label: 'Estimated Volume (m3)', type: 'number', helperText: 'Blank = unchanged' },
          { name: 'estimatedLoads', label: 'Estimated Loads', type: 'number', helperText: 'Blank = unchanged' },
          { name: 'estimatedLoadSize', label: 'Estimated Size (m3)', type: 'number', helperText: 'Blank = unchanged' },
          { name: 'estimatedStandardDeviation', label: 'Estimated Standard Deviation', type: 'number', helperText: 'Blank = unchanged' },
          { name: 'sampleCountConstraint', label: 'Constrained Samples', type: 'number', helperText: 'Blank = unchanged' },
          { name: 'precisionConstraint', label: 'Constrained Precision (%)', type: 'number', helperText: 'Blank = unchanged' },
        ],
        confirm: 'Save these estimates and recalculate the whole plan (P881 Save & Calculate)?',
      },
      {
        id: 'add-default-ratio',
        label: 'Add Default Ratio',
        command: 'sampling.stratumComposition.create',
        capability: 'SAMPLING_ADMIN',
        params: { stratumPlanId: 'stratumPlanId' },
        fields: compositionFields(),
      },
      {
        id: 'delete-stratum-plan',
        label: 'Delete',
        command: 'sampling.stratumPlan.delete',
        capability: 'SAMPLING_ADMIN',
        params: { stratumPlanId: 'stratumPlanId', updateTimestamp: 'updateTimestamp' },
        confirm: 'Delete this stratum plan and its default ratios? Strata referenced by scale returns or weigh slips cannot be deleted.',
        danger: true,
        then: { screen: 'sampling-plan', params: { planId: 'planId' } },
      },
    ],
    notes: 'Changes apply to strata of In Progress plans. Estimates recalculate the whole plan, as P881 Save & Calculate did.',
  },
  // ── P886 / P887 Edit Stratum Plan ──
  {
    kind: 'form',
    id: 'stratum-plan-edit',
    legacy: 'P886/P887',
    area: 'stratum-planner',
    title: 'Edit Stratum Plan',
    nav: false,
    capability: 'SAMPLING_ADMIN',
    command: 'sampling.stratumPlan.update',
    loadQuery: 'sampling.stratumPlans.detail',
    keys: ['stratumPlanId'],
    fields: [...stratumFields(), { name: 'updateTimestamp', label: 'Last Updated', readOnlyOnEdit: true }],
    then: { screen: 'stratum-plan', params: { stratumPlanId: 'stratumPlanId' } },
    submitLabel: 'Save',
    notes:
      'Stratum dates must lie within the plan period and not strand returns already scaled in the stratum; an approved stratum keeps its product and grade schedules. Default ratios are re-checked against the schedules and dates.',
  },
  // ── P885 Edit Default Ratios: one row ──
  {
    kind: 'detail',
    id: 'stratum-composition',
    legacy: 'P885',
    area: 'stratum-planner',
    title: 'Default Ratio',
    nav: false,
    capability: 'SAMPLING_VIEW',
    query: 'sampling.stratumComposition.list',
    keys: ['stratumPlanId', 'scaleSpeciesCode'],
    sections: [
      {
        title: 'Default Ratio',
        fields: [
          { key: 'stratumPlanId', label: 'Stratum Plan' },
          { key: 'scaleSpeciesCode', label: 'Species' },
          { key: 'scaleGradeCode', label: 'Grade' },
          { key: 'defaultRatio', label: 'Ratio', format: 'number' },
          { key: 'fraction', label: 'Fraction (%)', format: 'number' },
        ],
      },
    ],
    editLink: {
      screen: 'stratum-composition-edit',
      params: { stratumPlanId: 'stratumPlanId', scaleSpeciesCode: 'scaleSpeciesCode', scaleGradeCode: 'scaleGradeCode' },
      label: 'Update',
      capability: 'SAMPLING_ADMIN',
    },
    actions: [
      {
        id: 'remove-default-ratio',
        label: 'Delete',
        command: 'sampling.stratumComposition.remove',
        capability: 'SAMPLING_ADMIN',
        params: { stratumPlanId: 'stratumPlanId', scaleSpeciesCode: 'scaleSpeciesCode', scaleGradeCode: 'scaleGradeCode' },
        confirm: 'Delete this default ratio row?',
        danger: true,
        then: { screen: 'stratum-plan', params: { stratumPlanId: 'stratumPlanId' } },
      },
    ],
  },
  {
    kind: 'form',
    id: 'stratum-composition-edit',
    legacy: 'P885',
    area: 'stratum-planner',
    title: 'Update Default Ratio',
    nav: false,
    capability: 'SAMPLING_ADMIN',
    command: 'sampling.stratumComposition.update',
    loadQuery: 'sampling.stratumComposition.list',
    keys: ['stratumPlanId', 'scaleSpeciesCode'],
    fields: [
      ...compositionFields(),
      { name: 'originalScaleSpeciesCode', label: 'Species (before)', readOnlyOnEdit: true },
      { name: 'originalScaleGradeCode', label: 'Grade (before)', readOnlyOnEdit: true },
      { name: 'updateTimestamp', label: 'Last Updated', readOnlyOnEdit: true },
    ],
    then: { screen: 'stratum-plan', params: { stratumPlanId: 'stratumPlanId' } },
    submitLabel: 'Save',
  },

  // ── P851/P852 Search For Populations / List of Populations ──
  {
    kind: 'search',
    id: 'sampling-populations',
    legacy: 'P851/P852',
    area: 'stratum-planner',
    title: 'Search For Populations',
    navLabel: 'Populations',
    capability: 'SAMPLING_VIEW',
    query: 'sampling.populations.search',
    description: 'Supply one or more of the following search criteria.',
    criteria: [
      { name: 'population', label: 'Population', maxLength: 4 },
      { name: 'samplingYear', type: 'select', options: SAMPLING_YEAR_OPTIONS, label: 'Sampling Year' },
      { name: 'clientNumber', label: 'Population Owner Client', type: 'client', maxLength: 8 },
      { name: 'clientLocnCode', label: 'Location', maxLength: 2 },
    ],
    requireOneOf: ['population', 'samplingYear', 'clientNumber'],
    columns: [
      { key: 'populationNumber', header: 'Population', sortable: true },
      { key: 'samplingYear', header: 'Year', sortable: true },
      { key: 'clientNumber', header: 'Client', sortable: true },
      { key: 'clientLocnCode', header: 'Location' },
      { key: 'stratumCount', header: 'Strata Count', format: 'number' },
      { key: 'sampleLoads', header: 'Sample Loads', format: 'number' },
      { key: 'sampleVolume', header: 'Sample Volume', format: 'volume' },
      { key: 'sampleWeight', header: 'Sample Weight', format: 'number' },
      { key: 'ratio', header: 'Ratio', format: 'number' },
      { key: 'billedLoads', header: 'Billed Loads', format: 'number' },
      { key: 'billedVolume', header: 'Billed Volume', format: 'volume' },
      { key: 'billedWeight', header: 'Billed Weight', format: 'number' },
      { key: 'precision', header: 'Prec (%)', format: 'number' },
    ],
    rowLink: { screen: 'sampling-strata', params: { population: 'populationNumber', samplingYear: 'samplingYear' }, label: 'Strata' },
    reports: [
      {
        reportId: 'HBS3R852',
        label: 'Print',
        params: { PARAMYEAR: 'samplingYear', PARAMPOPULATION: 'population', PARAMCLIENTNO: 'clientNumber', PARAMCLIENTLOC: 'clientLocnCode' },
      },
    ],
  },

  // ── P853/P860 Search For Strata / List of Strata in a Population ──
  {
    kind: 'search',
    id: 'sampling-strata',
    legacy: 'P853/P860',
    area: 'stratum-planner',
    title: 'Search For Strata',
    navLabel: 'Strata',
    capability: 'SAMPLING_VIEW',
    query: 'sampling.strata.search',
    criteria: [
      { name: 'population', label: 'Population', required: true, maxLength: 4 },
      { name: 'samplingYear', label: 'Sampling Year', type: 'select', options: SAMPLING_YEAR_OPTIONS, required: true },
    ],
    columns: [
      { key: 'stratumNumber', header: 'Stratum Number' },
      { key: 'stratumName', header: 'Name' },
      { key: 'sampleLoads', header: 'Sample Loads', format: 'number' },
      { key: 'sampleVolume', header: 'Sample Volume', format: 'volume' },
      { key: 'sampleWeight', header: 'Sample Weight', format: 'number' },
      { key: 'ratio', header: 'Ratio', format: 'number' },
      { key: 'sampleLoadSize', header: 'Size', format: 'number' },
      { key: 'sampleStandardDeviation', header: 'St.Dev', format: 'number' },
      { key: 'samplingFrequency', header: 'Freq', format: 'number' },
      { key: 'billedLoads', header: 'Billed Loads', format: 'number' },
      { key: 'billedVolume', header: 'Billed Volume', format: 'volume' },
      { key: 'billedWeight', header: 'Billed Weight', format: 'number' },
      { key: 'precision', header: 'Prec', format: 'number' },
    ],
    // P860 stratum link → P865 Samples in Stratum (which links on to P866–P877).
    rowLink: {
      screen: 'stratum-samples',
      params: { population: 'populationNumber', samplingYear: 'samplingYear', stratumNumber: 'stratumNumber' },
    },
    reports: [{ reportId: 'HBS3R860', label: 'Print', params: { PARAMYEAR: 'samplingYear', PARAMPOPULATION: 'population' } }],
  },

  // ── P861/P862/P863 Species / Grades / Segregations in Population ──
  {
    kind: 'search',
    id: 'sampling-population-stats',
    legacy: 'P861/P862/P863',
    area: 'stratum-planner',
    title: 'Species / Grades / Segregations in Population',
    navLabel: 'Population Species / Grades',
    capability: 'SAMPLING_VIEW',
    query: 'sampling.populationStats.search',
    criteria: [
      { name: 'population', label: 'Population', required: true, maxLength: 4 },
      { name: 'samplingYear', label: 'Year', type: 'select', options: SAMPLING_YEAR_OPTIONS, required: true },
      statTypeField,
    ],
    columns: statColumns,
  },

  // ── P866/P867/P868 Species / Grades / Segregations in Stratum ──
  {
    kind: 'search',
    id: 'sampling-stratum-stats',
    legacy: 'P866/P867/P868',
    area: 'stratum-planner',
    title: 'Species / Grades / Segregations in Stratum',
    nav: false,
    capability: 'SAMPLING_VIEW',
    query: 'sampling.stratumStats.search',
    criteria: [
      { name: 'population', label: 'Population', required: true, maxLength: 4 },
      { name: 'samplingYear', label: 'Year', type: 'select', options: SAMPLING_YEAR_OPTIONS, required: true },
      { name: 'stratumNumber', label: 'Stratum', required: true, maxLength: 2 },
      statTypeField,
    ],
    columns: statColumns,
  },

  // ── P865 List of Samples in a Stratum (+ P870 categories, P875–P877 chart data) ──
  // The legacy P875–P877 Cewolf charts are shown as their data: the app has no chart
  // library. P876 (ratio vs scale date) and P877 (volume vs weight) plot the sample rows
  // of the "Samples" tab; P875 is the "Sample Loads by Scale Month" tab.
  {
    kind: 'detail',
    id: 'stratum-samples',
    legacy: 'P865/P870/P875/P876/P877',
    area: 'stratum-planner',
    title: 'List of Samples in a Stratum',
    nav: false,
    capability: 'SAMPLING_VIEW',
    query: 'sampling.stratum.detail',
    keys: ['population', 'samplingYear', 'stratumNumber'],
    extraLinks: [
      { path: STRATUM_STATS_PATH, params: { ...STRATUM_KEYS, type: '=SPC' }, label: 'Species in Stratum', capability: 'SAMPLING_VIEW' },
      { path: STRATUM_STATS_PATH, params: { ...STRATUM_KEYS, type: '=GRD' }, label: 'Grades in Stratum', capability: 'SAMPLING_VIEW' },
      { path: STRATUM_STATS_PATH, params: { ...STRATUM_KEYS, type: '=SGR' }, label: 'Segregations in Stratum', capability: 'SAMPLING_VIEW' },
    ],
    sections: [
      {
        title: 'Stratum',
        fields: [
          { key: 'populationNumber', label: 'Population' },
          { key: 'stratumNumber', label: 'Stratum' },
          { key: 'samplingYear', label: 'Year' },
          { key: 'ratioType', label: 'Ratio Type' },
          { key: 'stratumName', label: 'Stratum Name' },
          { key: 'gradeSchedule', label: 'Grade Schedule' },
          { key: 'productSchedule', label: 'Product Schedule' },
          { key: 'clientNumber', label: 'Stratum Owner' },
          { key: 'clientLocnCode', label: 'Location' },
          { key: 'clientName', label: 'Stratum Owner Name' },
        ],
      },
      {
        title: 'Stratum Totals',
        fields: [
          { key: 'sampleLoads', label: 'Loads', format: 'number' },
          { key: 'sampleVolume', label: 'Volume', format: 'volume' },
          { key: 'sampleWeight', label: 'Weight', format: 'number' },
          { key: 'ratio', label: 'Ratio', format: 'number' },
          { key: 'sampleLoadSize', label: 'Size', format: 'number' },
          { key: 'sampleStandardDeviation', label: 'StDev', format: 'number' },
          { key: 'samplingFrequency', label: 'Freq', format: 'number' },
          { key: 'precision', label: 'Prec', format: 'number' },
        ],
      },
      {
        title: 'Samples',
        table: {
          query: 'sampling.stratumSamples.list',
          params: STRATUM_RECORD_PARAMS,
          columns: [
            { key: 'documentControlNumber', header: 'Summary Document Number' },
            { key: 'version', header: 'Version Number' },
            { key: 'licenseNumber', header: 'Scaler' },
            { key: 'returnNumber', header: 'Return' },
            { key: 'scaleSite', header: 'Site' },
            { key: 'scaleDate', header: 'Scale Date', format: 'date' },
            { key: 'timberMark', header: 'Mark' },
            { key: 'segregationCount', header: 'Segr.', format: 'number' },
            { key: 'totalSampleVolume', header: 'Volume', format: 'volume' },
            { key: 'totalSampleWeight', header: 'Weight', format: 'number' },
            { key: 'ratio', header: 'Ratio', format: 'number' },
            { key: 'ratioOutlier', header: 'Outlier', format: 'yesno' },
            { key: 'predominantSpecies', header: 'Species 2/3' },
            { key: 'firstMajorSpecies', header: 'Species 1/3' },
            { key: 'secondMajorSpecies', header: 'Species 1/3 ' },
            { key: 'predominantGrade', header: 'Grade 2/3' },
            { key: 'firstMajorGrade', header: 'Grade 1/3' },
            { key: 'secondMajorGrade', header: 'Grade 1/3 ' },
          ],
          // Segr. / Species List / Grade List links → P871–P873.
          rowLink: { screen: 'sample-statistics', params: { trxId: 'trxId' } },
        },
      },
      {
        title: 'Sample Categories by Species Predominance',
        table: {
          query: 'sampling.stratumCategories.list',
          params: { ...STRATUM_RECORD_PARAMS, type: '=SPC' },
          columns: CATEGORY_COLUMNS,
        },
      },
      {
        title: 'Sample Categories by Grade Predominance',
        table: {
          query: 'sampling.stratumCategories.list',
          params: { ...STRATUM_RECORD_PARAMS, type: '=GRD' },
          columns: CATEGORY_COLUMNS,
        },
      },
      {
        title: 'Sample Loads by Scale Month',
        table: {
          query: 'sampling.stratumLoadsByMonth.list',
          params: STRATUM_RECORD_PARAMS,
          columns: [
            { key: 'monthLabel', header: 'Scale Month' },
            { key: 'sampleLoads', header: 'Sample Loads', format: 'number' },
          ],
        },
      },
    ],
  },

  // ── P871/P872/P873 List of Species / Grades / Segregations in a Sample ──
  {
    kind: 'detail',
    id: 'sample-statistics',
    legacy: 'P871/P872/P873',
    area: 'stratum-planner',
    title: 'Species / Grades / Segregations in a Sample',
    nav: false,
    capability: 'SAMPLING_VIEW',
    query: 'sampling.sample.detail',
    keys: ['trxId'],
    sections: [
      {
        title: 'Sample',
        fields: [
          { key: 'samplingYear', label: 'Year' },
          { key: 'populationNumber', label: 'Population' },
          { key: 'stratumNumber', label: 'Stratum' },
          { key: 'trxId', label: 'Transaction' },
          { key: 'timberMark', label: 'Mark' },
          { key: 'licenseNumber', label: 'Scaler' },
          { key: 'returnNumber', label: 'Return' },
          { key: 'scaleSite', label: 'Site' },
          { key: 'scaleDate', label: 'Scale Date', format: 'date' },
          { key: 'loadArrivalNumber', label: 'Arrival' },
          { key: 'totalSampleVolume', label: 'Sample Total Volume', format: 'volume' },
          { key: 'totalSampleWeight', label: 'Sample Total Weight', format: 'number' },
        ],
      },
      {
        title: 'Species',
        table: {
          query: 'sampling.sampleStats.list',
          params: { trxId: 'trxId', type: '=SPC' },
          columns: [{ key: 'scaleSpeciesCode', header: 'Species' }, ...SAMPLE_DATA_COLUMNS],
        },
      },
      {
        title: 'Grades',
        table: {
          query: 'sampling.sampleStats.list',
          params: { trxId: 'trxId', type: '=GRD' },
          columns: [{ key: 'scaleGradeCode', header: 'Grade' }, ...SAMPLE_DATA_COLUMNS],
        },
      },
      {
        title: 'Segregations',
        table: {
          query: 'sampling.sampleStats.list',
          params: { trxId: 'trxId', type: '=SGR' },
          columns: [
            { key: 'scaleSpeciesCode', header: 'Species' },
            { key: 'scaleGradeCode', header: 'Grade' },
            ...SAMPLE_DATA_COLUMNS,
          ],
        },
      },
    ],
  },
];
