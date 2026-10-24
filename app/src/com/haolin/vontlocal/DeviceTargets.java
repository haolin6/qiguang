package com.haolin.vontlocal;
import android.content.*;import org.json.*;import java.util.*;

/** Resolve owner-selected targets from local data, never from published device identifiers. */
final class DeviceTargets {
    static final String PREFS="linked-devices-v1",KEY="vontAddress";
    static boolean supported(JSONObject record){return record!=null&&record.optLong("productCode")==300029&&"TLB01".equals(record.optString("modelFromName"));}
    static boolean validMac(String address){return address!=null&&address.matches("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}");}
    static void remember(Context context,DeviceStore store,String address){if(validMac(address)&&supported(store.devices.get(address)))context.getSharedPreferences(PREFS,0).edit().putString(KEY,address).apply();}
    static String vont(Context context,DeviceStore store){
        String explicit=context.getSharedPreferences(PREFS,0).getString(KEY,"");if(validMac(explicit)&&supported(store.devices.get(explicit)))return explicit;
        String legacy=context.getSharedPreferences("MainActivity",0).getString("selected","");if(validMac(legacy)&&supported(store.devices.get(legacy)))return legacy;
        String only="";for(Map.Entry<String,JSONObject> entry:store.devices.entrySet())if(validMac(entry.getKey())&&supported(entry.getValue())){if(!only.isEmpty())return "";only=entry.getKey();}return only;
    }
    static boolean privateAddress(String address){
        if(address==null||!address.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}"))return false;
        String[] fields=address.split("\\.");int[] octets=new int[4];for(int i=0;i<4;i++){octets[i]=Integer.parseInt(fields[i]);if(octets[i]>255)return false;}
        return octets[0]==10||octets[0]==172&&octets[1]>=16&&octets[1]<=31||octets[0]==192&&octets[1]==168;
    }
}
