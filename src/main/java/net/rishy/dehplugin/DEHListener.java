package net.rishy.dehplugin;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CrossbowMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class DEHListener implements Listener {

    private final DEHSetHandler setHandler = new DEHSetHandler(List.of(
            new Location(Bukkit.getWorld("world"), 77, -59, 40),
            new Location(Bukkit.getWorld("world"), 80, -59, 43),
            new Location(Bukkit.getWorld("world"), 83, -59, 46),
            new Location(Bukkit.getWorld("world"), 86, -59, 40)), 3, List.of(5, 4, 1, 2));
    private final Block infoSign = new Location(Bukkit.getWorld("world"), 7,-60,46).getBlock();
    private final List<String> crewMembers;
    private final List<String> directors;
    private final String developer;
    private final Component devPrefix = Component.text("[DEV] ").color(NamedTextColor.RED);
    private final Component dirPrefix = Component.text("[DIRECTOR] ").color(NamedTextColor.GOLD);
    private final Component crewPrefix = Component.text("[CREW] ").color(NamedTextColor.DARK_GREEN);

    public DEHListener(FileConfiguration conf) {
        this.crewMembers = conf.getStringList("crew");
        this.directors = conf.getStringList("directors");
        this.developer = conf.getString("dev");
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();

        if (item != null && item.getType() == Material.CROSSBOW) {
            if (item.getItemMeta() instanceof CrossbowMeta crossbowMeta) {
                if (crossbowMeta.hasChargedProjectiles()) {
                    event.getPlayer().sendMessage(Component.text("ERR: You can't interact with blocks while holding a loaded crossbow").color(NamedTextColor.RED));
                    event.setCancelled(true);
                    return;
                }
            }
        }
        Block hitBlock = event.getClickedBlock();
        Player player = event.getPlayer();
        Location reverseNoteBlockPos = new Location(Bukkit.getWorld("world"),7,-60,45);
        Location forwardNoteBlock = new Location(Bukkit.getWorld("world"), 7, -60, 47);
        if (hitBlock == null)
            return;
        if (hitBlock.getType() == Material.NOTE_BLOCK) {
            if (hitBlock.getLocation().equals(reverseNoteBlockPos)) {
                setHandler.reverse();
                loadCurrentSet(player);
            }
            else if (hitBlock.getLocation().equals(forwardNoteBlock)) {
                setHandler.advance();
                loadCurrentSet(player);
            }
        }





    }

    private void loadCurrentSet(Player player) {
        CMDUtils.cloneCMD(setHandler.getCurrentSetStartPos(), setHandler.getCurrentSetEndPos(), setHandler.STAGE_START_POS);
        if (infoSign.getState() instanceof Sign s) {
            SignSide front = s.getSide(Side.FRONT);
            front.setGlowingText(true);
            front.line(1, Component.text("CURRENT SET:"));
            front.line(2, Component.text(setHandler.getGlobalSetNumber()));
            front.line(3, Component.text("SCENE " + setHandler.getSceneNumber()));
            s.update(true);
        }
        else {
            player.sendMessage(Component.text("NOT SIGN"));
        }
        player.sendMessage(Component.text("Scene " + setHandler.getSceneNumber() + " | Set " + setHandler.getGlobalSetNumber()));
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String name = player.getName();

        if (name.equalsIgnoreCase(developer)) {
            Component fullDisplayName = devPrefix.append(Component.text(name));
            player.displayName(fullDisplayName);
            player.playerListName(fullDisplayName);
        } else if (directors.contains(name)) {
            Component FDN = dirPrefix.append(Component.text(name));
            player.displayName(FDN);
            player.playerListName(FDN);
        } else if (crewMembers.contains(name)) {
            Component FDN = crewPrefix.append(Component.text(name));
            player.displayName(FDN);
            player.playerListName(FDN);
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String name = player.getName();

        if (name.equalsIgnoreCase(developer)) {
            event.renderer((source, sourceDisplayName, message, viewer) -> MiniMessage.miniMessage().deserialize(
                    "<red>[DEV] <name>: </red><white><msg></white>",
                    Placeholder.unparsed("name", name),
                    Placeholder.component("msg", message)
            ));
        } else if (directors.contains(name)) {
            event.renderer((source, sourceDisplayName, message, viewer) -> MiniMessage.miniMessage().deserialize(
                    "<yellow>[DIRECTOR] <name>: </yellow><white><msg></white>",
                    Placeholder.unparsed("name", name),
                    Placeholder.component("msg", message)
            ));
        } else if (crewMembers.contains(name)) {
            event.renderer((source, sourceDisplayName, message, viewer) -> MiniMessage.miniMessage().deserialize(
                    "<dark_green>[CREW] <name>: </dark_green><white><msg></white>",
                    Placeholder.unparsed("name", name),
                    Placeholder.component("msg", message)
            ));
        }
    }
}