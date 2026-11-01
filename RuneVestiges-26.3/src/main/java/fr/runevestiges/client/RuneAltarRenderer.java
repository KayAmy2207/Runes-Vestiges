package fr.runevestiges.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.mojang.math.Axis;
import fr.runevestiges.content.ModContent;
import fr.runevestiges.content.RuneAltarBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.book.BookModel;
import net.minecraft.client.model.object.book.BookModel.State;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.ExperienceOrbRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.ExperienceOrbRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

public final class RuneAltarRenderer implements BlockEntityRenderer<RuneAltarBlockEntity, RuneAltarRenderer.RenderState> {
   private static final int FULL_BRIGHT = 15728880;
   private static final int SHARD_COUNT = 8;
   private static final int EXPERIENCE_ORB_COUNT = 22;
   private static final Identifier ENCHANTING_BOOK_TEXTURE = Identifier.withDefaultNamespace("textures/entity/enchantment/enchanting_table_book.png");
   private static final Identifier RUNIC_SHARD_TEXTURE = Identifier.fromNamespaceAndPath("runevestiges", "textures/item/runic_shard.png");
   private static final Identifier FLOATING_RUNIC_BOOK_TEXTURE = Identifier.fromNamespaceAndPath("runevestiges", "textures/item/books/floating_runic_book.png");
   private final BookModel enchantingBookModel;
   private ExperienceOrb experienceOrb;
   private ExperienceOrbRenderer experienceOrbRenderer;

   public RuneAltarRenderer(Context context) {
      this.enchantingBookModel = new BookModel(context.entityModelSet().bakeLayer(ModelLayers.BOOK));
   }

   public RuneAltarRenderer.RenderState createRenderState() {
      return new RuneAltarRenderer.RenderState();
   }

   public void extractRenderState(
      RuneAltarBlockEntity blockEntity, RuneAltarRenderer.RenderState state, float partialTick, Vec3 cameraPosition, CrumblingOverlay crumblingOverlay
   ) {
      Level level = blockEntity.getLevel();
      if (level == null) {
         state.visible = false;
      } else {
         Block block = blockEntity.getBlockState().getBlock();
         state.enchanter = block == ModContent.RUNE_ALTAR;
         state.combining = block == ModContent.COMBINING_ALTAR;
         state.disenchanting = block == ModContent.DISENCHANTING_ALTAR;
         state.visible = state.enchanter || state.combining || state.disenchanting;
         state.time = (float)level.getGameTime() + partialTick;
         if (state.enchanter) {
            state.experienceOrbs = this.extractExperienceOrbs(level, blockEntity, partialTick, state.time);
         } else if (state.disenchanting) {
            Player player = level.getNearestPlayer(
               blockEntity.getBlockPos().getX() + 0.5, blockEntity.getBlockPos().getY() + 0.5, blockEntity.getBlockPos().getZ() + 0.5, 3.0, false
            );
            float playerRotation = 0.0F;
            if (player != null) {
               double x = player.getX() - blockEntity.getBlockPos().getX() - 0.5;
               double z = player.getZ() - blockEntity.getBlockPos().getZ() - 0.5;
               playerRotation = (float)Math.atan2(z, x);
            }

            blockEntity.tickBookAnimation(player != null, playerRotation);
            state.bookRotation = blockEntity.getBookRotation(partialTick);
            state.bookModelState = State.forAnimation(state.time, 0.1F, 0.9F, blockEntity.getBookOpen(partialTick));
         }
      }
   }

   public void submit(RuneAltarRenderer.RenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState cameraRenderState) {
      if (state.visible) {
         if (state.enchanter) {
            float pulse = 1.0F + (float)Math.sin(state.time * 0.08F) * 0.035F;
            this.submitExperienceOrbs(state, poseStack, collector, cameraRenderState);
            poseStack.pushPose();
            poseStack.translate(0.5, 1.32F + Math.sin(state.time * 0.08F) * 0.025F, 0.5);
            poseStack.rotate(Axis.YP, state.time * 0.04F);
            poseStack.rotateDegrees(Axis.ZP, 80.0F);
            poseStack.scale(0.45F * pulse, 0.45F * pulse, 0.45F * pulse);
            submitTexturedItem(poseStack, collector, FLOATING_RUNIC_BOOK_TEXTURE, 15728880);
            poseStack.popPose();
         } else if (state.combining) {
            for (int index = 0; index < 8; index++) {
               float age = (state.time - index * 3.5F) % 28.0F;
               if (age < 0.0F) {
                  age += 28.0F;
               }

               if (!(age >= 16.0F)) {
                  float progress = age / 16.0F;
                  float angle = index * 137.5F + state.time * 1.5F;
                  double radians = Math.toRadians(angle);
                  double radius = 0.08 + index % 3 * 0.055 + progress * 0.24;
                  poseStack.pushPose();
                  poseStack.translate(
                     0.5 + Math.cos(radians) * radius, 0.96 + progress * 0.46 + Math.sin(progress * Math.PI) * 0.08, 0.5 + Math.sin(radians) * radius
                  );
                  poseStack.rotateDegrees(Axis.YP, -angle);
                  poseStack.rotateDegrees(Axis.XP, 28.0F);
                  poseStack.scale(0.13F, 0.13F, 0.13F);
                  submitTexturedItem(poseStack, collector, RUNIC_SHARD_TEXTURE, 15728880);
                  poseStack.popPose();
               }
            }
         } else {
            if (state.disenchanting) {
               poseStack.pushPose();
               poseStack.translate(0.5, 1.12F + Math.sin(state.time * 0.08F) * 0.035F, 0.5);
               poseStack.rotate(Axis.YP, -state.bookRotation);
               poseStack.rotateDegrees(Axis.ZP, 67.5F);
               poseStack.scale(0.72F, 0.72F, 0.72F);
               collector.submitModel(
                  this.enchantingBookModel, state.bookModelState, poseStack, RenderTypes.entitySolid(ENCHANTING_BOOK_TEXTURE), 15728880, 0, 0
               );
               poseStack.popPose();
            }
         }
      }
   }

