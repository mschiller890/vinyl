package com.mschiller890.vinyl.client.hud;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Adds the previous / play-pause / next music controls to the survival
 * inventory screen.
 *
 * <p>On Minecraft 26.2 the rendering pipeline moved from an immediate-mode
 * {@code render()} that iterated the screen's {@code renderables} list to a
 * retained-mode {@code extractRenderState()} / {@code GuiGraphicsExtractor}
 * architecture. {@code AbstractContainerScreen} (which {@code InventoryScreen}
 * extends) builds its render state through that new path and does <em>not</em>
 * pick up widgets appended to the list returned by
 * {@code Screens.getWidgets(Screen)} after {@code init()}. That is why I was going crazy because buttons
 * added the "old" way show up on {@code TitleScreen} (a plain {@code Screen})
 * but never appear on the inventory screen.</p>
 *
 * <p>To work around this we own the {@link Button} widgets ourselves, render
 * them in {@link ScreenEvents#afterExtract(Screen)} by calling each button's
 * {@code extractRenderState}, and forward mouse clicks to them from
 * {@link ScreenEvents#beforeTick(Screen)}.</p>
 * 
 * <p>i feel like such a hacky little modder</p>
 */
public final class VinylInventoryControls {

    // keep these in sync with the layout constants used in VinylHud#render
    // so the buttons line up neatly under the song/artist text.
    private static final int HUD_X = 10;
    private static final int HUD_Y = 10;
    private static final float SONG_SCALE = 1f;
    private static final float ARTIST_SCALE = 0.50f;
    private static final int TEXT_PADDING = 3;

    private static final int BUTTON_SIZE = 18;
    private static final int BUTTON_GAP = 2;
    private static final int BUTTONS_TOP_MARGIN = 4;

    /**
     * The buttons for the inventory screen that is currently open.
     * Cleared whenever the screen is removed so we don't leak references.
     */
    private static List<AbstractWidget> currentButtons = List.of();

    /**
     * Tracks the previous frame's left-button state so we can detect a
     * click edge (down transition) rather than firing every tick the button
     * is held.
     */
    private static boolean wasLeftPressed;

    private VinylInventoryControls() {
    }

    public static void initialize() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            // Both the survival inventory and the creative inventory extend
            // AbstractContainerScreen, so both hit the MC 26.2 retained-mode
            // render path that ignores Screens.getWidgets(). Handle them both.
            if (!(screen instanceof InventoryScreen)
                    && !(screen instanceof CreativeModeInventoryScreen)) {
                return;
            }

            int y = calculateButtonsY();

            List<AbstractWidget> buttons = new ArrayList<>();
            buttons.add(Button.builder(Component.literal("\u23EE"), b -> onPrevious())
                    .bounds(HUD_X, y, BUTTON_SIZE, BUTTON_SIZE)
                    .tooltip(Tooltip.create(Component.literal("Previous")))
                    .build());
            buttons.add(Button.builder(Component.literal("\u23EF"), b -> onPlayPause())
                    .bounds(HUD_X + (BUTTON_SIZE + BUTTON_GAP), y, BUTTON_SIZE, BUTTON_SIZE)
                    .tooltip(Tooltip.create(Component.literal("Play / Pause")))
                    .build());
            buttons.add(Button.builder(Component.literal("\u23ED"), b -> onNext())
                    .bounds(HUD_X + (BUTTON_SIZE + BUTTON_GAP) * 2, y, BUTTON_SIZE, BUTTON_SIZE)
                    .tooltip(Tooltip.create(Component.literal("Next")))
                    .build());
            currentButtons = buttons;

            // Render our buttons on top of the inventory screen's own contents.
            ScreenEvents.afterExtract(screen).register((scr, graphics, mouseX, mouseY, partialTick) -> {
                for (AbstractWidget button : currentButtons) {
                    button.extractRenderState(graphics, mouseX, mouseY, partialTick);
                }
            });

            // Forward clicks to whichever button is under the cursor.
            ScreenEvents.beforeTick(screen).register(VinylInventoryControls::handleMouseClick);

            // Drop our references when the screen closes.
            ScreenEvents.remove(screen).register(scr -> currentButtons = List.of());
        });

        System.out.println("Vinyl inventory controls initialized");
    }

    private static void handleMouseClick(Screen screen) {
        Minecraft client = Minecraft.getInstance();
        if (client.mouseHandler == null) {
            return;
        }

        boolean isLeftPressed = client.mouseHandler.isLeftPressed();
        boolean clickEdge = isLeftPressed && !wasLeftPressed;
        wasLeftPressed = isLeftPressed;

        if (!clickEdge) {
            return;
        }

        // xpos()/ypos() are in screen pixels; convert to GUI-scaled coords.
        // Minecraft#getWindow() already returns the com.mojang.blaze3d.platform.Window.
        double scaledX = client.mouseHandler.getScaledXPos(client.getWindow());
        double scaledY = client.mouseHandler.getScaledYPos(client.getWindow());

        MouseButtonEvent event = new MouseButtonEvent(scaledX, scaledY, new MouseButtonInfo(0, 0));

        for (AbstractWidget button : currentButtons) {
            if (button.isMouseOver(scaledX, scaledY)) {
                button.mouseClicked(event, false);
                break;
            }
        }
    }

    private static int calculateButtonsY() {
        Minecraft client = Minecraft.getInstance();
        int songHeight = (int) (client.font.lineHeight * SONG_SCALE);
        int artistY = HUD_Y + songHeight + TEXT_PADDING;
        int artistHeight = (int) (client.font.lineHeight * ARTIST_SCALE);
        return artistY + artistHeight + TEXT_PADDING + BUTTONS_TOP_MARGIN;
    }

    private static void onPrevious() {
        System.out.println("[Vinyl] Previous pressed");
    }

    private static void onPlayPause() {
        System.out.println("[Vinyl] Play/Pause pressed");
    }

    private static void onNext() {
        System.out.println("[Vinyl] Next pressed");
    }
}