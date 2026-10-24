"""Scan tracked and non-ignored source plus optional release artifacts; never print values."""
from pathlib import Path
import argparse,json,re,subprocess,zipfile
ROOT=Path(__file__).resolve().parents[1]
SYNTHETIC_MACS={'02:00:00:00:00:01','02:00:00:00:00:02'}
RULES={
    'private-user-path':re.compile(rb'(?i)[A-Z]:[\\/]+Users[\\/]+[^\\/\s\"\x27]+'),
    'private-email':re.compile(rb'[\w.+-]+@(?:qq|gmail|163|outlook)\.com',re.I),
    'access-token':re.compile(rb'(?:gh[pousr]_[A-Za-z0-9_]{20,}|github_pat_[A-Za-z0-9_]{20,}|sk-[A-Za-z0-9]{20,})'),
    'device-mac':re.compile(rb'\b(?:[0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}\b'),
    'private-lan':re.compile(rb'\b(?:192\.168\.\d+\.\d+|10\.\d+\.\d+\.\d+|172\.(?:1[6-9]|2\d|3[01])\.\d+\.\d+)\b'),
}
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--artifact',action='append',default=[]);parser.add_argument('--denylist');args=parser.parse_args()
    result=subprocess.run(['git','ls-files','--cached','--others','--exclude-standard','-z'],cwd=ROOT,check=True,capture_output=True)
    names=set(v.decode('utf-8')for v in result.stdout.split(b'\0')if v)
    denied=[]
    if args.denylist:denied=[v.encode('utf-8')for v in json.loads(Path(args.denylist).read_text(encoding='utf-8')).get('values',[])if v]
    findings=[];scanned=0
    def inspect(name,raw,synthetic=False):
        nonlocal scanned
        scanned+=1
        for rule,pattern in RULES.items():
            for match in pattern.finditer(raw):
                value=match.group(0)
                if rule=='device-mac'and value.decode().upper()in SYNTHETIC_MACS:continue
                if rule=='private-lan'and synthetic:continue
                findings.append({'path':name,'rule':rule});break
        if any(value.lower()in raw.lower()for value in denied):findings.append({'path':name,'rule':'owner-private-denylist'})
    for name in sorted(names):
        path=ROOT/name
        if not path.is_file():continue
        if name.startswith(('private/','local/','exports/','backups/','evidence/','build/'))or path.suffix.lower()in {'.keystore','.jks','.dpapi','.pem','.key','.bin'}:
            findings.append({'path':name,'rule':'forbidden-tracked-file'});continue
        if path.suffix.lower()in {'.png','.jpg','.jpeg'}:continue
        inspect(name,path.read_bytes(),name.startswith('tests/'))
    for name in args.artifact:
        path=Path(name)
        if path.suffix.lower()in {'.apk','.zip'}:
            with zipfile.ZipFile(path)as archive:
                assert archive.testzip()is None
                for entry in archive.namelist():
                    if not entry.endswith('/')and not entry.endswith(('.png','.jpg','.jpeg')):inspect(path.name+'!'+entry,archive.read(entry),'tests/'in entry)
        else:inspect(path.name,path.read_bytes())
    print(json.dumps({'status':'passed'if not findings else'failed','filesOrEntriesScanned':scanned,'findings':findings},ensure_ascii=False),flush=True)
    raise SystemExit(bool(findings))
if __name__=='__main__':main()
