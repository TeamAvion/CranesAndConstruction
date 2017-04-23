package me.lordsaad.cc.client.gui;

import com.google.common.collect.HashMultimap;
import com.teamwizardry.librarianlib.core.client.ClientTickHandler;
import com.teamwizardry.librarianlib.features.gui.EnumMouseButton;
import com.teamwizardry.librarianlib.features.gui.GuiBase;
import com.teamwizardry.librarianlib.features.gui.GuiComponent;
import com.teamwizardry.librarianlib.features.gui.components.*;
import com.teamwizardry.librarianlib.features.gui.mixin.ButtonMixin;
import com.teamwizardry.librarianlib.features.gui.mixin.ScissorMixin;
import com.teamwizardry.librarianlib.features.gui.mixin.gl.GlMixin;
import com.teamwizardry.librarianlib.features.kotlin.ClientUtilMethods;
import com.teamwizardry.librarianlib.features.math.Vec2d;
import com.teamwizardry.librarianlib.features.network.PacketHandler;
import com.teamwizardry.librarianlib.features.sprite.Sprite;
import com.teamwizardry.librarianlib.features.sprite.Texture;
import me.lordsaad.cc.CCMain;
import me.lordsaad.cc.api.CraneChunkCache;
import me.lordsaad.cc.api.CraneManager;
import me.lordsaad.cc.common.network.PacketSyncBlockBuild;
import me.lordsaad.cc.common.tile.TileCraneCore;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.VertexBuffer;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.ChunkCache;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;

/**
 * Created by LordSaad.
 */
public class GuiCrane extends GuiBase {

	private static final Texture textureBackground = new Texture(new ResourceLocation(CCMain.MOD_ID, "textures/gui/crane_gui.png"));
	private static final Sprite spriteBackground = textureBackground.getSprite("bg", 245, 256);
	private static final Sprite tileSelector = new Sprite(new ResourceLocation(CCMain.MOD_ID, "textures/gui/tile_select.png"));
	private static final Sprite tileSelector2 = new Sprite(new ResourceLocation(CCMain.MOD_ID, "textures/gui/tile_select_2.png"));
	private static Minecraft mc = Minecraft.getMinecraft();
	private ComponentStack selected;
	private ComponentSprite selectionRect = new ComponentSprite(tileSelector, 0, 0, 32, 32);
	private ComponentSprite hoverRect = new ComponentSprite(tileSelector, 0, 0, 32, 32);

	private EnumMap<BlockRenderLayer, HashMultimap<IBlockState, BlockPos>> blocks = new EnumMap<>(BlockRenderLayer.class);
	private EnumMap<BlockRenderLayer, int[]> vboCaches = new EnumMap<>(BlockRenderLayer.class);

	private Vec2d offset, from;

	private double tick = 0;
	private int prevX = 0, prevY = 0;
	private int tileSize = 16;

	private int[] vbocache1 = null, vbocache2 = null;

	private BlockPos previousBlock = null;

