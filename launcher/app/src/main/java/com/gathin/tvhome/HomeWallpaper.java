package com.gathin.tvhome;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.view.*;
import java.io.File;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.*;

/** Decode off the UI thread; retain at most two cached Home/idle images. */
final class HomeWallpaper {
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final ExecutorService DECODER=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"home-wallpaper");t.setDaemon(true);return t;});
    private static final LinkedHashMap<String,Entry> CACHE=new LinkedHashMap<>(4,.75f,true);
    private static long revision;
    static long revision(){return revision;}
    private static final class Entry {Bitmap image;boolean done;final ArrayList<WeakReference<Wallpaper>> views=new ArrayList<>();}
    static Drawable background(Context context){return background(context,StudioConfig.wallpaper(context));}
    static Drawable background(Context context,String id){
        String layout=StudioConfig.layout(context);Wallpaper view=new Wallpaper(layout);
        if(id.isEmpty())return view;
        File file=StudioConfig.image(context,id);String key=id+":"+file.lastModified();Entry entry;
        synchronized(CACHE){
            entry=CACHE.get(key);
            if(entry==null){entry=new Entry();CACHE.put(key,entry);while(CACHE.size()>2)CACHE.remove(CACHE.keySet().iterator().next());Entry loading=entry;
                DECODER.execute(()->{Bitmap image=null;try{android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getPath(),options);
                    if(options.outWidth>0&&options.outHeight>0){options.inSampleSize=1;while(options.outWidth/options.inSampleSize>1920||options.outHeight/options.inSampleSize>1080)options.inSampleSize*=2;options.inJustDecodeBounds=false;options.inPreferredConfig=Bitmap.Config.RGB_565;image=BitmapFactory.decodeFile(file.getPath(),options);}
                }catch(RuntimeException|OutOfMemoryError ignored){}finally{Bitmap result=image;MAIN.post(()->{loading.image=result;loading.done=true;revision++;for(WeakReference<Wallpaper> ref:loading.views){Wallpaper target=ref.get();if(target!=null){target.invalidateSelf();Drawable.Callback cb=target.getCallback();if(cb instanceof View)invalidateTree((View)cb);}}loading.views.clear();});}});
            }
            view.entry=entry;if(!entry.done)entry.views.add(new WeakReference<>(view));
        }
        return view;
    }
    private static void invalidateTree(View view){view.invalidate();if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)invalidateTree(group.getChildAt(i));}}
    private static final class Wallpaper extends Drawable {
        Entry entry;final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);final RectF destination=new RectF();
        final GradientDrawable fallback,shade=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x3007101e,0x9907101e});
        Wallpaper(String layout){fallback=new GradientDrawable(GradientDrawable.Orientation.TL_BR,layout.equals("cinema")?new int[]{0xff222a40,0xff151021}:layout.equals("compact")?new int[]{0xff172945,0xff0b1426}:new int[]{0xff102534,0xff080f1b});}
        @Override protected void onBoundsChange(Rect b){fallback.setBounds(b);shade.setBounds(b);}
        @Override public void draw(Canvas canvas){Rect b=getBounds();fallback.draw(canvas);Bitmap image=entry==null?null:entry.image;if(image!=null){float scale=Math.max((float)b.width()/image.getWidth(),(float)b.height()/image.getHeight());float w=image.getWidth()*scale,h=image.getHeight()*scale;destination.set(b.exactCenterX()-w/2,b.exactCenterY()-h/2,b.exactCenterX()+w/2,b.exactCenterY()+h/2);int count=canvas.save();canvas.clipRect(b);canvas.drawBitmap(image,null,destination,paint);canvas.restoreToCount(count);}shade.draw(canvas);}
        @Override public void setAlpha(int alpha){paint.setAlpha(alpha);fallback.setAlpha(alpha);shade.setAlpha(alpha);invalidateSelf();}
        @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
        @Override public int getOpacity(){return PixelFormat.OPAQUE;}
    }
}
