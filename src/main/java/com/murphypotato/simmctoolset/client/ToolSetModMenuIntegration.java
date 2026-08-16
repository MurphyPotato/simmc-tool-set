package com.murphypotato.simmctoolset.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Optional Mod Menu entrypoint for the Tool Set control screen. */
public final class ToolSetModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new ToolSetScreen(parent, ToolSetScreen.Panel.OVERVIEW);
    }
}
