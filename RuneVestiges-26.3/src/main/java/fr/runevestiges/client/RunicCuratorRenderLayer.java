package fr.runevestiges.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

public final class RunicCuratorRenderLayer extends RenderLayer<VillagerRenderState, RunicCuratorModel> {
   private static final Identifier CURATOR_TEXTURE = Identifier.fromNamespaceAndPath("runevestiges", "textures/entity/curator.png");
   private static final Identifier CAPE_TEXTURE = Identifier.fromNamespaceAndPath("runevestiges", "textures/entity/curator_cape.png");
   private static final ModelLayerLocation CAPE_MODEL_LAYER = new ModelLayerLocation(
      Identifier.fromNamespaceAndPath("runevestiges", "runic_curator_cape"), "main"
   );
   private final RunicCuratorModel curatorModel;
   private final RunicCuratorRenderLayer.CapeModel capeModel;

   public RunicCuratorRenderLayer(ModelPart curatorRoot, ModelPart capeRoot) {
      this(new RunicCuratorModel(curatorRoot), new RunicCuratorRenderLayer.CapeModel(capeRoot));
   }

   private RunicCuratorRenderLayer(RunicCuratorModel curatorModel, RunicCuratorRenderLayer.CapeModel capeModel) {
      super(parentOf(curatorModel));
      this.curatorModel = curatorModel;
      this.capeModel = capeModel;
   }

   public void submit(PoseStack poseStack, SubmitNodeCollector collector, int packedLight, VillagerRenderState state, float limbSwing, float limbSwingAmount) {
      if (((RunicCuratorRenderState)state).runevestiges$isCurator()) {
         this.curatorModel.setupAnim(state);
         collector.submitModel(this.curatorModel, state, poseStack, CURATOR_TEXTURE, packedLight, LivingEntityRenderer.getOverlayCoords(state, 0.0F), 0);
         this.capeModel.cape.xRot = 0.12F + Mth.cos(state.ageInTicks * 0.08F) * 0.035F + state.walkAnimationSpeed * 0.08F;
         this.capeModel.cape.zRot = Mth.sin(state.walkAnimationPos * 0.6662F) * 0.025F * state.walkAnimationSpeed;
         collector.submitModel(this.capeModel, state, poseStack, CAPE_TEXTURE, packedLight, LivingEntityRenderer.getOverlayCoords(state, 0.0F), 0);
      }
   }

   public static LayerDefinition createCapeLayer() {
      MeshDefinition mesh = new MeshDefinition();
      PartDefinition root = mesh.getRoot();
      root.addOrReplaceChild("cape", CubeListBuilder.create().texOffs(0, 0).addBox(-5.0F, 0.0F, 0.0F, 10.0F, 16.0F, 1.0F), PartPose.offset(0.0F, 0.0F, 5.7F));
      return LayerDefinition.create(mesh, 64, 32);
   }

   public static ModelLayerLocation capeModelLayer() {
      return CAPE_MODEL_LAYER;
   }

   private static RenderLayerParent<VillagerRenderState, RunicCuratorModel> parentOf(RunicCuratorModel model) {
      return () -> model;
   }

   private static final class CapeModel extends EntityModel<VillagerRenderState> {
      private final ModelPart cape;

      private CapeModel(ModelPart root) {
         super(root);
         this.cape = root.getChild("cape");
      }

      public void setupAnim(VillagerRenderState state) {
         this.root().resetPose();
      }
   }
}
