package com.eliteinventorybackups.commands;

import com.eliteinventorybackups.EliteInventoryBackups;
import com.eliteinventorybackups.util.PermissionUtil;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = EliteInventoryBackups.MODID)
public class CommandRegistry {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        LiteralArgumentBuilder<CommandSourceStack> eibCommand = Commands.literal("eib")
            .requires(PermissionUtil::hasAdminPermission)
            .then(BackupCommand.register(dispatcher))
            .then(ListCommand.register(dispatcher))
            .then(ViewCommand.register(dispatcher))
            .then(RestoreCommand.register(dispatcher))
            .then(RemoveAllCommand.register(dispatcher))
            ;

        dispatcher.register(eibCommand);
    }
} 