	public GuiCrane(BlockPos pos) {
		super(490, 512);

		selectionRect.setVisible(false);
		hoverRect.setVisible(false);
		getMainComponents().add(selectionRect, hoverRect);

		CraneManager manager = new CraneManager(mc.world, pos);
		TileCraneCore core = (TileCraneCore) mc.world.getTileEntity(pos);

		int width = manager.width == 0 ? (core == null ? 10 : core.armLength) : manager.width;
		int height = manager.height == 0 ? 10 : manager.height;

		BlockPos highestBlock = manager.highestBlock, craneSeat = manager.seat;

		int extraHeight = ((highestBlock == null || craneSeat == null) ? 1 : Math.abs(highestBlock.getY() - craneSeat.getY()) + 1);

		blocks.clear();
		vbocache2 = null;
		vbocache1 = null;
		for (int i = -width; i < width; i++) {
			for (int j = -width; j < width; j++) {
				for (int k = -height - extraHeight; k < extraHeight; k++) {
					BlockPos pos1 = new BlockPos(pos.getX() + i, pos.getY() + k, pos.getZ() + j);
					if (mc.world.isAirBlock(pos1)) {
						continue;
					}

					double dist = new Vec2d(pos1.getX(), pos1.getZ()).add(0.5, 0.5).sub(new Vec2d(pos.getX(), pos.getZ()).add(0.5, 0.5)).length();

					if (dist > width) continue;

					IBlockState state = mc.world.getBlockState(pos1);
					int sky = mc.world.getLightFromNeighborsFor(EnumSkyBlock.SKY, pos1);
					int block = mc.world.getLightFromNeighborsFor(EnumSkyBlock.BLOCK, pos1);
					boolean surrounded = true;
					for (EnumFacing facing : EnumFacing.VALUES) {
						IBlockState offsetState = mc.world.getBlockState(pos1.offset(facing));
						if (mc.world.isAirBlock(pos1.offset(facing))
								|| !offsetState.isFullBlock()
								|| !offsetState.isOpaqueCube()
								|| !offsetState.isBlockNormalCube()
								|| !offsetState.isNormalCube()
								|| offsetState.isTranslucent()
								|| offsetState.getMaterial().isLiquid()
								|| !offsetState.getMaterial().isSolid()) {
							surrounded = false;
							break;
						}
					}
					if (Math.max(sky, block) >= 15 || !surrounded) {
						BlockRenderLayer layer = state.getBlock().getBlockLayer();
						HashMultimap<IBlockState, BlockPos> multimap = blocks.get(layer);
						if (multimap == null) {
							multimap = HashMultimap.create();
						}
						multimap.put(state, pos1);
						blocks.put(layer, multimap);
					}
				}
			}
		}

		ChunkCache blockAccess = new CraneChunkCache(mc.world, pos.add(-width, -height - extraHeight, -width), pos.add(width, extraHeight, width), 0, pos, width);

		ComponentSprite compBackground = new ComponentSprite(spriteBackground, 0, 0, 490, 512);
		getMainComponents().add(compBackground);

		ComponentVoid boxing2 = new ComponentVoid(175, 10, 150 * 2, 88 * 2);
		ComponentVoid sideView = new ComponentVoid(0, 0, 150 * 2, 88 * 2);

		boxing2.add(sideView);
		ScissorMixin.INSTANCE.scissor(sideView);

		int guiSideWidth = 70 * 2;
		double tileSideSize = guiSideWidth / (Math.max(height, width) / 1.5) / 2.0;
		sideView.BUS.hook(GuiComponent.PostDrawEvent.class, (event) -> {

			if (tick >= 360 * 2) {
				tick = 0;
			} else {
				tick++;
			}

			int horizontalAngle = 40;
			int verticalAngle = 45;

			GlStateManager.pushMatrix();
			GlStateManager.enableCull();

			GlStateManager.matrixMode(GL11.GL_MODELVIEW);
			GlStateManager.shadeModel(GL11.GL_SMOOTH);
			GlStateManager.translate(150, 75 - Math.max(10, tileSideSize / 20), 500);
			GlStateManager.rotate(horizontalAngle, -1, 0, 0);
			GlStateManager.rotate((float) ((tick + event.getPartialTicks()) / 2), 0, 1, 0);
			GlStateManager.translate(tileSideSize, -tileSideSize, tileSideSize);
			GlStateManager.scale(tileSideSize, -tileSideSize, tileSideSize);

			mc.renderEngine.bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
			for (BlockRenderLayer layer : blocks.keySet()) {
				Tessellator tes = Tessellator.getInstance();
				VertexBuffer buffer = tes.getBuffer();
				BlockRendererDispatcher dispatcher = Minecraft.getMinecraft().getBlockRendererDispatcher();

				if (vboCaches.get(layer) == null) {
					buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
					buffer.setTranslation(-pos.getX(), -pos.getY(), -pos.getZ());

					for (IBlockState state2 : blocks.get(layer).keySet()) {
						for (BlockPos pos2 : blocks.get(layer).get(state2)) {
							dispatcher.renderBlock(state2, pos2, blockAccess, buffer);
						}
					}

					vboCaches.put(layer, ClientUtilMethods.createCacheArrayAndReset(buffer));
				}

				buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
				buffer.addVertexData(vboCaches.get(layer));

				tes.draw();

				switch (layer) {
					case SOLID: {
						GlStateManager.enableAlpha();
						break;
					}
					case CUTOUT_MIPPED: {
						mc.getTextureManager().getTexture(TextureMap.LOCATION_BLOCKS_TEXTURE).setBlurMipmap(false, false);
						break;
					}
					case CUTOUT: {
						mc.getTextureManager().getTexture(TextureMap.LOCATION_BLOCKS_TEXTURE).restoreLastBlurMipmap();
						break;
					}
					case TRANSLUCENT: {
						break;
					}
				}
			}

			GlStateManager.disableCull();
			GlStateManager.popMatrix();
		});

		getMainComponents().add(boxing2);

		ComponentVoid boxing = new ComponentVoid(175, 200, 300, 300);
		ComponentRect topView = new ComponentRect(0, 0, 300, 300);
		topView.getColor().setValue(Color.BLACK);
		boxing.add(topView);
		getMainComponents().add(boxing);
		ScissorMixin.INSTANCE.scissor(topView);

		topView.BUS.hook(GuiComponent.PostDrawEvent.class, (event) -> {
			GlStateManager.pushMatrix();
			GlStateManager.disableCull();
			GlStateManager.matrixMode(GL11.GL_MODELVIEW);
			GlStateManager.shadeModel(GL11.GL_SMOOTH);

			GlStateManager.translate(133, 133, 1000);
			GlStateManager.scale(tileSize, -tileSize, tileSize);
			GlStateManager.rotate(90, 1, 0, 0);
			if (offset != null) GlStateManager.translate(-offset.getX() / tileSize, 0, -offset.getY() / tileSize);

			mc.renderEngine.bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
			for (BlockRenderLayer layer : blocks.keySet()) {
				Tessellator tes = Tessellator.getInstance();
				VertexBuffer buffer = tes.getBuffer();
				BlockRendererDispatcher dispatcher = Minecraft.getMinecraft().getBlockRendererDispatcher();

				if (vboCaches.get(layer) == null) {
					buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);

					for (IBlockState state2 : blocks.get(layer).keySet())
						for (BlockPos pos2 : blocks.get(layer).get(state2))
							dispatcher.renderBlock(state2, pos2, blockAccess, buffer);

					vboCaches.put(layer, ClientUtilMethods.createCacheArrayAndReset(buffer));
				}

				buffer.begin(7, DefaultVertexFormats.BLOCK);
				buffer.addVertexData(vboCaches.get(layer));

				tes.draw();

				switch (layer) {
					case SOLID: {
						GlStateManager.enableAlpha();
						break;
					}
					case CUTOUT_MIPPED: {
						mc.getTextureManager().getTexture(TextureMap.LOCATION_BLOCKS_TEXTURE).setBlurMipmap(false, false);
						break;
					}
					case CUTOUT: {
						mc.getTextureManager().getTexture(TextureMap.LOCATION_BLOCKS_TEXTURE).restoreLastBlurMipmap();
						break;
					}
					case TRANSLUCENT: {
						break;
					}
				}
			}

			GlStateManager.popMatrix();

			if (!event.getComponent().getMouseOver()) {
				return;
			}
			int gridX = event.getMousePos().getXi() / tileSize * tileSize;
			int gridY = event.getMousePos().getYi() / tileSize * tileSize;

			GlStateManager.pushMatrix();
			GlStateManager.translate(0, 0, 1000);
			tileSelector2.getTex().bind();
			tileSelector2.draw((int) ClientTickHandler.getPartialTicks(), gridX + 5, gridY + 5, tileSize, tileSize);
			GlStateManager.popMatrix();
		});

