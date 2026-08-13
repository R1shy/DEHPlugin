package net.rishy.dehplugin;

import org.bukkit.Bukkit;
import org.bukkit.Location;

public class DEHSetHandler {
    private static final int MAX_SET_NUMBER = 3;
    private static final int SET_WIDTH = 2;
    private static final int SET_GAP = 1;
    private final Location originOfSets;
    private final int setDistance;
    private int currentSetNumber;
    public Location STAGE_START_POS = new Location(Bukkit.getWorld("world"), 9, -59,45);

    public DEHSetHandler(Location originOfSets, int setDistance) {
        this.originOfSets = originOfSets;
        this.setDistance = setDistance;
        this.currentSetNumber = 1;
    }

    public int getCurrentSetNumber() {
        return this.currentSetNumber;
    }

    public void incrCurrentSetNumber() {
            this.currentSetNumber += 1;
    }

    public void decrCurrentSetNumber() {
            this.currentSetNumber -= 1;
    }

    private Location getSetStartPos(int setNum) {
        if (setNum == 1)
            return this.originOfSets;
        int zdist = this.setDistance * (setNum - 1);
        return new Location(Bukkit.getWorld("world"), originOfSets.blockX(), originOfSets.blockY(), originOfSets.blockZ() + zdist);
    }

    private Location getSetEndPos(int setNum) {
        Location sp = this.getSetStartPos(setNum);
        return new Location(Bukkit.getWorld("world"), sp.blockX() + SET_WIDTH - 1, sp.blockY() + SET_WIDTH - 1, sp.blockZ() + SET_WIDTH - 1);
    }

    public Location getPrevSetStartPos() {
        return this.getSetStartPos(this.currentSetNumber - 1);
    }

    public Location getPrevSetEndPos() {
        return this.getSetEndPos(this.currentSetNumber - 1);
    }

    public Location getNextSetStartPos() {
        return this.getSetStartPos(this.currentSetNumber + 1);
    }

    public Location getNextSetEndPos() {
        return this.getSetEndPos(this.currentSetNumber + 1);
    }

}
