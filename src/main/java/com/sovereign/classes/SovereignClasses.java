package com.sovereign.classes;

import net.minecraftforge.fml.common.Mod;

/** Data-driven classes and races. Everything else lives in the data, net, server and client packages. */
@Mod(SovereignClasses.MODID)
public class SovereignClasses {
    public static final String MODID = "sovereignclasses";

    public SovereignClasses() {
        com.sovereign.classes.Net.init();
    }
}
