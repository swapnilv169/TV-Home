package com.veyra.tvmanager;

import android.content.Context;
import android.util.AtomicFile;
import android.util.Base64;
import com.cgutman.adblib.AdbCrypto;
import com.veyra.shared.CleanupCatalog;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.*;

/** Explicit, reversible operations. No arbitrary command endpoint is exposed to the UI. */
final class TvController implements Closeable {
    static final String HOME="com.gathin.tvhome", PROVIDER="content://com.gathin.tvhome.studio", SERVICE=HOME+"/"+HOME+".HomeRedirectService";
    static final LinkedHashMap<String,String> CLEAN=CleanupCatalog.entries();
    static final String[] UPDATES={"com.amazon.tv.forcedotaupdater.v2","com.amazon.device.software.ota","com.amazon.device.software.ota.override"};

    private final Context context;
    private AdbClient adb;
    private String identity="", address="", model="", androidVersion="", buildVersion="", savedLedger="";
    private JSONObject ledger;
    private AtomicFile ledgerFile;
    private final Set<String> packages=new HashSet<>(), thirdParty=new HashSet<>();
    interface Progress {void show(String message);}
    private final Progress progress;
    TvController(Context context,Progress progress){this.context=context.getApplicationContext();this.progress=progress;}
    static String quote(String value){return "'"+value.replace("'","'\\''")+"'";}
    static void packageName(String value){if(!value.matches("[a-zA-Z][a-zA-Z0-9_.]{1,180}"))throw new IllegalArgumentException("Invalid package name");}
    private static String base64(String value){return Base64.encodeToString(value.getBytes(StandardCharsets.UTF_8),Base64.URL_SAFE|Base64.NO_WRAP);}
    static String commandWithResult(String cmd){
        // A batch may already end in ';'. A newline separates the result marker
        // without producing the invalid '; ;' sequence that broke 1.9 connect.
        return cmd+"\nprintf '\\n__TVSTUDIO_RC:%s\\n' $?";
    }
    private String command(String cmd)throws Exception{
        if(adb==null||!adb.isOpen())throw new IOException("Connect to your TV first");
        String result=adb.shell(commandWithResult(cmd));int index=result.lastIndexOf("__TVSTUDIO_RC:");if(index<0)throw new IOException("TV did not confirm the command result");String code=result.substring(index+14).trim();String output=result.substring(0,index).trim();if(!code.equals("0"))throw new IOException(output.isEmpty()?"Command refused ("+code+")":output);return output;
    }
    JSONObject connect(String host,int port)throws Exception{
        close();if(!host.matches("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}"))throw new IllegalArgumentException("Enter the TV IPv4 address");for(String part:host.split("\\."))if(Integer.parseInt(part)>255)throw new IllegalArgumentException("Invalid IP address");if(port<1||port>65535)throw new IllegalArgumentException("Invalid port");
        File priv=new File(context.getFilesDir(),"adb-private"),pub=new File(context.getFilesDir(),"adb-public");AdbCrypto crypto;
        if(priv.isFile()&&pub.isFile())crypto=AdbCrypto.loadAdbKeyPair(data->Base64.encodeToString(data,Base64.NO_WRAP),priv,pub);
        else{crypto=AdbCrypto.generateAdbKeyPair(data->Base64.encodeToString(data,Base64.NO_WRAP));crypto.saveAdbKeyPair(priv,pub);}
        progress.show("Connecting… accept the authorization prompt on your TV.");adb=new AdbClient(host,port,crypto);address=host+":"+port;
        String manufacturer=command("getprop ro.product.manufacturer");model=command("getprop ro.product.model");int sdk=Integer.parseInt(command("getprop ro.build.version.sdk"));loadPackages();
        if(!packages.contains("com.amazon.tv.launcher")||sdk<25){close();throw new IOException("This build supports Android-based Fire TV devices on Android 7.1 (Fire OS 6) or newer. Other TVs and Vega OS are not supported.");}
        identity=deviceIdentity();String key=hex(MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8)));ledgerFile=new AtomicFile(new File(context.getFilesDir(),"restore-"+key+".json"));ledger=null;
        if(ledgerFile.getBaseFile().exists()){try(InputStream in=ledgerFile.openRead()){ledger=new JSONObject(read(in,2*1024*1024));}if(!identity.equals(ledger.getString("identity")))throw new IOException("Restore point belongs to a different TV");}
        androidVersion=command("getprop ro.build.version.release");buildVersion=command("getprop ro.build.display.id");
        context.getSharedPreferences("studio",0).edit().putString("lastHost",host).putInt("lastPort",port).apply();return status();
    }
    private String deviceIdentity()throws Exception{String serial=command("getprop ro.serialno");if(serial.isEmpty())serial=command("getprop ro.boot.serialno");if(serial.isEmpty())serial=command("settings get secure android_id");if(serial.isEmpty()||serial.equals("null"))throw new IOException("Cannot identify TV safely");return model+":"+serial;}
    private void verify()throws Exception{if(identity.isEmpty()||!identity.equals(deviceIdentity())){close();throw new IOException("TV identity changed; reconnect before making changes");}}
    private void loadPackages()throws Exception{packages.clear();thirdParty.clear();for(String line:command("pm list packages --user 0").split("\\r?\\n"))if(line.startsWith("package:"))packages.add(line.substring(8).trim());for(String line:command("pm list packages -3 --user 0").split("\\r?\\n"))if(line.startsWith("package:"))thirdParty.add(line.substring(8).trim());}
    JSONObject status()throws Exception{
        verify();loadPackages();Map<String,Integer> states=packageStates();JSONObject info=new JSONObject().put("model",model).put("address",address).put("android",androidVersion).put("build",buildVersion).put("installed",packages.contains(HOME)).put("restorePoint",ledger!=null);
        JSONArray cleanup=new JSONArray();for(Map.Entry<String,String> item:CLEAN.entrySet())if(packages.contains(item.getKey()))cleanup.put(new JSONObject().put("package",item.getKey()).put("name",item.getValue()).put("state",states.get(item.getKey())));info.put("cleanup",cleanup);
        JSONArray updates=new JSONArray();for(String pkg:UPDATES)if(packages.contains(pkg))updates.put(new JSONObject().put("package",pkg).put("state",states.get(pkg)).put("background",opMode(pkg)));info.put("updates",updates);
        if(packages.contains(HOME))try{JSONObject config=provider("read",null);info.put("config",config);info.put("needsUpgrade",config.optInt("launcherVersion")<BuildConfig.BUNDLED_LAUNCHER_VERSION_CODE);info.put("apps",provider("apps",null).getJSONArray("apps"));}catch(Exception e){info.put("needsUpgrade",true);}
        info.put("history",ledger==null?new JSONArray():ledger.optJSONArray("history"));return info;
    }
    /** One shell round trip for package states; parse failures stay visible. */
    private Map<String,Integer> packageStates()throws Exception{
        LinkedHashSet<String> wanted=new LinkedHashSet<>(CLEAN.keySet());wanted.addAll(Arrays.asList(UPDATES));wanted.retainAll(packages);
        Map<String,Integer> result=new HashMap<>();if(wanted.isEmpty())return result;
        String output=command(packageStateCommand(wanted));
        for(String part:output.split("__TVSTUDIO_PACKAGE:")){int newline=part.indexOf('\n');if(newline<0)continue;String pkg=part.substring(0,newline).trim();if(wanted.contains(pkg))result.put(pkg,parseState(part.substring(newline+1)));}
        if(result.size()!=wanted.size())throw new IOException("Could not read app states; reconnect and retry");return result;
    }
    static String packageStateCommand(Collection<String> wanted){
        StringBuilder script=new StringBuilder();
        for(String pkg:wanted){packageName(pkg);script.append("printf '\n__TVSTUDIO_PACKAGE:").append(pkg).append("\n'; dumpsys package ").append(quote(pkg)).append(" | sed -n '/User 0:/p'; ");}
        return script.toString();
    }
    private static boolean stoppable(String pkg){return !pkg.equals(HOME)&&!pkg.equals("android")&&!pkg.startsWith("com.android.")&&!pkg.contains("settings")&&!pkg.contains("systemui")&&!pkg.contains("launcher")&&!pkg.contains("inputmethod")&&!pkg.contains("remote")&&!pkg.contains("permissioncontroller");}
    private Map<String,String> manageableApps()throws Exception{
        JSONArray apps=provider("apps",null).getJSONArray("apps");Map<String,String> result=new LinkedHashMap<>();
        for(int i=0;i<apps.length();i++){JSONObject app=apps.getJSONObject(i);String pkg=app.getString("package");packageName(pkg);if(stoppable(pkg))result.put(pkg,app.getString("name"));}return result;
    }
    private Set<String> processNames()throws Exception{
        try{return ProcessSnapshot.names(command("ps -A -o NAME"));}
        catch(IOException e){return ProcessSnapshot.names(command("ps -A"));}
    }
    JSONObject runningApps()throws Exception{
        verify();Map<String,String> apps=manageableApps();Set<String> names=processNames();JSONArray result=new JSONArray();
        for(Map.Entry<String,String> app:apps.entrySet())if(ProcessSnapshot.contains(names,app.getKey()))result.put(new JSONObject().put("package",app.getKey()).put("name",app.getValue()));
        return new JSONObject().put("apps",result).put("scannedAt",System.currentTimeMillis());
    }
    JSONObject stopApp(String pkg)throws Exception{
        packageName(pkg);verify();if(!stoppable(pkg)||!manageableApps().containsKey(pkg))throw new IOException("This app is protected or no longer launchable");
        command("am force-stop --user 0 "+quote(pkg));JSONObject result=runningApps();result.put("requested",pkg);return result;
    }
    JSONObject cleanImages()throws Exception{verify();return provider("cleanImages",null);}
    JSONObject recoverConfig(String choice)throws Exception{
        if(!Arrays.asList("last_good","defaults").contains(choice))throw new IllegalArgumentException("Invalid recovery choice");
        ensureLedger();provider("recoverConfig",choice);record("Launcher configuration recovered: "+choice);return status();
    }
    private void ensureLedger()throws Exception{
        verify();if(ledger!=null){saveLedger();return;}
        ledger=new JSONObject().put("schema",1).put("identity",identity).put("model",model).put("created",System.currentTimeMillis()).put("packages",new JSONObject()).put("appops",new JSONObject()).put("history",new JSONArray());
        ledger.put("homeWasEnabled",servicePresent(command("settings get secure enabled_accessibility_services")));
        if(packages.contains(HOME)){String dump=command("dumpsys package "+HOME);ledger.put("readLogsGranted",dump.contains("android.permission.READ_LOGS: granted=true")).put("secureGranted",dump.contains("android.permission.WRITE_SECURE_SETTINGS: granted=true"));try{ledger.put("config",provider("read",null));}catch(Exception ignored){}}
        try{saveLedger();}catch(Exception e){ledger=null;throw e;}
    }
    private void saveLedger()throws Exception{String serialized=ledger.toString(2);if(serialized.equals(savedLedger))return;FileOutputStream out=null;try{out=ledgerFile.startWrite();out.write(serialized.getBytes(StandardCharsets.UTF_8));ledgerFile.finishWrite(out);savedLedger=serialized;}catch(Exception e){if(out!=null)ledgerFile.failWrite(out);throw e;}}
    private void record(String text)throws Exception{JSONArray history=ledger.getJSONArray("history");history.put(new JSONObject().put("time",System.currentTimeMillis()).put("message",text));while(history.length()>150)history.remove(0);saveLedger();}
    static boolean servicePresent(String list){for(String entry:list.split(":"))if(entry.equals(SERVICE)||entry.equals(HOME+"/.HomeRedirectService"))return true;return false;}
    static int parseState(String dump)throws IOException{Matcher m=Pattern.compile("User 0:[^\\r\\n]*\\benabled=(\\d+)").matcher(dump);if(!m.find())throw new IOException("Cannot read original app state");int state=Integer.parseInt(m.group(1));if(state<0||state>4)throw new IOException("Unknown app state");return state;}
    private int packageState(String pkg)throws Exception{packageName(pkg);return parseState(command("dumpsys package "+pkg));}
    static String modeForState(int state){switch(state){case 0:return "default-state";case 1:return "enable";case 2:return "disable";case 3:return "disable-user";case 4:return "disable-until-used";default:throw new IllegalArgumentException("Unknown app state");}}
    private boolean allowed(String pkg){return CLEAN.containsKey(pkg)||Arrays.asList(UPDATES).contains(pkg)||(thirdParty.contains(pkg)&&!pkg.equals(HOME));}
    private void capturePackage(String pkg)throws Exception{packageName(pkg);if(!packages.contains(pkg)||!allowed(pkg))throw new IOException("This package is protected or unavailable");ensureLedger();JSONObject original=ledger.getJSONObject("packages");if(!original.has(pkg)){original.put(pkg,packageState(pkg));saveLedger();}}
    private void setPackage(String pkg,int state)throws Exception{capturePackage(pkg);String result=command("pm "+modeForState(state)+" --user 0 "+pkg);if(packageState(pkg)!=state)throw new IOException("Fire OS protected "+pkg+": "+result);record(pkg+" → "+modeForState(state));}
    JSONObject cleanup(JSONArray selected)throws Exception{ensureLedger();JSONArray results=new JSONArray();for(int i=0;i<selected.length();i++){String pkg=selected.getString(i);if(!CLEAN.containsKey(pkg))throw new IllegalArgumentException("Not in the optional cleanup list");progress.show("Disabling "+CLEAN.get(pkg));try{setPackage(pkg,3);results.put(pkg+": disabled");}catch(Exception e){results.put(pkg+": "+e.getMessage());}}return new JSONObject().put("results",results).put("state",status());}
    JSONObject packageAction(String pkg,String action)throws Exception{if(!Arrays.asList("disable","enable","restore").contains(action))throw new IllegalArgumentException("Invalid app action");ensureLedger();int state=action.equals("disable")?3:1;if(action.equals("restore")){if(!ledger.getJSONObject("packages").has(pkg))throw new IOException("No saved state for this app");state=ledger.getJSONObject("packages").getInt(pkg);}setPackage(pkg,state);return status();}
    private String opMode(String pkg)throws Exception{String output=command("cmd appops get "+pkg+" RUN_ANY_IN_BACKGROUND");Matcher m=Pattern.compile("RUN_ANY_IN_BACKGROUND: (allow|ignore|deny|default|foreground)").matcher(output);if(m.find())return m.group(1);if(output.contains("No operations")||output.contains("No op"))return "default";throw new IOException("Cannot read update background policy");}
    JSONObject updates(boolean restrict)throws Exception{
        ensureLedger();JSONArray results=new JSONArray();for(String pkg:UPDATES)if(packages.contains(pkg)){
            progress.show((restrict?"Restricting ":"Resuming ")+pkg);
            try{setPackage(pkg,restrict?3:1);results.put(pkg+": "+(restrict?"disabled":"enabled"));}catch(Exception e){results.put(pkg+": package change refused — "+e.getMessage());}
            if(!pkg.equals(UPDATES[0]))try{JSONObject saved=ledger.getJSONObject("appops");if(!saved.has(pkg)){saved.put(pkg,opMode(pkg));saveLedger();}String mode=restrict?"ignore":"allow";command("cmd appops set "+pkg+" RUN_ANY_IN_BACKGROUND "+mode);if(!mode.equals(opMode(pkg)))throw new IOException("Read-back differed");record(pkg+" background → "+mode);results.put(pkg+": background "+mode);}catch(Exception e){results.put(pkg+": background policy refused — "+e.getMessage());}
        }
        results.put(restrict?"Restrictions are partial when Fire OS protects an updater. Privileged updates can still run; no DNS or router changes were made.":"Known update packages enabled and background restrictions lifted where permitted. This does not force an immediate update.");return new JSONObject().put("results",results).put("state",status());
    }
    JSONObject install()throws Exception{
        ensureLedger();boolean selectedHome=servicePresent(command("settings get secure enabled_accessibility_services"));progress.show("Uploading the bundled TV Home launcher…");String path="/data/local/tmp/tvstudio-home.apk";
        File apk=new File(context.getCacheDir(),"launcher.apk");try(InputStream in=context.getAssets().open("TV-Home.apk");OutputStream out=new FileOutputStream(apk)){copy(in,out,20*1024*1024);}try(InputStream in=new FileInputStream(apk)){adb.push(in,path,apk.length());}
        try{String output=command("pm install -r "+path);if(!output.contains("Success"))throw new IOException(output+". Existing launcher data was preserved; do not uninstall to work around a signing mismatch.");}finally{try{command("rm -f "+path);}catch(Exception ignored){}}
        loadPackages();command("pm grant "+HOME+" android.permission.WRITE_SECURE_SETTINGS");command("pm grant "+HOME+" android.permission.READ_LOGS");if(selectedHome){provider("home","this");JSONObject repair=provider("repairHome",null);awaitHome(repair.optLong("previousConnection"));}record("TV Home "+BuildConfig.BUNDLED_LAUNCHER_VERSION_NAME+" installed; Home switching permissions granted");return status();
    }
    JSONObject provider(String method,String arg)throws Exception{
        String output=command("content call --uri "+PROVIDER+" --method "+method+(arg==null?"":" --arg "+quote(arg)));
        Matcher m=Pattern.compile("payload=([A-Za-z0-9_=-]+)").matcher(output);if(!m.find())throw new IOException(output.contains("error=")?output:"Install or update TV Home first");return new JSONObject(new String(Base64.decode(m.group(1),Base64.URL_SAFE),StandardCharsets.UTF_8));
    }
    JSONObject home(String choice)throws Exception{if(!choice.equals("this")&&!choice.equals("default"))throw new IllegalArgumentException("Choose a Home screen");ensureLedger();command("pm grant "+HOME+" android.permission.WRITE_SECURE_SETTINGS");if(choice.equals("this"))command("pm grant "+HOME+" android.permission.READ_LOGS");JSONObject r=provider("home",choice);if(choice.equals("this"))awaitHome();record("Selected "+choice+" Home");return r;}
    private void awaitHome()throws Exception{awaitHome(-1);}
    private void awaitHome(long previous)throws Exception{for(int i=0;i<32;i++){JSONObject health=provider("read",null).optJSONObject("homeHealth");if(health!=null&&health.optBoolean("connected")&&health.optLong("connectionEpoch")>previous)return;Thread.sleep(250);}throw new IOException("Launcher installed, but Home switching has not connected yet. Use Repair Home switching or select This home again.");}
    JSONObject repairHome()throws Exception{ensureLedger();command("pm grant "+HOME+" android.permission.WRITE_SECURE_SETTINGS");command("pm grant "+HOME+" android.permission.READ_LOGS");JSONObject repair=provider("repairHome",null);awaitHome(repair.optLong("previousConnection"));record("Home switching reconnected");return status();}
    JSONObject config(JSONObject value)throws Exception{ensureLedger();JSONObject result=provider("save",base64(value.toString()));record("Launcher appearance updated");return result;}
    JSONObject apps(JSONObject value)throws Exception{ensureLedger();JSONObject result=provider("appsSave",base64(value.toString()));record("Favorites and hidden apps updated");return result;}
    JSONObject wallpaper(File file,String id)throws Exception{if(!id.matches("[a-zA-Z0-9_-]{1,48}")||file.length()>3*1024*1024)throw new IOException("Invalid wallpaper");ensureLedger();String path="/data/local/tmp/tvstudio-"+id+".jpg";try(InputStream in=new FileInputStream(file)){adb.push(in,path,file.length());}try{command("content write --uri "+PROVIDER+"/wallpaper/"+id+" < "+path);return provider("commitImage",id);}finally{try{command("rm -f "+path);}catch(Exception ignored){}}}
    JSONObject weatherRetry()throws Exception{verify();return provider("weatherRetry",null);}
    JSONObject restore()throws Exception{
        verify();if(ledger==null)throw new IOException("No restore point for this TV");JSONArray results=new JSONArray();JSONObject original=ledger.getJSONObject("packages");for(Iterator<String> it=original.keys();it.hasNext();){String pkg=it.next();try{setPackage(pkg,original.getInt(pkg));results.put(pkg+": restored");}catch(Exception e){results.put(pkg+": "+e.getMessage());}}
        JSONObject ops=ledger.getJSONObject("appops");for(Iterator<String> it=ops.keys();it.hasNext();){String pkg=it.next();try{if(!Arrays.asList(UPDATES).contains(pkg))throw new IOException("Invalid restore entry");String mode=ops.getString(pkg);if(!Arrays.asList("allow","ignore","deny","default","foreground").contains(mode))throw new IOException("Invalid app-op");command("cmd appops set "+pkg+" RUN_ANY_IN_BACKGROUND "+mode);if(!mode.equals(opMode(pkg)))throw new IOException("Read-back differed");results.put(pkg+": background restored");}catch(Exception e){results.put(pkg+": "+e.getMessage());}}
        if(packages.contains(HOME)){
            JSONObject c=ledger.optJSONObject("config");
            if(ledger.has("config")&&c==null)results.put("Launcher appearance: invalid saved configuration");
            if(c!=null){
                try{
                    JSONObject storage=null;
                    try{storage=provider("read",null).optJSONObject("imageStorage");}catch(Exception ignored){}
                    provider("save",base64(restoredConfig(c,storage,results).toString()));results.put("Launcher appearance restored");
                }catch(Exception e){results.put("Launcher appearance: "+e.getMessage());}
                try{provider("appsSave",base64(new JSONObject().put("dock",new JSONArray(c.optString("dock","").isEmpty()?Collections.emptyList():Arrays.asList(c.optString("dock","").split(",")))).put("hidden",c.optJSONArray("hidden")).toString()));results.put("Favorites and hidden apps restored");}catch(Exception e){results.put("App layout: "+e.getMessage());}
            }
            // A missing old wallpaper or invalid appearance must not skip the Home preference.
            try{provider("home",ledger.optBoolean("homeWasEnabled")?"this":"default");results.put("Home preference restored");}catch(Exception e){results.put("Home: "+e.getMessage());}
        }
        if(packages.contains(HOME))for(String[] permission:new String[][]{{"readLogsGranted","READ_LOGS"},{"secureGranted","WRITE_SECURE_SETTINGS"}})if(!ledger.optBoolean(permission[0]))try{command("pm revoke "+HOME+" android.permission."+permission[1]);results.put(permission[1]+": restored to ungranted");}catch(Exception e){results.put(permission[1]+": "+e.getMessage());}
        record("Restore requested; individual results reported");return new JSONObject().put("results",results).put("state",status());
    }
    /** Restore snapshots contain image IDs, not image files. Never edit the stored baseline. */
    static JSONObject restoredConfig(JSONObject saved,JSONObject storage,JSONArray results)throws JSONException{
        JSONObject restored=new JSONObject(saved.toString());JSONArray images=storage==null?null:storage.optJSONArray("images");
        if(images==null){results.put("Wallpaper availability unavailable; saved image references retained");return restored;}
        Set<String> available=new HashSet<>(),omitted=new HashSet<>();
        for(int i=0;i<images.length();i++){JSONObject image=images.getJSONObject(i);String id=image.getString("id");if(!image.getBoolean("staged"))available.add(id);}
        String idle=restored.optString("idleWallpaper","");if(!idle.isEmpty()&&!available.contains(idle)){omitted.add(idle);restored.put("idleWallpaper","");}
        JSONArray themes=restored.getJSONArray("themes");
        for(int i=0;i<themes.length();i++){JSONObject theme=themes.getJSONObject(i);JSONArray old=theme.optJSONArray("wallpapers");if(old==null)continue;JSONArray kept=new JSONArray();
            for(int j=0;j<old.length();j++){String id=old.getString(j);if(available.contains(id))kept.put(id);else omitted.add(id);}theme.put("wallpapers",kept);}
        if(!omitted.isEmpty())results.put(omitted.size()+" unavailable wallpaper image(s) omitted; choose their files again to restore them");
        return restored;
    }
    JSONObject importRestore(String text)throws Exception{
        verify();if(text.length()>2*1024*1024)throw new IOException("Restore file too large");JSONObject incoming=new JSONObject(text);
        if(incoming.optInt("schema")!=1||!identity.equals(incoming.optString("identity")))throw new IOException("This restore point belongs to a different TV");
        JSONObject pkgs=incoming.getJSONObject("packages");for(Iterator<String> it=pkgs.keys();it.hasNext();){String pkg=it.next();packageName(pkg);if(!allowed(pkg))throw new IOException("Restore file includes a protected package");modeForState(pkgs.getInt(pkg));}
        JSONObject ops=incoming.getJSONObject("appops");for(Iterator<String> it=ops.keys();it.hasNext();){String pkg=it.next();if(!Arrays.asList(UPDATES).contains(pkg)||!Arrays.asList("allow","ignore","deny","default","foreground").contains(ops.getString(pkg)))throw new IOException("Invalid update restore entry");}
        if(ledger!=null)throw new IOException("This phone already has a restore point for this TV. It will not be overwritten.");
        incoming.put("history",new JSONArray());ledger=incoming;try{saveLedger();}catch(Exception e){ledger=null;throw e;}return status();
    }
    String export()throws Exception{verify();if(ledger==null)throw new IOException("No restore point yet");return ledger.toString(2);}
    private static String hex(byte[] b){StringBuilder s=new StringBuilder();for(byte v:b)s.append(String.format(Locale.ROOT,"%02x",v&255));return s.toString();}
    static String read(InputStream in,int maximum)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();copy(in,out,maximum);return out.toString("UTF-8");}
    static void copy(InputStream in,OutputStream out,int maximum)throws IOException{byte[] b=new byte[8192];int n,total=0;while((n=in.read(b))!=-1){total+=n;if(total>maximum)throw new IOException("File too large");out.write(b,0,n);}}
    boolean connected(){return adb!=null&&adb.isOpen();}
    @Override public void close(){if(adb!=null)adb.close();adb=null;identity="";ledger=null;ledgerFile=null;savedLedger="";androidVersion="";buildVersion="";packages.clear();thirdParty.clear();}
}
