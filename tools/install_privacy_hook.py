"""Install a local pre-commit scan without committing machine-specific hook paths."""
from pathlib import Path
import shlex,subprocess,sys
ROOT=Path(__file__).resolve().parents[1]
gitdir=subprocess.run(['git','rev-parse','--git-dir'],cwd=ROOT,check=True,capture_output=True,text=True).stdout.strip()
hooks=(ROOT/gitdir/'hooks');hooks.mkdir(parents=True,exist_ok=True)
denied=' --denylist private/privacy.local.json'if(ROOT/'private/privacy.local.json').exists()else''
text='#!/bin/sh\nexec '+shlex.quote(sys.executable.replace('\\','/'))+' tools/privacy_scan.py'+denied+'\n'
path=hooks/'pre-commit';path.write_text(text,encoding='utf-8');path.chmod(0o755)
print('Local pre-commit privacy scan installed.')
