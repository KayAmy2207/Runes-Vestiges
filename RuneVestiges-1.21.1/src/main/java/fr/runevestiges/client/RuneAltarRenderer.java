package fr.runevestiges.client;

import fr.runevestiges.content.ModContent;
import fr.runevestiges.content.RuneAltarBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.BookModel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.util.Mth;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

public final class RuneAltarRenderer implements BlockEntityRenderer<RuneAltarBlockEntity> {
	private static final ItemStack RUNIC_SHARD = new ItemStack(ModContent.RUNIC_SHARD);
	private static final ItemStack FLOATING_RUNIC_BOOK = createFloatingRunicBook();
	private static final ResourceLocation ENCHANTING_BOOK_TEXTURE =
		ResourceLocation.withDefaultNamespace("textures/entity/enchanting_table_book.png");
	private static final int ORB_COUNT = 22;
	private ExperienceOrb experienceOrb;
	private Level experienceOrbLevel;
	private BookModel openBook;

	public RuneAltarRenderer(BlockEntityRendererProvider.Context context) {
		openBook = new BookModel(context.bakeLayer(ModelLayers.BOOK));
	}

	@Override
	public void render(
		RuneAltarBlockEntity blockEntity,
		float partialTick,
		PoseStack poseStack,
		MultiBufferSource bufferSource,
		int packedLight,
		int packedOverlay
	) {
		Level level = blockEntity.getLevel();
		if (level == null) {
			return;
		}

		Block block = blockEntity.getBlockState().getBlock();
		float time = level.getGameTime() + partialTick;
		int light = LightTexture.FULL_BRIGHT;

		if (block == ModContent.RUNE_ALTAR) {
			renderEnchanter(blockEntity, time, poseStack, bufferSource, light);
		} else if (block == ModContent.COMBINING_ALTAR) {
			renderAnvil(time, poseStack, bufferSource, light);
		} else if (block == ModContent.DISENCHANTING_ALTAR) {
			renderDisenchanter(blockEntity, partialTick, time, poseStack, bufferSource, light);
		}
	}

	private void renderEnchanter(
		RuneAltarBlockEntity blockEntity,
		float time,
		PoseStack poseStack,
		MultiBufferSource bufferSource,
		int light
	) {
		float pulse = 1.0F + (float) Math.sin(time * 0.08F) * 0.035F;
		float bookHeight = 1.32F + (float) Math.sin(time * 0.08F) * 0.025F;
		poseStack.pushPose();
		poseStack.translate(0.5, bookHeight, 0.5);
		poseStack.mulPose(Axis.YP.rotation(time * 0.04F));
		poseStack.mulPose(Axis.ZP.rotationDegrees(80.0F));
		poseStack.scale(0.45F * pulse, 0.45F * pulse, 0.45F * pulse);
		renderItem(FLOATING_RUNIC_BOOK, poseStack, bufferSource, light);
		poseStack.popPose();

		for (int i = 0; i < ORB_COUNT; i++) {
			double vertical = 1.0 - 2.0 * (i + 0.5) / ORB_COUNT;
			double horizontalRadius = Math.sqrt(1.0 - vertical * vertical);
			float angle = time * 0.045F + i * 2.3999632F;
			double x = 0.5 + Math.cos(angle) * horizontalRadius * 0.45;
			double y = bookHeight + vertical * 0.3;
			double z = 0.5 + Math.sin(angle) * horizontalRadius * 0.45;
			renderExperienceOrb(x, y, z, time + i * 5.0F, 0.55F, poseStack, bufferSource, light);
		}
	}

	private static void renderAnvil(
		float time,
		PoseStack poseStack,
		MultiBufferSource bufferSource,
		int light
	) {
		for (int i = 0; i < 8; i++) {
			float age = (time - i * 3.5F) % 28.0F;
			if (age < 0.0F) {
				age += 28.0F;
			}
			if (age >= 16.0F) {
				continue;
			}

			float progress = age / 16.0F;
			float angle = i * 137.5F + time * 1.5F;
			double radians = Math.toRadians(angle);
			double startRadius = 0.08 + (i % 3) * 0.055;
			double x = 0.5 + Math.cos(radians) * (startRadius + progress * 0.24);
			double y = 0.96 + progress * 0.46 + Math.sin(progress * Math.PI) * 0.08;
			double z = 0.5 + Math.sin(radians) * (startRadius + progress * 0.24);
			renderShard(x, y, z, angle, 0.13F, poseStack, bufferSource, light);
		}
	}

