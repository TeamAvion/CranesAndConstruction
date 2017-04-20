package me.lordsaad.cc.client.gui;

import com.google.common.collect.HashMultimap;
import com.teamwizardry.librarianlib.core.client.ClientTickHandler;
import com.teamwizardry.librarianlib.features.gui.GuiBase;
import com.teamwizardry.librarianlib.features.gui.GuiComponent;
import com.teamwizardry.librarianlib.features.gui.components.*;
import com.teamwizardry.librarianlib.features.gui.mixin.ButtonMixin;
import com.teamwizardry.librarianlib.features.gui.mixin.ScissorMixin;
import com.teamwizardry.librarianlib.features.gui.mixin.gl.GlMixin;
import com.teamwizardry.librarianlib.features.kotlin.ClientUtilMethods;
import com.teamwizardry.librarianlib.features.math.Vec2d;
import com.teamwizardry.librarianlib.features.math.interpolate.StaticInterp;
import com.teamwizardry.librarianlib.features.network.PacketHandler;
import com.teamwizardry.librarianlib.features.particle.ParticleBuilder;
import com.teamwizardry.librarianlib.features.particle.ParticleSpawner;
import com.teamwizardry.librarianlib.features.particle.functions.InterpFadeInOut;
import com.teamwizardry.librarianlib.features.sprite.Sprite;
import com.teamwizardry.librarianlib.features.sprite.Texture;
import kotlin.Pair;
import me.lordsaad.cc.CCMain;
import me.lordsaad.cc.api.PosUtils;
import me.lordsaad.cc.common.network.PacketReduceStackFromPlayer;
import me.lordsaad.cc.common.network.PacketSendBlockToCrane;
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
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.EnumSkyBlock;

import java.awt.*;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;

/**
 * Created by LordSaad.
 */
public class GuiCrane extends GuiBase {

	private static Minecraft mc = Minecraft.getMinecraft();
	Texture textureBackground = new Texture(new ResourceLocation(CCMain.MOD_ID, "textures/gui/crane_gui.png"));
	Sprite spriteBackground = textureBackground.getSprite("bg", 245, 256);
	Sprite tileSelector = new Sprite(new ResourceLocation(CCMain.MOD_ID, "textures/gui/tile_select.png"));
	Sprite tileSelector2 = new Sprite(new ResourceLocation(CCMain.MOD_ID, "textures/gui/tile_select_2.png"));
	private double tick = 0;
	private HashMultimap<IBlockState, BlockPos> blocks = HashMultimap.create();
	private ComponentStack selected;
	private ComponentSprite selectionRect = new ComponentSprite(tileSelector, 0, 0, 32, 32);
	private ComponentSprite hoverRect = new ComponentSprite(tileSelector, 0, 0, 32, 32);

	private Vec2d offset, from;

	private int[] vbocache1 = null, vbocache2 = null;

