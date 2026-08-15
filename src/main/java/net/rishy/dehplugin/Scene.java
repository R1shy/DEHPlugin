package net.rishy.dehplugin;

import org.bukkit.Location;

public class Scene {
    private final Location firstSetLoc;
    private final int setDistance;
    private final int setWidth;
    private final int numSets;
    private final String setDirection;

    public Scene(Location firstSetLoc, int setDistance, int setWidth, int numSets, String setDirection) {
        this.firstSetLoc = firstSetLoc;
        this.setDistance = setDistance;
        this.setWidth = setWidth;
        this.numSets = numSets;
        this.setDirection = setDirection;
    }

    public int getNumSets() {
        return numSets;
    }

    public Location getSetStartPos(int setNum) {
        return move(firstSetLoc, setDistance * (setNum - 1), setDirection);
    }

    public Location getSetEndPos(int setNum) {
        Location start = getSetStartPos(setNum);
        return start.add(setWidth - 1, setWidth - 1, setWidth - 1);
    }

    static Location move(Location loc, int distance, String direction) {
        return switch (direction.toLowerCase()) {
            case "x", "+x" -> loc.clone().add(distance, 0, 0);
            case "-x" -> loc.clone().add(-distance, 0, 0);
            case "z", "+z" -> loc.clone().add(0, 0, distance);
            case "-z" -> loc.clone().add(0, 0, -distance);
            case "y", "+y" -> loc.clone().add(0, distance, 0);
            case "-y" -> loc.clone().add(0, -distance, 0);
            default -> throw new IllegalArgumentException("Invalid direction: " + direction);
        };
    }
}
