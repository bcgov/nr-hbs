#!/usr/bin/env python3
# Re-vendors the legacy JCRS HBS report units into the backend (see docs/reports.md).
# Usage (from repo root): python3 backend/scripts/vendor-jcrs-reports.py [JCRS_HBS_EXPORT_DIR] [DEST_DIR]
import os, re, shutil, glob, json, html
import sys
SRC=sys.argv[1] if len(sys.argv) > 1 else '../hba-archive/hbs/trunk/source/jasper-server/src/main/resources/resources/JCRS/HBS'
DST=sys.argv[2] if len(sys.argv) > 2 else 'backend/src/main/resources/reports/hbs'
os.makedirs(DST+'/_images', exist_ok=True)
for img in ['hbs_logo','hbs_logo1.jpg','hbs_logo2.jpg','hbs_logo3.jpg']:
    shutil.copy(f'{SRC}/Images/{img}.data', f'{DST}/_images/{img}')
catalog=[]
for unit in sorted(glob.glob(f'{SRC}/Reports/HBS/*.rpt.xml')):
    name=os.path.basename(unit)[:-len('.rpt.xml')]
    meta=open(unit,encoding='utf-8',errors='replace').read()
    label=re.search(r'<label>(.*?)</label>',meta,re.S)
    label=html.unescape(label.group(1).strip()) if label else name
    controls=[c.split('/')[-1] for c in re.findall(r'<uri>(/JCRS/HBS/Input_Controls/[^<]+)</uri>',meta)]
    files=glob.glob(f'{SRC}/Reports/HBS/{name}.rpt_files/*.data')
    if not files: continue
    out=f'{DST}/{name}'; os.makedirs(out,exist_ok=True)
    main=None; proc=None; subs=[]
    for f in files:
        b=os.path.basename(f)[:-len('.data')]
        if b==f'{name}.rpt_jrxml': target=f'{name}.jrxml'; ismain=True
        elif b.endswith('.jrxml'): target=b; ismain=False
        else:
            shutil.copy(f, f'{out}/{b}'); continue
        s=open(f,encoding='utf-8',errors='replace').read()
        s=s.replace('class="com.jaspersoft.jasperserver.api.metadata.user.domain.User"','class="java.lang.Object"')
        # JCRS bundled commons-lang 2; the backend ships commons-lang3.
        s=s.replace('org.apache.commons.lang.StringUtils','org.apache.commons.lang3.StringUtils')
        open(f'{out}/{target}','w',encoding='utf-8').write(s)
        if ismain:
            main=target
            m=re.search(r'\{\s*call\s+([A-Za-z0-9_]+)',s,re.I); proc=m.group(1).upper() if m else None
        else: subs.append(target[:-6])
    catalog.append({'id':name,'title':label,'procedure':proc,'parameters':controls,'subreports':sorted(subs)})
json.dump(catalog,open(f'{DST}/catalog.json','w'),indent=2)
print(len(catalog),'report units')
