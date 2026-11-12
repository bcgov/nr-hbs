// Builds the role × page access matrix from the code:
//
//   cd frontend && npm run access:matrix
//
// Output: docs/access-matrix.xlsx (and docs/access-matrix.csv, the main sheet).
//
// Sources, all read from the repository (nothing is typed in by hand except
// the BESPOKE pages below, which are React pages outside the screen framework):
//   - capability → roles: backend Capability.java (enforced), checked against
//     frontend/src/routes/access.ts (the SPA mirror);
//   - pages: frontend/src/screens/areas/*.ts and pages/public/publicScreens.ts;
//   - what each page lets you do: the screen's capability (READ), its create /
//     edit links, form command, row / bulk / record actions (WRITE, UPDATE,
//     DELETE), each classified by its command id and label;
//   - every command id is looked up in backend/src/main/java and the
//     Capability tokens in that file are compared with the SPA's capability.
import { readdirSync, readFileSync, writeFileSync, mkdtempSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
import { crc32 } from 'node:zlib';
import { execSync } from 'node:child_process';

import { build } from 'esbuild';

const FRONTEND = path.resolve(import.meta.dirname, '..');
const ROOT = path.resolve(FRONTEND, '..');
const SRC = path.join(FRONTEND, 'src');
const JAVA = path.join(ROOT, 'backend', 'src', 'main', 'java');
const OUT_XLSX = path.join(ROOT, 'docs', 'access-matrix.xlsx');
const OUT_CSV = path.join(ROOT, 'docs', 'access-matrix.csv');

// ── roles ────────────────────────────────────────────────────────────
const typesTs = readFileSync(path.join(SRC, 'context', 'auth', 'types.ts'), 'utf-8');
const ROLE_LABELS = Object.fromEntries(
  [...typesTs.matchAll(/^\s+(HBS_[A-Z_]+): '([^']+)',$/gm)].map((m) => [m[1], m[2]]),
);
const ROLES = Object.keys(ROLE_LABELS).filter((r) => r !== 'HBS_LEGACY_BRIDGE');
const roleGroup = (r) => (/^HBS_CLI_/.test(r) ? 'Industry (BCeID)' : /^HBS_SPC_/.test(r) ? 'Other industry (BCeID)' : 'Ministry (IDIR)');

// ── capabilities: backend enum (enforced) vs SPA mirror ─────────────
const capJava = readFileSync(path.join(JAVA, 'ca/bc/gov/nrs/hbs/api/query/Capability.java'), 'utf-8')
  .replace(/\/\/.*$/gm, '')
  .replace(/\/\*[\s\S]*?\*\//g, '');
const CAPS = {};
for (const m of capJava.matchAll(/^\s+([A-Z_]+)\(([^)]*)\)[,;]/gm)) {
  CAPS[m[1]] = m[2]
    .split(',')
    .map((s) => s.trim().replace(/^HbsRoles\./, ''))
    .filter(Boolean)
    .map((s) => `HBS_${s}`);
}
const accessTs = readFileSync(path.join(SRC, 'routes', 'access.ts'), 'utf-8');
const SPA_CAPS = {};
for (const m of accessTs.matchAll(/^\s+([A-Z_]+): \[([^\]]*)\]/gm)) {
  SPA_CAPS[m[1]] = [...m[2].matchAll(/'([A-Z_]+)'/g)].map((x) => x[1]);
}
const capProblems = [];
for (const c of new Set([...Object.keys(CAPS), ...Object.keys(SPA_CAPS)])) {
  const a = [...(CAPS[c] ?? [])].sort().join(',');
  const b = [...(SPA_CAPS[c] ?? [])].sort().join(',');
  if (a !== b) capProblems.push(`${c}: backend [${a}] vs SPA [${b}]`);
}
const rolesOf = (cap) => {
  if (cap === 'PUBLIC') return ROLES;
  if (Array.isArray(cap)) return cap;
  if (!CAPS[cap]) throw new Error(`Unknown capability ${cap}`);
  return CAPS[cap];
};

