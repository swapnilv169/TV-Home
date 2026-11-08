package com.gathin.tvhome;
import android.content.*;
import android.media.tv.*;
import java.util.*;
final class TvInputs {
    static List<TvInputInfo> list(Context c){try{TvInputManager manager=(TvInputManager)c.getSystemService(Context.TV_INPUT_SERVICE);ArrayList<TvInputInfo> result=new ArrayList<>();if(manager!=null)for(TvInputInfo input:manager.getTvInputList())if(input.isPassthroughInput()&&!input.isHidden(c))result.add(input);return result;}catch(RuntimeException e){return Collections.emptyList();}}
    static boolean available(Context c){return !list(c).isEmpty();}
    static boolean visible(Context c){String mode=StudioConfig.theme(c).optString("inputs","auto");return mode.equals("show")||(mode.equals("auto")&&available(c));}
    static Intent open(TvInputInfo input){return new Intent(Intent.ACTION_VIEW,TvContract.buildChannelUriForPassthroughInput(input.getId()));}
}
