package com.veyra.tvmanager;
import com.cgutman.adblib.AdbCrypto;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public class AdbClientTest {
 static class Frame {int cmd,a,b;byte[] data;}
 static byte[] bytes(String s){return s.getBytes(StandardCharsets.UTF_8);}
 static Frame read(InputStream in)throws Exception{byte[] header=in.readNBytes(24);if(header.length!=24)throw new EOFException();ByteBuffer h=ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);Frame f=new Frame();f.cmd=h.getInt();f.a=h.getInt();f.b=h.getInt();int size=h.getInt(),sum=h.getInt(),magic=h.getInt();if(magic!=(f.cmd^0xffffffff)||size<0||size>65536)throw new AssertionError("bad client header");f.data=in.readNBytes(size);if(f.data.length!=size||sum!=AdbClient.checksum(f.data))throw new AssertionError("bad client payload");return f;}
 static void send(OutputStream out,String cmd,int a,int b,byte[] data,boolean bad)throws Exception{ByteBuffer h=ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);int id=AdbClient.id(cmd);h.putInt(id).putInt(a).putInt(b).putInt(data.length).putInt(AdbClient.checksum(data)+(bad?1:0)).putInt(id^0xffffffff);for(byte value:h.array())out.write(value);for(int i=0;i<data.length;i+=17)out.write(data,i,Math.min(17,data.length-i));out.flush();}
 static void expect(boolean good,String message){if(!good)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception{
  AdbCrypto crypto=AdbCrypto.generateAdbKeyPair(data->Base64.getEncoder().encodeToString(data));ExecutorService executor=Executors.newSingleThreadExecutor();
  try(ServerSocket server=new ServerSocket(0)){
   Future<?> peer=executor.submit(()->{try(Socket socket=server.accept()){socket.setSoTimeout(10000);InputStream in=socket.getInputStream();OutputStream out=socket.getOutputStream();Frame first=read(in);expect(first.cmd==AdbClient.CNXN,"CNXN missing");byte[] token=new byte[20];Arrays.fill(token,(byte)7);send(out,"AUTH",1,0,token,false);Frame signature=read(in);expect(signature.cmd==AdbClient.AUTH&&signature.a==2&&signature.data.length==256,"RSA signature missing");send(out,"AUTH",1,0,token,false);Frame pub=read(in);expect(pub.a==3&&pub.data[pub.data.length-1]==0,"public key not offered");send(out,"CNXN",0x01000000,4096,bytes("device::\0"),false);
    Frame open=read(in);expect(new String(open.data,StandardCharsets.UTF_8).equals("shell:echo test\0"),"wrong shell");int local=open.a;send(out,"OKAY",101,local,new byte[0],false);send(out,"WRTE",101,local,bytes("hello\n"),false);expect(read(in).cmd==AdbClient.OKAY,"shell not acknowledged");send(out,"CLSE",101,local,new byte[0],false);expect(read(in).cmd==AdbClient.CLSE,"shell close not acknowledged");
    open=read(in);expect(new String(open.data,StandardCharsets.UTF_8).equals("sync:\0"),"sync not opened");local=open.a;send(out,"OKAY",102,local,new byte[0],false);ByteArrayOutputStream sync=new ByteArrayOutputStream();int expected=0;boolean done=false;
    while(!done){Frame write=read(in);expect(write.cmd==AdbClient.WRTE&&write.data.length<=4096,"unbounded write");sync.write(write.data);byte[] data=sync.toByteArray();int offset=0;
      while(data.length-offset>=8){ByteBuffer b=ByteBuffer.wrap(data,offset,8).order(ByteOrder.LITTLE_ENDIAN);int id=b.getInt(),length=b.getInt();if(id==AdbClient.id("DONE")){done=true;break;}if(data.length-offset-8<length)break;if(id==AdbClient.id("DATA"))expected+=length;offset+=8+length;}
      if(offset>0){sync.reset();sync.write(data,offset,data.length-offset);}
      if(done){expect(expected==50000,"incomplete file");byte[] response=ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(AdbClient.id("OKAY")).putInt(0).array();send(out,"WRTE",102,local,response,false);expect(read(in).cmd==AdbClient.OKAY,"early sync reply not acknowledged");}
      send(out,"OKAY",102,local,new byte[0],false);
    }
    expect(read(in).cmd==AdbClient.CLSE,"sync not closed");send(out,"CLSE",102,local,new byte[0],false);
    open=read(in);local=open.a;send(out,"OKAY",103,local,new byte[0],false);send(out,"WRTE",103,local,bytes("tampered"),true);
   }catch(Exception e){throw new RuntimeException(e);}});
   try(AdbClient client=new AdbClient("127.0.0.1",server.getLocalPort(),crypto)){
    expect(client.shell("echo test").equals("hello\n"),"shell result wrong");byte[] file=new byte[50000];new Random(42).nextBytes(file);client.push(new ByteArrayInputStream(file),"/data/local/tmp/tvstudio-test.apk",file.length);
    boolean rejected=false;try{client.push(new ByteArrayInputStream(new byte[0]),"/system/anything",0);}catch(IOException e){rejected=true;}expect(rejected,"arbitrary upload path accepted");
    rejected=false;try{client.shell("corrupt");}catch(IOException e){rejected=e.getMessage().contains("checksum");}expect(rejected&&!client.isOpen(),"corrupt frame accepted or connection left open");
   }
   peer.get(15,TimeUnit.SECONDS);
  }finally{executor.shutdownNow();}
  expect(TvController.parseState("User 0: installed=true hidden=false stopped=false enabled=3\nUser 10: enabled=0")==3,"wrong user state");
  boolean rejected=false;try{TvController.parseState("User 10: enabled=0");}catch(IOException e){rejected=true;}expect(rejected,"missing user 0 accepted");
  expect(TvController.quote("a'b").equals("'a'\\''b'"),"shell quoting broken");
  expect(TvController.servicePresent("other/service:com.gathin.tvhome/.HomeRedirectService"),"short component not recognized");
  expect(!TvController.servicePresent("other/com.gathin.tvhome.HomeRedirectService"),"partial component accepted");
  for(int i=0;i<=4;i++)expect(!TvController.modeForState(i).isEmpty(),"restore state missing");
  System.out.println("PASS: RSA auth/key offer, fragmented ADB frames, shell lifecycle, 50KB sync transfer, early sync response, upload path restriction, corrupt-frame disconnect, user-scoped state parsing, shell quoting, Home service identity, all five restore states.");
 }
}
