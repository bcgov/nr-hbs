import type { ColumnDef, ScreenDef } from '../types';

/**
 * Stratum Planner — legacy tab "Stratum Planner" (module smp, menu P850
 * "Stratum Advisor Menu"). Read-only for now: the plan workflow (add / edit /
 * propose / approve / reject / delete / copy) is Java logic still to be ported.
 * Backend: backend/.../catalog/SamplingCatalog.java (ids sampling.*).
 * Mapping + unported logic: docs/areas/rating-and-sampling.md.
 */

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
      { name: 'samplingYear', label: 'Sampling Year', type: 'number', maxLength: 4 },
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
            { key: 'stratumNumber', header: 'No.' },
            { key: 'stratumName', header: 'Name' },
            { key: 'segs', header: 'Segs', format: 'number' },
            { key: 'estimatedVolume', header: 'Est. Volume', format: 'volume' },
            { key: 'estimatedLoads', header: 'Est. Loads', format: 'number' },
            { key: 'estimatedLoadSize', header: 'Est. Size', format: 'number' },
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
            { key: 'stratumNumber', header: 'Stratum No.' },
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
    notes:
      'The legacy Edit, Propose, Approve, Reject, Delete, Copy and Create New Plan From Actuals functions are not yet available. The comparison applies to Active, sample-based plans; reports apply to Active plans.',
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
          { key: 'clientLocnCode', label: 'Loc' },
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
      { name: 'samplingYear', label: 'Sampling Year', type: 'number', maxLength: 4 },
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
      { name: 'samplingYear', label: 'Sampling Year', type: 'number', required: true, maxLength: 4 },
    ],
    columns: [
      { key: 'stratumNumber', header: 'Stratum No.' },
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
    rowLink: {
      screen: 'sampling-stratum-stats',
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
      { name: 'samplingYear', label: 'Year', type: 'number', required: true, maxLength: 4 },
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
      { name: 'samplingYear', label: 'Year', type: 'number', required: true, maxLength: 4 },
      { name: 'stratumNumber', label: 'Stratum', required: true, maxLength: 2 },
      statTypeField,
    ],
    columns: statColumns,
  },
];
