package net.rishy.dehplugin;

import org.bukkit.Location;

public class Scene {
    private final Location firstSetLoc;
    private final int setDistance;
    private final int SET_WIDTH = 2;
    private final int numSets;

    public Scene(Location firstSetLoc, int setDistance, int numSets) {
        this.firstSetLoc = firstSetLoc;
        this.setDistance = setDistance;
        this.numSets = numSets;
    }

    public int getNumSets() {
        return numSets;
    }

    public Location getSetStartPos(int setNum) {
        return firstSetLoc.clone().add(0, 0, setDistance * (setNum - 1));
    }

    public Location getSetEndPos(int setNum) {
        Location start = getSetStartPos(setNum);
        return start.add(SET_WIDTH - 1, SET_WIDTH - 1, SET_WIDTH - 1);
    }
}