	private void renderDisenchanter(
		RuneAltarBlockEntity blockEntity,
		float partialTick,
		float time,
		PoseStack poseStack,
		MultiBufferSource bufferSource,
		int light
	) {
		Level level = blockEntity.getLevel();
		Player player = level == null
			? null
			: level.getNearestPlayer(
				blockEntity.getBlockPos().getX() + 0.5,
				blockEntity.getBlockPos().getY() + 0.5,
				blockEntity.getBlockPos().getZ() + 0.5,
				3.0,
				false
			);
		float playerRotation = 0.0F;
		if (player != null) {
			double x = player.getX() - blockEntity.getBlockPos().getX() - 0.5;
			double z = player.getZ() - blockEntity.getBlockPos().getZ() - 0.5;
			playerRotation = (float) Mth.atan2(z, x);
		}
		blockEntity.tickBookAnimation(player != null, playerRotation);
		renderOpenBook(
			time,
			blockEntity.getBookOpen(partialTick),
			blockEntity.getBookRotation(partialTick),
			poseStack,
			bufferSource,
			light
		);
	}

	private void renderOpenBook(
		float time,
		float open,
		float rotation,
		PoseStack poseStack,
		MultiBufferSource bufferSource,
		int light
	) {
		poseStack.pushPose();
		poseStack.translate(0.5, 1.12F + Math.sin(time * 0.08F) * 0.035F, 0.5);
		poseStack.mulPose(Axis.YP.rotation(-rotation));
		poseStack.mulPose(Axis.ZP.rotationDegrees(67.5F));
		poseStack.scale(0.72F, 0.72F, 0.72F);
		openBook.setupAnim(time, 0.1F, 0.9F, open);
		openBook.renderToBuffer(
			poseStack,
			bufferSource.getBuffer(RenderType.entitySolid(ENCHANTING_BOOK_TEXTURE)),
			light,
			0,
			0xffffffff
		);
		openBook.renderToBuffer(
			poseStack,
			bufferSource.getBuffer(RenderType.entityGlint()),
			light,
			0,
			0xffffffff
		);
		poseStack.popPose();
	}

	private static ItemStack createFloatingRunicBook() {
		ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
		book.set(DataComponents.CUSTOM_MODEL_DATA, ModContent.FLOATING_RUNIC_BOOK_MODEL_DATA);
		book.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		return book;
	}

	private static void renderShard(
		double x,
		double y,
		double z,
		float angle,
		float scale,
		PoseStack poseStack,
		MultiBufferSource bufferSource,
		int light
	) {
		poseStack.pushPose();
		poseStack.translate(x, y, z);
		poseStack.mulPose(Axis.YP.rotationDegrees(-angle));
		poseStack.mulPose(Axis.XP.rotationDegrees(28.0F));
		poseStack.scale(scale, scale, scale);
		renderItem(RUNIC_SHARD, poseStack, bufferSource, light);
		poseStack.popPose();
	}

	private void renderExperienceOrb(
		double x,
		double y,
		double z,
		float time,
		float scale,
		PoseStack poseStack,
		MultiBufferSource bufferSource,
		int light
	) {
		Level level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		if (experienceOrb == null || experienceOrbLevel != level) {
			experienceOrb = new ExperienceOrb(level, 0.0, 0.0, 0.0, 3);
			experienceOrbLevel = level;
		}

		poseStack.pushPose();
		poseStack.translate(x, y, z);
		poseStack.mulPose(Axis.YP.rotationDegrees(time * 4.0F));
		poseStack.translate(0.0, Math.sin(time * 0.08F) * 0.025F, 0.0);
		poseStack.scale(scale, scale, scale);
		experienceOrb.setPos(x, y, z);
		Minecraft.getInstance().getEntityRenderDispatcher()
			.getRenderer(experienceOrb)
			.render(experienceOrb, 0.0F, 0.0F, poseStack, bufferSource, light);
		poseStack.popPose();
	}

	private static void renderItem(
		ItemStack item,
		PoseStack poseStack,
		MultiBufferSource bufferSource,
		int light
	) {
		Minecraft.getInstance().getItemRenderer().renderStatic(
			item,
			ItemDisplayContext.FIXED,
			light,
			0,
			poseStack,
			bufferSource,
			Minecraft.getInstance().level,
			0
		);
	}

}
