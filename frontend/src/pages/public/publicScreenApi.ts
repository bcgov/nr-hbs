import type { ScreenApi } from '@/components/screens/screenApi';
import {
  downloadPublic,
  exportPublicPdf,
  exportPublicQuery,
  exportPublicXml,
  listPublicQuery,
  onePublicQuery,
  publicCodeList,
  runPublicQuery,
} from '@/services/publicApi';

import { publicScreenPath } from './publicScreens';

/**
 * The shared screen templates' data source for the public area: anonymous
 * fetches against /api/v1/hbs/public/**, links only to other public screens,
 * and "Back to Public Queries" for records opened directly.
 */
export const publicScreenApi: ScreenApi = {
  runQuery: runPublicQuery,
  listQuery: listPublicQuery,
  oneQuery: onePublicQuery,
  exportQuery: exportPublicQuery,
  exportQueryXml: exportPublicXml,
  exportQueryPdf: exportPublicPdf,
  codeList: publicCodeList,
  download: downloadPublic,
  screenHref: publicScreenPath,
  home: { path: '/public', label: 'Public Queries' },
};
