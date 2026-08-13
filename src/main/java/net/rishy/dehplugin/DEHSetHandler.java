package net.rishy.dehplugin;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import java.util.ArrayList;
import java.util.List;

public class DEHSetHandler {
    private final List<Location> sceneOrigins;
    private final int setDistance;
    private final List<Integer> setsPerScene;
    private final int totalSets;
    private boolean loaded;
    private int sceneNumber = 1;
    private int localSetNumber = 1;
    private int globalSetNumber = 1;
    public Location STAGE_START_POS = new Location(Bukkit.getWorld("world"), 9, -59, 45);

    public DEHSetHandler(List<Location> sceneOrigins, int setDistance, List<Integer> setsPerScene) {
        this.sceneOrigins = sceneOrigins;
        this.setDistance = setDistance;
        this.setsPerScene = setsPerScene;
        this.totalSets = setsPerScene.stream().mapToInt(Integer::intValue).sum();
    }

    public DEHSetHandler(Location originOfSets, int setDistance) {
        this(originOfSets, setDistance, setDistance, setDistance, List.of(5, 4, 1, 2));
    }

    public DEHSetHandler(Location originOfSets, int setDistance, int sceneXStep, int sceneZStep, List<Integer> setsPerScene) {
        this(buildSceneOrigins(originOfSets, sceneXStep, sceneZStep, setsPerScene.size()), setDistance, setsPerScene);
    }

    private static List<Location> buildSceneOrigins(Location origin, int sceneXStep, int sceneZStep, int numScenes) {
        List<Location> origins = new ArrayList<>();
        for (int i = 0; i < numScenes; i++) {
            origins.add(origin.clone().add(i * sceneXStep, 0, i * sceneZStep));
        }
        return origins;
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

    public int getMaxSet(int sceneIdx) {
        return setsPerScene.get(sceneIdx - 1);
    }

    public Scene getScene(int sceneIdx) {
        return new Scene(sceneOrigins.get(sceneIdx - 1), setDistance, getMaxSet(sceneIdx));
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
