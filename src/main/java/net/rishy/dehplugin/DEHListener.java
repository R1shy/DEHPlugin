package net.rishy.dehplugin;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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

    private final JavaPlugin plugin;
    private static final Logger log = LoggerFactory.getLogger(DEHListener.class);
    private final List<String> crewMembers;
    private final List<String> directors;
    private final String developer;

    private final Component devPrefix = Component.text("[DEV] ").color(NamedTextColor.RED);
    private final Component dirPrefix = Component.text("[DIRECTOR] ").color(NamedTextColor.GOLD);
    private final Component crewPrefix = Component.text("[CREW] ").color(NamedTextColor.DARK_GREEN);

    public DEHListener(FileConfiguration conf, JavaPlugin plugin) {
        this.plugin = plugin;
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

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        if (block.getType() != Material.OAK_DOOR) return;

        Player player = event.getPlayer();
        String playerName = player.getName();

        boolean isTechCrew = crewMembers.contains(playerName)
                || playerName.equalsIgnoreCase(developer);


        if (!isTechCrew) {
            event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
            event.setCancelled(true);

            player.sendMessage("§cYou do not have permission to open this!");

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (block.getType() != Material.AIR) {
                    player.sendBlockChange(block.getLocation(), block.getBlockData());

                    Block topHalf = block.getRelative(0, 1, 0);
                    Block bottomHalf = block.getRelative(0, -1, 0);

                    if (topHalf.getType() == Material.OAK_DOOR) {
                        player.sendBlockChange(topHalf.getLocation(), topHalf.getBlockData());
                    }
                    if (bottomHalf.getType() == Material.OAK_DOOR) {
                        player.sendBlockChange(bottomHalf.getLocation(), bottomHalf.getBlockData());
                    }
                }
            }, 1L);
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