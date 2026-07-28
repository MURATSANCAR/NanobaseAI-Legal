#!/usr/bin/env python3
"""Point portal hub /contracts SPA routes at SpecAI Legal (/legal/)."""
from pathlib import Path
import sys

index = Path(sys.argv[1] if len(sys.argv) > 1 else "/data/nanobaseai-mobile/portal/dist/assets")
if index.is_dir():
    files = sorted(index.glob("index-*.js"))
    if not files:
        raise SystemExit(f"no index-*.js in {index}")
    path = files[0]
else:
    path = index

t = path.read_text()
redir = 't.jsx(function(){return window.location.replace("/legal/"),null},{})'
old = (
    't.jsx(y,{path:"contracts",element:t.jsx(Te,{})}),'
    't.jsx(y,{path:"contracts/projects",element:t.jsx(Te,{})}),'
    't.jsx(y,{path:"contracts/upload",element:t.jsx(Te,{})}),'
    't.jsx(y,{path:"contracts/documents/:documentId",element:t.jsx(Yt,{})})'
)
new = (
    f't.jsx(y,{{path:"contracts",element:{redir}}}),'
    f't.jsx(y,{{path:"contracts/projects",element:{redir}}}),'
    f't.jsx(y,{{path:"contracts/upload",element:{redir}}}),'
    f't.jsx(y,{{path:"contracts/documents/:documentId",element:{redir}}})'
)

if "window.location.replace(\"/legal/\")" in t and 'path:"contracts",element:t.jsx(Te,{})' not in t:
    print(f"already patched: {path}")
    raise SystemExit(0)

if old not in t:
    raise SystemExit(f"route block not found in {path}")

path.write_text(t.replace(old, new, 1))
print(f"patched contracts routes → /legal/ in {path}")
