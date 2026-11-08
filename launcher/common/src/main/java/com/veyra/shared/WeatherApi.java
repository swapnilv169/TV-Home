package com.veyra.shared;
import org.json.*;
import java.io.*;
import java.net.*;

/** Shared, bounded weather requests; the fetcher is injectable for offline regression tests. */
public final class WeatherApi {
    public interface Fetcher { JSONObject get(String url)throws Exception; }
    public static JSONObject json(String url)throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(5000);c.setReadTimeout(7000);c.setUseCaches(false);c.setRequestProperty("User-Agent","TVHomeStudio/1.1");c.setRequestProperty("Accept","application/json");
        try{int code=c.getResponseCode();if(code!=200)throw new IOException("HTTP "+code);ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=c.getInputStream()){byte[] b=new byte[2048];int n;while((n=in.read(b))!=-1){if(bytes.size()+n>65536)throw new IOException("Response too large");bytes.write(b,0,n);}}return new JSONObject(bytes.toString("UTF-8"));}finally{c.disconnect();}
    }
    public static void coordinates(double latitude,double longitude)throws IOException{if(!Double.isFinite(latitude)||!Double.isFinite(longitude)||Math.abs(latitude)>90||Math.abs(longitude)>180)throw new IOException("Invalid coordinates");}
    public static JSONObject current(JSONObject place,Fetcher fetch)throws Exception {
        double lat=place.getDouble("latitude"),lon=place.getDouble("longitude");coordinates(lat,lon);
        JSONObject response=fetch.get("https://api.open-meteo.com/v1/forecast?latitude="+lat+"&longitude="+lon+"&current=temperature_2m,weather_code&temperature_unit=celsius&timeformat=unixtime&forecast_days=1");
        JSONObject current=response.getJSONObject("current");double temperature=current.getDouble("temperature_2m");if(!Double.isFinite(temperature)||temperature< -90||temperature>65)throw new IOException("Invalid temperature");
        if(!"°C".equals(response.getJSONObject("current_units").getString("temperature_2m")))throw new IOException("Unexpected weather unit");
        if(Math.abs(System.currentTimeMillis()-current.getLong("time")*1000L)>21600000)throw new IOException("Weather timestamp is stale; check TV date and time");
        return current;
    }
}
