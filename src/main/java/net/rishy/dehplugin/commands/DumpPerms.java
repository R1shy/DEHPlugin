package net.rishy.dehplugin.commands;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.jspecify.annotations.NonNull;

import java.util.HashSet;
import java.util.List;

public class DumpPerms implements BasicCommand {
    @Override
    public void execute(@NonNull CommandSourceStack commandSourceStack, String[] args) {
        if (args.length > 0) {
            Player p = Bukkit.getPlayer(args[0]);
            assert p != null;
            List<PermissionAttachmentInfo> X = new HashSet<>(p.getEffectivePermissions()).stream().toList();
            for (PermissionAttachmentInfo i : X) {
                commandSourceStack.getSender().sendMessage(i.getPermission());
            }
        }
    }

}
