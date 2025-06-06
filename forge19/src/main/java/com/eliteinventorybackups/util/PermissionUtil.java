package com.eliteinventorybackups.util;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;
import net.luckperms.api.LuckPermsProvider;
// import dev.ftb.mods.ftbranks.api.FTBRanksAPI; // Temporarily commented out

public class PermissionUtil {

    private static final String ADMIN_PERMISSION_NODE = "eliteinventorybackups.admin";

    public static boolean hasAdminPermission(CommandSourceStack source) {
        // Check FTB Ranks first
        /* // Temporarily commented out FTB Ranks integration
        if (ModList.get().isLoaded("ftbranks")) {
            try {
                ServerPlayer player = source.getPlayer(); // Can be null if not a player
                if (player != null && FTBRanksAPI.api().getManager().getPlayer(player).hasPermission(ADMIN_PERMISSION_NODE)) {
                    return true;
                }
            } catch (Exception e) {
                // Log error or handle, e.g., FTB Ranks API not available as expected
                // For now, just proceed to next check
            }
        }
        */

        // Then check LuckPerms
        if (ModList.get().isLoaded("luckperms")) {
            try {
                 ServerPlayer player = source.getPlayer();
                 if (player != null) {
                    net.luckperms.api.model.user.User user = LuckPermsProvider.get().getUserManager().getUser(player.getUUID());
                    if (user != null && user.getCachedData().getPermissionData().checkPermission(ADMIN_PERMISSION_NODE).asBoolean()) {
                        return true;
                    }
                 }
            } catch (Exception e) {
                // Log error or handle
            }
        }

        // Fallback to OP level 2 if player or other permission checks fail/not available
        return source.hasPermission(2);
    }
    
    // You can add more specific permission checks here, e.g., for each subcommand
    // public static boolean canUseBackupCommand(CommandSourceStack source) {
    //     return checkPermission(source, "eliteinventorybackups.command.backup");
    // }
    // private static boolean checkPermission(CommandSourceStack source, String node) { ... implementation ... }
} 