package com.securetrade;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;

public class XPMath {

    public static int getPlayerXP(net.minecraft.world.entity.player.Player player) {
        int level = player.experienceLevel;
        int xp = getXpForLevels(level);
        xp += Math.round(player.experienceProgress * player.getXpNeededForNextLevel());
        return xp;
    }

    public static int getXpForLevels(int level) {
        if (level <= 16) {
            return level * level + 6 * level;
        } else if (level <= 31) {
            return (int) (2.5 * level * level - 40.5 * level + 360);
        } else {
            return (int) (4.5 * level * level - 162.5 * level + 2220);
        }
    }

    public static int getLevelForXp(int xp) {
        int level = 0;
        while (getXpForLevels(level + 1) <= xp) {
            level++;
        }
        return level;
    }

    public static void setPlayerXP(ServerPlayer player, int totalXp) {
        player.totalExperience = totalXp;
        player.experienceLevel = getLevelForXp(totalXp);
        int xpForCurrentLevel = getXpForLevels(player.experienceLevel);
        int xpForNextLevel = getXpForLevels(player.experienceLevel + 1);
        int difference = xpForNextLevel - xpForCurrentLevel;
        if (difference > 0) {
            player.experienceProgress = (float) (totalXp - xpForCurrentLevel) / (float) difference;
        } else {
            player.experienceProgress = 0.0f;
        }
        player.experienceProgress = Math.max(0.0f, Math.min(1.0f, player.experienceProgress));
        player.connection.send(new ClientboundSetExperiencePacket(
            player.experienceProgress,
            player.totalExperience,
            player.experienceLevel
        ));
    }
}
