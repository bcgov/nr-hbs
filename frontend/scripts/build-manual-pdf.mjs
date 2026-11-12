// Builds the user manual PDF from docs/manual/*.md (and docs/manual/images/).
//
//   cd frontend && npm run manual:pdf
//
// Output: docs/manual/HBS-User-Manual.pdf. Uses marked for Markdown and the
// Playwright Chromium the e2e tests already install (`npx playwright install
// chromium`). Screenshots come from e2e/manual.screenshots.spec.ts.
import { execSync } from 'node:child_process';
import { mkdtempSync, readdirSync, readFileSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

import { chromium } from '@playwright/test';
import { Marked } from 'marked';

const MANUAL = path.resolve(import.meta.dirname, '..', '..', 'docs', 'manual');
const OUT = path.join(MANUAL, 'HBS-User-Manual.pdf');

const chapters = readdirSync(MANUAL)
  .filter((f) => /^\d\d-.+\.md$/.test(f))
  .sort();

const slug = (text) =>
  text
    .toLowerCase()
    .replace(/<[^>]+>/g, '')
    .replace(/&[a-z]+;|&#\d+;/g, '')
    .replace(/[^\p{L}\p{N}\s-]/gu, '')
    .trim()
    .replace(/\s+/g, '-');

const escapeHtml = (s) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');

/** Markdown → HTML for one chapter; heading ids and links are made unique across the book. */
const render = (markdown, chapterId) => {
  const marked = new Marked({
    gfm: true,
    renderer: {
      heading({ tokens, depth, text }) {
        const inner = this.parser.parseInline(tokens);
        const id = depth === 1 ? chapterId : `${chapterId}--${slug(text)}`;
        return `<h${depth} id="${id}">${inner}</h${depth}>\n`;
      },
      link({ href, tokens }) {
        const inner = this.parser.parseInline(tokens);
        if (/^https?:|^mailto:/.test(href)) return `<a href="${href}">${inner}</a>`;
        const [file, anchor] = href.split('#');
        if (!file) return `<a href="#${chapterId}--${anchor}">${inner}</a>`;
        const target = path.basename(file, '.md');
        if (!chapters.includes(`${target}.md`)) return inner; // developer docs aren't in the book
        return `<a href="#${anchor ? `${target}--${anchor}` : target}">${inner}</a>`;
      },
      image({ href, text }) {
        const src = pathToFileURL(path.join(MANUAL, href)).href;
        return `<figure><img src="${src}" alt="${escapeHtml(text)}"><figcaption>${escapeHtml(text)}</figcaption></figure>`;
      },
    },
  });
  return marked.parse(markdown);
};

/** The README's "How to read this manual" section is the book's introduction. */
const readme = readFileSync(path.join(MANUAL, 'README.md'), 'utf-8');
const howToRead = readme.match(/## How to read this manual\n([\s\S]*?)(?=\n## )/)?.[1] ?? '';
const intro = render(
  `# About this manual\n\n${readme.split('\n## Contents')[0].replace(/^# .*\n/, '').replace(/\[([^\]]+)\]\([^)]*\)/g, '$1')}\n\n## How to read this manual\n${howToRead}`,
  'about',
);

const body = chapters
  .map((f) => {
    const id = path.basename(f, '.md');
    return `<section class="chapter">${render(readFileSync(path.join(MANUAL, f), 'utf-8'), id)}</section>`;
  })
  .join('\n');

const toc = chapters
  .map((f) => {
    const id = path.basename(f, '.md');
    const title = readFileSync(path.join(MANUAL, f), 'utf-8').match(/^# (.+)$/m)?.[1] ?? id;
    const [, num, name] = title.match(/^(\d+)\.\s*(.*)$/) ?? [, '', title];
    return `<li><a href="#${id}"><span class="toc-num">${num}</span><span class="toc-name">${escapeHtml(name)}</span></a></li>`;
  })
  .join('\n');

let revision = '';
try {
  revision = execSync('git rev-parse --short HEAD', { cwd: MANUAL }).toString().trim();
} catch {
  /* not a git checkout */
}
const date = new Date().toLocaleDateString('en-CA', { year: 'numeric', month: 'long', day: 'numeric' });

const css = `
  @page { size: Letter; margin: 22mm 18mm 20mm 18mm; }
  @page :first { margin: 0; }
  :root { --blue: #013366; --blue-2: #1e5189; --gold: #fcba19; --ink: #2d2d2d; --muted: #5b6370; --rule: #d8dce2; --tint: #f3f6fa; }
  * { box-sizing: border-box; }
  html { font-family: Arial, "Helvetica Neue", Helvetica, sans-serif; font-size: 10.5pt; color: var(--ink); line-height: 1.5; }
  body { margin: 0; }
  a { color: var(--blue-2); text-decoration: none; }

  .cover { height: 279.4mm; width: 215.9mm; position: relative; background: var(--blue); color: #fff; page-break-after: always; overflow: hidden; }
  .cover::before { content: ""; position: absolute; left: 0; right: 0; top: 0; height: 9mm; background: var(--gold); }
  .cover::after { content: ""; position: absolute; right: -60mm; bottom: -60mm; width: 170mm; height: 170mm; border-radius: 50%; border: 18mm solid rgba(255,255,255,.06); }
  .cover .inner { position: absolute; left: 22mm; right: 22mm; top: 70mm; }
  .cover .kicker { font-size: 11pt; letter-spacing: .18em; text-transform: uppercase; color: var(--gold); font-weight: 700; }
  .cover h1 { font-size: 38pt; line-height: 1.1; margin: 6mm 0 4mm; padding: 0; border: 0; color: #fff; font-weight: 700; }
  .cover .sub { font-size: 18pt; font-weight: 300; opacity: .92; }
  .cover .bar { width: 28mm; height: 2mm; background: var(--gold); margin: 12mm 0; }
  .cover .meta { font-size: 10pt; opacity: .85; line-height: 1.8; }
  .cover .foot { position: absolute; left: 22mm; bottom: 20mm; font-size: 9.5pt; opacity: .8; }

  .toc { page-break-after: always; }
  .toc h1 { border: 0; }
  .toc ol { list-style: none; padding: 0; margin: 8mm 0 0; }
  .toc li { border-bottom: 1px solid var(--rule); }
  .toc li a { display: flex; gap: 6mm; padding: 3.2mm 0; color: var(--ink); font-size: 12pt; }
  .toc-num { width: 9mm; color: var(--blue-2); font-weight: 700; text-align: right; }

  .intro { page-break-after: always; }
  .chapter { page-break-before: always; }

  h1 { font-size: 24pt; color: var(--blue); margin: 0 0 6mm; padding-bottom: 3mm; border-bottom: 2.5px solid var(--gold); line-height: 1.2; }
  h2 { font-size: 15pt; color: var(--blue); margin: 9mm 0 3mm; page-break-after: avoid; }
  h3 { font-size: 12pt; color: var(--blue-2); margin: 6mm 0 2mm; page-break-after: avoid; }
  p, ul, ol { margin: 0 0 3mm; }
  li { margin-bottom: 1mm; }
  strong { color: #1a1a1a; }
  code { font-family: "SFMono-Regular", Menlo, Consolas, monospace; font-size: 9pt; background: var(--tint); padding: .3mm 1.2mm; border-radius: 1mm; }

  table { width: 100%; border-collapse: collapse; margin: 3mm 0 5mm; font-size: 9.3pt; page-break-inside: auto; }
  thead { display: table-header-group; }
  tr { page-break-inside: avoid; }
  th { background: var(--blue); color: #fff; text-align: left; padding: 2mm 2.5mm; font-weight: 700; }
  td { padding: 2mm 2.5mm; border-bottom: 1px solid var(--rule); vertical-align: top; }
  tbody tr:nth-child(even) td { background: var(--tint); }
  td:first-child { width: 34%; }

  figure { margin: 5mm 0 6mm; page-break-inside: avoid; text-align: center; }
  figure img { max-width: 88%; max-height: 100mm; border: 1px solid var(--rule); border-radius: 1.5mm; box-shadow: 0 1mm 3mm rgba(1,51,102,.12); }
  figcaption { font-size: 8.8pt; color: var(--muted); margin-top: 2mm; font-style: italic; }
`;

const html = `<!doctype html><html lang="en"><head><meta charset="utf-8"><title>HBS User Manual</title><style>${css}</style></head><body>
<section class="cover"><div class="inner">
  <div class="kicker">Ministry of Forests</div>
  <h1>Harvest Billing System</h1>
  <div class="sub">User Manual</div>
  <div class="bar"></div>
  <div class="meta">${date}${revision ? `<br>Revision ${revision}` : ''}</div>
</div><div class="foot">For ministry (IDIR) and industry (Business BCeID) users</div></section>
<section class="toc"><h1>Contents</h1><ol>${toc}</ol></section>
<section class="intro">${intro}</section>
${body}
</body></html>`;

const dir = mkdtempSync(path.join(tmpdir(), 'hbs-manual-'));
const htmlPath = path.join(dir, 'manual.html');
writeFileSync(htmlPath, html);

const browser = await chromium.launch();
try {
  const page = await browser.newPage();
  await page.goto(pathToFileURL(htmlPath).href, { waitUntil: 'load' });
  await page.pdf({
    path: OUT,
    format: 'Letter',
    printBackground: true,
    preferCSSPageSize: true,
    displayHeaderFooter: true,
    headerTemplate: '<span></span>',
    footerTemplate: `<div style="width:100%;font-family:Arial,sans-serif;font-size:7.5pt;color:#5b6370;padding:0 18mm;display:flex;justify-content:space-between">
      <span>Harvest Billing System — User Manual</span><span><span class="pageNumber"></span> / <span class="totalPages"></span></span></div>`,
  });
} finally {
  await browser.close();
}
console.log(`Wrote ${path.relative(process.cwd(), OUT)}`);
