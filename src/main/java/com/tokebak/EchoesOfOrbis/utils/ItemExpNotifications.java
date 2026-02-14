package com.tokebak.EchoesOfOrbis.utils;


import com.hypixel.hytale.protocol.ItemWithAllMetadata;
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
     * Send a notification when a weapon levels up, using the new notification format with icon.
     * Combines level up message and embue availability into a single notification.
     */
    public static void sendLevelUpNotificationWithIcon(
            @Nonnull final PlayerRef playerRef,
            @Nonnull final ItemStack weapon,
            final int newLevel,
            @Nonnull final ItemExpService service
    ) {
        final PacketHandler packetHandler = playerRef.getPacketHandler();
        if (packetHandler == null) {
            return;
        }

        final String itemName = getDisplayName(weapon);
        
        // Primary message: Level up info
        final Message primaryMessage = Message.raw(
                String.format("LEVEL UP! %s reached Level %d!", itemName, newLevel)
        ).color(COLOR_LEVEL_UP);
        
        // Secondary message: Embue availability or XP progress
        final int pendingEmbues = service.getPendingEmbues(weapon);
        final Message secondaryMessage;
        
        if (pendingEmbues > 0) {
            secondaryMessage = Message.raw(
                    String.format("%d Upgrade%s available! Press F to choose!", 
                            pendingEmbues, 
                            pendingEmbues == 1 ? "" : "s")
            ).color(COLOR_EMBUE);
        } else {
            // Show progress if no embues pending
            final String progress = service.getProgressString(weapon);
            secondaryMessage = Message.raw(progress).color(COLOR_PROGRESS);
        }
        
        // Use the weapon as the icon
        final ItemWithAllMetadata icon = (ItemWithAllMetadata) weapon.toPacket();
        
        NotificationUtil.sendNotification(packetHandler, primaryMessage, secondaryMessage, icon);
    }

    /**
     * Get a display-friendly name for an item.
     * Uses translation if available, falls back to formatting the item ID.
     */
    @Nonnull
    private static String getDisplayName(@Nonnull final ItemStack item) {
        // Try to get the translated name first
        final String translationKey = item.getItem().getTranslationKey();
        final String translated = Message.translation(translationKey).getAnsiMessage();
        if (translated != null && !translated.isEmpty() && !translated.equals(translationKey)) {
            return translated;
        }
        
        // Fallback: format the item ID (e.g., "Weapon_Sword_Iron" -> "Iron Sword")
        return formatItemId(item.getItem().getId());
    }
    
    /**
     * Fallback formatter for item IDs.
     * Converts "Weapon_Sword_Iron" to "Iron Sword".
     */
    @Nonnull
    private static String formatItemId(@Nonnull final String raw) {
        if (raw == null || raw.isEmpty()) {
            return "Unknown";
        }
        
        String cleaned = raw;
        
        // Strip common prefixes
        if (cleaned.startsWith("Weapon_")) {
            cleaned = cleaned.substring(7);
        }
        if (cleaned.startsWith("Tool_")) {
            cleaned = cleaned.substring(5);
        }
        
        // Split by underscore and reverse order (e.g., "Sword_Iron" -> "Iron Sword")
        final String[] parts = cleaned.split("_");
        final StringBuilder result = new StringBuilder();
        
        for (int i = parts.length - 1; i >= 0; i--) {
            if (parts[i].isEmpty()) {
                continue;
            }
            if (result.length() > 0) {
                result.append(" ");
            }
            // Capitalize first letter
            result.append(Character.toUpperCase(parts[i].charAt(0)));
            if (parts[i].length() > 1) {
                result.append(parts[i].substring(1).toLowerCase());
            }
        }
        
        return result.toString();
    }
}
