package net.rishy.dehplugin;

import org.bukkit.Bukkit;
import org.bukkit.Location;

public class CMDUtils {
    public static void cloneCMD(Location startPos1, Location endPos1, Location pos2) {
        String formattedString = String.format("minecraft:clone %d %d %d %d %d %d to minecraft:overworld %d %d %d",
                startPos1.blockX(),
                startPos1.blockY(),
                startPos1.blockZ(),
                endPos1.blockX(),
                endPos1.blockY(),
                endPos1.blockZ(),
                pos2.blockX(),
                pos2.blockY(),
                pos2.blockZ()
        );
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), formattedString);
    }
}
