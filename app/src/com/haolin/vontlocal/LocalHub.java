package com.haolin.vontlocal;

import android.content.Context;
import java.util.*;

/** One registry and one GATT queue shared by activities and the short-lived service. */
final class LocalHub implements BleController.Listener {
    static LocalHub instance;static volatile boolean busy=false;
    static final Object SCREENBAR_LOCK=new Object();
    final DeviceStore store;final BleController ble;
    final Set<BleController.Listener> observers=new HashSet<>();
    static synchronized LocalHub get(Context context){if(instance==null)instance=new LocalHub(context.getApplicationContext());return instance;}
    LocalHub(Context context){store=new DeviceStore(context);ble=new BleController(context,store,this);}
    void add(BleController.Listener listener){observers.add(listener);}
    void remove(BleController.Listener listener){observers.remove(listener);if(observers.isEmpty()&&!busy)ble.close();}
    public void changed(){for(BleController.Listener listener:new ArrayList<>(observers))listener.changed();}
    public void notice(String value){for(BleController.Listener listener:new ArrayList<>(observers))listener.notice(value);}
}