	public GuiCrane(BlockPos pos) {
		super(490, 512);

		selectionRect.setVisible(false);
		hoverRect.setVisible(false);
		getMainComponents().add(selectionRect, hoverRect);

		int width;
		int height;

		HashSet<BlockPos> vertical = PosUtils.getCraneVerticalPole(mc.world, pos, true, new HashSet<>());
		HashSet<BlockPos> horizontal = PosUtils.getCraneHorizontalPole(mc.world, pos);
		BlockPos highestBlock = PosUtils.getHighestCranePoint(vertical);
		BlockPos craneSeat = PosUtils.findCraneSeat(mc.world, pos);

		if (horizontal == null) width = 0;
		else width = horizontal.size() - 1;

		if (vertical == null) height = 0;
		else height = vertical.size() - 1;

		if (height == 0 || width == 0) return;

		int extraHeight = ((highestBlock == null || craneSeat == null) ? 1 : Math.abs(highestBlock.getY() - craneSeat.getY()) + 1);

		for (int i = -width; i < width; i++)
			for (int j = -width; j < width; j++)
				for (int k = -height - extraHeight + (width > 12 ? width / 5 : 0); k < extraHeight; k++) {
					BlockPos pos1 = new BlockPos(pos.getX() + i, pos.getY() + k, pos.getZ() + j);
					if (mc.world.isAirBlock(pos1)) continue;

					IBlockState state = mc.world.getBlockState(pos1);
					int sky = mc.world.getLightFromNeighborsFor(EnumSkyBlock.SKY, pos1);
					int block = mc.world.getLightFromNeighborsFor(EnumSkyBlock.BLOCK, pos1);
					boolean surrounded = true;
					for (EnumFacing facing : EnumFacing.VALUES)
						if (mc.world.isAirBlock(pos1.offset(facing))) {
							surrounded = false;
							break;
						}
					if (Math.max(sky, block) >= 15 || !surrounded) {
							blocks.put(state, pos1.subtract(pos));
					}
				}

		ComponentSprite compBackground = new ComponentSprite(spriteBackground, 0, 0, 490, 512);
		getMainComponents().add(compBackground);

		ComponentVoid sideView = new ComponentVoid(175, 10, 150 * 2, 88 * 2);
		int guiSideWidth = 70 * 2;
		int tileSideSize = guiSideWidth / (height * 2);
		sideView.BUS.hook(GuiComponent.PostDrawEvent.class, (event) -> {

			if (tick >= 360) tick = 0;
			else tick++;

			int horizontalAngle = 40;
			int verticalAngle = 45;

			GlStateManager.pushMatrix();
			GlStateManager.disableCull();

			GlStateManager.translate(325, 75, 500);
			GlStateManager.rotate(180, 1, 0, 0);
			GlStateManager.rotate((float) ((tick + event.getPartialTicks())), 0, 1, 0);
			GlStateManager.translate(tileSideSize, tileSideSize, tileSideSize);
			GlStateManager.scale(tileSideSize, tileSideSize, tileSideSize);

			Tessellator tes = Tessellator.getInstance();
			VertexBuffer buffer = tes.getBuffer();
			BlockRendererDispatcher dispatcher = Minecraft.getMinecraft().getBlockRendererDispatcher();

			if (vbocache2 == null) { // if there is no cache, create one
				buffer.begin(7, DefaultVertexFormats.BLOCK); // init the buffer with the settings

				for (IBlockState state2 : blocks.keySet())
					for (BlockPos pos2 : blocks.get(state2))
						dispatcher.getBlockModelRenderer().renderModelFlat(mc.world, dispatcher.getModelForState(state2), state2, pos2, buffer, false, 0);

				vbocache2 = ClientUtilMethods.createCacheArrayAndReset(buffer); // cache your values
			}

			// once that’s done, draw the cache
			mc.renderEngine.bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);

			buffer.begin(7, DefaultVertexFormats.BLOCK);
			buffer.addVertexData(vbocache2);

			tes.draw();

			GlStateManager.popMatrix();
		});

		getMainComponents().add(sideView);

		int guiSize = 300;
		int tileSize = 17;
		//ArrayList<Pair<BlockPos, IBlockState>> grid = new ArrayList<>();
		IBlockState[][] grid = new IBlockState[width * 2][width * 2];
		for (IBlockState state : blocks.keySet())
			for (BlockPos blockPos : blocks.get(state)) {

			}

		ComponentVoid boxing = new ComponentVoid(175, 200, 300, 300);

		ComponentRect topView = new ComponentRect(0, 0, 300, 300);

		boxing.add(topView);
		getMainComponents().add(boxing);
		ScissorMixin.INSTANCE.scissor(topView);

		topView.BUS.hook(GuiComponent.PostDrawEvent.class, (event) -> {
			GlStateManager.pushMatrix();
			GlStateManager.disableCull();
			GlStateManager.enableAlpha();
			GlStateManager.enableBlend();
			GlStateManager.enableLighting();

			GlStateManager.translate(133, 133, 200);
			if (offset != null)
				GlStateManager.translate(-offset.getX(), -offset.getY(), 0);
			GlStateManager.rotate(-90, 1, 0, 0);

			Tessellator tes = Tessellator.getInstance();
			VertexBuffer buffer = tes.getBuffer();
			BlockRendererDispatcher dispatcher = Minecraft.getMinecraft().getBlockRendererDispatcher();

			if (vbocache1 == null) { // if there is no cache, create one
				buffer.begin(7, DefaultVertexFormats.BLOCK); // init the buffer with the settings

				for (IBlockState state : blocks.keySet())
					for (BlockPos pos1 : blocks.get(state))
						dispatcher.getBlockModelRenderer().renderModelFlat(mc.world, dispatcher.getModelForState(state), state, pos1, buffer, false, 0);

				vbocache1 = ClientUtilMethods.createCacheArrayAndReset(buffer); // cache your values
			}

			// once that’s done, draw the cache
			mc.renderEngine.bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
			GlStateManager.scale(tileSize, tileSize, tileSize);
			//GlStateManager.translate(-centerPos.x, -centerPos.y, -centerPos.z);

			buffer.begin(7, DefaultVertexFormats.BLOCK);
			buffer.addVertexData(vbocache1);

			tes.draw();

			GlStateManager.popMatrix();

			if (!event.getComponent().getMouseOver()) return;
			int gridX = event.getMousePos().getXi() / tileSize * tileSize - (offset == null ? 0 : offset.getXi() / tileSize * tileSize);
			int gridY = event.getMousePos().getYi() / tileSize * tileSize - (offset == null ? 0 : offset.getYi() / tileSize * tileSize);

			GlStateManager.pushMatrix();
			GlStateManager.translate(0, 0, 1000);
			tileSelector2.getTex().bind();
			tileSelector2.draw((int) ClientTickHandler.getPartialTicks(), gridX - 3, gridY - 3, tileSize, tileSize);
			GlStateManager.popMatrix();
		});


