package com.gathin.tvhome;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.View;

/** Static, 160-pixel-wide wallpaper blur. Never captures apps or runs a blur loop. */
final class CinemaDock extends Drawable {
    private final View home, dock;
    private final String style;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Path clip=new Path();
    private final RectF destination=new RectF();
    private final int[] homePosition=new int[2],dockPosition=new int[2];
    private Bitmap blurred;
    private Drawable source;
    private int width,height;
    private long wallpaperRevision=-1;
    CinemaDock(View home,View dock,String style){this.home=home;this.dock=dock;this.style=style;}
    @Override protected void onBoundsChange(Rect b){float r=24*home.getResources().getDisplayMetrics().density;clip.reset();clip.addRoundRect(new RectF(b),r,r,Path.Direction.CW);}
    private void prepare(){
        Drawable background=home.getBackground();int w=home.getWidth(),h=home.getHeight();
        long revision=HomeWallpaper.revision();if(background==source&&w==width&&h==height&&wallpaperRevision==revision)return;
        wallpaperRevision=revision;
        source=background;width=w;height=h;blurred=null;
        if(background==null||w<=0||h<=0)return;
        int bw=160,bh=Math.max(1,Math.min(160,Math.round(160f*h/w)));
        Bitmap small=Bitmap.createBitmap(bw,bh,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(small);canvas.scale((float)bw/w,(float)bh/h);
        Rect old=new Rect(background.getBounds());background.setBounds(0,0,w,h);background.draw(canvas);background.setBounds(old);
        int[] pixels=new int[bw*bh],out=new int[bw*bh];small.getPixels(pixels,0,bw,0,0,bw,bh);
        // Two separable box passes on the thumbnail; retained bitmap is about 56 KB at 16:9.
        for(int pass=0;pass<4;pass++){
            boolean horizontal=pass%2==0;
            for(int y=0;y<bh;y++)for(int x=0;x<bw;x++){
                int r=0,g=0,b=0;for(int k=-3;k<=3;k++){int xx=horizontal?Math.max(0,Math.min(bw-1,x+k)):x,yy=horizontal?y:Math.max(0,Math.min(bh-1,y+k));int color=pixels[yy*bw+xx];r+=(color>>16)&255;g+=(color>>8)&255;b+=color&255;}
                out[y*bw+x]=0xff000000|((r/7)<<16)|((g/7)<<8)|(b/7);
            }int[] swap=pixels;pixels=out;out=swap;
        }
        small.setPixels(pixels,0,bw,0,0,bw,bh);blurred=small;
    }
    @Override public void draw(Canvas canvas){
        int save=canvas.save();canvas.clipPath(clip);
        if(style.equals("blur")){
            prepare();if(blurred!=null){home.getLocationOnScreen(homePosition);dock.getLocationOnScreen(dockPosition);float x=homePosition[0]-dockPosition[0],y=homePosition[1]-dockPosition[1];destination.set(x,y,x+width,y+height);canvas.drawBitmap(blurred,null,destination,paint);}
            canvas.drawColor(0x88101929);
        }else canvas.drawColor(style.equals("light")?0xee596779:0xe6161c2a);
        canvas.restoreToCount(save);
    }
    @Override public void setAlpha(int alpha){paint.setAlpha(alpha);invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
