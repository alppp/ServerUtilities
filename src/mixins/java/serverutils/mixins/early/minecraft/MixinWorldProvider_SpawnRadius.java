package serverutils.mixins.early.minecraft;

import java.util.Random;

import net.minecraft.world.WorldProvider;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Makes the "spawnRadius" gamerule (like modern Minecraft) control where brand-new players first spawn.
 *
 * <p>
 * Vanilla 1.7.10 hardcodes the spawn offset inside the EntityPlayerMP constructor, but Forge patched that logic into
 * WorldProvider.getRandomizedSpawnPoint(), which is what the constructor and ServerConfigurationManager call. This
 * mixin redirects the two Random.nextInt(int) draws in that method. Forge then subtracts spawnFuzzHalf from the result,
 * so the handler returns nextInt(2N) - N + fuzzHalf (N = spawnRadius): the final delta is nextInt(2N) - N, i.e.
 * spawnRadius 0 -> delta 0 (exact world spawn) and spawnRadius N -> random offset within radius N. The vanilla
 * !hasNoSky && !ADVENTURE guard and Forge's defaultHasSpawnFuzz flag stay untouched, so nether/end/adventure worlds
 * keep vanilla behavior automatically.
 */
@SuppressWarnings("unused")
@Mixin(WorldProvider.class)
public abstract class MixinWorldProvider_SpawnRadius {

    @Redirect(
            method = "getRandomizedSpawnPoint",
            at = @At(value = "INVOKE", target = "Ljava/util/Random;nextInt(I)I", ordinal = 0),
            remap = false)
    public int serverutilities$nextSpawnRandomX(Random instance, int bound) {
        return serverutilities$spawnRandom(instance, bound);
    }

    @Redirect(
            method = "getRandomizedSpawnPoint",
            at = @At(value = "INVOKE", target = "Ljava/util/Random;nextInt(I)I", ordinal = 1),
            remap = false)
    public int serverutilities$nextSpawnRandomZ(Random instance, int bound) {
        return serverutilities$spawnRandom(instance, bound);
    }

    @Unique
    private int serverutilities$spawnRandom(Random instance, int bound) {
        int radius = serverutilities$spawnRadius();
        if (radius >= 0) {
            // Forge subtracts bound / 2 (spawnFuzzHalf) after the draw, so compensate: the final delta is
            // nextInt(2N) - N (N = clamped radius); N = 0 -> delta 0 -> exact world spawn. Cap avoids
            // nextInt(negative) on overflow for absurd gamerule values.
            int r = Math.min(radius, 100000);
            return r == 0 ? bound / 2 : instance.nextInt(r * 2) - r + bound / 2;
        }
        // rule missing or unparseable: vanilla behavior (bound = worldtype spawn fuzz)
        return instance.nextInt(bound);
    }

    @Unique
    private int serverutilities$spawnRadius() {
        try {
            WorldProvider provider = (WorldProvider) (Object) this;
            if (provider.worldObj == null) {
                return -1;
            }
            return Integer.parseInt(provider.worldObj.getGameRules().getGameRuleStringValue("spawnRadius"));
        } catch (NumberFormatException e) {
            // 1.7.10 GameRules returns "" for unknown rules
            return -1;
        }
    }
}
