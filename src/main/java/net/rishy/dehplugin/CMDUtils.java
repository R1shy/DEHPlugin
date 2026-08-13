package net.rishy.dehplugin;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public class CMDUtils {
    public static void cloneCMD(Location startPos1, Location endPos1, Location pos2) {
        loadRegion(startPos1, endPos1);
        loadRegion(pos2, pos2);
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

    private static void loadRegion(Location a, Location b) {
        World world = a.getWorld();
        int minX = Math.min(a.blockX(), b.blockX()) >> 4;
        int maxX = Math.max(a.blockX(), b.blockX()) >> 4;
        int minZ = Math.min(a.blockZ(), b.blockZ()) >> 4;
        int maxZ = Math.max(a.blockZ(), b.blockZ()) >> 4;
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                world.getChunkAt(cx, cz);
            }
        }
    }
}
