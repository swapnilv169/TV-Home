package com.veyra.shared;
import org.json.*;import java.io.*;
public class WeatherApiTest {
 static int cases;static void check(boolean value){if(!value)throw new AssertionError();cases++;}
 static JSONObject place()throws Exception{return new JSONObject().put("name","Fixture City").put("latitude",12.34).put("longitude",56.78);}
 static JSONObject forecast()throws Exception{return new JSONObject().put("current_units",new JSONObject().put("temperature_2m","°C")).put("current",new JSONObject().put("temperature_2m",25.4).put("weather_code",3).put("time",System.currentTimeMillis()/1000));}
 public static void main(String[] args)throws Exception{
  check(WeatherApi.current(place(),url->{if(!url.contains("latitude=12.34&longitude=56.78"))throw new AssertionError("Wrong selected city");return forecast();}).getDouble("temperature_2m")==25.4);
  for(int mode=0;mode<3;mode++){final int m=mode;boolean rejected=false;try{WeatherApi.current(place(),url->{JSONObject f=forecast();if(m==0)f.getJSONObject("current_units").put("temperature_2m","F");if(m==1)f.getJSONObject("current").put("time",1);if(m==2)f.getJSONObject("current").put("temperature_2m",999);return f;});}catch(IOException e){rejected=true;}check(rejected);}
  for(double[] coordinates:new double[][]{{91,0},{0,181},{Double.NaN,0},{0,Double.POSITIVE_INFINITY}}){boolean rejected=false;try{WeatherApi.coordinates(coordinates[0],coordinates[1]);}catch(IOException e){rejected=true;}check(rejected);}
  boolean failed=false;try{WeatherApi.current(place(),url->{throw new IOException("Network unavailable");});}catch(IOException e){failed=e.getMessage().equals("Network unavailable");}check(failed);
  System.out.println("PASS: "+cases+" city-weather cases: selected coordinates, Celsius, wrong unit/stale/invalid temperature, geographic ranges, NaN/infinity, network failure. No IP-location API.");
 }
}
