"""Dependency-free native Android build. SDK/JDK/private signing paths stay local."""
from pathlib import Path
import argparse,hashlib,json,os,shutil,subprocess,sys,zipfile
ROOT=Path(__file__).resolve().parents[1]
VERSION='0.8.3'
def executable(name):return name+('.exe'if os.name=='nt'else'')
def settings():
    path=ROOT/'local.properties'
    if not path.exists():return {}
    return dict(line.split('=',1)for line in path.read_text(encoding='utf-8-sig').splitlines()if '='in line and not line.lstrip().startswith('#'))
def run(command,cwd=None,env=None):
    command=[str(v)for v in command]
    # Keep Java's temporary files on the build volume, including on sandboxed Windows.
    temp=ROOT/'build';temp.mkdir(exist_ok=True)
    name=Path(command[0]).stem.lower()
    if name=='java':command.insert(1,'-Djava.io.tmpdir='+str(temp))
    elif name in {'javac','jar','keytool'}:command.insert(1,'-J-Djava.io.tmpdir='+str(temp))
    subprocess.run(command,cwd=cwd,env=env,check=True)
def java_home(value):
    if value:return Path(value)
    javac=shutil.which('javac')
    if javac:return Path(javac).resolve().parents[1]
    raise SystemExit('Set JAVA_HOME or provide --jdk (JDK 21 required).')
def unit(jdk):
    build=ROOT/'build/unit';build.mkdir(parents=True,exist_ok=True)
    src=ROOT/'app/src/com/haolin/vontlocal'
    files=[src/name for name in ['Protocol.java','LinkPolicy.java','VontSleepCodec.java','ScreenbarPolicy.java','FocusPolicy.java','FocusTransition.java']]+list((ROOT/'tests').glob('*.java'))
    run([jdk/'bin'/executable('javac'),'-encoding','UTF-8','-d',build,*files])
    for name in ['LinkPolicyTest','ScreenbarPolicyTest','FocusPolicyTest']:run([jdk/'bin'/executable('java'),'-cp',build,'com.haolin.vontlocal.'+name])
def build(args):
    local=settings();jdk=java_home(args.jdk or local.get('jdk.dir')or os.environ.get('JAVA_HOME'))
    if args.unit:unit(jdk);return
    sdk_value=args.sdk or local.get('sdk.dir')or os.environ.get('ANDROID_HOME')or os.environ.get('ANDROID_SDK_ROOT')
    if not sdk_value:raise SystemExit('Set ANDROID_HOME or provide --sdk (platform 35 and build-tools 35.0.0).')
    sdk=Path(sdk_value);tools=sdk/'build-tools/35.0.0';android=sdk/'platforms/android-35/android.jar'
    target=ROOT/'build';out=ROOT/'dist';source=ROOT/'app'
    for folder in [target/'generated',target/'classes',target/'dex',out]:folder.mkdir(parents=True,exist_ok=True)
    javac=jdk/'bin'/executable('javac');java=jdk/'bin'/executable('java');jar=jdk/'bin'/executable('jar');aapt=tools/executable('aapt');align=tools/executable('zipalign')
    run([aapt,'package','-f','-m','-J',target/'generated','-M',source/'AndroidManifest.xml','-S',source/'res','-I',android])
    boot=str(android)+os.pathsep+str(tools/'core-lambda-stubs.jar')
    run([javac,'-encoding','UTF-8','-source','8','-target','8','-Xlint:-options','-bootclasspath',boot,'-d',target/'classes',*sorted((source/'src').rglob('*.java')),*sorted((target/'generated').rglob('*.java'))])
    run([jar,'--create','--file',target/'classes.jar','-C',target/'classes','.'])
    run([java,'-cp',tools/'lib/d8.jar','com.android.tools.r8.D8','--lib',android,'--min-api','26','--output',target/'dex',target/'classes.jar'])
    run([aapt,'package','-f','-M',source/'AndroidManifest.xml','-S',source/'res','-I',android,'-F',target/'unsigned.apk'])
    run([aapt,'add',target/'unsigned.apk','classes.dex'],cwd=target/'dex')
    run([align,'-f','4',target/'unsigned.apk',target/'aligned.apk'])
    key=Path(args.keystore or local.get('store.file')or Path.home()/'.android/debug.keystore').expanduser()
    alias=os.environ.get('QIGUANG_KEY_ALIAS','androiddebugkey');password=os.environ.get('QIGUANG_KEYSTORE_PASSWORD','android')
    signing_env={**os.environ,'QIGUANG_STORE_PASS':password}
    if not key.exists():
        key.parent.mkdir(parents=True,exist_ok=True)
        run([jdk/'bin'/executable('keytool'),'-genkeypair','-noprompt','-keystore',key,'-storepass:env','QIGUANG_STORE_PASS','-keypass:env','QIGUANG_STORE_PASS','-alias',alias,'-keyalg','RSA','-keysize','2048','-validity','10000','-dname','CN=Android Debug,O=Android,C=US'],env=signing_env)
    def sign(inp,output):
        run([java,'-jar',tools/'lib/apksigner.jar','sign','--ks',key,'--ks-pass','env:QIGUANG_STORE_PASS','--key-pass','env:QIGUANG_STORE_PASS','--ks-key-alias',alias,'--out',output,inp],env=signing_env)
        run([java,'-jar',tools/'lib/apksigner.jar','verify',output])
    apk=out/('qiguang-'+VERSION+'.apk');sign(target/'aligned.apk',apk)
    if args.acceptance:
        test=target/'acceptance'
        for p in [test/'classes',test/'dex']:p.mkdir(parents=True,exist_ok=True)
        run([javac,'-encoding','UTF-8','-source','8','-target','8','-Xlint:-options','-bootclasspath',boot,'-cp',target/'classes.jar','-d',test/'classes',*sorted((ROOT/'tests/android').glob('*.java'))])
        run([jar,'--create','--file',test/'classes.jar','-C',test/'classes','.'])
        run([java,'-cp',tools/'lib/d8.jar','com.android.tools.r8.D8','--lib',android,'--classpath',target/'classes.jar','--min-api','26','--output',test/'dex',test/'classes.jar'])
        run([aapt,'package','-f','-M',ROOT/'tests/android/AndroidManifest.xml','-I',android,'-F',test/'unsigned.apk'])
        run([aapt,'add',test/'unsigned.apk','classes.dex'],cwd=test/'dex')
        run([align,'-f','4',test/'unsigned.apk',test/'aligned.apk']);sign(test/'aligned.apk',test/'acceptance.apk')
    with zipfile.ZipFile(apk)as archive:assert archive.testzip()is None
    print(json.dumps({'version':VERSION,'apk':apk.relative_to(ROOT).as_posix(),'sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'testInstrumentationSeparate':True}),flush=True)
if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--sdk');parser.add_argument('--jdk');parser.add_argument('--keystore');parser.add_argument('--unit',action='store_true');parser.add_argument('--acceptance',action='store_true')
    build(parser.parse_args())
