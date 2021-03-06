package me.lordsaad.cc.common;

import com.teamwizardry.librarianlib.features.network.PacketHandler;
import me.lordsaad.cc.common.network.PacketShowCraneParticles;
import me.lordsaad.cc.common.network.PacketSyncBlockBuild;
import me.lordsaad.cc.init.ModBlocks;
import me.lordsaad.cc.init.ModTab;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Created by LordSaad.
 */
public class CommonProxy {

	public void preInit(FMLPreInitializationEvent event) {
		new ModTab();

		ModBlocks.init();

		CommonEventHandler.INSTANCE.getClass();

		PacketHandler.register(PacketSyncBlockBuild.class, Side.SERVER);
		PacketHandler.register(PacketShowCraneParticles.class, Side.CLIENT);
	}

	public void init(FMLInitializationEvent event) {

	}

	public void postInit(FMLPostInitializationEvent event) {

	}
}
