package xerca.xercamusic.common.sync;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
public final class AudioSpace {
    private AudioSpace() {}
    public static double distance(Level level, Vec3 a, Vec3 b) {
        return ModList.get().isLoaded("sable") ? SableAccess.distance(level,a,b) : a.distanceToSqr(b);
    }
    private static final class SableAccess {
        static double distance(Level level,Vec3 a,Vec3 b) { return dev.ryanhcode.sable.Sable.HELPER.distanceSquaredWithSubLevels(level,a,b); }
    }
}
