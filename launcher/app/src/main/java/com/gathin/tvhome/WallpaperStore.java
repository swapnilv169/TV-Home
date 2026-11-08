package com.gathin.tvhome;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import org.json.*;
import java.io.*;
import java.util.*;

/** Private image housekeeping. Never deletes referenced images or fresh upload staging. */
final class WallpaperStore {
    static JSONObject clean(Context c,boolean explicit)throws JSONException{synchronized(StudioConfig.class){return cleanLocked(c,explicit);}}
    static void afterSave(Context c,Set<String> before,Set<String> after){synchronized(StudioConfig.class){afterSaveLocked(c,before,after);}}
    static ParcelFileDescriptor open(Context c,String id)throws FileNotFoundException{synchronized(StudioConfig.class){return openLocked(c,id);}}
    static void commit(Context c,String id)throws IOException{synchronized(StudioConfig.class){commitLocked(c,id);}}
    static JSONObject status(Context c)throws JSONException{synchronized(StudioConfig.class){return statusLocked(c);}}
    static final long MAX_IMAGE=3L*1024*1024, MAX_STORE=64L*1024*1024, GRACE=24L*60*60*1000;
    private static final Set<String> writing=new HashSet<>();
    private static File directory(Context c){File d=new File(c.getFilesDir(),"wallpapers");d.mkdirs();return d;}
    private static boolean recognized(File f){return f.getName().matches("[a-zA-Z0-9_-]{1,48}\\.jpg(?:\\.upload)?");}
    private static String id(File f){return f.getName().substring(0,f.getName().indexOf('.'));}
    private static JSONObject cleanLocked(Context c,boolean explicit)throws JSONException{
        Set<String> keep=StudioConfig.cleanupReferences(c);int deleted=0,skipped=0;long bytes=0,now=System.currentTimeMillis();
        File[] files=directory(c).listFiles();if(files!=null)for(File f:files){
            if(!f.isFile()||!recognized(f))continue;
            boolean stage=f.getName().endsWith(".upload"),old=now-f.lastModified()>GRACE;
            if(keep==null||writing.contains(f.getName())||keep.contains(id(f))||(!old&&(stage||!explicit))){skipped++;continue;}
            long size=f.length();if(f.delete()){deleted++;bytes+=size;}else skipped++;
        }
        return new JSONObject().put("deleted",deleted).put("bytes",bytes).put("skipped",skipped);
    }
    private static void afterSaveLocked(Context c,Set<String> before,Set<String> after){
        Set<String> keep=StudioConfig.cleanupReferences(c);if(keep==null)return;
        for(String id:before)if(!after.contains(id)&&!keep.contains(id)){File f=StudioConfig.image(c,id);if(!writing.contains(f.getName()+".upload"))f.delete();}
        try{clean(c,false);}catch(JSONException ignored){}
    }
    private static ParcelFileDescriptor openLocked(Context c,String id)throws FileNotFoundException{
        if(StudioConfig.health(c).optBoolean("needsRecovery"))throw new FileNotFoundException("Recover saved settings before importing images");
        try{clean(c,false);}catch(JSONException e){throw new FileNotFoundException("Cannot inspect wallpaper storage");}
        File target=StudioConfig.image(c,id),stage=new File(target.getPath()+".upload");
        if(writing.contains(stage.getName()))throw new FileNotFoundException("This image is still uploading");
        long total=0;File[] files=directory(c).listFiles();if(files!=null)for(File f:files)if(f.isFile())total+=f.length();
        if(total-stage.length()+MAX_IMAGE>MAX_STORE)throw new FileNotFoundException("Wallpaper storage is full. Remove unused images in Studio.");
        writing.add(stage.getName());
        try{return ParcelFileDescriptor.open(stage,ParcelFileDescriptor.MODE_CREATE|ParcelFileDescriptor.MODE_TRUNCATE|ParcelFileDescriptor.MODE_WRITE_ONLY,new Handler(Looper.getMainLooper()),error->{synchronized(StudioConfig.class){writing.remove(stage.getName());if(error!=null||stage.length()>MAX_IMAGE)stage.delete();}});}
        catch(IOException e){writing.remove(stage.getName());throw new FileNotFoundException(e.getMessage());}
    }
    private static void commitLocked(Context c,String id)throws IOException{
        File target=StudioConfig.image(c,id),stage=new File(target.getPath()+".upload");
        try{
            if(stage.length()<1||stage.length()>MAX_IMAGE)throw new IOException("Wallpaper must be under 3 MB");
            BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(stage.getPath(),o);
            if(o.outWidth<1||o.outHeight<1||o.outWidth>4096||o.outHeight>4096||(long)o.outWidth*o.outHeight>8500000)throw new IOException("Wallpaper dimensions too large");
            if(target.exists()&&!target.delete())throw new IOException("Could not replace wallpaper");if(!stage.renameTo(target))throw new IOException("Could not save wallpaper");target.setLastModified(System.currentTimeMillis());
        }catch(IOException|RuntimeException e){stage.delete();throw e;}
    }
    private static JSONObject statusLocked(Context c)throws JSONException{
        Set<String> keep=StudioConfig.cleanupReferences(c);JSONArray images=new JSONArray();long total=0;File[] files=directory(c).listFiles();
        if(files!=null)for(File f:files)if(f.isFile()&&recognized(f)){total+=f.length();images.put(new JSONObject().put("id",id(f)).put("bytes",f.length()).put("staged",f.getName().endsWith(".upload")).put("referenced",keep==null||keep.contains(id(f))));}
        return new JSONObject().put("bytes",total).put("limit",MAX_STORE).put("images",images);
    }
}
