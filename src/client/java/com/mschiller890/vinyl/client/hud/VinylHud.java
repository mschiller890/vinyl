package com.mschiller890.vinyl.client.hud;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

public class VinylHud {

    public static String currentSong = "No song playing";
    public static String currentArtist = "Is a streaming service running?";

    private VinylHud() {
    }

    public static void initialize() {
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath("vinyl", "music_hud"),
                VinylHud::render
        );

        System.out.println("Vinyl HUD initialized");
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {

        Minecraft client = Minecraft.getInstance();

        if (client.player == null) {
            return;
        }

        Font font = client.font;

        int x = 10;
        int y = 10;

/// SONG
        float songScale = 1f;

        graphics.pose().pushMatrix();
        graphics.pose().scale(songScale, songScale);
        graphics.text(
                font,
                currentSong,
                (int) (x / songScale),
                (int) (y / songScale),
                0xFFFFFFFF,
                true
        );
        graphics.pose().popMatrix();

/// ARTIST
        float artistScale = 0.50f;
        int padding = 3;
        int songHeight = (int) (font.lineHeight * songScale);
        int artistY = y + songHeight + padding; // there was probs an easier way to do this 

        graphics.pose().pushMatrix();
        graphics.pose().scale(artistScale, artistScale);
        graphics.text(
                font,
                currentArtist,
                (int) (x / artistScale),
                (int) (artistY / artistScale),
                0xFFAAAAAA,
                true
        );
        graphics.pose().popMatrix();
    }

    public static void setSong(String song, String artist) {
        currentSong = song;
        currentArtist = artist;
    }
}