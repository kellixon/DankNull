package p455w0rd.danknull.blocks.tiles;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.datafix.FixTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import p455w0rd.danknull.api.IDankNullHandler;
import p455w0rd.danknull.api.IRedstoneControllable;
import p455w0rd.danknull.init.ModDataFixing.DankNullFixer;
import p455w0rd.danknull.init.ModGlobals.NBT;
import p455w0rd.danknull.integration.PwLib;
import p455w0rd.danknull.inventory.DankNullHandler;
import p455w0rd.danknull.inventory.cap.CapabilityDankNull;
import p455w0rd.danknull.items.ItemDankNull;
import p455w0rd.danknull.network.VanillaPacketDispatcher;
import p455w0rdslib.integration.Albedo;

/**
 * @author p455w0rd
 *
 */
public class TileDankNullDock extends TileEntity implements IRedstoneControllable {

	private RedstoneMode redstoneMode = RedstoneMode.REQUIRED;
	private boolean hasRedstoneSignal = false;
	private ItemStack dankNull = ItemStack.EMPTY;
	private final DankNullFixer fixer = new DankNullFixer(FixTypes.BLOCK_ENTITY);
	private IDankNullHandler dankNullHandler = null;

	@Override
	public boolean hasCapability(final Capability<?> capability, final EnumFacing facing) {
		return !getDankNull().isEmpty() && (capability == CapabilityDankNull.DANK_NULL_CAPABILITY || capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY || Albedo.albedoCapCheck(capability) || PwLib.checkCap(capability) || super.hasCapability(capability, facing));
	}

	@Override
	public <T> T getCapability(final Capability<T> capability, final EnumFacing facing) {
		if (!getDankNull().isEmpty()) {
			if (Albedo.albedoCapCheck(capability)) {
				return p455w0rd.danknull.integration.Albedo.getTileCapability(getPos(), getDankNull());
			}
			if (PwLib.checkCap(capability)) {
				return PwLib.getTileCapability(this);
			}
			if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
				return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(dankNullHandler);
			}
			if (capability == CapabilityDankNull.DANK_NULL_CAPABILITY) {
				return CapabilityDankNull.DANK_NULL_CAPABILITY.cast(dankNullHandler);
			}
		}
		return super.getCapability(capability, facing);
	}

	public void removeDankNull() {
		if (!getDankNull().isEmpty()) {
			dankNull = ItemStack.EMPTY;
			dankNullHandler = null;
			markDirty();
		}
	}

	public void setDankNull(final ItemStack dankNull) {
		this.dankNull = new ItemStack(fixer.fixTagCompound(dankNull.serializeNBT()));
		if (!this.dankNull.isEmpty()) {
			dankNullHandler = new DankNullHandler(ItemDankNull.getTier(this.dankNull)) {

				@Override
				public ItemStack getStackInSlot(final int slot) {
					return getExtractableStackInSlot(slot);
				}

				@Nonnull
				@Override
				public ItemStack extractItem(final int slot, final int amount, final boolean simulate) {
					if (amount < 1) {
						return ItemStack.EMPTY;
					}
					validateSlot(slot);
					final ItemStack existing = getFullStackInSlot(slot);
					if (existing.isEmpty()) {
						return ItemStack.EMPTY;
					}
					final int existingCount = existing.getCount();
					final int extract = Math.min(amount, existing.getMaxStackSize());
					if (existingCount <= extract) {
						if (!simulate) {
							getStackList().set(slot, ItemStack.EMPTY);
							onContentsChanged(slot);
						}
						return existing;
					}
					else {
						if (!simulate) {
							getStackList().set(slot, ItemHandlerHelper.copyStackWithSize(existing, existingCount - extract));
							onContentsChanged(slot);
						}
						return ItemHandlerHelper.copyStackWithSize(existing, extract);
					}
				}

				@Override
				protected void onContentsChanged(final int slot) {
					super.onContentsChanged(slot);
					TileDankNullDock.this.markDirty();
				}

				@Override
				protected void onSettingsChanged() {
					super.onSettingsChanged();
					TileDankNullDock.this.markDirty();
				}
			};
			CapabilityDankNull.DANK_NULL_CAPABILITY.readNBT(dankNullHandler, null, this.dankNull.getTagCompound());
		}
		markDirty();
	}

	public ItemStack getDankNull() {
		return dankNull;
	}

	@Override
	public RedstoneMode getRedstoneMode() {
		return redstoneMode;
	}

	@Override
	public void setRedstoneMode(final RedstoneMode mode) {
		redstoneMode = mode;
		markDirty();
	}

	@Override
	public boolean isRedstoneRequirementMet() {
		switch (getRedstoneMode()) {
		default:
		case IGNORED:
			return true;
		case REQUIRED:
			return hasRSSignal();
		case REQUIRE_NONE:
			return !hasRSSignal();
		}
	}

	@Override
	public boolean hasRSSignal() {
		return hasRedstoneSignal;
	}

	@Override
	public void setRSSignal(final boolean isPowered) {
		hasRedstoneSignal = isPowered;
	}

	@Override
	public boolean shouldRefresh(final World world, final BlockPos pos, final IBlockState oldState, final IBlockState newSate) {
		return super.shouldRefresh(world, pos, oldState, newSate);
	}

	@Override
	@Nonnull
	public NBTTagCompound getUpdateTag() {
		return writeToNBT(new NBTTagCompound());
	}

	@Override
	@Nullable
	public SPacketUpdateTileEntity getUpdatePacket() {
		return new SPacketUpdateTileEntity(getPos(), -1, getUpdateTag());
	}

	@Override
	public void onDataPacket(final NetworkManager net, final SPacketUpdateTileEntity pkt) {
		handleUpdateTag(pkt.getNbtCompound());
	}

	@Override
	public void markDirty() {
		super.markDirty();
		VanillaPacketDispatcher.dispatchTEToNearbyPlayers(this);
		if (world != null) { // Shouldn't be null
			world.markBlockRangeForRenderUpdate(pos, pos);
			world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
			world.scheduleBlockUpdate(pos, getBlockType(), 0, 0);
		}
	}

	@Override
	public void readFromNBT(final NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		if (nbt.hasKey(NBT.DOCKEDSTACK, Constants.NBT.TAG_COMPOUND)) {
			final NBTTagCompound dockedTag = nbt.getCompoundTag(NBT.DOCKEDSTACK);
			final ItemStack dankNull = new ItemStack(dockedTag);
			setDankNull(dankNull);
			if (!dankNull.isEmpty() && dankNull.hasTagCompound()) {
				CapabilityDankNull.DANK_NULL_CAPABILITY.readNBT(dankNullHandler, null, dankNull.getTagCompound());
			}
		}
	}

	@Override
	@Nonnull
	public NBTTagCompound writeToNBT(NBTTagCompound compound) {
		compound = super.writeToNBT(compound);
		final ItemStack dankNull = getDankNull();
		if (dankNullHandler != null) {
			dankNull.setTagCompound((NBTTagCompound) CapabilityDankNull.DANK_NULL_CAPABILITY.writeNBT(dankNullHandler, null));
		}
		compound.setTag(NBT.DOCKEDSTACK, dankNull.serializeNBT());
		return compound;
	}

	public enum RedstoneMode {
			REQUIRED, REQUIRE_NONE, IGNORED
	}

}
