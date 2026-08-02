package net.rishy.dehplugin;


import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.meta.CrossbowMeta;

import java.util.List;

public class DEHListener implements Listener {

    private final List<String> crewMembers;
    private final List<String> directors;
    private final String developer;

    private final Component devPrefix = Component.text("[DEV] ")
            .color(NamedTextColor.RED);
    private final Component dirPrefix = Component.text("[DIRECTOR] ")
            .color(NamedTextColor.GOLD);

    private final Component crewPrefix = Component.text("[CREW] ")
            .color(NamedTextColor.DARK_GREEN);




    public DEHListener(FileConfiguration conf) {
        crewMembers = conf.getStringList("crew");
        directors = conf.getStringList("directors");
        developer = conf.getString("dev");
}

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getMaterial() == Material.CROSSBOW) {
            assert event.getItem() != null;
            if (event.getItem().getItemMeta() instanceof CrossbowMeta) {
                if (((CrossbowMeta) event.getItem().getItemMeta()).hasChargedProjectiles()) event.setCancelled(true);
            }
        }
    }

    // works in theory not practice
    @EventHandler public void onShoot(EntityShootBowEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (crewMembers.contains(player.getName())) {
            Component FDN = crewPrefix.append(Component.text(player.getName()));
            player.displayName(FDN);
            player.playerListName(FDN);
        }

        if (directors.contains(player.getName()) ) {
            Component FDN = dirPrefix.append(Component.text(player.getName()));
            player.displayName(FDN);
            player.playerListName(FDN);
        }

        if (player.getName().equalsIgnoreCase(developer)) {
            Component fullDisplayName = devPrefix.append(Component.text(player.getName()));
            player.displayName(fullDisplayName);
            player.playerListName(fullDisplayName);
        }
    }



    @EventHandler
    public void onPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();

        if (crewMembers.contains(player.getName())) {
            event.renderer(((source, sourceDisplayName, message, viewer) -> {
                return MiniMessage.miniMessage().deserialize(
                        "<dark_green>[CREW] <name>: </dark_green><white><msg></white>",
                        Placeholder.unparsed("name", player.getName()),
                        Placeholder.component("msg", message));
            }));
        }

        if (player.getName().equalsIgnoreCase(developer)) {
            event.renderer((source, sourceDisplayName, message, viewer) -> {
                return MiniMessage.miniMessage().deserialize(
                        "<red>[DEV] rishypeasy: </red><white><msg></white>",
                        Placeholder.component("msg", message)
                );
            });
        }

        if (directors.contains(player.getName())) {
            event.renderer((source, sourceDisplayName, message, viewer) -> {
                return MiniMessage.miniMessage().deserialize(
                        "<yellow>[DIRECTOR] LilCeen: </yellow><white><msg></white>",
                        Placeholder.component("msg", message));
            });
        }
    }
}
