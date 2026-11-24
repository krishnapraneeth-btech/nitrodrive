#!/usr/bin/env python3
"""Normalize Java declarations: one declarator per statement, `Type[] name` style.
Keeps the source valid Java, just friendlier to the JS transpiler."""
import re, sys, glob

DECL = re.compile(
    r'^(\s*)((?:(?:public|private|protected|static|final|volatile|transient)\s+)*)'
    r'((?:float|double|int|long|boolean|char|byte|short|String)(?:\[\])*)\s+(.*)$')

def strip_empty(s):
    return '\n'.join(l for l in s.split('\n'))

def normalize(text):
    # Type name[] -> Type[] name
    text = re.sub(r'\b(float|double|int|long|boolean|char|byte|short|String)\s+(\w+)\s*\[\]',
                  r'\1[] \2', text)
    lines = text.split('\n')
    out = []
    i = 0
    n = len(lines)
    depth = 0
    depths = []
    for ln in lines:
        depths.append(depth)
        depth += ln.count('(') - ln.count(')')
    while i < n:
        line = lines[i]
        m = DECL.match(line) if depths[i] == 0 else None
        if m:
            indent, mods, typ, rest = m.groups()
            # gather continuation lines until ';' (max 6 lines) if the statement is incomplete
            stmt = rest
            j = i
            while (';' not in stmt) and j + 1 < n and (j - i) < 6 and (lines[j].rstrip().endswith(',') or lines[j].rstrip().endswith('=')):
                j += 1
                stmt += ' ' + lines[j].strip()
            if ';' not in stmt:
                out.append(line)
                i += 1
                continue
            if ';' in stmt:
                body = stmt.split(';', 1)[0].strip()
                # avoid matching things like "int x = foo(a, b)" where commas are in parens
                parts, cur, d = [], '', 0
                for ch in body:
                    if ch in '([{':
                        d += 1
                    elif ch in ')]}':
                        d -= 1
                    if ch == ',' and d == 0:
                        parts.append(cur)
                        cur = ''
                    else:
                        cur += ch
                parts.append(cur)
                if len(parts) > 1:
                    after = stmt.split(';', 1)[1]
                    for p in parts:
                        p = p.strip()
                        if p:
                            out.append('%s%s%s %s;' % (indent, mods, typ, p))
                    if after.strip():
                        out.append(indent + after.strip())
                    i = j + 1
                    continue
        out.append(line)
        i += 1
    return '\n'.join(out)

if __name__ == '__main__':
    for f in glob.glob(sys.argv[1]):
        s = open(f, encoding='utf-8').read()
        t = normalize(s)
        if t != s:
            open(f, 'w', encoding='utf-8').write(t)
            print('normalized', f)
