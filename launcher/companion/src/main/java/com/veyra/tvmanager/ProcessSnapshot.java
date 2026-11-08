package com.veyra.tvmanager;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

/** A process snapshot includes cached and foreground processes, not just background work. */
final class ProcessSnapshot {
    static Set<String> names(String text)throws IOException {
        String[] lines=text.trim().split("\\r?\\n");
        if(lines.length==0)throw new IOException("Process listing unavailable");
        String[] header=lines[0].trim().split("\\s+");int column=-1;
        for(int i=0;i<header.length;i++)if(header[i].equals("NAME")||header[i].equals("CMD")||header[i].equals("CMDLINE"))column=i;
        if(column<0)throw new IOException("This Fire OS does not expose a recognized process listing");
        Set<String> result=new LinkedHashSet<>();
        for(int i=1;i<lines.length;i++){String[] row=lines[i].trim().split("\\s+");if(row.length>column)result.add(row[column]);}
        if(result.isEmpty())throw new IOException("Process listing unavailable; no app state inferred");
        return result;
    }
    static boolean contains(Set<String> names,String pkg){for(String name:names)if(name.equals(pkg)||name.startsWith(pkg+":"))return true;return false;}
}
