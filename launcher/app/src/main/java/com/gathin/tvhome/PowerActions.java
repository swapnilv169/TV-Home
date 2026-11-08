package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.util.List;

/** Operates only the Android power dialog after the user selects Restart in our own UI. */
final class PowerActions {
    private final AccessibilityService service;
    private final Handler handler;
    private final Runnable failed;
    private boolean pending, chosen;
    private final Runnable inspect = this::inspectDialog;
    private final Runnable timeout = this::fail;

    PowerActions(AccessibilityService service, Handler handler, Runnable failed) {
        this.service=service;this.handler=handler;this.failed=failed;
    }
    boolean pending(){return pending;}
    void begin(){
        close();pending=true;chosen=false;
        if(!service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)){fail();return;}
        handler.postDelayed(inspect,120);
        handler.postDelayed(timeout,8000);
    }
    void windowChanged(){if(pending){handler.removeCallbacks(inspect);handler.postDelayed(inspect,80);}}
    private void inspectDialog(){
        if(!pending)return;
        List<AccessibilityWindowInfo> windows=service.getWindows();
        try{for(AccessibilityWindowInfo w:windows){
            if(w.getType()!=AccessibilityWindowInfo.TYPE_SYSTEM)continue;
            AccessibilityNodeInfo root=w.getRoot();
            if(root==null)continue;
            inspectRoot(root);
        }}finally{for(AccessibilityWindowInfo w:windows)w.recycle();}
    }
    private void inspectRoot(AccessibilityNodeInfo root){
        try{
            // Never inspect content in streaming apps, Settings, or our own launcher.
            if(!"android".contentEquals(root.getPackageName()==null?"":root.getPackageName()))return;
            if(!chosen){
                // Both action labels must exist: fail closed if this isn't the expected power menu.
                if(!has(root,"Power Off")||!has(root,"Restart"))return;
                if(click(root,"Restart")){chosen=true;handler.postDelayed(inspect,180);}
            }else{
                // The user explicitly requested immediate Restart without a confirmation dialog.
                // Accept only the standard positive button in a system restart confirmation.
                boolean message=has(root,"restart");
                if(!message)return;
                List<AccessibilityNodeInfo> buttons=root.findAccessibilityNodeInfosByViewId("android:id/button1");
                try{for(AccessibilityNodeInfo b:buttons){String t=String.valueOf(b.getText());
                    if(t.equalsIgnoreCase("OK")||t.equalsIgnoreCase("Yes")||t.equalsIgnoreCase("Restart")){
                        b.performAction(AccessibilityNodeInfo.ACTION_CLICK);break;
                    }
                }}finally{for(AccessibilityNodeInfo b:buttons)b.recycle();}
            }
        }finally{root.recycle();}
    }
    private static boolean has(AccessibilityNodeInfo root,String label){
        List<AccessibilityNodeInfo> nodes=root.findAccessibilityNodeInfosByText(label);
        boolean found=!nodes.isEmpty();for(AccessibilityNodeInfo n:nodes)n.recycle();return found;
    }
    private static boolean click(AccessibilityNodeInfo root,String label){
        List<AccessibilityNodeInfo> nodes=root.findAccessibilityNodeInfosByText(label);
        try{for(AccessibilityNodeInfo n:nodes){
            if(!label.equalsIgnoreCase(String.valueOf(n.getText())))continue;
            AccessibilityNodeInfo current=AccessibilityNodeInfo.obtain(n);
            for(int depth=0;current!=null&&depth<5;depth++){
                if(current.isClickable()){boolean result=current.performAction(AccessibilityNodeInfo.ACTION_CLICK);current.recycle();return result;}
                AccessibilityNodeInfo parent=current.getParent();current.recycle();current=parent;
            }
            if(current!=null)current.recycle();
        }}finally{for(AccessibilityNodeInfo n:nodes)n.recycle();}
        return false;
    }
    private void fail(){
        if(!pending)return;close();
        List<AccessibilityWindowInfo> windows=service.getWindows();
        boolean powerMenu=false;
        try{for(AccessibilityWindowInfo w:windows){
            if(w.getType()!=AccessibilityWindowInfo.TYPE_SYSTEM)continue;
            AccessibilityNodeInfo root=w.getRoot();if(root==null)continue;
            try{if("android".contentEquals(root.getPackageName()==null?"":root.getPackageName())
                    &&has(root,"Power Off")&&has(root,"Restart"))powerMenu=true;}
            finally{root.recycle();}
        }}finally{for(AccessibilityWindowInfo w:windows)w.recycle();}
        if(powerMenu)service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
        failed.run();
    }
    void close(){pending=false;handler.removeCallbacks(inspect);handler.removeCallbacks(timeout);}
}
