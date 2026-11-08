package com.veyra.tvmanager;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.webkit.*;
import android.util.Base64;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private WebView web;
    private TvController tv;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final ExecutorService updateWorker=Executors.newSingleThreadExecutor();
    private String pickerRequest, exportRequest, exportData, importRequest;
    private volatile boolean destroyed;
    private volatile String updateReleaseUrl;
    private final Set<String> liveImages=new HashSet<>();
    private static final String ORIGIN="https://appassets.androidplatform.net/";
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);web=new WebView(this);web.setBackgroundColor(0xff0b141a);setContentView(web);
        WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(false);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);settings.setSupportMultipleWindows(false);
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest r){return true;}
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
                String url=request.getUrl().toString();String name=url.startsWith(ORIGIN)?url.substring(ORIGIN.length()):"";
                try{if(!Arrays.asList("index.html","style.css","app.js").contains(name))return new WebResourceResponse("text/plain","UTF-8",403,"Blocked",Collections.emptyMap(),new ByteArrayInputStream(new byte[0]));
                    return new WebResourceResponse(name.endsWith("html")?"text/html":name.endsWith("css")?"text/css":"application/javascript","UTF-8",getAssets().open(name));
                }catch(IOException e){return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));}
            }
        });
        worker.execute(this::clearAbandonedImages);tv=new TvController(this,message->event("progress",message));web.addJavascriptInterface(new Bridge(),"Studio");web.loadUrl(ORIGIN+"index.html");
    }
    private void event(String type,String text){try{JSONObject value=new JSONObject().put("type",type).put("message",text);runOnUiThread(()->{if(!destroyed)web.evaluateJavascript("window.studioEvent("+value+")",null);});}catch(JSONException ignored){}}
    private void reply(String id,Object result,String error){try{JSONObject value=new JSONObject().put("id",id).put("ok",error==null).put("data",result==null?JSONObject.NULL:result).put("error",error==null?JSONObject.NULL:error);runOnUiThread(()->{if(!destroyed)web.evaluateJavascript("window.studioReply("+value+")",null);});}catch(JSONException ignored){}}
    final class Bridge {
        @JavascriptInterface public void request(String id,String action,String raw){
            if(destroyed||id==null||!id.matches("[0-9]{1,12}")||raw==null||raw.length()>100000)return;
            if("autoAppUpdate".equals(action)||"checkAppUpdate".equals(action)){
                updateWorker.execute(()->{try{reply(id,"autoAppUpdate".equals(action)?automaticAppUpdate():checkAppUpdate(),null);}catch(Exception e){reply(id,null,e.getMessage()==null?"Update check unavailable":e.getMessage());}});return;
            }
            worker.execute(()->{try{JSONObject arg=new JSONObject(raw);Object response;
                switch(action){
                    case "init":response=new JSONObject().put("phoneVersion",getPackageManager().getPackageInfo(getPackageName(),0).versionName).put("tvVersion",BuildConfig.BUNDLED_LAUNCHER_VERSION_NAME).put("appUpdate",cachedAppUpdate()).put("host",getSharedPreferences("studio",0).getString("lastHost","")).put("port",getSharedPreferences("studio",0).getInt("lastPort",5555));break;
                    case "connect":response=tv.connect(arg.getString("host"),arg.optInt("port",5555));break;
                    case "disconnect":tv.close();response=new JSONObject();break;
                    case "status":response=tv.status();break;
                    case "install":response=tv.install();break;
                    case "home":response=tv.home(arg.getString("choice"));break;
                    case "cleanup":response=tv.cleanup(arg.getJSONArray("packages"));break;
                    case "package":response=tv.packageAction(arg.getString("package"),arg.getString("action"));break;
                    case "updates":response=tv.updates(arg.getBoolean("restrict"));break;
                    case "repairHome":response=tv.repairHome();break;
                    case "weatherRetry":response=tv.weatherRetry();break;
                    case "config":response=tv.config(arg.getJSONObject("config"));break;
                    case "apps":response=tv.apps(arg);break;
                    case "runningApps":response=tv.runningApps();break;
                    case "stopApp":response=tv.stopApp(arg.getString("package"));break;
                    case "cleanImages":response=tv.cleanImages();break;
                    case "recoverConfig":response=tv.recoverConfig(arg.getString("choice"));break;
                    case "discardImages":discardImages(arg.getJSONArray("images"));response=new JSONObject();break;
                    case "restore":response=tv.restore();break;
                    case "pickImages":runOnUiThread(()->pick(id,arg.optBoolean("multiple",true)));return;
                    case "upload":String image=arg.getString("image");if(!image.matches("[a-zA-Z0-9_-]{1,48}"))throw new IOException("Invalid image");response=tv.wallpaper(new File(getCacheDir(),image+".jpg"),image);break;
                    case "citySearch":response=citySearch(arg.getString("query"));break;
                    case "export":String data=tv.export();runOnUiThread(()->export(id,data));return;
                    case "importRestore":runOnUiThread(()->{if(importRequest!=null){reply(id,null,"A picker is already open");return;}importRequest=id;try{startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE),12);}catch(RuntimeException e){importRequest=null;reply(id,null,"No document picker available");}});return;
                    case "openAppUpdate":String release=updateReleaseUrl;if(release==null)throw new IOException("Check for an update first");runOnUiThread(()->{try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(release)));reply(id,new JSONObject(),null);}catch(RuntimeException e){reply(id,null,"No browser available to open GitHub");}});return;
                    case "licenses":response=new JSONObject().put("text",TvController.read(getAssets().open("THIRD-PARTY-NOTICES.txt"),30000));break;
                    default:throw new IllegalArgumentException("Unknown action");
                }
                reply(id,response,null);
            }catch(Exception e){if(!tv.connected()&&!Arrays.asList("checkAppUpdate","openAppUpdate","licenses","init","discardImages").contains(action))event("disconnected","Connection closed");reply(id,null,e.getMessage()==null?e.getClass().getSimpleName():e.getMessage());}});
        }
    }
    private JSONObject cachedAppUpdate()throws Exception{
        SharedPreferences cache=getSharedPreferences("app_updates",MODE_PRIVATE);String tag=cache.getString("tag","");
        String current=getPackageManager().getPackageInfo(getPackageName(),0).versionName;
        if(tag.isEmpty())return new JSONObject().put("available",false).put("current",current);
        try{String latest=ReleaseVersion.clean(tag);boolean available=ReleaseVersion.newer(latest,current);
            updateReleaseUrl=available?"https://github.com/sreeego/tv-home-studio/releases/tag/"+tag:null;
            return new JSONObject().put("current",current).put("latest",latest).put("available",available);
        }catch(IllegalArgumentException e){return new JSONObject().put("current",current).put("available",false);}
    }
    private JSONObject automaticAppUpdate()throws Exception{
        SharedPreferences cache=getSharedPreferences("app_updates",MODE_PRIVATE);
        long now=System.currentTimeMillis(),attempt=cache.getLong("attempt",0);
        if(attempt>0&&now>=attempt&&now-attempt<24L*60*60*1000)return cachedAppUpdate();
        // Save attempts before networking, so offline launches and rate limits do not cause retry loops.
        try{return checkAppUpdate();}catch(Exception ignored){return cachedAppUpdate();}
    }
    private JSONObject checkAppUpdate()throws Exception{
        getSharedPreferences("app_updates",MODE_PRIVATE).edit().putLong("attempt",System.currentTimeMillis()).commit();
        HttpURLConnection connection=(HttpURLConnection)new URL("https://api.github.com/repos/sreeego/tv-home-studio/releases/latest").openConnection();
        connection.setConnectTimeout(6000);connection.setReadTimeout(8000);connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept","application/vnd.github+json");connection.setRequestProperty("User-Agent","TV-Home-Studio");
        try{
            int status=connection.getResponseCode();if(status!=200)throw new IOException(status==403||status==429?"GitHub is limiting requests. Try again later.":"Could not check releases. Try again later.");
            JSONObject release;try(InputStream in=connection.getInputStream()){release=new JSONObject(TvController.read(in,262144));}
            String tag=release.getString("tag_name"),latest=ReleaseVersion.clean(tag),current=getPackageManager().getPackageInfo(getPackageName(),0).versionName;
            if(release.optBoolean("draft")||release.optBoolean("prerelease"))throw new IOException("No stable release available");
            String url="https://github.com/sreeego/tv-home-studio/releases/tag/"+tag;
            if(!url.equals(release.getString("html_url")))throw new IOException("Unexpected release address");
            boolean available=ReleaseVersion.newer(latest,current);updateReleaseUrl=available?url:null;
            getSharedPreferences("app_updates",MODE_PRIVATE).edit().putString("tag",tag).apply();
            return new JSONObject().put("current",current).put("latest",latest).put("available",available);
        }catch(java.net.SocketTimeoutException e){throw new IOException("Update check timed out. Check your internet connection and retry.");}
        finally{connection.disconnect();}
    }
    private JSONObject citySearch(String query)throws Exception{if(query.length()<2||query.length()>80)throw new IllegalArgumentException("Enter a city name");HttpURLConnection connection=(HttpURLConnection)new URL("https://geocoding-api.open-meteo.com/v1/search?count=6&language=en&format=json&name="+URLEncoder.encode(query,"UTF-8")).openConnection();connection.setConnectTimeout(6000);connection.setReadTimeout(8000);try{if(connection.getResponseCode()!=200)throw new IOException("City search unavailable");try(InputStream in=connection.getInputStream()){return new JSONObject(TvController.read(in,65536));}}finally{connection.disconnect();}}
    private void discardImages(JSONArray images)throws JSONException{
        for(int i=0;i<Math.min(64,images.length());i++){String id=images.getString(i);if(!id.matches("image_[a-f0-9]{32}"))continue;File file=new File(getCacheDir(),id+".jpg");if(!file.exists()||file.delete())liveImages.remove(id);}
    }
    private void clearAbandonedImages(){
        File[] files=getCacheDir().listFiles();if(files==null)return;
        for(File file:files)if(file.getName().matches("image_[a-f0-9]{32}\\.jpg"))file.delete();
    }
    private void pruneImageCache(){
        File[] files=getCacheDir().listFiles();if(files==null)return;long cutoff=System.currentTimeMillis()-24L*60*60*1000;
        for(File file:files){String name=file.getName();if(name.matches("image_[a-f0-9]{32}\\.jpg")&&!liveImages.contains(name.substring(0,name.length()-4))&&file.lastModified()<cutoff)file.delete();}
    }
    private JSONArray importImages(List<Uri> uris)throws Exception{
        pruneImageCache();JSONArray created=new JSONArray(),images=new JSONArray();
        try{
            for(Uri uri:uris){Bitmap bitmap=null,thumb=null;
                try{
                    bitmap=ImageDecoder.decodeBitmap(ImageDecoder.createSource(getContentResolver(),uri),(decoder,info,source)->{
                        float scale=Math.min(1f,Math.min(1920f/info.getSize().getWidth(),1080f/info.getSize().getHeight()));
                        decoder.setTargetSize(Math.max(1,Math.round(info.getSize().getWidth()*scale)),Math.max(1,Math.round(info.getSize().getHeight()*scale)));decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                    });
                    String name="image_"+UUID.randomUUID().toString().replace("-","");created.put(name);liveImages.add(name);File file=new File(getCacheDir(),name+".jpg");
                    try(OutputStream out=new FileOutputStream(file)){if(!bitmap.compress(Bitmap.CompressFormat.JPEG,88,out))throw new IOException("Image conversion failed");}
                    if(file.length()>3*1024*1024)throw new IOException("Wallpaper too large");
                    float scale=Math.min(240f/bitmap.getWidth(),135f/bitmap.getHeight());thumb=Bitmap.createScaledBitmap(bitmap,Math.max(1,Math.round(bitmap.getWidth()*scale)),Math.max(1,Math.round(bitmap.getHeight()*scale)),true);
                    ByteArrayOutputStream preview=new ByteArrayOutputStream();thumb.compress(Bitmap.CompressFormat.JPEG,65,preview);
                    images.put(new JSONObject().put("id",name).put("width",bitmap.getWidth()).put("height",bitmap.getHeight()).put("preview","data:image/jpeg;base64,"+Base64.encodeToString(preview.toByteArray(),Base64.NO_WRAP)));
                }finally{if(thumb!=null&&thumb!=bitmap)thumb.recycle();if(bitmap!=null)bitmap.recycle();}
            }
            return images;
        }catch(Exception|OutOfMemoryError e){discardImages(created);throw e;}
    }
    private void pick(String id,boolean multiple){if(pickerRequest!=null){reply(id,null,"A photo picker is already open");return;}pickerRequest=id;try{startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/jpeg","image/png","image/webp"}).addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE,multiple),10);}catch(RuntimeException e){pickerRequest=null;reply(id,null,"No photo picker available");}}
    private void export(String id,String data){if(exportRequest!=null){reply(id,null,"An export is already open");return;}exportRequest=id;exportData=data;try{startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"TV-Home-restore-point.json"),11);}catch(RuntimeException e){exportRequest=null;exportData=null;reply(id,null,"No document picker available");}}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);
        if(request==10){String id=pickerRequest;pickerRequest=null;if(result!=RESULT_OK||data==null){reply(id,new JSONArray(),null);return;}ArrayList<Uri> uris=new ArrayList<>();if(data.getClipData()!=null)for(int i=0;i<Math.min(8,data.getClipData().getItemCount());i++)uris.add(data.getClipData().getItemAt(i).getUri());else if(data.getData()!=null)uris.add(data.getData());worker.execute(()->{try{reply(id,importImages(uris),null);}catch(Exception|OutOfMemoryError e){reply(id,null,"Could not import this image. Choose a smaller JPEG or PNG.");}});}
        else if(request==12){String id=importRequest;importRequest=null;if(result!=RESULT_OK||data==null||data.getData()==null){reply(id,new JSONObject(),null);return;}Uri uri=data.getData();worker.execute(()->{try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("Cannot read restore point");reply(id,tv.importRestore(TvController.read(in,2*1024*1024)),null);}catch(Exception e){reply(id,null,e.getMessage());}});}
        else if(request==11){String id=exportRequest,text=exportData;exportRequest=null;exportData=null;if(result!=RESULT_OK||data==null||data.getData()==null){reply(id,new JSONObject(),null);return;}Uri uri=data.getData();worker.execute(()->{try(OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null)throw new IOException("Could not save file");out.write(text.getBytes(StandardCharsets.UTF_8));reply(id,new JSONObject().put("saved",true),null);}catch(Exception e){reply(id,null,e.getMessage());}});}
    }
    @Override public void onBackPressed(){web.evaluateJavascript("window.studioBack()",null);}
    @Override public void onDestroy(){destroyed=true;tv.close();worker.shutdownNow();updateWorker.shutdownNow();web.removeJavascriptInterface("Studio");web.destroy();super.onDestroy();}
}
