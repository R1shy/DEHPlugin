package net.rishy.dehplugin.commands;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.donnypz.displayentityutils.managers.DisplayGroupManager;
import net.donnypz.displayentityutils.utils.Axis;
import net.donnypz.displayentityutils.utils.DisplayEntities.ActiveGroup;
import net.donnypz.displayentityutils.utils.DisplayEntities.DisplayEntityGroup;
import net.donnypz.displayentityutils.utils.DisplayEntities.SpawnedDisplayEntityGroup;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

public class ZeroCommand implements BasicCommand {

    private static final Logger log = LoggerFactory.getLogger(ZeroCommand.class);

    @Override
    public void execute(@NonNull CommandSourceStack commandSourceStack, String @NonNull [] args) {
        if (args.length == 0) {
           commandSourceStack.getSender().sendMessage("ERR: No model (group) name provided");
           return;
        }
        List<SpawnedDisplayEntityGroup> g = DisplayGroupManager
                .getSpawnedGroups()
                .stream()
                .filter((x) -> {return Objects.equals(x.getTag(), args[0]); })
                .toList();
        if (g.getFirst() == null) {
            commandSourceStack.getSender().sendMessage("ERR: No model (group) with name (tag) " + args[0]);
            return;
        }
        ActiveGroup<?> z = g.getFirst();

        z.setRotation(0, Axis.X, false);
        z.setRotation(0, Axis.Y, false);
        z.setRotation(0, Axis.Z, false);

    }
}
