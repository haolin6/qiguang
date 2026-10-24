package com.haolin.vontlocal;
import android.security.keystore.*;import android.app.*;import android.appwidget.*;import android.content.*;import android.os.*;import org.json.*;import java.io.*;import java.nio.*;import java.security.*;import java.util.*;import javax.crypto.*;

/** Synthetic emulator-only upgrade data. No real addresses, tokens, or network calls. */
public final class ConfigUpgradeAcceptance extends UiAcceptance {
    String phase,fixtureMac,fixtureIp;
    static final String BASE="upgrade-fixture-baseline.json";
    static final String[] PREFS={"scene-presets-v1","sleep-settings-v1","focus-settings-v1","appearance-v1"};
    static final String[] FILES={"registry.json","screenbar-credential.bin","screenbar-state-v04.json"};
    @Override public void onCreate(Bundle args){phase=args.getString("phase","");fixtureMac=args.getString("fixtureMac","02:00:00:00:00:02");fixtureIp=args.getString("fixtureIp","10.0.2.2");super.onCreate(args);}
    String sha(byte[] bytes)throws Exception{return Protocol.hex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    File file(String name){return new File(context().getFilesDir(),name);}
    void save(String name,String value)throws Exception{ScreenbarClient.atomic(file(name),value.getBytes("UTF-8"));}
    void seed()throws Exception{
        check(context().getPackageManager().getPackageInfo(context().getPackageName(),0).versionName.equals("0.8.2"),"seed runs on original 0.8.2");
        check(!file(ScreenbarClient.SEALED).exists(),"no preexisting connection credential on emulator");
        SharedPreferences.Editor scenes=context().getSharedPreferences(PREFS[0],0).edit().clear().putBoolean("initialized",true);
        for(String id:new String[]{"work","wind_down","bed"})scenes.putInt(id+".warm1",17).putInt(id+".warm2",23).putInt(id+".brightness",id.equals("work")?87:id.equals("bed")?3:63).putInt(id+".temperature",id.equals("work")?4200:id.equals("bed")?2800:3400).putInt(id+".minutes",27);
        check(scenes.commit(),"custom scenes saved");
        check(context().getSharedPreferences(PREFS[1],0).edit().clear().putInt("warm1",17).putInt("warm2",23).putInt("minutes",27).putInt("brightness",3).putInt("temperature",2800).commit(),"legacy sleep parameters saved");
        check(context().getSharedPreferences(PREFS[2],0).edit().clear().putInt("workMinutes",47).putInt("restMinutes",7).putInt("restBrightness",23).putInt("restTemperature",3100).commit(),"custom focus parameters saved");
        check(context().getSharedPreferences(PREFS[3],0).edit().clear().putString("theme","dark").commit(),"appearance saved");
        save("registry.json",new JSONObject().put("devices",new JSONArray().put(new JSONObject().put("bleAddress","02:00:00:00:00:01").put("alias","升级保留示例灯").put("attributes",new JSONObject()))).put("scenes",new JSONArray().put(new JSONObject().put("name","升级保留静态场景"))).put("events",new JSONArray()).toString());
        save("screenbar-state-v04.json",new JSONObject().put("fixture","offline-upgrade-example").toString());
        String marker=UUID.randomUUID().toString();KeyStore keys=KeyStore.getInstance("AndroidKeyStore");keys.load(null);if(!keys.containsAlias(ScreenbarClient.KEY_ALIAS)){KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");generator.init(new KeyGenParameterSpec.Builder(ScreenbarClient.KEY_ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());generator.generateKey();}Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,(SecretKey)keys.getKey(ScreenbarClient.KEY_ALIAS,null));char[] invalidToken=new char[32];Arrays.fill(invalidToken,(char)48);byte[] encrypted=cipher.doFinal(new JSONObject().put("fixture",marker).put("mac",fixtureMac).put("model",ScreenbarClient.MODEL).put("localip",fixtureIp).put("token",new String(invalidToken)).toString().getBytes("UTF-8"));ScreenbarClient.atomic(file(ScreenbarClient.SEALED),ByteBuffer.allocate(cipher.getIV().length+encrypted.length).put(cipher.getIV()).put(encrypted).array());
        JSONObject baseline=new JSONObject().put("uid",android.os.Process.myUid()).put("marker",marker);JSONObject prefs=new JSONObject(),files=new JSONObject();
        for(String name:PREFS)prefs.put(name,new JSONObject(context().getSharedPreferences(name,0).getAll()));
        for(String name:FILES)files.put(name,sha(ScreenbarClient.readBytes(file(name))));baseline.put("preferences",prefs).put("fileSha256",files);save(BASE,baseline.toString());
        check(new ScreenbarClient(context()).loadCredential().getString("fixture").equals(marker),"synthetic sealed example decrypts before upgrade");
    }
    void verify()throws Exception{
        JSONObject before=new JSONObject(new String(ScreenbarClient.readBytes(file(BASE)),"UTF-8"));
        check(context().getPackageManager().getPackageInfo(context().getPackageName(),0).versionName.equals("0.8.3"),"overlay update reached 0.8.3");check(before.getInt("uid")==android.os.Process.myUid(),"application uid preserved");
        JSONObject prefs=before.getJSONObject("preferences");for(String name:PREFS){JSONObject expected=prefs.getJSONObject(name);Map<String,?> actual=context().getSharedPreferences(name,0).getAll();check(actual.size()==expected.length(),"preference keys preserved "+name);Iterator<String> keys=expected.keys();while(keys.hasNext()){String key=keys.next();check(Objects.equals(String.valueOf(actual.get(key)),String.valueOf(expected.get(key))),"saved preference preserved "+name+":"+key);}}
        for(String name:FILES)check(sha(ScreenbarClient.readBytes(file(name))).equals(before.getJSONObject("fileSha256").getString(name)),"file bytes unchanged "+name);
        check(new ScreenbarClient(context()).loadCredential().getString("fixture").equals(before.getString("marker")),"same Android Keystore key decrypts synthetic connection example");
        check(SceneStore.settings(context(),"work").brightness==87&&SceneStore.settings(context(),"bed").warm2==23&&SceneStore.settings(context(),"bed").minutes==27,"custom scene values loaded by app");
        check(FocusStore.settings(context()).workMinutes==47&&FocusStore.settings(context()).restMinutes==7&&FocusStore.settings(context()).restTemperature==3100,"custom focus values loaded by app");
        DeviceStore store=new DeviceStore(context());check(store.devices.get("02:00:00:00:00:01").optString("alias").equals("升级保留示例灯")&&store.scenes.length()==1,"device record and saved static scene loaded");
        AppWidgetProviderInfo provider=null;for(AppWidgetProviderInfo value:AppWidgetManager.getInstance(context()).getInstalledProviders())if(value.provider.getClassName().equals("com.haolin.vontlocal.LinkWidget"))provider=value;
        check(provider!=null,"same widget receiver registered");check(provider.targetCellWidth==4&&provider.targetCellHeight==2,"widget target remains 4 by 2");
        check(provider.minResizeHeight==Ui.dp(context(),124),"lower widget resize limit installed");
    }
    void clean()throws Exception{for(String name:FILES)if(file(name).exists())check(file(name).delete(),"remove synthetic fixture "+name);for(String name:PREFS)check(context().getSharedPreferences(name,0).edit().clear().commit(),"clear synthetic preferences "+name);if(file(BASE).exists())check(file(BASE).delete(),"remove synthetic baseline");}
    @Override public void onStart(){int code=0;try{check(Build.HARDWARE.contains("ranchu")||Build.HARDWARE.contains("goldfish"),"emulator only");if(phase.equals("seed"))seed();else if(phase.equals("verify"))verify();else if(phase.equals("clean"))clean();else throw new IOException("Unknown phase");report.put("status","passed");}catch(Exception e){code=1;try{report.put("status","failed").put("error",e.toString());}catch(Exception ignored){}}try{report.put("phase",phase).put("runId",runId).put("scope","Synthetic emulator data only; no real credentials or hardware").put("checkCount",count).put("checks",checks);save("upgrade-"+phase+"-report.json",report.toString(2));}catch(Exception e){code=1;}Bundle result=new Bundle();result.putString("result",report.toString());finish(code,result);}
}
