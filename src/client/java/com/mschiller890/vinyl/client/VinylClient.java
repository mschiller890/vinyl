package com.mschiller890.vinyl.client;

import com.mschiller890.vinyl.client.hud.VinylHud;
import com.mschiller890.vinyl.client.hud.VinylInventoryControls;
import net.fabricmc.api.ClientModInitializer;

public class VinylClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        VinylHud.initialize();
        VinylInventoryControls.initialize();
    }
}