		new ButtonMixin<>(topView, () -> {
		});

		topView.BUS.hook(GuiComponent.MouseWheelEvent.class, (event) -> {
			if (event.getDirection() == GuiComponent.MouseWheelDirection.UP) {
				if (tileSize < 50) {
					tileSize += 2;
				}
			} else {
				if (tileSize > 2) {
					tileSize -= 2;
				}
			}
		});

		topView.BUS.hook(GuiComponent.MouseDragEvent.class, (event) -> {
			if (!event.getComponent().getMouseOver()) {
				return;
			}
			if (event.getButton() != EnumMouseButton.MIDDLE) {
				int x = event.getMousePos().getXi() / tileSize - 8;
				int y = event.getMousePos().getYi() / tileSize - 8;
				if (x == prevX && y == prevY) {
					return;
				} else {
					prevX = x;
					prevY = y;
				}

				BlockPos block = pos.add(new BlockPos(x, 0, y));

				if (previousBlock != null && previousBlock.toLong() == block.toLong()) {
					return;
				} else {
					previousBlock = block;
				}

				double dist = new Vec2d(block.getX(), block.getZ()).add(0.5, 0.5).sub(new Vec2d(pos.getX(), pos.getZ()).add(0.5, 0.5)).length();

				if (dist > width) return;

				if (selected != null) {
					ItemStack stack = selected.getStack().getValue(selected);
					IBlockState checkAgainstBlock = mc.world.getBlockState(getHighestBlock(mc.world, block));
					if (!stack.canPlaceOn(checkAgainstBlock.getBlock()) && !mc.player.capabilities.allowEdit) {
						return;
					}

					PacketHandler.NETWORK.sendToServer(new PacketSyncBlockBuild(pos, mc.player.inventory.getSlotFor(stack), block, width));
				}
			} else {
				if (from != null) {
					offset = from.sub(event.getMousePos());
				}
			}
		});

