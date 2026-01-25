package com.tokebak.EchoesOfOrbis.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import java.util.function.Supplier;

public class EchoesOfOrbisConfig {
    public static final BuilderCodec<EchoesOfOrbisConfig> CODEC;
    private double xpPerDamage = 1.0;
    private double xpMultiplier = 1.0;

    // Base XP required for level 2 (level 1 requires 0)
    private double levelBaseXP = 100.0;

    // How much the XP requirement scales per level
    // Formula: XP for level N = levelBaseXP * N^levelScaling
    private double levelScaling = 1.5;

    private int maxLevel = 100;

    private boolean showXpNotifications = false;
    private double minXpForNotification = 1.0;

    private boolean debug = true;

    public double getXpPerDamage() {
        return this.xpPerDamage;
    }

    public double getXpMultiplier() {
        return this.xpMultiplier;
    }

    public double getLevelBaseXP() {
        return this.levelBaseXP;
    }

    public double getLevelScaling() {
        return this.levelScaling;
    }

    public int getMaxLevel() {
        return this.maxLevel;
    }

    public boolean isShowXpNotifications() {
        return this.showXpNotifications;
    }

    public double getMinXpForNotification() {
        return this.minXpForNotification;
    }

    public boolean isDebug() {
        return this.debug;
    }

    // -- Setters (for config file merging) --

    public void setXpPerDamage(double value) {
        this.xpPerDamage = value;
    }

    public void setXpMultiplier(double value) {
        this.xpMultiplier = value;
    }

    public void setLevelBaseXP(double value) {
        this.levelBaseXP = value;
    }

    public void setLevelScaling(double value) {
        this.levelScaling = value;
    }

    public void setMaxLevel(int value) {
        this.maxLevel = value;
    }

    public void setShowXpNotifications(boolean value) {
        this.showXpNotifications = value;
    }

    public void setMinXpForNotification(double value) {
        this.minXpForNotification = value;
    }

    public void setDebug(boolean value) {
        this.debug = value;
    }

    // Static initializer for the codec
    static {
        CODEC = BuilderCodec.builder(EchoesOfOrbisConfig.class, (Supplier<EchoesOfOrbisConfig>) EchoesOfOrbisConfig::new)
                .append(
                        new KeyedCodec<>("XpPerDamage", Codec.DOUBLE),
                        (cfg, val) -> cfg.xpPerDamage = val,
                        cfg -> cfg.xpPerDamage
                ).add()
                .append(
                        new KeyedCodec<>("XpMultiplier", Codec.DOUBLE),
                        (cfg, val) -> cfg.xpMultiplier = val,
                        cfg -> cfg.xpMultiplier
                ).add()
                .append(
                        new KeyedCodec<>("LevelBaseXP", Codec.DOUBLE),
                        (cfg, val) -> cfg.levelBaseXP = val,
                        cfg -> cfg.levelBaseXP
                ).add()
                .append(
                        new KeyedCodec<>("LevelScaling", Codec.DOUBLE),
                        (cfg, val) -> cfg.levelScaling = val,
                        cfg -> cfg.levelScaling
                ).add()
                .append(
                        new KeyedCodec<>("MaxLevel", Codec.INTEGER),
                        (cfg, val) -> cfg.maxLevel = val,
                        cfg -> cfg.maxLevel
                ).add()
                .append(
                        new KeyedCodec<>("ShowXpNotifications", Codec.BOOLEAN),
                        (cfg, val) -> cfg.showXpNotifications = val,
                        cfg -> cfg.showXpNotifications
                ).add()
                .append(
                        new KeyedCodec<>("MinXpForNotification", Codec.DOUBLE),
                        (cfg, val) -> cfg.minXpForNotification = val,
                        cfg -> cfg.minXpForNotification
                ).add()
                .append(
                        new KeyedCodec<>("Debug", Codec.BOOLEAN),
                        (cfg, val) -> cfg.debug = val,
                        cfg -> cfg.debug
                ).add()
                .build();
    }

}
