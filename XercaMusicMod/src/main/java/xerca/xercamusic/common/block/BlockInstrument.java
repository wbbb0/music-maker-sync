package xerca.xercamusic.common.block;


import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import xerca.xercamusic.client.ModClient;
import xerca.xercamusic.common.entity.EntityMusicSpirit;
import xerca.xercamusic.common.item.IItemInstrument;
import xerca.xercamusic.common.item.ItemMusicSheet;

import java.util.List;

import static xerca.xercamusic.common.Mod.onlyRunOnClient;

public abstract class BlockInstrument extends Block {
    protected BlockInstrument(Properties properties) {
        super(properties);
    }

    public abstract IItemInstrument getItemInstrument();

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (xerca.xercamusic.common.sync.AudioSpace.distance(level, Vec3.atCenterOf(pos), player.position()) > 16) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        ItemStack handStack = player.getItemInHand(hand);
        if (handStack.getItem() instanceof ItemMusicSheet) {
            playMusic(level, player, pos);
            return ItemInteractionResult.SUCCESS;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (xerca.xercamusic.common.sync.AudioSpace.distance(level, Vec3.atCenterOf(pos), player.position()) > 16) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            onlyRunOnClient(() -> () -> ModClient.showInstrumentGui(getItemInstrument(), pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private void playMusic(Level worldIn, Player playerIn, BlockPos pos) {
        List<EntityMusicSpirit> musicSpirits = worldIn.getEntitiesOfClass(EntityMusicSpirit.class, playerIn.getBoundingBox().inflate(3.0), entity -> playerIn.equals(entity.getBody()));
        if (musicSpirits.isEmpty()) {
            worldIn.addFreshEntity(new EntityMusicSpirit(worldIn, playerIn, pos, getItemInstrument()));
        } else {
            musicSpirits.forEach(spirit -> spirit.setPlaying(false));
        }
    }
}