		topView.BUS.hook(GuiComponent.MouseDownEvent.class, (event) -> {
			if (!event.getComponent().getMouseOver()) {
				return;
			}
			if (event.getButton() != EnumMouseButton.MIDDLE) {

				if (!event.getComponent().getMouseOver()) {
					return;
				}
				double x = (event.getMousePos().getXi() / tileSize);
				double y = (event.getMousePos().getYi() / tileSize);
				BlockPos block = pos.add(new BlockPos(x, 0, y));

				double dist = new Vec2d(block.getX(), block.getZ()).add(0.5, 0.5).sub(new Vec2d(pos.getX(), pos.getZ()).add(0.5, 0.5)).length();

				if (dist > width) return;

				if (selected != null) {
					ItemStack stack = selected.getStack().getValue(selected);
					IBlockState checkAgainstBlock = mc.world.getBlockState(getHighestBlock(mc.world, block));
					if (!stack.canPlaceOn(checkAgainstBlock.getBlock()) && !mc.player.capabilities.allowEdit) {
						return;
					}

					PacketHandler.NETWORK.sendToServer(new PacketSyncBlockBuild(pos, mc.player.inventory.getSlotFor(stack), block, width));
				}
			} else {
				if (offset == null) {
					from = event.getMousePos();
				} else {
					from = event.getMousePos().add(offset);
				}
			}
		});

		Deque<ItemStack> itemBlocks = new ArrayDeque<>();
		for (ItemStack stack : mc.player.inventory.mainInventory) {
			if (stack.getItem() instanceof ItemBlock) {
				itemBlocks.add(stack);
			}
		}

		final int size = itemBlocks.size();
		for (int i = 0; i < Math.ceil(size / 9.0); i++) {
			ComponentList inventory = new ComponentList(16 + (i * 36), 16);
			inventory.setChildScale(2);

			for (int j = 0; j < 9; j++) {
				if (itemBlocks.isEmpty()) {
					break;
				}
				ItemStack stack = itemBlocks.pop();
				ComponentStack compStack = new ComponentStack(0, 0);
				compStack.setMarginBottom(2);
				compStack.getStack().setValue(stack);

				final int finalI = i, finalJ = j;
				compStack.BUS.hook(GuiComponent.MouseClickEvent.class, (event) -> {
					if (!event.getComponent().getMouseOver()) {
						return;
					}
					selected = compStack;
					selectionRect.setVisible(true);
					selectionRect.setPos(new Vec2d(16 + finalI * 36, 16 + finalJ * 36));
					GlMixin.INSTANCE.transform(selectionRect).setValue(new Vec3d(0, 0, 100));
				});

				compStack.BUS.hook(GuiComponent.MouseOverEvent.class, (event) -> {
					if (!event.getComponent().getMouseOver()) {
						return;
					}
					hoverRect.setVisible(true);
					hoverRect.setPos(new Vec2d(16 + finalI * 36, 16 + finalJ * 36));
					GlMixin.INSTANCE.transform(hoverRect).setValue(new Vec3d(0, 0, 100));
				});
				inventory.add(compStack);
			}
			getMainComponents().add(inventory);
		}
	}

	@Override
	public boolean doesGuiPauseGame() {
		return false;
	}

	public BlockPos getHighestBlock(World world, BlockPos pos) {
		BlockPos.MutableBlockPos highest = new BlockPos.MutableBlockPos(pos.getX(), 255, pos.getZ());
		IBlockState stateHighest = world.getBlockState(highest);
		while (world.isAirBlock(highest) || stateHighest.getMaterial().isLiquid()) {
			if (highest.getY() <= 0) {
				break;
			}
			highest.move(EnumFacing.DOWN);
			stateHighest = world.getBlockState(highest);
		}

		return highest;
	}
}
