package net.rishy.dehplugin;

import org.bukkit.Location;
import java.util.List;

public class DEHSetHandler {
    private final Location firstSetPos;
    private final int setDistance;
    private final int sceneSpacing;
    private final int setWidth;
    private final int setLength;
    private final int setHeight;
    private final String setDirection;
    private final String sceneDirection;
    private final List<Integer> setsPerScene;
    private final Location stageStartPos;
    private final int totalSets;
    private boolean loaded;
    private int sceneNumber = 1;
    private int localSetNumber = 1;
    private int globalSetNumber = 1;

    public DEHSetHandler(Location firstSetPos, int setDistance, int sceneSpacing, int setWidth, int setLength,
                         int setHeight, String setDirection, String sceneDirection, List<Integer> setsPerScene,
                         Location stageStartPos) {
        this.firstSetPos = firstSetPos;
        this.setDistance = setDistance;
        this.sceneSpacing = sceneSpacing;
        this.setWidth = setWidth;
        this.setLength = setLength;
        this.setHeight = setHeight;
        this.setDirection = setDirection;
        this.sceneDirection = sceneDirection;
        this.setsPerScene = setsPerScene;
        this.stageStartPos = stageStartPos;
        this.totalSets = setsPerScene.stream().mapToInt(Integer::intValue).sum();
    }

    public int getNumScenes() {
        return setsPerScene.size();
    }

    public int getSceneNumber() {
        return sceneNumber;
    }

    public int getLocalSetNumber() {
        return localSetNumber;
    }

    public int getGlobalSetNumber() {
        return globalSetNumber;
    }

    public Location getStageStartPos() {
        return stageStartPos;
    }

    public int getMaxSet(int sceneIdx) {
        return setsPerScene.get(sceneIdx - 1);
    }

    public Scene getScene(int sceneIdx) {
        Location origin = Scene.move(firstSetPos, (sceneIdx - 1) * sceneSpacing, sceneDirection);
        return new Scene(origin, setDistance, setWidth, setLength, setHeight, getMaxSet(sceneIdx),
                setDirection, sceneDirection);
    }

    public Scene getCurrentScene() {
        return getScene(sceneNumber);
    }

    public Location getCurrentSetStartPos() {
        return getCurrentScene().getSetStartPos(localSetNumber);
    }

    public Location getCurrentSetEndPos() {
        return getCurrentScene().getSetEndPos(localSetNumber);
    }

    public void advance() {
        if (!loaded) {
            loaded = true;
            sceneNumber = 1;
            localSetNumber = 1;
            globalSetNumber = 1;
            return;
        }
        if (localSetNumber < getMaxSet(sceneNumber)) {
            localSetNumber++;
        } else if (sceneNumber < getNumScenes()) {
            sceneNumber++;
            localSetNumber = 1;
        } else {
            sceneNumber = 1;
            localSetNumber = 1;
        }
        globalSetNumber = globalSetNumber == totalSets ? 1 : globalSetNumber + 1;
    }

    public void reverse() {
        if (!loaded) {
            loaded = true;
            sceneNumber = getNumScenes();
            localSetNumber = getMaxSet(sceneNumber);
            globalSetNumber = totalSets;
            return;
        }
        if (localSetNumber > 1) {
            localSetNumber--;
        } else if (sceneNumber > 1) {
            sceneNumber--;
            localSetNumber = getMaxSet(sceneNumber);
        } else {
            sceneNumber = getNumScenes();
            localSetNumber = getMaxSet(sceneNumber);
        }
        globalSetNumber = globalSetNumber == 1 ? totalSets : globalSetNumber - 1;
    }
}
