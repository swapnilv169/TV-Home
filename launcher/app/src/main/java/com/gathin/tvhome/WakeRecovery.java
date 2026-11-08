package com.gathin.tvhome;
/** Bounded wake checks act only on Home; explicit apps and Settings retain the screen. */
final class WakeRecovery {
 static final int WAIT=0,HOME=1,STOP=2;
 static int decide(String pkg,String cls,String own,boolean settingsGrace,boolean wake){
  if(pkg.equals(own))return HOME;
  if(pkg.equals("com.amazon.tv.launcher"))return cls.contains("SettingsActivity")||settingsGrace?STOP:cls.contains("HomeActivity")||wake?HOME:WAIT;
  if(pkg.isEmpty()||pkg.equals("null")||pkg.equals("android")||pkg.equals("com.android.systemui")||pkg.contains("dream")||pkg.contains("screensaver"))return WAIT;
  return STOP;
 }
}
