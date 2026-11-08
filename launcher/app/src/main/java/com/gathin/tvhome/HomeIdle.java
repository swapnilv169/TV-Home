package com.gathin.tvhome;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.LinearLayout;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Runs only in the focused Home surface. No service, media polling or global timeout changes. */
final class HomeIdle {
    private final HomeSurface home;
    private boolean active, consumeKey, dismissing;
    private long touched;
    private FrameLayout shade;
    private TextView clock, day, date, weather;
    private LinearLayout information;
    private int position;
    private final Runnable tick=this::tick;
    HomeIdle(HomeSurface home){this.home=home;}
    void active(boolean value){
        if(active==value)return;
        active=value;home.setKeepScreenOn(value);
        if(value){touched=SystemClock.uptimeMillis();home.postDelayed(tick,HomeIdlePolicy.DIM_MS);}
        else stop();
    }
    void stop(){
        active=false;home.setKeepScreenOn(false);home.removeCallbacks(tick);hide(false);
    }
    boolean key(KeyEvent e){
        if(e.getAction()==KeyEvent.ACTION_DOWN){
            if(shade!=null)consumeKey=true;
            hide(true);touched=SystemClock.uptimeMillis();home.removeCallbacks(tick);
            if(active)home.postDelayed(tick,HomeIdlePolicy.DIM_MS);
        }
        boolean result=consumeKey;
        if(e.getAction()==KeyEvent.ACTION_UP)consumeKey=false;
        return result;
    }
    private void tick(){
        if(!active||!home.hasWindowFocus()||home.getWindowVisibility()!=View.VISIBLE)return;
        int action=HomeIdlePolicy.action(SystemClock.uptimeMillis()-touched);
        if(action==HomeIdlePolicy.SLEEP){
            // Releasing KEEP_SCREEN_ON is also the fallback if OS sleep is unavailable.
            stop();HomeRedirectService.sleep();return;
        }
        if(action==HomeIdlePolicy.DIM){
            boolean entering=shade==null;
            if(shade==null){
                shade=new FrameLayout(home.getContext());
                shade.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
                View contrast=new View(home.getContext());
                contrast.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                        new int[]{0xc908101c,0x8808101c,0x1808101c}));
                shade.addView(contrast,new FrameLayout.LayoutParams(-1,-1));
                information=new LinearLayout(home.getContext());information.setOrientation(LinearLayout.VERTICAL);
                day=label(16,0xffd8e4ef);day.setLetterSpacing(.16f);information.addView(day);
                clock=label(88,Color.WHITE);clock.setLetterSpacing(-.045f);
                LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.topMargin=dp(10);information.addView(clock,cp);
                date=label(24,0xfff0f4fa);LinearLayout.LayoutParams dt=new LinearLayout.LayoutParams(-1,-2);dt.topMargin=dp(6);information.addView(date,dt);
                weather=label(18,0xffd8e4ef);weather.setMaxLines(2);
                LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(-1,-2);wp.topMargin=dp(22);information.addView(weather,wp);
                FrameLayout.LayoutParams info=new FrameLayout.LayoutParams(Math.min(dp(520),home.getWidth()-dp(128)),-2,Gravity.BOTTOM|Gravity.START);
                info.leftMargin=dp(64);info.rightMargin=dp(64);info.bottomMargin=dp(62);shade.addView(information,info);
                home.addView(shade,new FrameLayout.LayoutParams(-1,-1));
            }
            Date now=new Date();Locale locale=Locale.getDefault();
            clock.setText(new SimpleDateFormat(android.text.format.DateFormat.is24HourFormat(home.getContext())?"HH:mm":"h:mm a",locale).format(now));
            day.setText(new SimpleDateFormat("EEEE",locale).format(now).toUpperCase(locale));
            date.setText(new SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(locale,"dMMMMyyyy"),locale).format(now));
            shade.setBackground(HomeWallpaper.background(home.getContext(),StudioConfig.idleWallpaper(home.getContext())));WeatherInfo.bind(weather);
            // Reuse the wallpaper bitmap and weather cache. Only update once per minute.
            int[][] spots={{0,0},{8,-6},{-6,-10},{6,4},{-8,6},{0,-4}};int[] spot=spots[position++%spots.length];
            information.setTranslationX(dp(spot[0]));information.setTranslationY(dp(spot[1]));
            if(entering&&StudioConfig.flag(home.getContext(),"animations")){
                shade.setTranslationY(home.getHeight());
                shade.animate().translationY(0).setDuration(340)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator(2f)).start();
            }
        }
        home.postDelayed(tick,60000);
    }
    private int dp(float value){return Math.round(value*home.getResources().getDisplayMetrics().density);}
    private TextView label(int size,int color){TextView v=new TextView(home.getContext());v.setTextSize(size);v.setTextColor(color);v.setIncludeFontPadding(false);v.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));v.setShadowLayer(dp(3),0,dp(1),0x66000000);return v;}
    private void hide(boolean animated){
        if(shade==null)return;
        if(!animated){removeShade();return;}
        if(dismissing)return;
        if(!active||!home.hasWindowFocus()||!StudioConfig.flag(home.getContext(),"animations")){removeShade();return;}
        dismissing=true;WeatherInfo.unbind(weather);
        shade.animate().cancel();
        shade.animate().translationY(-home.getHeight()).setDuration(260)
                .setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator())
                .withEndAction(this::removeShade).start();
    }
    private void removeShade(){
        if(shade!=null){shade.animate().withEndAction(null);shade.animate().cancel();WeatherInfo.unbind(weather);home.removeView(shade);}
        shade=null;clock=null;day=null;date=null;weather=null;information=null;dismissing=false;
    }
}
