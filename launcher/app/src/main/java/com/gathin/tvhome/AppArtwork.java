package com.gathin.tvhome;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Rounds the artwork itself, with a focus outline and no surrounding card. */
final class AppArtwork extends Drawable {
    private final Drawable art;
    private final float radius, stroke;
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF image = new RectF();
    private final Path clip = new Path();
    private final Rect original = new Rect();
    private boolean focused;
    AppArtwork(Drawable art, float density, int accent) {
        this.art=art;radius=10*density;stroke=2*density;
        ring.setColor(accent);ring.setStyle(Paint.Style.STROKE);ring.setStrokeWidth(stroke);
    }
    @Override protected void onBoundsChange(Rect bounds) {
        float w=Math.max(1,art.getIntrinsicWidth()),h=Math.max(1,art.getIntrinsicHeight());
        float scale=Math.min(Math.max(0,bounds.width()-stroke)/w,Math.max(0,bounds.height()-stroke)/h);
        w*=scale;h*=scale;image.set(bounds.exactCenterX()-w/2,bounds.exactCenterY()-h/2,bounds.exactCenterX()+w/2,bounds.exactCenterY()+h/2);
        clip.reset();clip.addRoundRect(image,radius,radius,Path.Direction.CW);
    }
    @Override public void draw(Canvas canvas) {
        int save=canvas.save();canvas.clipPath(clip);original.set(art.getBounds());
        art.setBounds(Math.round(image.left),Math.round(image.top),Math.round(image.right),Math.round(image.bottom));
        art.draw(canvas);art.setBounds(original);canvas.restoreToCount(save);
        if(focused)canvas.drawRoundRect(image,radius,radius,ring);
    }
    @Override public boolean isStateful(){return true;}
    @Override protected boolean onStateChange(int[] states){boolean next=false;for(int s:states)if(s==android.R.attr.state_focused)next=true;if(focused==next)return false;focused=next;invalidateSelf();return true;}
    @Override public void setAlpha(int alpha){art.setAlpha(alpha);ring.setAlpha(alpha);invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){art.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
