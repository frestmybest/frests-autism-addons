package com.forest.frest;

import autismclient.api.ApiVersion;
import autismclient.api.AutismAddon;
import autismclient.api.AutismAddons;
import com.forest.frest.modules.AutoBreedModule;
import com.forest.frest.modules.AutoGappleModule;
import com.forest.frest.modules.AutoBucketModule;
import com.forest.frest.modules.AutoMilkModule;
import com.forest.frest.modules.AutoPotModule;
import com.forest.frest.modules.AutoRespawnModule;
import com.forest.frest.modules.AutoShieldModule;
import com.forest.frest.modules.AutoSmeltModule;
import com.forest.frest.modules.ChestStealerModule;
import com.forest.frest.modules.ElytraSwapModule;
import com.forest.frest.modules.SurroundModule;
import com.forest.frest.modules.AntiEntityPushModule;
import com.forest.frest.modules.AntiWaterPushModule;
import com.forest.frest.modules.AntiWobbleModule;
import com.forest.frest.modules.AutoDropModule;
import com.forest.frest.modules.AutoEatModule;
import com.forest.frest.modules.AutoFarmModule;
import com.forest.frest.modules.AutoMineModule;
import com.forest.frest.modules.AutoRefillModule;
import com.forest.frest.modules.AutoSprintModule;
import com.forest.frest.modules.BowSpamModule;
import com.forest.frest.modules.BunnyhopModule;
import com.forest.frest.modules.VeinMinerModule;

public final class FrestAddon extends AutismAddon {
    public static final String ID = "frest-autism-addons";

    /** Shown on the title screen; keep in sync with gradle.properties. */
    public static final String VERSION = "v2.13";

    @Override
    public int apiVersion() {
        return ApiVersion.CURRENT;
    }

    @Override
    public void onInitialize() {
        AutismAddons.modules().register(new AutoEatModule());
        AutismAddons.modules().register(new AutoMineModule());
        AutismAddons.modules().register(new VeinMinerModule());
        AutismAddons.modules().register(new AutoFarmModule());
        AutismAddons.modules().register(new AutoDropModule());
        AutismAddons.modules().register(new AutoRefillModule());
        AutismAddons.modules().register(new AntiEntityPushModule());
        AutismAddons.modules().register(new AntiWaterPushModule());
        AutismAddons.modules().register(new AntiWobbleModule());
        AutismAddons.modules().register(new AutoSprintModule());
        AutismAddons.modules().register(new BunnyhopModule());

        // Self-maintenance: your inventory, your body, your menus.
        AutismAddons.modules().register(new AutoPotModule());
        AutismAddons.modules().register(new AutoMilkModule());
        AutismAddons.modules().register(new ElytraSwapModule());
        AutismAddons.modules().register(new AutoBucketModule());
        AutismAddons.modules().register(new AutoRespawnModule());
        AutismAddons.modules().register(new ChestStealerModule());
        AutismAddons.modules().register(new AutoSmeltModule());
        AutismAddons.modules().register(new AutoBreedModule());
        AutismAddons.modules().register(new BowSpamModule());
        AutismAddons.modules().register(new AutoGappleModule());
        AutismAddons.modules().register(new AutoShieldModule());
        AutismAddons.modules().register(new SurroundModule());
    }

    @Override
    public String getPackage() {
        return "com.forest.frest";
    }
}
