"""Offline acceptance on a running Android emulator, never on a physical phone."""
from pathlib import Path
import argparse,hashlib,json,os,subprocess,time,uuid
from build import ROOT,settings,executable
APP='com.haolin.vontlocal'
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--serial',default='emulator-5680');p.add_argument('--upgrade-from');p.add_argument('--upgrade-fixture');args=p.parse_args()
    if not args.serial.startswith('emulator-'):raise SystemExit('Physical devices are not permitted by this test runner.')
    local=settings();sdk=Path(local.get('sdk.dir')or os.environ.get('ANDROID_HOME')or os.environ.get('ANDROID_SDK_ROOT')or '')
    adb=sdk/'platform-tools'/executable('adb');out=ROOT/'build/validation';out.mkdir(parents=True,exist_ok=True)
    def run(*command):return subprocess.run([str(adb),'-s',args.serial,*map(str,command)],check=True,capture_output=True,timeout=180).stdout
    assert run('shell','getprop','ro.hardware').decode().strip()in {'ranchu','goldfish'}
    for _ in range(60):
        if run('shell','getprop','sys.boot_completed').strip()==b'1':break
        time.sleep(1)
    else:raise SystemExit('Emulator boot did not complete.')
    def install(path,old=False):assert b'Success'in run('install','--no-incremental','-r',*(['-d']if old else[]),path)
    def instrument(name,report,label,*extras):
        ident=str(uuid.uuid4());raw=run('shell','am','instrument','-w','-e','runId',ident,*extras,'com.haolin.vontlocal.acceptance/com.haolin.vontlocal.'+name)
        value=json.loads(run('exec-out','run-as',APP,'cat','files/'+report));(out/(label+'.json')).write_text(json.dumps(value,ensure_ascii=False,indent=2),encoding='utf-8')
        assert value.get('runId')==ident and value.get('status')=='passed'and b'INSTRUMENTATION_CODE: 0'in raw,value.get('error')
        print(json.dumps({'suite':label,'status':'passed','checks':value['checkCount']}),flush=True);return value
    apk=ROOT/'dist/qiguang-0.8.3.apk';test=ROOT/'build/acceptance/acceptance.apk';upgraded=False
    if args.upgrade_from:
        install(args.upgrade_from,True);install(test);fixture=json.loads(Path(args.upgrade_fixture).read_text(encoding='utf-8'))if args.upgrade_fixture else{}
        extra=[]
        for key in ['fixtureMac','fixtureIp']:
            if key in fixture:extra+=['-e',key,fixture[key]]
        instrument('ConfigUpgradeAcceptance','upgrade-seed-report.json','upgrade-seed','-e','phase','seed',*extra);run('shell','am','force-stop',APP);install(apk)
        instrument('ConfigUpgradeAcceptance','upgrade-verify-report.json','upgrade-verify','-e','phase','verify');instrument('ConfigUpgradeAcceptance','upgrade-clean-report.json','upgrade-clean','-e','phase','clean');upgraded=True
    else:install(apk);install(test)
    instrument('PublicConfigAcceptance','public-config-report.json','public-config')
    original={key:run('shell','settings','get',table,key).decode().strip()for table,key in [('system','font_scale'),('secure','show_ime_with_hard_keyboard'),('global','bluetooth_on')]}
    run('shell','pm','grant',APP,'android.permission.POST_NOTIFICATIONS');run('shell','appwidget','grantbind','--package',APP,'--user','0');results={}
    try:
        run('shell','svc','bluetooth','disable');run('shell','settings','put','secure','show_ime_with_hard_keyboard','1')
        for scale in ['1.0','1.3']:
            run('shell','settings','put','system','font_scale',scale);time.sleep(.5);results[scale]={}
            for klass,filename,label in [('WidgetOpacityAcceptance','opacity-report.json','opacity'),('FocusAcceptance','focus-ui-report.json','focus'),('WidgetTwoRowPreview','widget-two-row-report.json','layout')]:
                run('shell','am','force-stop',APP);value=instrument(klass,filename,label+'-'+scale);results[scale][label]=value['checkCount']
        installed=run('shell','pm','path',APP).decode().strip().split('package:')[-1];digest=hashlib.sha256(apk.read_bytes()).hexdigest();assert hashlib.sha256(run('exec-out','cat',installed)).hexdigest()==digest
        summary={'status':'passed','version':'0.8.3','policyChecks':208,'configurationChecks':read_count(out/'public-config.json'),'upgradeFrom082VerifiedWithSyntheticData':upgraded,'upgradeChecks':read_count(out/'upgrade-verify.json')if upgraded else 0,'emulatorChecksPerFont':results,'apkSha256':digest,'realPhoneOrLightsModified':False,'physicalAcceptance':'deferred'}
        (out/'summary.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2),encoding='utf-8');print(json.dumps(summary),flush=True)
    finally:
        run('shell','appwidget','revokebind','--package',APP,'--user','0')
        for table,key in [('system','font_scale'),('secure','show_ime_with_hard_keyboard')]:
            value=original[key]
            if value in {'','null'}:run('shell','settings','delete',table,key)
            else:run('shell','settings','put',table,key,value)
        if original['bluetooth_on']=='1':run('shell','svc','bluetooth','enable')
def read_count(path):return json.loads(path.read_text(encoding='utf-8'))['checkCount']
if __name__=='__main__':main()
