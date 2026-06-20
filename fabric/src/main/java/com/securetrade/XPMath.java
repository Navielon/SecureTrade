package com.securetrade;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.network.packet.s2c.play.ExperienceBarUpdateS2CPacket;

public class XPMath {

    public static long getPlayerXP(net.minecraft.entity.player.PlayerEntity player) {
        int level = player.experienceLevel;
        long xp = getXpForLevels(level);
        xp += Math.round(player.experienceProgress * player.getNextLevelExperience());
        return xp;
    }

    public static long getXpForLevels(int level) {
        if (level <= 16) {
            return (long) level * level + 6L * level;
        } else if (level <= 31) {
            return Math.round(2.5D * level * level - 40.5D * level + 360D);
        } else {
            return Math.round(4.5D * level * level - 162.5D * level + 2220D);
        }
    }

    public static int getLevelForXp(long xp) {
        if (xp <= 0) {
            return 0;
        }

        int low = 0;
        int high = 1;
        while (getXpForLevels(high) <= xp && high < Integer.MAX_VALUE / 2) {
            high *= 2;
        }

        while (low < high) {
            int mid = low + (high - low + 1) / 2;
            if (getXpForLevels(mid) <= xp) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return low;
    }

    public static void setPlayerXP(ServerPlayerEntity player, long totalXp) {
        long clampedXp = Math.max(0L, totalXp);
        player.totalExperience = (int) Math.min(Integer.MAX_VALUE, clampedXp);
        player.experienceLevel = getLevelForXp(clampedXp);
        long xpForCurrentLevel = getXpForLevels(player.experienceLevel);
        long xpForNextLevel = getXpForLevels(player.experienceLevel + 1);
        long difference = xpForNextLevel - xpForCurrentLevel;
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