   private static void submitTexturedItem(PoseStack poseStack, SubmitNodeCollector collector, Identifier texture, int light) {
      collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(texture), (pose, vertices) -> submitTexturedFace(vertices, pose, light));
   }

   private static void submitTexturedFace(VertexConsumer vertices, Pose pose, int light) {
      submitTexturedVertex(vertices, pose, light, -0.5F, -0.5F, 0.0F, 0.0F, 1.0F);
      submitTexturedVertex(vertices, pose, light, 0.5F, -0.5F, 0.0F, 1.0F, 1.0F);
      submitTexturedVertex(vertices, pose, light, 0.5F, 0.5F, 0.0F, 1.0F, 0.0F);
      submitTexturedVertex(vertices, pose, light, -0.5F, 0.5F, 0.0F, 0.0F, 0.0F);
   }

   private static void submitTexturedVertex(VertexConsumer vertices, Pose pose, int light, float x, float y, float z, float u, float v) {
      vertices.addVertex(pose, x, y, z)
         .setColor(255, 255, 255, 255)
         .setUv(u, v)
         .setOverlay(OverlayTexture.NO_OVERLAY)
         .setLight(light)
         .setNormal(pose, 0.0F, 0.0F, 1.0F);
   }

   private ExperienceOrbRenderState[] extractExperienceOrbs(Level level, RuneAltarBlockEntity blockEntity, float partialTick, float time) {
      if (this.experienceOrb == null || this.experienceOrb.level() != level) {
         this.experienceOrb = new ExperienceOrb(
            level, blockEntity.getBlockPos().getX() + 0.5, blockEntity.getBlockPos().getY() + 1.0, blockEntity.getBlockPos().getZ() + 0.5, 3
         );
         this.experienceOrbRenderer = (ExperienceOrbRenderer)Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(this.experienceOrb);
      }

      this.experienceOrb.setPos(blockEntity.getBlockPos().getX() + 0.5, blockEntity.getBlockPos().getY() + 1.0, blockEntity.getBlockPos().getZ() + 0.5);
      EntityRenderState renderState = Minecraft.getInstance().getEntityRenderDispatcher().extractEntity(this.experienceOrb, partialTick);
      ExperienceOrbRenderState baseState = (ExperienceOrbRenderState)renderState;
      ExperienceOrbRenderState[] orbStates = new ExperienceOrbRenderState[22];

      for (int index = 0; index < orbStates.length; index++) {
         ExperienceOrbRenderState orbState = new ExperienceOrbRenderState();
         orbState.entityType = baseState.entityType;
         orbState.x = baseState.x;
         orbState.y = baseState.y;
         orbState.z = baseState.z;
         orbState.ageInTicks = time + index * 5.0F;
         orbState.distanceToCameraSq = baseState.distanceToCameraSq;
         orbState.shadowRadius = baseState.shadowRadius;
         orbState.shadowPieces.addAll(baseState.shadowPieces);
         orbState.outlineColor = 0;
         orbState.icon = baseState.icon;
         orbState.lightCoords = 15728880;
         orbStates[index] = orbState;
      }

      return orbStates;
   }

   private void submitExperienceOrbs(
      RuneAltarRenderer.RenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState cameraRenderState
   ) {
      if (state.experienceOrbs != null && this.experienceOrbRenderer != null) {
         for (int index = 0; index < 22; index++) {
            double horizontal = (index + 0.5) / 22.0;
            double y = 1.0 - 2.0 * horizontal;
            double ring = Math.sqrt(1.0 - y * y);
            double angle = index * 2.3999632 + state.time * 0.035;
            poseStack.pushPose();
            poseStack.translate(
               0.5 + Math.cos(angle) * ring * 0.46, 1.32 + y * 0.3 + Math.sin(state.time * 0.08F + index * 5.0F) * 0.025F, 0.5 + Math.sin(angle) * ring * 0.46
            );
            poseStack.rotateDegrees(Axis.YP, state.time * 4.0F + index * 5.0F);
            poseStack.scale(0.65F, 0.65F, 0.65F);
            this.experienceOrbRenderer.submit(state.experienceOrbs[index], poseStack, collector, cameraRenderState);
            poseStack.popPose();
         }
      }
   }

   public static final class RenderState extends BlockEntityRenderState {
      private boolean visible;
      private boolean enchanter;
      private boolean combining;
      private boolean disenchanting;
      private float time;
      private float bookRotation;
      private State bookModelState = new State(0.0F, 0.1F, 0.9F);
      private ExperienceOrbRenderState[] experienceOrbs;
   }
}
