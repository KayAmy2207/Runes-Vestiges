package fr.runevestiges.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.npc.WanderingTrader;

public final class RunicCuratorCapeLayer
	extends RenderLayer<WanderingTrader, VillagerModel<WanderingTrader>> {
	private static final ResourceLocation TEXTURE =
		ResourceLocation.fromNamespaceAndPath("runevestiges", "textures/entity/curator_cape.png");
	public static final ModelLayerLocation MODEL_LAYER = new ModelLayerLocation(
		ResourceLocation.fromNamespaceAndPath("runevestiges", "runic_curator_cape"),
		"main"
	);

	private final ModelPart cape;

	public RunicCuratorCapeLayer(
		RenderLayerParent<WanderingTrader, VillagerModel<WanderingTrader>> renderer,
		ModelPart cape
	) {
		super(renderer);
		this.cape = cape;
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild(
			"cape",
			CubeListBuilder.create().texOffs(0, 0).addBox(-5.0F, 0.0F, 0.0F, 10.0F, 16.0F, 1.0F),
			PartPose.offset(0.0F, 0.0F, 5.7F)
		);
		return LayerDefinition.create(mesh, 64, 32);
	}

	@Override
	public void render(
		PoseStack poseStack,
		MultiBufferSource bufferSource,
		int packedLight,
		WanderingTrader trader,
		float limbSwing,
		float limbSwingAmount,
		float partialTick,
		float ageInTicks,
		float netHeadYaw,
		float headPitch
	) {
		if (!RunicCuratorModel.isCurator(trader)) {
			return;
		}

		cape.xRot = 0.12F + Mth.cos(ageInTicks * 0.08F) * 0.035F + limbSwingAmount * 0.08F;
		cape.zRot = Mth.sin(limbSwing * 0.6662F) * 0.025F * limbSwingAmount;
		poseStack.pushPose();
		cape.render(
			poseStack,
			bufferSource.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)),
			packedLight,
			LivingEntityRenderer.getOverlayCoords(trader, 0.0F)
		);
		poseStack.popPose();
	}
}
