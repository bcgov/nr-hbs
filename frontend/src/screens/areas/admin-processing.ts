import type { ScreenDef } from '../types';

/**
 * P038 HBS Alert and Processing Parameters (Production Control), read-only.
 * Backend: SubmissionsProcessingParamsCatalog.java. The values live in the
 * backend's settings ConfigMap and are changed there (docs/coexistence.md);
 * the legacy P038 Save / Set to Defaults / Delete actions and their P039
 * confirmations are not carried over.
 */

export const screens: ScreenDef[] = [
  {
    kind: 'detail',
    id: 'processing-parameters',
    legacy: 'P038',
    area: 'admin',
    title: 'HBS Alert and Processing Parameters',
    capability: 'PRODUCTION_CONTROL',
    nav: true,
    query: 'admin.processingParameters',
    keys: [],
    notes:
      'Batch jobs B2031/B2041 read the process start location and time limit; batch printing reads the printer names; ' +
      'the alert text is shown on the Home page. These values are set in the HBS settings ConfigMap in OpenShift ' +
      '(legacy defaults: start location 0, time limit 9999999999); the share bridge copies them to the legacy batch files.',
    sections: [
      {
        title: 'Batch Processing Parameters',
        fields: [
          { key: 'b2031StartLoc', label: 'B2031 Process Start Location' },
          { key: 'b2031TimeLimit', label: 'B2031 Process Time Limit (in minutes)' },
          { key: 'b2041StartLoc', label: 'B2041 Process Start Location' },
          { key: 'b2041TimeLimit', label: 'B2041 Process Time Limit (in minutes)' },
        ],
      },
      {
        title: 'Printer Names (\\\\domain\\printer)',
        fields: [
          { key: 'sendToPrinter', label: 'Send To Printer' },
          { key: 'copyToPrinter', label: 'Copy To Printer' },
        ],
      },
      {
        title: 'Alert Text',
        fields: [{ key: 'alertText', label: 'Alert Text' }],
      },
    ],
  },
];