// ── screens (bundled so the TypeScript definitions load as data) ─────
const tmp = mkdtempSync(path.join(tmpdir(), 'hbs-matrix-'));
const areaFiles = readdirSync(path.join(SRC, 'screens', 'areas')).filter((f) => f.endsWith('.ts')).sort();
const entry = path.join(tmp, 'entry.ts');
writeFileSync(
  entry,
  [
    ...areaFiles.map((f, i) => `import { screens as a${i} } from '@/screens/areas/${f.replace(/\.ts$/, '')}';`),
    `import { PUBLIC_SCREENS } from '@/pages/public/publicScreens';`,
    `import { AREAS } from '@/screens/registry-areas';`,
    `export const SCREENS = [${areaFiles.map((_, i) => `...a${i}`).join(', ')}];`,
    `export { PUBLIC_SCREENS, AREAS };`,
  ].join('\n'),
);
// registry.ts uses import.meta.glob (Vite only); take just its AREAS list.
const registry = readFileSync(path.join(SRC, 'screens', 'registry.ts'), 'utf-8');
writeFileSync(
  path.join(tmp, 'registry-areas.ts'),
  `export const AREAS = ${registry.match(/export const AREAS: AreaDef\[\] = (\[[\s\S]*?\n\]);/)[1]};`,
);
const bundle = path.join(tmp, 'bundle.mjs');
await build({
  entryPoints: [entry],
  bundle: true,
  format: 'esm',
  platform: 'node',
  outfile: bundle,
  logLevel: 'error',
  plugins: [
    {
      name: 'alias',
      setup(b) {
        b.onResolve({ filter: /^@\/screens\/registry-areas$/ }, () => ({ path: path.join(tmp, 'registry-areas.ts') }));
        b.onResolve({ filter: /^@\// }, async (args) =>
          b.resolve(`./${args.path.slice(2)}`, { resolveDir: SRC, kind: args.kind }),
        );
      },
    },
  ],
});
const { SCREENS, PUBLIC_SCREENS, AREAS } = await import(pathToFileURL(bundle).href);
const AREA_LABEL = Object.fromEntries(AREAS.map((a) => [a.id, a.label]));

// ── operation of a command / link ────────────────────────────────────
const OPS = ['R', 'W', 'U', 'D'];
const OP_NAME = { R: 'READ', W: 'WRITE (create)', U: 'UPDATE', D: 'DELETE' };
const classify = (command = '', label = '') => {
  const last = command.split('.').pop() ?? '';
  if (/^(delete|remove|discard)/i.test(last) || /^(delete|remove|discard)\b/i.test(label)) return 'D';
  if (/^(create|add|new|copy|submit|upload)/i.test(last) || /^(add|create|new|copy|submit)\b/i.test(label)) return 'W';
  return 'U';
};

// ── backend cross-check: Capability tokens in the file declaring the command ──
const javaFiles = execSync(`find "${JAVA}" -name '*.java'`).toString().trim().split('\n');
const javaText = new Map(javaFiles.map((f) => [f, readFileSync(f, 'utf-8')]));
const javaByClass = new Map(javaFiles.map((f) => [path.basename(f, '.java'), f]));
const capsIn = (text, seen = new Set()) => {
  const caps = [...text.matchAll(/Capability\.([A-Z_]+)/g)].map((m) => m[1]);
  // a command class may inherit capability() from its superclass
  for (const m of text.matchAll(/extends (\w+)/g)) {
    const f = javaByClass.get(m[1]);
    if (f && !seen.has(f)) { seen.add(f); caps.push(...capsIn(javaText.get(f), seen)); }
  }
  return caps;
};
const commandFiles = (command) =>
  [...javaText].filter(([, t]) => t.includes(`"${command}"`) || t.includes(`"${command.split('.').slice(0, -1).join('.')}." +`));
/** Class declarations in a file: { name, parent, start } in source order. */
const classesIn = (t) => [...t.matchAll(/\bclass (\w+)(?:\s+extends (\w+))?/g)].map((m) => ({ name: m[1], parent: m[2], start: m.index }));
/** capability() declared in a class body (text up to the next class declaration), else its parent's (same file first). */
const classCap = (name, file, depth = 0) => {
  if (depth > 5) return null;
  const order = [file, ...[...javaText.keys()].filter((f) => f !== file && path.basename(f, '.java') === name)];
  for (const f of order) {
    const t = javaText.get(f);
    const cls = classesIn(t);
    const k = cls.findIndex((c) => c.name === name);
    if (k < 0) continue;
    const body = t.slice(cls[k].start, cls[k + 1]?.start ?? t.length);
    const m = body.match(/capability\(\)\s*\{\s*return Capability\.([A-Z_]+)/);
    if (m) return m[1];
    if (!cls[k].parent) return null;
    const parentFile = cls.some((c) => c.name === cls[k].parent) ? f : javaByClass.get(cls[k].parent);
    return parentFile ? classCap(cls[k].parent, parentFile, depth + 1) : null;
  }
  return null;
};
/**
 * The Capability the backend declares for a command: a catalog CommandDefinition's
 * .capability(...) after its builder id, or the capability() of the WorkflowCommand
 * class whose body holds the id (inherited from its superclass when not overridden).
 */
const backendCap = (command) => {
  for (const [file, t] of commandFiles(command)) {
    const i = t.indexOf(`"${command}"`);
    if (i < 0) continue;
    const builder = t.slice(i, i + 600).match(/^"[^"]+"\)[\s\S]*?\.capability\(Capability\.([A-Z_]+)\)/);
    if (builder && t.slice(Math.max(0, i - 40), i).includes('builder(')) return builder[1];
    const owner = classesIn(t).filter((c) => c.start < i).pop();
    if (owner) {
      const cap = classCap(owner.name, file);
      if (cap) return cap;
    }
  }
  return null;
};
const backendCheck = (command, cap, op) => {
  if (!command || op === 'R') return '';
  const files = commandFiles(command);
  if (!files.length) return 'NOT FOUND in backend';
  const caps = new Set(files.flatMap(([, t]) => capsIn(t)));
  if (Array.isArray(cap) || caps.has(cap)) return 'ok';
  return `check: backend file uses ${[...caps].join(', ') || 'no Capability'}`;
};

// ── collect rows ─────────────────────────────────────────────────────
const SCREEN_BY_PATH = new Map(SCREENS.map((x) => [`/${x.area}/${x.id}`, x]));
/** Write pages outside the screen framework, by path → operation and command. */
const BESPOKE_WRITES = {
  '/scale-returns/submit': ['W', ''],
  '/profiles/scaler-key-request': ['W', 'profiles.scalers.requestKey'],
  '/scale-returns/summary-return-children': ['U', 'summaryReturns.saveSegregations'],
  '/scale-returns/detail-entry': ['U', 'detailReturns.save'],
};
const linkOp = (target, fallback) => {
  const s = SCREEN_BY_PATH.get(target);
  if (s) return s.kind === 'form' ? (s.loadQuery || s.keys?.length ? 'U' : 'W') : null;
  if (BESPOKE_WRITES[target]) return BESPOKE_WRITES[target][0];
  return fallback && !target ? fallback : null;
};
const linkCommand = (target) => SCREEN_BY_PATH.get(target)?.command ?? BESPOKE_WRITES[target]?.[1] ?? '';

/** One page: { area, page, menu, path, legacy, kind, ops: [{op, label, command, cap, note}] } */
const pages = [];
const screenPath = (s) => `/${s.area}/${s.id}`;

for (const s of SCREENS) {
  const ops = [];
  if (s.kind === 'form') {
    const op = s.loadQuery || s.keys?.length ? 'U' : 'W';
    ops.push({ op, label: s.submitLabel ?? (op === 'W' ? 'Save (new)' : 'Save'), command: s.command, cap: s.capability });
  } else {
    ops.push({ op: 'R', label: 'Open, search, print, export', command: s.query, cap: s.capability });
  }
  // Links only navigate: they count as the target page's write (a save form, or a
  // bespoke write page) and add nothing when the target is a read-only page.
  for (const [l, fallback] of [[s.createLink, 'W'], [s.editLink, 'U'], ...(s.extraLinks ?? []).map((x) => [x, null])]) {
    if (!l) continue;
    const op = linkOp(l.screen ? `/${s.area}/${l.screen}` : l.path, fallback);
    if (op) ops.push({ op, label: l.label ?? (op === 'W' ? 'Add' : 'Edit'), command: linkCommand(l.screen ? `/${s.area}/${l.screen}` : l.path), cap: l.capability, note: `opens ${l.screen ?? l.path}` });
  }
  for (const a of [...(s.actions ?? []), ...(s.rowActions ?? [])]) ops.push({ op: classify(a.command, a.label), label: a.label, command: a.command, cap: a.capability, note: a.showWhen ? 'only for records in the right state' : '' });
  for (const a of [...(s.bulkActions ?? []), ...(s.sections ?? []).flatMap((x) => x.table?.bulkActions ?? [])])
    ops.push({ op: classify(a.command, a.label), label: `${a.label} (selected rows)`, command: a.command, cap: a.capability, note: '' });
  if (s.actionsAvailable) ops.forEach((o) => o.op !== 'R' && (o.note = [o.note, 'buttons also filtered per record by the backend'].filter(Boolean).join('; ')));
  pages.push({ area: AREA_LABEL[s.area] ?? s.area, page: s.title, menu: s.nav === false || (s.kind !== 'search' && s.nav !== true) ? '' : (s.navLabel ?? s.title), path: screenPath(s), legacy: s.legacy, kind: s.kind, ops });
}

/** React pages outside the screen framework (App.tsx bespoke routes). Keep in step with those pages. */
const DETAIL_BUTTONS = 'per document: scale-site / scaler-licence data domain and document state (DetailAuthority)';
const BESPOKE = [
  { area: 'Home', page: 'Home (work queues, quick links)', path: '/home', legacy: 'home.jsp', ops: [{ op: 'R', label: 'Open', cap: 'ANY_USER', note: 'work-queue panels depend on roles' }] },
  { area: 'Scale Returns', page: 'Submit File of Detail Returns', path: '/scale-returns/submit', legacy: 'P565', ops: [{ op: 'W', label: 'Upload XML file', command: '', cap: 'XML_SUBMIT' }] },
  { area: 'Scale Returns', page: 'Download HBS Public Key', path: '/scale-returns/public-key', legacy: 'P567', ops: [{ op: 'R', label: 'Open, download', cap: 'ANY_USER' }] },
  {
    area: 'Scale Returns', page: 'Detail Return entry / view', path: '/scale-returns/detail-entry', legacy: 'P004, P550–P760',
    ops: [
      { op: 'R', label: 'Open', command: '', cap: 'SCALE_RETURNS_VIEW' },
      { op: 'W', label: 'Add detail return', command: 'detailReturns.add', cap: 'DETAIL_RETURN_EDIT' },
      { op: 'U', label: 'Save', command: 'detailReturns.save', cap: 'DETAIL_RETURN_EDIT', note: DETAIL_BUTTONS },
      { op: 'U', label: 'Change event', command: 'detailReturns.changeEvent', cap: 'DETAIL_RETURN_EDIT', note: DETAIL_BUTTONS },
      { op: 'U', label: 'Release', command: 'detailReturns.release', cap: 'DETAIL_RETURN_EDIT', note: DETAIL_BUTTONS },
      { op: 'U', label: 'Re-Edit', command: 'detailReturns.reEdit', cap: 'DETAIL_RETURN_EDIT', note: DETAIL_BUTTONS },
      { op: 'U', label: 'Hold', command: 'detailReturns.hold', cap: 'DETAIL_RETURN_EDIT', note: DETAIL_BUTTONS },
      { op: 'U', label: 'Cancel', command: 'detailReturns.cancel', cap: 'DETAIL_RETURN_EDIT', note: DETAIL_BUTTONS },
      { op: 'U', label: 'Cancel and Replace with Changes', command: 'detailReturns.cancelReplaceChanges', cap: 'DETAIL_RETURN_EDIT', note: DETAIL_BUTTONS },
      { op: 'U', label: 'Replace with Changes', command: 'detailReturns.replaceChanges', cap: 'DETAIL_RETURN_EDIT', note: DETAIL_BUTTONS },
      { op: 'U', label: 'Replace without Changes', command: 'detailReturns.replaceNoChanges', cap: 'DETAIL_RETURN_EDIT', note: DETAIL_BUTTONS },
      { op: 'U', label: 'Approve Change', command: 'detailReturns.approve', cap: ['HBS_INV_CORR_APP'], note: 'capability DETAIL_RETURN_APPROVE (BILL_ADMIN, INV_CORR_APP) but the button also requires HBS_INV_CORR_APP and the district domain' },
      { op: 'U', label: 'Reject Change', command: 'detailReturns.reject', cap: 'DETAIL_RETURN_APPROVE' },
      { op: 'U', label: 'Clear Digital Signature Failure', command: 'detailReturns.clearSignatureFailure', cap: ['HBS_SCALE_ADMIN'], note: 'requires HBS_SCALE_ADMIN and the scale-site domain' },
      { op: 'D', label: 'Discard', command: 'detailReturns.discard', cap: 'DETAIL_RETURN_EDIT', note: DETAIL_BUTTONS },
    ],
  },
  {
    area: 'Scale Returns', page: 'Detail Returns bulk requests', path: '/scale-returns/detail-bulk', legacy: 'P560A/P561',
    ops: [
      { op: 'R', label: 'Open, search', command: 'detailReturns.search', cap: 'DETAIL_RETURN_EDIT' },
      { op: 'U', label: 'Release / Re-Edit / Hold / Update return data (selected)', command: 'detailReturns.bulk', cap: 'DETAIL_RETURN_EDIT', note: 'page also opens with DETAIL_RETURN_APPROVE' },
      { op: 'D', label: 'Discard (selected)', command: 'detailReturns.bulk', cap: 'DETAIL_RETURN_EDIT' },
    ],
  },
  {
    area: 'Scale Returns', page: 'Summary return segregations / notations / daily loads', path: '/scale-returns/summary-return-children', legacy: 'P053/P054',
    ops: [
      { op: 'U', label: 'Save segregations', command: 'summaryReturns.saveSegregations', cap: 'SUMMARY_RETURN_EDIT', note: 'only when the return allows update' },
      { op: 'U', label: 'Save notations', command: 'summaryReturns.saveNotations', cap: 'SUMMARY_RETURN_EDIT', note: 'only when the return allows update' },
      { op: 'U', label: 'Save daily loads', command: 'summaryReturns.saveDailyLoads', cap: 'SUMMARY_RETURN_EDIT', note: 'only when the return allows update' },
    ],
  },
  { area: 'Rating', page: 'Stumpage Rate', path: '/rating/stumpage-rate', legacy: 'P230/P231', ops: [{ op: 'R', label: 'Open, calculate', cap: 'RATING_VIEW' }] },
  {
    area: 'Profiles', page: 'Request a new scaler key', path: '/profiles/scaler-key-request', legacy: 'P362/P363',
    ops: [{ op: 'W', label: 'Submit key request', command: 'profiles.scalers.requestKey', cap: 'SCALER_PROFILE_EDIT', note: 'scaling admins for any licence; industry scalers only their own' }],
  },
  { area: 'Statement delivery', page: 'Statement / transmission download links', path: '/StatementDelivery/…', legacy: 'B2065', ops: [{ op: 'R', label: 'Download', cap: 'ANY_USER', note: 'client-scoped' }] },
];
for (const b of BESPOKE) pages.push({ ...b, menu: '', kind: 'bespoke' });

const publicPages = [
  { page: 'Public home', path: '/public' },
  { page: 'How to get access', path: '/public/access' },
  { page: 'Invoice Information', path: '/public/invoice-information' },
  ...PUBLIC_SCREENS.map((s) => ({ page: s.title, path: `/public/${s.id}`, legacy: s.legacy })),
];
for (const p of publicPages) pages.push({ area: 'Public (no login)', menu: '', kind: 'public', legacy: p.legacy ?? '', ...p, ops: [{ op: 'R', label: 'Open, search (no login)', cap: 'PUBLIC' }] });

// ── per-role cell ────────────────────────────────────────────────────
const mismatches = [];
const unresolved = new Set();
for (const p of pages) {
  for (const o of p.ops) {
    o.roles = rolesOf(o.cap);
    o.check = o.cap === 'PUBLIC' ? '' : backendCheck(o.command, o.cap, o.op);
    // The SPA shows the button / opens the page; the backend decides whether it runs.
    // Where they differ, only roles holding both can actually do it.
    const be = o.op !== 'R' && o.command && !Array.isArray(o.cap) ? backendCap(o.command) : null;
    if (o.op !== 'R' && o.command && !Array.isArray(o.cap) && !be) unresolved.add(o.command);
    if (be && be !== o.cap) {
      const beRoles = rolesOf(be);
      // Roles stack (a Rate Administrator also holds Ministry User), so the
      // backend's roles are the ones that can actually do it.
      o.roles = beRoles;
      o.check = `SPA ${o.cap} but backend ${be}: cells use the backend roles`;
      o.note = [o.note, `the SPA offers this to ${o.cap}; the backend only accepts ${be}`].filter(Boolean).join('; ');
      mismatches.push(`${p.path} — ${o.label} (${o.command}): SPA ${o.cap}, backend ${be}`);
    }
  }
  p.cell = Object.fromEntries(ROLES.map((r) => [r, OPS.filter((op) => p.ops.some((o) => o.op === op && o.roles.includes(r))).join('')]));
}

// ── xlsx writer (stored zip, inline strings, a few styles) ───────────
const esc = (s) => String(s ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
const colName = (i) => { let s = ''; for (i += 1; i; i = Math.floor((i - 1) / 26)) s = String.fromCharCode(65 + ((i - 1) % 26)) + s; return s; };
// style ids: 0 normal, 1 header, 2 header rotated, 3 R, 4 write/update, 5 delete, 6 wrap, 7 group header
const cellStyle = (v) => (!v ? 0 : v.includes('D') ? 5 : /[WU]/.test(v) ? 4 : 3);
const sheetXml = ({ rows, widths, freeze = { r: 1, c: 0 }, filter = true, headerStyle = () => 1, center = () => false, heights = {} }) => {
  const nCols = Math.max(...rows.map((r) => r.length));
  const cols = widths.map((w, i) => `<col min="${i + 1}" max="${i + 1}" width="${w}" customWidth="1"/>`).join('');
  const body = rows
    .map((r, ri) => {
      const ht = heights[ri] ? ` ht="${heights[ri]}" customHeight="1"` : '';
      const cells = r
        .map((v, ci) => {
          const ref = `${colName(ci)}${ri + 1}`;
          const st = ri === 0 ? headerStyle(ci) : typeof v === 'object' && v ? v.s : center(ci) ? cellStyle(v) : 6;
          const text = typeof v === 'object' && v ? v.v : v;
          return text === '' || text == null ? `<c r="${ref}" s="${st}"/>` : `<c r="${ref}" s="${st}" t="inlineStr"><is><t xml:space="preserve">${esc(text)}</t></is></c>`;
        })
        .join('');
      return `<row r="${ri + 1}"${ht}>${cells}</row>`;
    })
    .join('');
  const pane = freeze.r || freeze.c
    ? `<sheetViews><sheetView workbookViewId="0"><pane ${freeze.c ? `xSplit="${freeze.c}" ` : ''}ySplit="${freeze.r}" topLeftCell="${colName(freeze.c)}${freeze.r + 1}" activePane="bottomRight" state="frozen"/></sheetView></sheetViews>`
    : '';
  const af = filter ? `<autoFilter ref="A1:${colName(nCols - 1)}${rows.length}"/>` : '';
  return `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">${pane}<cols>${cols}</cols><sheetData>${body}</sheetData>${af}</worksheet>`;
};
const styles = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<fonts count="3"><font><sz val="10"/><name val="Arial"/></font><font><b/><sz val="10"/><color rgb="FFFFFFFF"/><name val="Arial"/></font><font><b/><sz val="10"/><name val="Arial"/></font></fonts>
<fills count="7"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill>
<fill><patternFill patternType="solid"><fgColor rgb="FF013366"/></patternFill></fill>
<fill><patternFill patternType="solid"><fgColor rgb="FFDCEFD9"/></patternFill></fill>
<fill><patternFill patternType="solid"><fgColor rgb="FFFFE8A3"/></patternFill></fill>
<fill><patternFill patternType="solid"><fgColor rgb="FFF6C4C0"/></patternFill></fill>
<fill><patternFill patternType="solid"><fgColor rgb="FFE7ECF3"/></patternFill></fill></fills>
<borders count="2"><border/><border><left style="thin"><color rgb="FFD8DCE2"/></left><right style="thin"><color rgb="FFD8DCE2"/></right><top style="thin"><color rgb="FFD8DCE2"/></top><bottom style="thin"><color rgb="FFD8DCE2"/></bottom></border></borders>
<cellStyleXfs count="1"><xf/></cellStyleXfs>
<cellXfs count="8">
<xf fontId="0" borderId="1" applyBorder="1"><alignment horizontal="center" vertical="top"/></xf>
<xf fontId="1" fillId="2" borderId="1" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment vertical="bottom" wrapText="1"/></xf>
<xf fontId="1" fillId="2" borderId="1" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="bottom" textRotation="90"/></xf>
<xf fontId="0" fillId="3" borderId="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="top"/></xf>
<xf fontId="2" fillId="4" borderId="1" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="top"/></xf>
<xf fontId="2" fillId="5" borderId="1" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="top"/></xf>
<xf fontId="0" borderId="1" applyBorder="1" applyAlignment="1"><alignment vertical="top" wrapText="1"/></xf>
<xf fontId="2" fillId="6" borderId="1" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment vertical="top" wrapText="1"/></xf>
</cellXfs></styleSheet>`;

const zip = (files) => {
  const local = [];
  const central = [];
  let offset = 0;
  for (const [name, content] of files) {
    const data = Buffer.from(content, 'utf-8');
    const nameBuf = Buffer.from(name, 'utf-8');
    const crc = crc32(data) >>> 0;
    const h = Buffer.alloc(30);
    h.writeUInt32LE(0x04034b50, 0); h.writeUInt16LE(20, 4); h.writeUInt16LE(0x0800, 6); h.writeUInt16LE(0, 8);
    h.writeUInt32LE(0, 10); h.writeUInt32LE(crc, 14); h.writeUInt32LE(data.length, 18); h.writeUInt32LE(data.length, 22);
    h.writeUInt16LE(nameBuf.length, 26); h.writeUInt16LE(0, 28);
    local.push(h, nameBuf, data);
    const c = Buffer.alloc(46);
    c.writeUInt32LE(0x02014b50, 0); c.writeUInt16LE(20, 4); c.writeUInt16LE(20, 6); c.writeUInt16LE(0x0800, 8); c.writeUInt16LE(0, 10);
    c.writeUInt32LE(0, 12); c.writeUInt32LE(crc, 16); c.writeUInt32LE(data.length, 20); c.writeUInt32LE(data.length, 24);
    c.writeUInt16LE(nameBuf.length, 28); c.writeUInt32LE(offset, 42);
    central.push(c, nameBuf);
    offset += h.length + nameBuf.length + data.length;
  }
  const cd = Buffer.concat(central);
  const end = Buffer.alloc(22);
  end.writeUInt32LE(0x06054b50, 0); end.writeUInt16LE(files.length, 8); end.writeUInt16LE(files.length, 10);
  end.writeUInt32LE(cd.length, 12); end.writeUInt32LE(offset, 16);
  return Buffer.concat([...local, cd, end]);
};

// Sheet 1: page × role
const FIXED = ['Area', 'Page', 'Menu item', 'Path', 'Legacy', 'Type'];
const matrixRows = [
  [...FIXED, ...ROLES.map((r) => r.replace(/^HBS_/, ''))],
  ...pages.map((p) => [p.area, p.page, p.menu, p.path, p.legacy, p.kind, ...ROLES.map((r) => p.cell[r])]),
];
// Sheet 2: one row per action
const actionRows = [
  ['Area', 'Page', 'Path', 'Action', 'Operation', 'Command / query', 'Capability', 'Roles', 'Backend check', 'Notes'],
  ...pages.flatMap((p) =>
    p.ops.map((o) => [p.area, p.page, p.path, o.label, OP_NAME[o.op], o.command ?? '', Array.isArray(o.cap) ? '(role check)' : o.cap, o.roles.map((r) => r.replace(/^HBS_/, '')).join(', '), o.check, o.note ?? '']),
  ),
];
// Sheet 3: role summary
const roleRows = [
  ['Role', 'Label', 'User type', 'Pages it can open (R)', 'Pages with W', 'Pages with U', 'Pages with D'],
  ...ROLES.map((r) => {
    const n = (op) => String(pages.filter((p) => p.cell[r].includes(op)).length);
    return [r, ROLE_LABELS[r], roleGroup(r), n('R'), n('W'), n('U'), n('D')];
  }),
];
// Sheet 4: capabilities
const capRows = [['Capability', 'Roles (backend Capability.java)'], ...Object.entries(CAPS).map(([c, rs]) => [c, rs.map((r) => r.replace(/^HBS_/, '')).join(', ')])];
// Sheet 5: notes
const rev = (() => { try { return execSync('git rev-parse --short HEAD', { cwd: ROOT }).toString().trim(); } catch { return ''; } })();
const notes = [
  ['HBS access matrix'],
  [`Generated ${new Date().toISOString().slice(0, 10)}${rev ? ` from revision ${rev}` : ''} by frontend/scripts/build-access-matrix.mjs (npm run access:matrix). Do not edit by hand.`],
  [''],
  ['R = READ: open the page, search, print and export.'],
  ['W = WRITE: create a new record (Add / Create / Copy / Submit).'],
  ['U = UPDATE: change an existing record, including state changes (hold, release, cancel, approve, generate).'],
  ['D = DELETE: delete, remove or discard a record.'],
  [''],
  ['Each cell is what that role grants ON ITS OWN. Roles stack: almost every functional role is held together with its base role (MOF_USER, CLI_USER or SPC_USER), which supplies READ. A functional-role column showing "U" without "R" means the role adds that update to whatever the base role lets the person open.'],
  ['A cell is the most a role can do. Per-record rules can narrow it: record state (only a Pending rule can be activated), data domains (districts, scale sites, client locations), industry users fenced to their own client, and per-button role checks on detail and summary returns (see the Notes column of the Actions sheet).'],
  ['Industry roles (CLI_*, SPC_*) are granted per client; their access is always limited to that client\'s data.'],
  ['HBS_LEGACY_BRIDGE is a service account with no pages and is left out.'],
  ['Operations are classified from the command id and button label; check the Actions sheet for any you disagree with.'],
  [''],
  ['Consistency checks at generation time:'],
  [capProblems.length ? `Capability differences backend vs SPA: ${capProblems.join(' | ')}` : 'Capability role sets: backend Capability.java and SPA access.ts agree.'],
  [mismatches.length ? `SPA vs backend capability differences (cells use the backend roles): ${mismatches.join(' | ')}` : 'Every action\'s SPA capability matches its backend command.'],
  [unresolved.size ? `Backend capability not machine-readable (built at runtime), cells use the SPA capability: ${[...unresolved].join(', ')}. The summaryReturns.* state commands were checked by hand against SummaryStateCommands.java (Nov 2026) and match.` : ''],
  ['Summary returns: the backend also filters each record\'s buttons by state and role (SummaryButtons.java). Detail returns: each button also checks scale-site / scaler-licence domains and document state (DetailAuthority.java).'],
  [`Commands whose backend file does not show the SPA capability: ${actionRows.filter((r) => /^check|NOT FOUND/.test(r[8])).length} (filter the Backend check column).`],
];

const sheets = [
  ['Page x role', sheetXml({ rows: matrixRows, widths: [16, 40, 30, 36, 12, 9, ...ROLES.map(() => 5.2)], freeze: { r: 1, c: 2 }, headerStyle: (c) => (c >= FIXED.length ? 2 : 1), center: (c) => c >= FIXED.length, heights: { 0: 150 } })],
  ['Actions', sheetXml({ rows: actionRows, widths: [16, 34, 30, 34, 14, 34, 26, 44, 26, 50], freeze: { r: 1, c: 0 } })],
  ['Roles', sheetXml({ rows: roleRows, widths: [24, 36, 22, 12, 12, 12, 12], freeze: { r: 1, c: 0 } })],
  ['Capabilities', sheetXml({ rows: capRows, widths: [32, 90], freeze: { r: 1, c: 0 } })],
  ['Notes', sheetXml({ rows: notes.map((r, i) => [i === 0 ? { v: r[0], s: 7 } : r[0]]), widths: [140], freeze: { r: 0, c: 0 }, filter: false, headerStyle: () => 7 })],
];
const writeWorkbook = (out, sheets, filtered = sheets.length - 1) => {
  const files = [
  ['[Content_Types].xml', `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>${sheets.map((_, i) => `<Override PartName="/xl/worksheets/sheet${i + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>`).join('')}</Types>`],
  ['_rels/.rels', `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>`],
  ['xl/workbook.xml', `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>${sheets.map(([n], i) => `<sheet name="${esc(n)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>`).join('')}</sheets>${`<definedNames>${sheets.slice(0, filtered).map((_, i) => `<definedName name="_xlnm._FilterDatabase" localSheetId="${i}" hidden="1">'${sheets[i][0]}'!$A$1:$A$1</definedName>`).join('')}</definedNames>`}</workbook>`],
  ['xl/_rels/workbook.xml.rels', `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">${sheets.map((_, i) => `<Relationship Id="rId${i + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${i + 1}.xml"/>`).join('')}<Relationship Id="rId${sheets.length + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>`],
  ['xl/styles.xml', styles],
  ...sheets.map(([, xml], i) => [`xl/worksheets/sheet${i + 1}.xml`, xml]),
];
  writeFileSync(out, zip(files));
};
writeWorkbook(OUT_XLSX, sheets);

const csvCell = (v) => (/[",\n]/.test(String(v)) ? `"${String(v).replace(/"/g, '""')}"` : String(v ?? ''));
writeFileSync(OUT_CSV, `${matrixRows.map((r) => r.map(csvCell).join(',')).join('\n')}\n`);

console.log(`Wrote ${path.relative(process.cwd(), OUT_XLSX)} and ${path.relative(process.cwd(), OUT_CSV)}: ${pages.length} pages × ${ROLES.length} roles, ${actionRows.length - 1} actions.`);
if (unresolved.size) console.log(`Commands whose backend capability could not be read (cells use the SPA capability): ${[...unresolved].join(', ')}`);
if (mismatches.length) console.log(`SPA vs backend capability differences:\n  ${mismatches.join('\n  ')}`);
if (capProblems.length) console.log(`Capability differences:\n  ${capProblems.join('\n  ')}`);
const flagged = actionRows.filter((r) => /^check|NOT FOUND/.test(r[8]));
if (flagged.length) console.log(`Backend checks to review (${flagged.length}):\n  ${flagged.map((r) => `${r[5]} [${r[6]}] ${r[8]}`).join('\n  ')}`);

// ── proposed role layout (docs/role-rationalization.md) ──────────────
// Each proposed role grants what the legacy roles it replaces granted, plus the
// read access of its user type (no separate base roles). The mapping is the
// "Replaces" column of the proposal tables, so that page stays the one source.
const OUT_PROPOSED_XLSX = path.join(ROOT, 'docs', 'access-matrix-proposed.xlsx');
const OUT_PROPOSED_CSV = path.join(ROOT, 'docs', 'access-matrix-proposed.csv');
const proposalMd = readFileSync(path.join(ROOT, 'docs', 'role-rationalization.md'), 'utf-8');
const proposalPart = proposalMd.slice(proposalMd.indexOf('## Proposed roles'), proposalMd.indexOf('### Further cuts'));
const BASE_READ = { 'Ministry (IDIR)': 'HBS_MOF_USER', 'Industry (BCeID)': 'HBS_CLI_USER' };
/**
 * Capabilities a proposed role deliberately does NOT inherit from a role it
 * replaces (the proposal's "Does" column moves them elsewhere).
 */
const EXCLUDE = {
  // MOF_MGR's user-data-domain admin stays with HBS_USER_ADMIN (all 9 MOF_MGR holders also hold MOF_ADMIN).
  HBS_SYSTEM_MANAGER: ['USER_ADMIN'],
};
const NEW_ROLES = [];
let group = '';
for (const line of proposalPart.split('\n')) {
  const h = line.match(/^### (Ministry \(IDIR\)|Industry \(BCeID[^)]*\)|Service account)/);
  if (h) group = h[1].startsWith('Ministry') ? 'Ministry (IDIR)' : h[1].startsWith('Industry') ? 'Industry (BCeID)' : 'Service';
  const m = line.match(/^\| `(HBS_[A-Z_]+)` \| ([^|]+) \| ([^|]+) \|/);
  if (!m || group === 'Service') continue;
  const replaces = [...m[2].matchAll(/`([A-Z_]+)`/g)].map((x) => `HBS_${x[1]}`);
  for (const r of replaces) if (!ROLES.includes(r)) throw new Error(`role-rationalization.md: ${m[1]} replaces unknown role ${r}`);
  // A role that replaces a base role (VIEWER, CLIENT_VIEWER, SUBMISSION_AGENT ← SPC_USER) reads what
  // that base role reads; the others add their user type's base read.
  const base = replaces.some((r) => /^HBS_(MOF|CLI|SPC)_USER$/.test(r)) ? [] : [BASE_READ[group]];
  NEW_ROLES.push({ role: m[1], group, replaces, does: m[3].trim(), grants: [...new Set([...replaces, ...base])], exclude: EXCLUDE[m[1]] ?? [] });
}
const mapped = new Set(NEW_ROLES.flatMap((n) => n.replaces));
const unmapped = ROLES.filter((r) => !mapped.has(r));
if (unmapped.length) throw new Error(`role-rationalization.md: no proposed role replaces ${unmapped.join(', ')}`);

const newCell = (p, n) =>
  OPS.filter((op) => p.ops.some((o) => o.op === op && !n.exclude.includes(o.cap) && o.roles.some((r) => n.grants.includes(r)))).join('');
const baseOf = (r) => (/^HBS_CLI_/.test(r) ? 'HBS_CLI_USER' : /^HBS_SPC_/.test(r) ? 'HBS_SPC_USER' : 'HBS_MOF_USER');
/** What a person holding legacy role r (with its base role) can do on page p today. */
const legacyHolderCell = (p, r) => OPS.filter((op) => [...p.cell[r], ...p.cell[baseOf(r)]].includes(op)).join('');

const pMatrix = [
  [...FIXED, ...NEW_ROLES.map((n) => n.role.replace(/^HBS_/, ''))],
  ...pages.map((p) => [p.area, p.page, p.menu, p.path, p.legacy, p.kind, ...NEW_ROLES.map((n) => newCell(p, n))]),
];
const pMapping = [
  ['Proposed role', 'User type', 'Replaces (legacy)', 'Does', 'Not inherited', 'Pages R', 'Pages W', 'Pages U', 'Pages D'],
  ...NEW_ROLES.map((n) => {
    const count = (op) => String(pages.filter((p) => newCell(p, n).includes(op)).length);
    return [n.role, n.group, n.replaces.map((r) => r.replace(/^HBS_/, '')).join(', '), n.does, n.exclude.join(', '), count('R'), count('W'), count('U'), count('D')];
  }),
];
// What each legacy role's holders gain (or lose) by moving to their proposed role.
const pChanges = [['Legacy role', 'Moves to', 'Area', 'Page', 'Path', 'Today (with base role)', 'Proposed', 'Gains', 'Loses']];
for (const n of NEW_ROLES) {
  for (const r of n.replaces) {
    for (const p of pages) {
      if (p.kind === 'public') continue;
      const before = legacyHolderCell(p, r);
      const after = newCell(p, n);
      const gains = [...after].filter((c) => !before.includes(c)).join('');
      const loses = [...before].filter((c) => !after.includes(c)).join('');
      if (gains || loses) pChanges.push([r.replace(/^HBS_/, ''), n.role.replace(/^HBS_/, ''), p.area, p.page, p.path, before, after, gains, loses]);
    }
  }
}
const pNotes = [
  ['HBS access matrix: proposed role layout'],
  [`Generated ${new Date().toISOString().slice(0, 10)}${rev ? ` from revision ${rev}` : ''} by frontend/scripts/build-access-matrix.mjs from today's code and the proposal in docs/role-rationalization.md. PROPOSAL ONLY: the app still uses the legacy roles (see access-matrix.xlsx).`],
  [''],
  ['R = READ, W = WRITE (create), U = UPDATE (incl. state changes), D = DELETE, as in access-matrix.xlsx.'],
  ['Each proposed role = everything the legacy roles in its "Replaces" column grant today, plus read access for its user type (ministry roles include Ministry User; industry roles include Industry User; Submission Agent keeps Other Industry User read). So unlike the legacy matrix, every cell already includes the read access.'],
  ['Client Administrator also replaces SPC_ADMIN (other-industry administrators, 0 holders today): with Industry User read it would show an agent organization\'s administrator the forest-client views, which the Changes sheet lists as gains.'],
  [`Not inherited: ${Object.entries(EXCLUDE).map(([k, v]) => `${k} does not take ${v.join(', ')}`).join('; ') || 'none'}.`],
  ['"Changes" lists, for a person holding each legacy role (plus its base role) today, every page where the proposed role gives them more (Gains) or less (Loses). Public pages are left out. People holding several legacy roles that map to different proposed roles keep them all, so their access is the union.'],
  ['Per-record rules are unchanged by the proposal: record state, data domains, client fence, and per-button role checks on detail and summary returns (DetailAuthority / SummaryButtons name legacy roles and must be re-pointed when the roles change; see role-rationalization.md "What changes in code").'],
  ['Industry roles are granted per client in FAM; the client fence applies to every industry column.'],
];
writeWorkbook(OUT_PROPOSED_XLSX, [
  ['Page x proposed role', sheetXml({ rows: pMatrix, widths: [16, 40, 30, 36, 12, 9, ...NEW_ROLES.map(() => 5.2)], freeze: { r: 1, c: 2 }, headerStyle: (c) => (c >= FIXED.length ? 2 : 1), center: (c) => c >= FIXED.length, heights: { 0: 150 } })],
  ['Roles', sheetXml({ rows: pMapping, widths: [26, 18, 44, 60, 16, 9, 9, 9, 9], freeze: { r: 1, c: 0 } })],
  ['Changes', sheetXml({ rows: pChanges.map((r, i) => (i === 0 ? r : [...r.slice(0, 5), ...r.slice(5).map((v, k) => (k >= 2 && v ? { v, s: k === 2 ? 4 : 5 } : v))])), widths: [18, 22, 16, 40, 36, 14, 12, 10, 10], freeze: { r: 1, c: 0 }, center: (c) => c >= 5 })],
  ['Notes', sheetXml({ rows: pNotes.map((r, i) => [i === 0 ? { v: r[0], s: 7 } : r[0]]), widths: [140], freeze: { r: 0, c: 0 }, filter: false, headerStyle: () => 7 })],
]);
writeFileSync(OUT_PROPOSED_CSV, `${pMatrix.map((r) => r.map(csvCell).join(',')).join('\n')}\n`);
console.log(`Wrote ${path.relative(process.cwd(), OUT_PROPOSED_XLSX)} and ${path.relative(process.cwd(), OUT_PROPOSED_CSV)}: ${pages.length} pages × ${NEW_ROLES.length} proposed roles, ${pChanges.length - 1} changes for current holders.`);
