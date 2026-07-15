package com.mschiller890.vinyl.client;

import com.mschiller890.vinyl.client.hud.VinylHud;
import com.mschiller890.vinyl.client.hud.VinylInventoryControls;
import com.mschiller890.vinyl.client.media.MusicController;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;

public class VinylClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        VinylHud.initialize();
        VinylInventoryControls.initialize();
        MusicController.getInstance().initialize();

        // Tear down the GSMTC host process when the client closes so we
        // don't leave a stray PowerShell process behind.
        ClientLifecycleEvents.CLIENT_STOPPING.register(client ->
                MusicController.getInstance().shutdown());
    }
}