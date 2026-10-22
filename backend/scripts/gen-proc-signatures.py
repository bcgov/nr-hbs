#!/usr/bin/env python3
"""Extract the HBS_* procedure/function signatures from nr-mof-db.

Writes src/main/resources/db/hbs-proc-signatures.json: proc name ->
{"kind": "procedure"|"function", "source": file, "params": [{"name", "mode", "type"}]}.
Package members (HBS_RATES.RETRIEVE_NORMAL_RATE, …) come from the package
specs, named PACKAGE.MEMBER; overloaded members keep the first declaration.
Procs this app has asked the DBA for (docs/db/*.sql) are included with
"proposed": true until nr-mof-db has them.
`TableApi` orders call arguments by these names, and `ProcSignatureTest`
fails the build when a ported workflow calls a proc or parameter that
doesn't exist. Re-run after nr-mof-db changes:

    python3 scripts/gen-proc-signatures.py ../../nr-mof-db
"""
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else "../../nr-mof-db") / "scripts" / "THE"
OUT = pathlib.Path(__file__).resolve().parent.parent / "src/main/resources/db/hbs-proc-signatures.json"

HEADER = re.compile(
    r'CREATE\s+OR\s+REPLACE\s+(?:EDITIONABLE\s+|NONEDITIONABLE\s+)?(PROCEDURE|FUNCTION)\s+'
    r'(?:"?THE"?\.)?"?([A-Z0-9_$#]+)"?\s*(\(|RETURN|IS|AS)', re.I)
PARAM = re.compile(r'^\s*"?([A-Za-z0-9_$#]+)"?\s+(IN\s+OUT|OUT|IN)?\s*(?:NOCOPY\s+)?([A-Za-z0-9_$#.%]+(?:\s*\([^)]*\))?)', re.I)
VERSION = re.compile(r'^V(\d+)\.(\d+)__')


def strip_comments(text):
    text = re.sub(r'/\*.*?\*/', ' ', text, flags=re.S)
    return re.sub(r'--[^\n]*', ' ', text)


def param_block(text, start):
    """Text between the '(' at start and its matching ')'."""
    depth = 0
    for i in range(start, len(text)):
        if text[i] == '(':
            depth += 1
        elif text[i] == ')':
            depth -= 1
            if depth == 0:
                return text[start + 1:i]
    return ''


def split_params(block):
    parts, depth, cur = [], 0, ''
    for ch in block:
        if ch == '(':
            depth += 1
        elif ch == ')':
            depth -= 1
        if ch == ',' and depth == 0:
            parts.append(cur)
            cur = ''
        else:
            cur += ch
    if cur.strip():
        parts.append(cur)
    return parts


def parse(path, text=None):
    text = strip_comments(text if text is not None else path.read_text(encoding='utf-8', errors='replace'))
    m = HEADER.search(text)
    if not m:
        return None
    kind, name, after = m.group(1).lower(), m.group(2).upper(), m.group(3)
    params = []
    if after == '(':
        for raw in split_params(param_block(text, m.end() - 1)):
            pm = PARAM.match(raw.strip())
            if not pm:
                raise ValueError(f'{path.name}: cannot parse parameter {raw.strip()!r}')
            mode = re.sub(r'\s+', ' ', (pm.group(2) or 'IN').upper())
            params.append({'name': pm.group(1).lower(), 'mode': mode, 'type': pm.group(3).upper()})
    return name, {'kind': kind, 'source': path.name, 'params': params}


MEMBER = re.compile(r'\b(PROCEDURE|FUNCTION)\s+"?([A-Za-z0-9_$#]+)"?\s*(\(|RETURN|;|IS|AS)', re.I)
SPEC = re.compile(r'CREATE\s+OR\s+REPLACE\s+(?:EDITIONABLE\s+|NONEDITIONABLE\s+)?PACKAGE\s+(?!BODY)(?:"?THE"?\.)?"?([A-Z0-9_$#]+)"?', re.I)


def parse_package(path):
    """Public members of a package spec as PACKAGE.MEMBER signatures."""
    text = strip_comments(path.read_text(encoding='utf-8', errors='replace'))
    m = SPEC.search(text)
    if not m:
        return {}
    package = m.group(1).upper()
    out = {}
    for mm in MEMBER.finditer(text, m.end()):
        kind, member, after = mm.group(1).lower(), mm.group(2).upper(), mm.group(3)
        name = f'{package}.{member}'
        if name in out:
            continue
        params = []
        if after == '(':
            try:
                for raw in split_params(param_block(text, mm.end() - 1)):
                    pm = PARAM.match(raw.strip())
                    if not pm:
                        raise ValueError(raw)
                    mode = re.sub(r'\s+', ' ', (pm.group(2) or 'IN').upper())
                    params.append({'name': pm.group(1).lower(), 'mode': mode, 'type': pm.group(3).upper()})
            except ValueError:
                continue  # record/collection-typed members aren't callable over JDBC anyway
        out[name] = {'kind': kind, 'source': path.name, 'params': params}
    return out


def version(path):
    m = VERSION.match(path.name)
    return (int(m.group(1)), int(m.group(2))) if m else (0, 0)


def main():
    found = {}
    for folder in ('PROCEDURES', 'FUNCTIONS'):
        for path in sorted((ROOT / folder).glob('*.sql'), key=version):
            if '__HBS' not in path.name.upper():
                continue
            parsed = parse(path)
            if parsed:
                found[parsed[0]] = parsed[1]  # later versions replace earlier ones
    for path in sorted((ROOT / 'PACKAGES').glob('*.sql'), key=version):
        if '__HBS' in path.name.upper():
            found.update(parse_package(path))
    proposed_dir = pathlib.Path(__file__).resolve().parent.parent.parent / 'docs' / 'db'
    for path in sorted(proposed_dir.glob('*.sql')):
        # A proposal file can hold several objects; split on each CREATE.
        text = path.read_text(encoding='utf-8', errors='replace')
        for chunk in re.split(r'(?=CREATE\s+OR\s+REPLACE)', text, flags=re.I):
            parsed = parse(path, chunk)
            if parsed and parsed[0] not in found:
                parsed[1]['proposed'] = True
                found[parsed[0]] = parsed[1]
    OUT.parent.mkdir(parents=True, exist_ok=True)
    # One proc per line keeps diffs readable after an nr-mof-db change.
    lines = [f' {json.dumps(k)}: {json.dumps(v, separators=(",", ":"))}' for k, v in sorted(found.items())]
    OUT.write_text('{\n' + ',\n'.join(lines) + '\n}\n', encoding='utf-8')
    print(f'{len(found)} signatures -> {OUT}')


if __name__ == '__main__':
    main()
