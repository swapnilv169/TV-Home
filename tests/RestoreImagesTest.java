package com.veyra.tvmanager;
import org.json.*;

public final class RestoreImagesTest {
    private static int cases;
    private static void check(boolean value){if(!value)throw new AssertionError("Restore image case "+cases);cases++;}
    private static JSONObject image(String id,boolean staged)throws Exception{return new JSONObject().put("id",id).put("staged",staged);}
    public static void main(String[] args)throws Exception{
        JSONObject original=new JSONObject().put("idleWallpaper","removed").put("themes",new JSONArray()
            .put(new JSONObject().put("id","classic").put("wallpapers",new JSONArray().put("retained").put("removed")))
            .put(new JSONObject().put("id","cinema").put("wallpapers",new JSONArray().put("staging"))));
        String before=original.toString();JSONArray results=new JSONArray();
        JSONObject restored=TvController.restoredConfig(original,new JSONObject().put("images",new JSONArray().put(image("retained",false)).put(image("staging",true))),results);
        check(original.toString().equals(before));
        check(restored.getString("idleWallpaper").isEmpty());
        check(restored.getJSONArray("themes").getJSONObject(0).getJSONArray("wallpapers").length()==1);
        check(restored.getJSONArray("themes").getJSONObject(0).getJSONArray("wallpapers").getString(0).equals("retained"));
        check(restored.getJSONArray("themes").getJSONObject(1).getJSONArray("wallpapers").length()==0);
        check(results.length()==1&&results.getString(0).startsWith("2 unavailable"));
        for(JSONObject unavailable:new JSONObject[]{null,new JSONObject()}){
            results=new JSONArray();check(TvController.restoredConfig(original,unavailable,results).toString().equals(before));check(results.getString(0).contains("references retained"));
        }
        restored=TvController.restoredConfig(original,new JSONObject().put("images",new JSONArray()),new JSONArray());
        check(restored.getJSONArray("themes").getJSONObject(0).getJSONArray("wallpapers").length()==0);
        results=new JSONArray();restored=TvController.restoredConfig(original,new JSONObject().put("images",new JSONArray().put(image("retained",false)).put(image("removed",false)).put(image("staging",false))),results);
        check(restored.toString().equals(before)&&results.length()==0);
        boolean rejected=false;try{TvController.restoredConfig(original,new JSONObject().put("images",new JSONArray().put(new JSONObject().put("id","retained"))),new JSONArray());}catch(JSONException e){rejected=true;}
        check(rejected);check(original.toString().equals(before));
        System.out.println("PASS: "+cases+" restore wallpaper cases");
    }
}
