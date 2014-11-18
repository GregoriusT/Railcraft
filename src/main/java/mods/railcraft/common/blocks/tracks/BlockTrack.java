/* 
 * Copyright (c) CovertJaguar, 2014 http://railcraft.info
 * 
 * This code is the property of CovertJaguar
 * and may only be used with explicit written
 * permission unless otherwise specified on the
 * license page at http://railcraft.info/wiki/info:license.
 */
package mods.railcraft.common.blocks.tracks;

import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.*;
import org.apache.logging.log4j.Level;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.block.BlockRailBase;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityMinecart;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.world.IBlockAccess;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.stats.StatList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import mods.railcraft.api.core.IPostConnection;
import mods.railcraft.api.core.ITextureLoader;
import mods.railcraft.api.electricity.IElectricGrid;
import mods.railcraft.api.tracks.ITrackBlocksMovement;
import mods.railcraft.api.tracks.ITrackCustomShape;
import mods.railcraft.api.tracks.ITrackEmitter;
import mods.railcraft.api.tracks.ITrackInstance;
import mods.railcraft.api.tracks.TrackRegistry;
import mods.railcraft.api.tracks.TrackSpec;
import mods.railcraft.client.particles.ParticleHelper;
import mods.railcraft.common.blocks.RailcraftBlocks;
import mods.railcraft.common.core.Railcraft;
import mods.railcraft.common.items.ItemOveralls;
import mods.railcraft.common.plugins.forge.PowerPlugin;
import mods.railcraft.common.util.inventory.InvTools;
import mods.railcraft.common.util.misc.Game;
import mods.railcraft.common.util.misc.MiscTools;
import mods.railcraft.common.util.misc.RailcraftDamageSource;
import net.minecraft.block.Block;
import net.minecraft.client.particle.EffectRenderer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;

public class BlockTrack extends BlockRailBase implements IPostConnection {

    protected final int renderType;

    public BlockTrack(int modelID) {
        super(false);
        renderType = modelID;
        setBlockBounds(0.0F, 0.0F, 0.0F, 1.0F, 0.125F, 1.0F);
        setResistance(3.5F);
        setHardness(1.05F);
        setStepSound(soundTypeMetal);
        setCreativeTab(CreativeTabs.tabTransport);
        GameRegistry.registerTileEntity(TileTrack.class, "RailcraftTrackTile");
        GameRegistry.registerTileEntity(TileTrackTESR.class, "RailcraftTrackTESRTile");

        try {
            TrackSpec.blockTrack = this;
        } catch (Throwable error) {
            Game.logErrorAPI(Railcraft.getModId(), error, TrackSpec.class);
        }
    }

    @Override
    public void getSubBlocks(Item item, CreativeTabs tab, List list) {
        Map<Short, TrackSpec> specs = TrackRegistry.getTrackSpecIDs();

        Set<TrackSpec> railcraftSpecs = new HashSet<TrackSpec>();
        for (EnumTrack track : EnumTrack.getCreativeList()) {
            TrackSpec spec = specs.get((short) track.ordinal());
            if (spec == null)
                continue;
            railcraftSpecs.add(spec);
            if (track.isEnabled())
                list.add(spec.getItem());
        }

        Set<TrackSpec> otherSpecs = new HashSet<TrackSpec>(specs.values());
        otherSpecs.removeAll(railcraftSpecs);
        otherSpecs.remove(TrackRegistry.getTrackSpec("Railcraft:default"));
        for (TrackSpec spec : otherSpecs) {
            list.add(spec.getItem());
        }
    }

    @Override
    public ItemStack getPickBlock(MovingObjectPosition target, World world, int x, int y, int z) {
        TileEntity tile = world.getTileEntity(x, y, z);
        if (tile instanceof TileTrack) {
            ITrackInstance track = ((TileTrack) tile).getTrackInstance();
            return track.getTrackSpec().getItem();
        }
        return null;
    }

    @Override
    public boolean rotateBlock(World worldObj, int x, int y, int z, ForgeDirection axis) {
        return false;
    }

    @Override
    public int getRenderType() {
        return renderType;
    }

    @Override
    public boolean hasTileEntity(int metadata) {
        return true;
    }

