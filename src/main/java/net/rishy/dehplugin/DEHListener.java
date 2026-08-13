package net.rishy.dehplugin;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
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

    private static final Logger log = LoggerFactory.getLogger(DEHListener.class);
    private final DEHSetHandler setHandler = new DEHSetHandler(new Location(Bukkit.getWorld("world"),77,-59,40), 3);
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
        assert hitBlock != null;
        if (hitBlock.getType() == Material.NOTE_BLOCK) {
            Sound no = Sound.sound(Key.key("entity.villager.no"), Sound.Source.PLAYER, 1f,1f);
            if (hitBlock.getLocation().equals(reverseNoteBlockPos)) {
            if (setHandler.getCurrentSetNumber() == 1) {
                player.sendActionBar(Component.text("Already at first set"));
                player.playSound(no, 7,-60,45);
            }
            else {
                CMDUtils.cloneCMD(setHandler.getPrevSetStartPos(), setHandler.getPrevSetEndPos(), setHandler.STAGE_START_POS);
                setHandler.decrCurrentSetNumber();
                player.sendMessage(Component.text(setHandler.getCurrentSetNumber()));
            }
        }
        if (hitBlock.getLocation().equals(forwardNoteBlock)) {
            if (setHandler.getCurrentSetNumber() == 3) {
                player.sendActionBar(Component.text("At last set"));
                player.playSound(no, 7, -60,47);
            }
            else {
                CMDUtils.cloneCMD(setHandler.getNextSetStartPos(), setHandler.getNextSetEndPos(), setHandler.STAGE_START_POS);
                setHandler.incrCurrentSetNumber();
            }

        }


        }





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