		new ButtonMixin<>(topView, () -> {
		});

		topView.BUS.hook(GuiComponent.MouseDragEvent.class, (event) -> {
			if (!event.getComponent().getMouseOver()) return;
			if (!isShiftKeyDown()) {
				double x = event.getMousePos().getXi() / tileSize;
				double y = event.getMousePos().getYi() / tileSize;
				BlockPos block = pos.subtract(new Vec3i(width, 0, width)).add(x, 0, y);

				ParticleBuilder glitter = new ParticleBuilder(40);
				glitter.setRenderNormalLayer(new ResourceLocation(CCMain.MOD_ID, "particles/sparkle_blurred"));
				glitter.setAlphaFunction(new InterpFadeInOut(1f, 1f));
				glitter.setColor(Color.GREEN);
				glitter.setAlphaFunction(new InterpFadeInOut(1f, 1f));
				glitter.setScale(2);
				ParticleSpawner.spawn(glitter, mc.world, new StaticInterp<>(new Vec3d(block).addVector(0.5, 0.5, 0.5)), 1, 0, (aFloat, particleBuilder) -> {
				});

				if (selected != null) {
					ItemBlock itemBlock = (ItemBlock) selected.getStack().getValue(selected).getItem();
					PacketHandler.NETWORK.sendToServer(new PacketSendBlockToCrane(pos, new Pair<>(itemBlock.block.getDefaultState(), block)));
				}
			} else {
				if (from != null) {
					offset = from.sub(event.getMousePos());
				}
			}
		});

		topView.BUS.hook(GuiComponent.MouseDownEvent.class, (event) -> {
			if (!event.getComponent().getMouseOver()) return;
			if (!isShiftKeyDown()) {

				if (!event.getComponent().getMouseOver()) return;
				double x = event.getMousePos().getXi() / tileSize;
				double y = event.getMousePos().getYi() / tileSize;
				BlockPos block = pos.subtract(new Vec3i(width, 0, width)).add(x, 0, y);

				ParticleBuilder glitter = new ParticleBuilder(40);
				glitter.setRenderNormalLayer(new ResourceLocation(CCMain.MOD_ID, "particles/sparkle_blurred"));
				glitter.setAlphaFunction(new InterpFadeInOut(1f, 1f));
				glitter.setColor(Color.GREEN);
				glitter.setAlphaFunction(new InterpFadeInOut(1f, 1f));
				glitter.setScale(2);
				ParticleSpawner.spawn(glitter, mc.world, new StaticInterp<>(new Vec3d(block).addVector(0.5, 0.5, 0.5)), 1, 0, (aFloat, particleBuilder) -> {
				});

				if (selected != null) {
					ItemBlock itemBlock = (ItemBlock) selected.getStack().getValue(selected).getItem();
					PacketHandler.NETWORK.sendToServer(new PacketSendBlockToCrane(pos, new Pair<>(itemBlock.block.getDefaultState(), block)));
					if (!mc.player.isCreative())
						PacketHandler.NETWORK.sendToServer(new PacketReduceStackFromPlayer(mc.player.inventory.getSlotFor(selected.getStack().getValue(selected))));
				}
			} else {
				from = event.getMousePos();
			}
		});

		Deque<ItemStack> itemBlocks = new ArrayDeque<>();
		for (ItemStack stack : mc.player.inventory.mainInventory)
			if (stack.getItem() instanceof ItemBlock)
				itemBlocks.add(stack);

		final int size = itemBlocks.size();
		for (int i = 0; i < Math.ceil(size / 9.0); i++) {
			ComponentList inventory = new ComponentList(16 + (i * 36), 16);
			inventory.setChildScale(2);

			for (int j = 0; j < 9; j++) {
				if (itemBlocks.isEmpty()) break;
				ItemStack stack = itemBlocks.pop();
				ComponentStack compStack = new ComponentStack(0, 0);
				compStack.setMarginBottom(2);
				compStack.getStack().setValue(stack);

				final int finalI = i, finalJ = j;
				compStack.BUS.hook(GuiComponent.MouseClickEvent.class, (event) -> {
					if (!event.getComponent().getMouseOver()) return;
					selected = compStack;
					selectionRect.setVisible(true);
					selectionRect.setPos(new Vec2d(16 + finalI * 36, 16 + finalJ * 36));
					GlMixin.INSTANCE.transform(selectionRect).setValue(new Vec3d(0, 0, 100));
				});

				compStack.BUS.hook(GuiComponent.MouseOverEvent.class, (event) -> {
					if (!event.getComponent().getMouseOver()) return;
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
}