    @Override
    public int getMobilityFlag() {
        return 0;
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBoxFromPool(World world, int i, int j, int k) {
        TileEntity tile = world.getTileEntity(i, j, k);
        if (tile instanceof TileTrack) {
            ITrackInstance track = ((TileTrack) tile).getTrackInstance();
            if (track instanceof ITrackCustomShape)
                return ((ITrackCustomShape) track).getCollisionBoundingBoxFromPool();
        }
        return null;
    }

    @Override
    public AxisAlignedBB getSelectedBoundingBoxFromPool(World world, int i, int j, int k) {
        TileEntity tile = world.getTileEntity(i, j, k);
        if (tile instanceof TileTrack) {
            ITrackInstance track = ((TileTrack) tile).getTrackInstance();
            if (track instanceof ITrackCustomShape)
                return ((ITrackCustomShape) track).getSelectedBoundingBoxFromPool();
        }
        return AxisAlignedBB.getBoundingBox((double) i + minX, (double) j + minY, (double) k + minZ, (double) i + maxX, (double) j + maxY, (double) k + maxZ);
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public MovingObjectPosition collisionRayTrace(World world, int i, int j, int k, Vec3 vec3d, Vec3 vec3d1) {
        TileEntity tile = world.getTileEntity(i, j, k);
        if (tile instanceof TileTrack) {
            ITrackInstance track = ((TileTrack) tile).getTrackInstance();
            if (track instanceof ITrackCustomShape)
                return ((ITrackCustomShape) track).collisionRayTrace(vec3d, vec3d1);
        }
        return super.collisionRayTrace(world, i, j, k, vec3d, vec3d1);
    }

    @Override
    public void setBlockBoundsBasedOnState(IBlockAccess iblockaccess, int i, int j, int k) {
        int l = iblockaccess.getBlockMetadata(i, j, k);
        if (l >= 2 && l <= 5)
            setBlockBounds(0.0F, 0.0F, 0.0F, 1.0F, 0.625F, 1.0F);
        else
            setBlockBounds(0.0F, 0.0F, 0.0F, 1.0F, 0.125F, 1.0F);
    }

    @Override
    public boolean getBlocksMovement(IBlockAccess world, int x, int y, int z) {
        TileEntity tile = world.getTileEntity(x, y, z);
        if (tile instanceof TileTrack) {
            ITrackInstance track = ((TileTrack) tile).getTrackInstance();
            if (track instanceof ITrackBlocksMovement)
                return !((ITrackBlocksMovement) track).blocksMovement();
        }
        return super.getBlocksMovement(world, x, y, z);
    }

    @Override
    public void onEntityCollidedWithBlock(World world, int x, int y, int z, Entity entity) {
        if (Game.isNotHost(world))
            return;

        if (!MiscTools.isKillabledEntity(entity))
            return;

        TileEntity tile = world.getTileEntity(x, y, z);
        if (!(tile instanceof TileTrack))
            return;

        ITrackInstance track = ((TileTrack) tile).getTrackInstance();
        if (!(track instanceof IElectricGrid))
            return;

        IElectricGrid.ChargeHandler chargeHandler = ((IElectricGrid) track).getChargeHandler();
        if (chargeHandler.getCharge() > 2000)
            if (entity instanceof EntityPlayer && ItemOveralls.isPlayerWearing((EntityPlayer) entity)) {
                if (!((EntityPlayer) entity).capabilities.isCreativeMode && MiscTools.RANDOM.nextInt(150) == 0) {
                    EntityPlayer player = ((EntityPlayer) entity);
                    ItemStack pants = player.getCurrentArmor(MiscTools.ArmorSlots.LEGS.ordinal());
                    player.setCurrentItemOrArmor(MiscTools.ArmorSlots.LEGS.ordinal() + 1, InvTools.damageItem(pants, 1));
                }
            } else if (((EntityLivingBase) entity).attackEntityFrom(RailcraftDamageSource.TRACK_ELECTRIC, 2))
                chargeHandler.removeCharge(2000);
    }

    @Override
    public boolean canPlaceBlockAt(World world, int i, int j, int k) {
        return !TrackTools.isRailBlockAt(world, i, j + 1, k);
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    @Override
    public boolean canProvidePower() {
        return true;
    }

    @Override
    public boolean canConnectRedstone(IBlockAccess world, int x, int y, int z, int side) {
        TileEntity tile = world.getTileEntity(x, y, z);
        if (tile instanceof TileTrack) {
            ITrackInstance track = ((TileTrack) tile).getTrackInstance();
            return track instanceof ITrackEmitter;
        }
        return false;
    }

    @Override
    public int isProvidingWeakPower(IBlockAccess world, int x, int y, int z, int side) {
        TileEntity tile = world.getTileEntity(x, y, z);
        if (tile instanceof TileTrack) {
            ITrackInstance track = ((TileTrack) tile).getTrackInstance();
            return track instanceof ITrackEmitter ? ((ITrackEmitter) track).getPowerOutput() : PowerPlugin.NO_POWER;
        }
        return PowerPlugin.NO_POWER;
    }

    @Override
    public void onMinecartPass(World world, EntityMinecart cart, int i, int j, int k) {
        TileEntity tile = world.getTileEntity(i, j, k);
        if (tile instanceof TileTrack)
            ((TileTrack) tile).getTrackInstance().onMinecartPass(cart);
    }

    @Override
    public int getBasicRailMetadata(IBlockAccess world, EntityMinecart cart, int i, int j, int k) {
        TileEntity tile = world.getTileEntity(i, j, k);
        if (tile instanceof TileTrack)
            return ((TileTrack) tile).getTrackInstance().getBasicRailMetadata(cart);
        return world.getBlockMetadata(i, j, k);
    }

    @Override
    public float getRailMaxSpeed(World world, EntityMinecart cart, int i, int j, int k) {
        TileEntity tile = world.getTileEntity(i, j, k);
        if (tile instanceof TileTrack)
            return ((TileTrack) tile).getTrackInstance().getRailMaxSpeed(cart);
        return 0.4f;
    }

    @Override
    public boolean onBlockActivated(World world, int i, int j, int k, EntityPlayer player, int side, float u1, float u2, float u3) {
        TileEntity tile = world.getTileEntity(i, j, k);
        if (tile instanceof TileTrack)
            return ((TileTrack) tile).getTrackInstance().blockActivated(player);
        return false;
    }

    @Override
    public boolean isFlexibleRail(IBlockAccess world, int i, int j, int k) {
        TileEntity tile = world.getTileEntity(i, j, k);
        if (tile instanceof TileTrack)
            return ((TileTrack) tile).getTrackInstance().isFlexibleRail();
        return false;
    }

    @Override
    public boolean canMakeSlopes(IBlockAccess world, int i, int j, int k) {
        TileEntity tile = world.getTileEntity(i, j, k);
        if (tile instanceof TileTrack)
            return ((TileTrack) tile).getTrackInstance().canMakeSlopes();
        return true;
    }

    @Override
    public IIcon getIcon(int side, int meta) {
        return Blocks.rail.getIcon(side, meta);
    }

    @Override
    public IIcon getIcon(IBlockAccess world, int i, int j, int k, int side) {
        TileEntity tile = world.getTileEntity(i, j, k);
        if (tile instanceof TileTrack)
            return ((TileTrack) tile).getTrackInstance().getIcon();
        return null;
    }

    @Override
    public void registerBlockIcons(IIconRegister iconRegister) {
        for (ITextureLoader iconLoader : TrackRegistry.getIconLoaders()) {
            iconLoader.registerIcons(iconRegister);
        }
    }

    @SideOnly(Side.CLIENT)
    @Override
    public boolean addHitEffects(World worldObj, MovingObjectPosition target, EffectRenderer effectRenderer) {
        return ParticleHelper.addHitEffects(worldObj, RailcraftBlocks.getBlockTrack(), target, effectRenderer, null);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public boolean addDestroyEffects(World worldObj, int x, int y, int z, int meta, EffectRenderer effectRenderer) {
        return ParticleHelper.addDestroyEffects(worldObj, RailcraftBlocks.getBlockTrack(), x, y, z, meta, effectRenderer, null);
    }

    @Override
    public ArrayList<ItemStack> getDrops(World world, int i, int j, int k, int md, int fortune) {
        TileEntity tile = world.getTileEntity(i, j, k);
        ArrayList<ItemStack> items = new ArrayList<ItemStack>();
        if (tile instanceof TileTrack)
            items.add(((TileTrack) tile).getTrackInstance().getTrackSpec().getItem());
        else {
            Game.log(Level.WARN, "Rail Tile was invalid when harvesting rail");
            items.add(new ItemStack(Blocks.rail));
        }
        return items;
    }

    @Override
    public int quantityDropped(int meta, int fortune, Random random) {
        return 1;
    }
//
//    @Override
//    public int idDropped(int i, Random random, int j) {
//        Game.log(Level.WARN, "Wrong function called when harvesting rail");
//        return Blocks.rail.idDropped(i, random, j);
//    }
//

    public TileEntity getBlockEntity(int md) {
        return null;
    }

    // Determine direction here
    @Override
    public void onBlockPlacedBy(World world, int i, int j, int k, EntityLivingBase entityliving, ItemStack stack) {
        TileEntity tile = world.getTileEntity(i, j, k);
        if (tile instanceof TileTrack) {
            ((TileTrack) tile).onBlockPlacedBy(entityliving);
            ((TileTrack) tile).getTrackInstance().onBlockPlacedBy(entityliving);
        }
    }

    @Override
    public void onPostBlockPlaced(World world, int i, int j, int k, int meta) {
//        if(Game.isNotHost(world)) {
//            return;
//        }
        TileEntity tile = world.getTileEntity(i, j, k);
        if (tile instanceof TileTrack)
            ((TileTrack) tile).getTrackInstance().onBlockPlaced();
    }

    @Override
    public void harvestBlock(World world, EntityPlayer entityplayer, int i, int j, int k, int l) {
    }

    @Override
    public boolean removedByPlayer(World world, EntityPlayer player, int x, int y, int z) {
        player.addStat(StatList.mineBlockStatArray[getIdFromBlock(this)], 1);
        player.addExhaustion(0.025F);
        if (Game.isHost(world) && !player.capabilities.isCreativeMode)
            dropBlockAsItem(world, x, y, z, 0, 0);
        return world.setBlockToAir(x, y, z);
    }

    @Override
    public void breakBlock(World world, int i, int j, int k, Block block, int meta) {
        super.breakBlock(world, i, j, k, block, meta);

        try {
            TileEntity tile = world.getTileEntity(i, j, k);
            if (tile instanceof TileTrack)
                ((TileTrack) tile).getTrackInstance().onBlockRemoved();

        } catch (Throwable error) {
            Game.logErrorAPI("Railcraft", error, ITrackInstance.class
            );
        }

        world.removeTileEntity(i, j, k);
    }

    @Override
    public void onNeighborBlockChange(World world, int i, int j, int k, Block block) {
        if (Game.isNotHost(world))
            return;
        TileEntity t = world.getTileEntity(i, j, k);
        if (t instanceof TileTrack) {
            TileTrack tile = (TileTrack) t;
            tile.onNeighborBlockChange(block);
            tile.getTrackInstance().onNeighborBlockChange(block);
        }
    }

    @Override
    public float getBlockHardness(World world, int x, int y, int z) {
        TileEntity tile = world.getTileEntity(x, y, z);
        if (tile instanceof TileTrack)
            try {
                return ((TileTrack) tile).getTrackInstance().getHardness();

            } catch (Throwable error) {
                Game.logErrorAPI("Railcraft", error, ITrackInstance.class
                );
            }
        return super.getBlockHardness(world, x, y, z);
    }

    @Override
    public float getExplosionResistance(Entity exploder, World world, int x, int y, int z, double srcX, double srcY, double srcZ) {
        TileEntity tile = world.getTileEntity(x, y, z);
        if (tile instanceof TileTrack)
            return ((TileTrack) tile).getTrackInstance().getExplosionResistance(srcX, srcY, srcZ, exploder) * 3f / 5f;
        return getExplosionResistance(exploder);
    }

    @Override
    public boolean canBeReplacedByLeaves(IBlockAccess world, int x, int y, int z) {
        return false;
    }

    @Override
    public boolean canCreatureSpawn(EnumCreatureType type, IBlockAccess world, int x, int y, int z) {
        return false;
    }

    @Override
    public ConnectStyle connectsToPost(IBlockAccess world, int i, int j, int k, ForgeDirection side) {
        TileEntity tile = world.getTileEntity(i, j, k);
        if (tile instanceof TileTrack) {
            ITrackInstance track = ((TileTrack) tile).getTrackInstance();
            if (track instanceof IPostConnection)
                return ((IPostConnection) track).connectsToPost(world, i, j, k, side);
        }
        return ConnectStyle.NONE;
    }

}
