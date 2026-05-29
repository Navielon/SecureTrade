package com.securetrade;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.network.packet.s2c.play.ExperienceBarUpdateS2CPacket;

public class XPMath {

    public static int getPlayerXP(net.minecraft.entity.player.PlayerEntity player) {
        int level = player.experienceLevel;
        int xp = getXpForLevels(level);
        xp += Math.round(player.experienceProgress * player.getNextLevelExperience());
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

    public static void setPlayerXP(ServerPlayerEntity player, int totalXp) {
        int clampedXp = Math.max(0, totalXp);
        int delta = clampedXp - getPlayerXP(player);
        player.totalExperience = Math.max(0, player.totalExperience + delta);
        player.experienceLevel = getLevelForXp(clampedXp);
        int xpForCurrentLevel = getXpForLevels(player.experienceLevel);
        int xpForNextLevel = getXpForLevels(player.experienceLevel + 1);
        int difference = xpForNextLevel - xpForCurrentLevel;
        if (difference > 0) {
            player.experienceProgress = (float) (clampedXp - xpForCurrentLevel) / (float) difference;
        } else {
            player.experienceProgress = 0.0f;
        }
        player.experienceProgress = Math.max(0.0f, Math.min(1.0f, player.experienceProgress));
        player.networkHandler.sendPacket(new ExperienceBarUpdateS2CPacket(
            player.experienceProgress,
            player.totalExperience,
            player.experienceLevel
        ));
    }
}
