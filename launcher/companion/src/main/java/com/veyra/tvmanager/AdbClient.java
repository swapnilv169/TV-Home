package com.veyra.tvmanager;

import com.cgutman.adblib.AdbCrypto;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Sequential ADB transport: one authenticated connection and one service at a time. */
public final class AdbClient implements Closeable {
    static final int CNXN=id("CNXN"), AUTH=id("AUTH"), OPEN=id("OPEN"), OKAY=id("OKAY"), WRTE=id("WRTE"), CLSE=id("CLSE");
    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;
    private int sequence=1, remote, local, maximum=65536;
    private boolean closed;
    private final ByteArrayOutputStream pending=new ByteArrayOutputStream();
    public AdbClient(String host,int port,AdbCrypto crypto)throws Exception{
        socket=new Socket();
        try{
            socket.connect(new InetSocketAddress(host,port),8000);socket.setSoTimeout(60000);socket.setTcpNoDelay(true);in=socket.getInputStream();out=socket.getOutputStream();
            send(CNXN,0x01000000,65536,"host::\0".getBytes(StandardCharsets.UTF_8));boolean signed=false,offered=false;
            while(true){Message m=receive();if(m.command==CNXN){maximum=Math.min(65536,m.arg1);if(maximum<1024)throw new IOException("Unsupported ADB payload");break;}
                if(m.command!=AUTH||m.arg0!=1||m.data.length!=20)throw new IOException("Unsupported ADB authentication. Use Fire TV ADB debugging, not a pairing port.");
                if(!signed){send(AUTH,2,0,crypto.signAdbTokenPayload(m.data));signed=true;}
                else if(!offered){send(AUTH,3,0,crypto.getAdbPublicKeyPayload());offered=true;}
                else throw new IOException("ADB authorization rejected. Accept this phone on the TV.");
            }
            socket.setSoTimeout(30000);
        }catch(Exception e){socket.close();throw e;}
    }
    static int id(String s){byte[] b=s.getBytes(StandardCharsets.US_ASCII);return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).getInt();}
    static int checksum(byte[] b){int total=0;for(byte v:b)total+=v&255;return total;}
    private void send(int command,int a,int b,byte[] data)throws IOException{
        ByteBuffer header=ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);header.putInt(command).putInt(a).putInt(b).putInt(data.length).putInt(checksum(data)).putInt(command^0xffffffff);out.write(header.array());out.write(data);out.flush();
    }
    private byte[] exact(int n)throws IOException{byte[] b=new byte[n];int offset=0;while(offset<n){int count=in.read(b,offset,n-offset);if(count<0)throw new EOFException("TV disconnected");offset+=count;}return b;}
    private Message receive()throws IOException{
        ByteBuffer h=ByteBuffer.wrap(exact(24)).order(ByteOrder.LITTLE_ENDIAN);Message m=new Message();m.command=h.getInt();m.arg0=h.getInt();m.arg1=h.getInt();int n=h.getInt(),sum=h.getInt(),magic=h.getInt();if(magic!=(m.command^0xffffffff)||n<0||n>1024*1024)throw new IOException("Invalid ADB frame");m.data=exact(n);if(checksum(m.data)!=sum)throw new IOException("ADB checksum mismatch");return m;
    }
    private void open(String service)throws IOException{
        if(closed)throw new IOException("Reconnect to your TV");local=sequence++;remote=0;pending.reset();send(OPEN,local,0,(service+'\0').getBytes(StandardCharsets.UTF_8));
        while(true){Message m=receive();if(m.arg1!=local)continue;if(m.command==CLSE)throw new IOException("TV refused "+service);if(m.command!=OKAY)throw new IOException("Unexpected ADB service response");remote=m.arg0;return;}
    }
    private void acknowledge(Message m)throws IOException{send(OKAY,local,m.arg0,new byte[0]);}
    private void write(byte[] data)throws IOException{
        for(int offset=0;offset<data.length;offset+=maximum){send(WRTE,local,remote,Arrays.copyOfRange(data,offset,Math.min(data.length,offset+maximum)));while(true){Message m=receive();if(m.arg1!=local)continue;if(m.command==OKAY)break;if(m.command==CLSE)throw new IOException("TV closed transfer");if(m.command!=WRTE)throw new IOException("Unexpected transfer response");if(pending.size()+m.data.length>4*1024*1024)throw new IOException("Transfer response too large");pending.write(m.data);acknowledge(m);}}
    }
    public synchronized String shell(String command)throws IOException{
        try{open("shell:"+command);ByteArrayOutputStream result=new ByteArrayOutputStream();long deadline=System.nanoTime()+120000000000L;
            while(true){if(System.nanoTime()>deadline)throw new IOException("Command timed out");Message m=receive();if(m.arg1!=local)continue;if(m.command==CLSE){send(CLSE,local,remote,new byte[0]);return result.toString("UTF-8");}if(m.command==WRTE){if(result.size()+m.data.length>4*1024*1024)throw new IOException("Command output too large");result.write(m.data);acknowledge(m);}else if(m.command!=OKAY)throw new IOException("Unexpected ADB response");}
        }catch(IOException e){close();throw e;}
    }
    private static byte[] sync(String id,byte[] data,int length){ByteBuffer b=ByteBuffer.allocate(8+data.length).order(ByteOrder.LITTLE_ENDIAN);b.putInt(id(id)).putInt(length).put(data);return b.array();}
    public synchronized void push(InputStream source,String path,long length)throws IOException{
        if(!path.matches("/data/local/tmp/tvstudio-[a-zA-Z0-9_.-]+"))throw new IOException("Invalid upload path");
        try{open("sync:");byte[] name=(path+",33188").getBytes(StandardCharsets.UTF_8);write(sync("SEND",name,name.length));byte[] buffer=new byte[65536];int n;long count=0;while((n=source.read(buffer))!=-1){count+=n;if(count>length||count>20*1024*1024)throw new IOException("Upload size mismatch");write(sync("DATA",Arrays.copyOf(buffer,n),n));}if(count!=length)throw new IOException("Incomplete upload");write(sync("DONE",new byte[0],(int)(System.currentTimeMillis()/1000)));
            while(pending.size()<8){Message m=receive();if(m.arg1!=local)continue;if(m.command==CLSE)throw new IOException("Transfer closed before completion");if(m.command==WRTE){pending.write(m.data);acknowledge(m);}}
            byte[] response=pending.toByteArray();ByteBuffer b=ByteBuffer.wrap(response).order(ByteOrder.LITTLE_ENDIAN);int status=b.getInt();if(status!=id("OKAY"))throw new IOException("TV rejected file transfer");send(CLSE,local,remote,new byte[0]);
        }catch(IOException e){close();throw e;}
    }
    public boolean isOpen(){return !closed&&!socket.isClosed();}
    @Override public void close(){closed=true;try{socket.close();}catch(IOException ignored){}}
    private static class Message{int command,arg0,arg1;byte[] data;}
}
