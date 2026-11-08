package com.veyra.tvmanager;
import android.app.Instrumentation;
import android.os.Bundle;
import android.content.Intent;
import android.webkit.WebView;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Runs only in the QA emulator, against an in-process fake TV, never a LAN device. */
public final class StudioInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    @Override public void onStart(){Bundle results=new Bundle();try{
        // Verify the actual installed WebView can execute the shipped frontend.
        MainActivity activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        java.lang.reflect.Field field=MainActivity.class.getDeclaredField("web");field.setAccessible(true);WebView web=(WebView)field.get(activity);
        boolean ready=false;for(int i=0;i<30&&!ready;i++){Thread.sleep(200);CountDownLatch latch=new CountDownLatch(1);String[] result={""};runOnMainSync(()->web.evaluateJavascript("document.getElementById('main') ? document.getElementById('main').textContent : ''",value->{result[0]=value;latch.countDown();}));latch.await(2,TimeUnit.SECONDS);ready=result[0].contains("Connect your Fire TV");}check(ready,"Phone UI failed on the installed system WebView");
        try(MockTv mock=new MockTv();TvController tv=new TvController(getTargetContext(),message->{})){
            JSONObject status=tv.connect("127.0.0.1",mock.server.getLocalPort());check(status.getJSONArray("cleanup").length()==2,"Wrong cleanup inventory");check(!status.getBoolean("installed"),"Fixture unexpectedly has launcher");
            String serial=mock.serial;JSONObject clean=tv.cleanup(new JSONArray().put("com.amazon.dp.logger"));check(mock.states.get("com.amazon.dp.logger")==3,"Cleanup not applied");check(mock.states.get("com.amazon.sneakpeek")==3,"Unselected app changed");
            tv.packageAction("com.amazon.sneakpeek","enable");check(mock.states.get("com.amazon.sneakpeek")==1,"Enable failed");
            JSONObject restricted=tv.updates(true);check(mock.states.get("com.amazon.device.software.ota")==0,"Protected updater altered");check(mock.ops.get("com.amazon.device.software.ota").equals("ignore"),"Background restriction not applied");check(restricted.getJSONArray("results").toString().contains("refused"),"Partial result falsely reported success");
            JSONObject saved=new JSONObject(tv.export());check(saved.getJSONObject("packages").getInt("com.amazon.dp.logger")==0,"Original default state lost");check(saved.getJSONObject("packages").getInt("com.amazon.sneakpeek")==3,"Pre-existing disabled state lost");
            tv.restore();check(mock.states.get("com.amazon.dp.logger")==0&&mock.states.get("com.amazon.sneakpeek")==3,"Restore did not preserve baseline");check(mock.ops.get("com.amazon.device.software.ota").equals("default"),"App-op baseline not restored");
            boolean refused=false;try{tv.packageAction("com.amazon.tv.launcher","disable");}catch(Exception e){refused=true;}check(refused,"Core launcher was not protected");
            mock.serial="different-tv";refused=false;try{tv.cleanup(new JSONArray().put("com.amazon.dp.logger"));}catch(Exception e){refused=true;}check(refused,"Device identity change accepted");mock.serial=serial;
        }
        // An ordinary phone-app UID must never access the launcher's shell-only provider.
        boolean denied=false;try{getTargetContext().getContentResolver().call(android.net.Uri.parse("content://com.gathin.tvhome.studio"),"read",null,null);}catch(SecurityException e){denied=true;}check(denied,"Provider accepted ordinary app UID");
        results.putString("stream","PASS: shipped UI on Android system WebView; native ADB-to-controller connect, selected cleanup, original-state ledger, protected updater partial results, app-op restore, pre-existing disable preservation, core package guard, changed-device refusal, provider access control.\n");finish(0,results);
    }catch(Throwable error){java.io.StringWriter trace=new java.io.StringWriter();error.printStackTrace(new java.io.PrintWriter(trace));results.putString("stream",trace.toString());finish(1,results);}}
    static final class MockTv implements AutoCloseable {
        final ServerSocket server=new ServerSocket(0);
        final Map<String,Integer> states=new ConcurrentHashMap<>();
        final Map<String,String> ops=new ConcurrentHashMap<>();
        volatile String serial="fixture-"+System.nanoTime();
        final Thread thread;
        Socket socket;
        MockTv()throws IOException{states.put("com.amazon.tv.launcher",0);states.put("com.amazon.dp.logger",0);states.put("com.amazon.sneakpeek",3);states.put("com.amazon.tv.forcedotaupdater.v2",0);states.put("com.amazon.device.software.ota",0);ops.put("com.amazon.device.software.ota","default");ops.put("com.amazon.tv.forcedotaupdater.v2","default");thread=new Thread(()->{try{socket=server.accept();socket.setTcpNoDelay(true);socket.setSoTimeout(15000);InputStream in=socket.getInputStream();OutputStream out=socket.getOutputStream();read(in);send(out,"CNXN",0x01000000,4096,"device::\0");while(!socket.isClosed()){Frame f=read(in);if(f.command==AdbClient.CLSE)continue;if(f.command!=AdbClient.OPEN)throw new IOException("Unexpected frame");String request=new String(f.data,StandardCharsets.UTF_8);String cmd=request.substring(6,request.indexOf("; printf "));int local=f.a;send(out,"OKAY",101,local,"");String[] response=execute(cmd);send(out,"WRTE",101,local,response[0]+"\n__TVSTUDIO_RC:"+response[1]+"\n");read(in);send(out,"CLSE",101,local,"");read(in);}}catch(Exception ignored){}});thread.start();}
        String[] execute(String cmd){String output="";String code="0";
            if(cmd.equals("getprop ro.product.manufacturer"))output="Toshiba";
            else if(cmd.equals("getprop ro.product.model"))output="Fixture Fire TV";
            else if(cmd.equals("getprop ro.build.version.sdk"))output="28";
            else if(cmd.equals("getprop ro.build.version.release"))output="9";
            else if(cmd.equals("getprop ro.build.display.id"))output="fixture-build";
            else if(cmd.equals("getprop ro.serialno"))output=serial;
            else if(cmd.equals("pm list packages --user 0")){StringBuilder s=new StringBuilder();for(String pkg:states.keySet())s.append("package:").append(pkg).append('\n');output=s.toString();}
            else if(cmd.equals("pm list packages -3 --user 0"))output="";
            else if(cmd.startsWith("dumpsys package "))output="User 0: installed=true hidden=false stopped=false enabled="+states.get(cmd.substring(16));
            else if(cmd.equals("settings get secure enabled_accessibility_services"))output="other/service";
            else if(cmd.startsWith("cmd appops get ")){String pkg=cmd.split(" ")[3];String mode=ops.get(pkg);output=mode.equals("default")?"No operations.":"RUN_ANY_IN_BACKGROUND: "+mode;}
            else if(cmd.startsWith("cmd appops set ")){String[] split=cmd.split(" ");ops.put(split[3],split[5]);}
            else if(cmd.startsWith("pm ")){String[] split=cmd.split(" ");String pkg=split[4];if(pkg.equals("com.amazon.device.software.ota")&&split[1].equals("disable-user")){output="SecurityException: protected updater";code="1";}else{int state=Arrays.asList("default-state","enable","disable","disable-user","disable-until-used").indexOf(split[1]);if(state<0)throw new IllegalArgumentException(cmd);states.put(pkg,state);output="Package "+pkg+" new state";}}
            else {output="Unexpected command: "+cmd;code="1";}
            return new String[]{output,code};
        }
        static Frame read(InputStream in)throws Exception{byte[] bytes=exact(in,24);ByteBuffer b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);Frame f=new Frame();f.command=b.getInt();f.a=b.getInt();b.getInt();int size=b.getInt();f.data=exact(in,size);return f;}
        static byte[] exact(InputStream in,int count)throws Exception{byte[] bytes=new byte[count];int offset=0;while(offset<count){int n=in.read(bytes,offset,count-offset);if(n<0)throw new EOFException();offset+=n;}return bytes;}
        static void send(OutputStream out,String command,int a,int b,String text)throws Exception{byte[] data=text.getBytes(StandardCharsets.UTF_8);int c=AdbClient.id(command);ByteBuffer h=ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);h.putInt(c).putInt(a).putInt(b).putInt(data.length).putInt(AdbClient.checksum(data)).putInt(c^0xffffffff);out.write(h.array());out.write(data);out.flush();}
        @Override public void close()throws Exception{server.close();if(socket!=null)socket.close();thread.join(2000);}
        static class Frame{int command,a;byte[] data;}
    }
}
