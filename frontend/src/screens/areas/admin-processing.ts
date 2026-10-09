import type { ScreenDef } from '../types';

/**
 * P038/P039 HBS Alert and Processing Parameters (Production Control).
 * Backend: SubmissionsProcessingParamsCatalog.java — backed by the PROPOSED
 * table THE.HBS_APP_SETTING / proc THE.HBS_STORE_APP_SETTING
 * (docs/db/proposed-ddl.sql); requires the DBA change before use.
 * Each legacy Save/Delete went to P039 for Yes/No — here the action's
 * confirmation modal.
 */
export const screens: ScreenDef[] = [
  {
    kind: 'detail',
    id: 'processing-parameters',
    legacy: 'P038/P039',
    area: 'admin',
    title: 'HBS Alert and Processing Parameters',
    capability: 'PRODUCTION_CONTROL',
    nav: true,
    query: 'admin.processingParameters',
    keys: [],
    notes:
      'Batch jobs B2031/B2041 read the process start location and time limit; batch printing reads the printer names; ' +
      'the alert text is shown on the Home page. Defaults: start location 0, time limit 9999999999.',
    actions: [
      {
        id: 'process-params',
        label: 'Save Batch Processing Parameter',
        command: 'admin.processParameter.save',
        capability: 'PRODUCTION_CONTROL',
        confirm: 'Confirm HBS Processing Parameters',
        fields: [
          {
            name: 'settingKey',
            label: 'Process Name',
            type: 'select',
            required: true,
            options: [
              { value: 'B2031.START_LOC', label: 'B2031 - Process Start Location' },
              { value: 'B2031.TIME_LIMIT', label: 'B2031 - Process Time Limit (in minutes)' },
              { value: 'B2041.START_LOC', label: 'B2041 - Process Start Location' },
              { value: 'B2041.TIME_LIMIT', label: 'B2041 - Process Time Limit (in minutes)' },
            ],
            span: 2,
          },
          { name: 'settingValue', label: 'Value', type: 'number', required: true, maxLength: 10, span: 2 },
        ],
      },
      {
        id: 'printers',
        label: 'Save Printer Name',
        command: 'admin.printer.save',
        capability: 'PRODUCTION_CONTROL',
        confirm: 'Confirm HBS Processing Parameters',
        fields: [
          {
            name: 'settingKey',
            label: 'Printer',
            type: 'select',
            required: true,
            options: [
              { value: 'PRINTER.SEND_TO', label: 'Send To Printer' },
              { value: 'PRINTER.COPY_TO', label: 'Copy To Printer' },
            ],
            span: 2,
          },
          {
            name: 'settingValue',
            label: 'Printer Name',
            required: true,
            maxLength: 50,
            placeholder: '\\\\domain\\printer',
            span: 2,
          },
        ],
      },
      {
        id: 'alert-save',
        label: 'Save Alert Text',
        command: 'admin.alertText.save',
        capability: 'PRODUCTION_CONTROL',
        confirm: 'Confirm Alert Text',
        fields: [{ name: 'alertText', label: 'Alert Text', type: 'textarea', required: true, maxLength: 296, span: 4 }],
      },
      {
        id: 'alert-delete',
        label: 'Delete Alert Text',
        command: 'admin.alertText.delete',
        capability: 'PRODUCTION_CONTROL',
        confirm: 'Delete Alert Text',
        danger: true,
      },
    ],
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
        fields: [
          { key: 'alertText', label: 'Alert Text' },
          { key: 'lastUpdateTimestamp', label: 'Last Updated', format: 'datetime' },
        ],
      },
      {
        title: 'Settings History',
        table: {
          query: 'admin.appSettings',
          params: {},
          columns: [
            { key: 'settingKey', header: 'Setting' },
            { key: 'settingValue', header: 'Value' },
            { key: 'updateUserid', header: 'Updated By' },
            { key: 'updateTimestamp', header: 'Updated', format: 'datetime' },
          ],
        },
      },
    ],
  },
];
