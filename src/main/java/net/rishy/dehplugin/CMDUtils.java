package net.rishy.dehplugin;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.World;
import org.bukkit.Location;

import java.util.logging.Logger;

public class CMDUtils {
    private static final Logger LOGGER = Logger.getLogger("DEHPlugin");

    public static void cloneCMD(Location startPos1, Location endPos1, Location pos2) {
        loadRegion(startPos1, endPos1);

        org.bukkit.World bukkitWorld = startPos1.getWorld();
        World weWorld = BukkitAdapter.adapt(bukkitWorld);
        CuboidRegion region = new CuboidRegion(weWorld,
                BlockVector3.at(startPos1.blockX(), startPos1.blockY(), startPos1.blockZ()),
                BlockVector3.at(endPos1.blockX(), endPos1.blockY(), endPos1.blockZ()));

        // The clipboard origin is the set's start corner, so the paste lands that
        // exact corner on stage-start-pos (the set extends from it in -x/-z/+y,
        // matching the source layout orientation).
        BlockVector3 origin = BlockVector3.at(startPos1.blockX(), startPos1.blockY(), startPos1.blockZ());
        BlockVector3 to = BlockVector3.at(pos2.blockX(), pos2.blockY(), pos2.blockZ());

        // chunk-load the full destination box
        BlockVector3 destMin = to.add(region.getMinimumPoint().subtract(origin));
        BlockVector3 destMax = to.add(region.getMaximumPoint().subtract(origin));
        loadRegion(new Location(bukkitWorld, destMin.x(), destMin.y(), destMin.z()),
                new Location(bukkitWorld, destMax.x(), destMax.y(), destMax.z()));

        LOGGER.info("clone: region " + region.getMinimumPoint() + " -> " + region.getMaximumPoint()
                + " (" + region.getArea() + " cells) pasting into " + destMin + " -> " + destMax);

        BlockArrayClipboard clipboard = new BlockArrayClipboard(region);
        clipboard.setOrigin(origin);

        try (EditSession source = WorldEdit.getInstance().newEditSessionBuilder()
                .world(weWorld)
                .maxBlocks(-1)
                .build()) {
            ForwardExtentCopy copy = new ForwardExtentCopy(source, region, clipboard, region.getMinimumPoint());
            copy.setCopyingEntities(false);
            copy.setCopyingBiomes(false);
            Operations.complete(copy);
        } catch (Exception e) {
            LOGGER.severe("DEHPlugin copy failed: " + e);
            return;
        }

        int copied = 0;
        for (com.sk89q.worldedit.math.BlockVector3 v : clipboard.getRegion()) {
            com.sk89q.worldedit.world.block.BlockState b = clipboard.getBlock(v);
            if (b != null && !b.getBlockType().getMaterial().isAir()) {
                copied++;
            }
        }
        LOGGER.info("clone: copied " + copied + " non-air blocks into clipboard");

        try (EditSession dest = WorldEdit.getInstance().newEditSessionBuilder()
                .world(weWorld)
                .maxBlocks(-1)
                .build()) {
            ClipboardHolder holder = new ClipboardHolder(clipboard);
            Operation paste = holder.createPaste(dest)
                    .to(to)
                    .ignoreAirBlocks(false)
                    .build();
            Operations.complete(paste);
            LOGGER.info("clone: paste operation completed");
        } catch (Exception e) {
            LOGGER.severe("DEHPlugin paste failed: " + e);
        }

        org.bukkit.block.Block srcSample = bukkitWorld.getBlockAt(startPos1.getBlockX(), startPos1.getBlockY(), startPos1.getBlockZ());
        org.bukkit.block.Block dstSample = bukkitWorld.getBlockAt(pos2.getBlockX(), pos2.getBlockY(), pos2.getBlockZ());
        LOGGER.info("clone: source sample " + srcSample.getType() + " | stage corner sample " + dstSample.getType());
    }

    private static void loadRegion(Location a, Location b) {
        org.bukkit.World world = a.getWorld();
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
