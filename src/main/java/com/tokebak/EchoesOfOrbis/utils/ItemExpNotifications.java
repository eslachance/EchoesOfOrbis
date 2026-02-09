package com.tokebak.EchoesOfOrbis.utils;


import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import com.tokebak.EchoesOfOrbis.services.ItemExpService;
import javax.annotation.Nonnull;
import java.awt.Color;

/**
 * Utility class for sending item XP notifications to players.
 * Handles formatting and sending messages for XP gains and level ups.
 */
public final class ItemExpNotifications {

    // Color constants for notifications
    private static final Color COLOR_XP_GAIN = new Color(0x55FF55);    // Bright green
    private static final Color COLOR_LEVEL_UP = new Color(0xFFD700);   // Gold
    private static final Color COLOR_ITEM_NAME = new Color(0x00BFFF);  // Deep sky blue
    private static final Color COLOR_PROGRESS = new Color(0xAAAAAA);   // Gray
    private static final Color COLOR_EMBUE = new Color(0x9966FF);      // Purple for embue

    // Private constructor - utility class
    private ItemExpNotifications() {}

    /**
     * Send a notification when a player gains XP on their weapon.
     */
    public static void sendXpGainNotification(
            @Nonnull final PlayerRef playerRef,
            final double xpGained,
            @Nonnull final ItemStack weapon,
            @Nonnull final ItemExpService service
    ) {
        final PacketHandler packetHandler = playerRef.getPacketHandler();
        if (packetHandler == null) {
            return;
        }

        // Get item name for display
        final String itemName = getDisplayName(weapon);

        // Get progress info
        final String progress = service.getProgressString(weapon);

        // Format: "+5.0 XP | Iron Sword | Level 3 | 150/200 XP (75%)"
        final String text = String.format(
                "+%.1f XP | %s | %s",
                xpGained,
                itemName,
                progress
        );

        final Message message = Message.raw(text).color(COLOR_XP_GAIN);
        NotificationUtil.sendNotification(packetHandler, message);
    }

    /**
     * Send a notification when a weapon levels up.
     */
    public static void sendLevelUpNotification(
            @Nonnull final PlayerRef playerRef,
            @Nonnull final ItemStack weapon,
            final int newLevel
    ) {
        final PacketHandler packetHandler = playerRef.getPacketHandler();
        if (packetHandler == null) {
            return;
        }

        final String itemName = getDisplayName(weapon);

        // Format: "LEVEL UP! Iron Sword reached Level 5!"
        final String text = String.format(
                "LEVEL UP! %s reached Level %d!",
                itemName,
                newLevel
        );

        final Message message = Message.raw(text).color(COLOR_LEVEL_UP);
        NotificationUtil.sendNotification(packetHandler, message);
    }

    /**
     * Send a notification showing the current item's XP status.
     * Useful for a command like /itemxp or inspecting a weapon.
     */
    public static void sendStatusNotification(
            @Nonnull final PlayerRef playerRef,
            @Nonnull final ItemStack weapon,
            @Nonnull final ItemExpService service
    ) {
        final PacketHandler packetHandler = playerRef.getPacketHandler();
        if (packetHandler == null) {
            return;
        }

        final String itemName = getDisplayName(weapon);
        final double totalXp = service.getItemXp(weapon);
        final int level = service.getItemLevel(weapon);
        final String progress = service.getProgressString(weapon);

        // Format: "Iron Sword | Total XP: 1234 | Level 5 | 150/200 XP (75%)"
        final String text = String.format(
                "%s | Total XP: %.0f | %s",
                itemName,
                totalXp,
                progress
        );

        final Message message = Message.raw(text).color(COLOR_ITEM_NAME);
        NotificationUtil.sendNotification(packetHandler, message);
    }

    /**
     * Send a notification when embues are available for selection.
     */
    public static void sendEmbueAvailableNotification(
            @Nonnull final PlayerRef playerRef,
            final int embueCount
    ) {
        final PacketHandler packetHandler = playerRef.getPacketHandler();
        if (packetHandler == null) {
            return;
        }

        // Format: "2 Upgrades available! Press F to choose! (when not looking at a chest/NPC)"
        final String text = String.format(
                "%d Upgrade%s available! Press F to choose!",
                embueCount,
                embueCount == 1 ? "" : "s"
        );

        final Message message = Message.raw(text).color(COLOR_EMBUE);
        NotificationUtil.sendNotification(packetHandler, message);
    }

    /**
     * Get a display-friendly name for an item.
     * Converts "hytale:iron_sword" to "Iron Sword"
     */
    @Nonnull
    private static String getDisplayName(@Nonnull final ItemStack item) {
        String itemId = item.getItemId();

        // Remove namespace prefix (e.g., "hytale:")
        final int colonIndex = itemId.indexOf(':');
        if (colonIndex != -1) {
            itemId = itemId.substring(colonIndex + 1);
        }

        // Replace underscores with spaces and capitalize each word
        final String[] words = itemId.split("_");
        final StringBuilder result = new StringBuilder();

        for (final String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (result.length() > 0) {
                result.append(" ");
            }
            // Capitalize first letter
            result.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                result.append(word.substring(1).toLowerCase());
            }
        }

        return result.toString();
    }
}
