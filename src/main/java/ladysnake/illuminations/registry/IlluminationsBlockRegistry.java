package ladysnake.illuminations.registry;

import ladysnake.illuminations.block.IlluminationsBlocks;
import ladysnake.illuminations.mod.Illuminations;
import net.minecraft.block.Block;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

@EventBusSubscriber(modid = Illuminations.MODID, bus = Bus.MOD)
public class IlluminationsBlockRegistry {

    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> e) {

        e.getRegistry().registerAll(IlluminationsBlocks.registry());
    }
}
