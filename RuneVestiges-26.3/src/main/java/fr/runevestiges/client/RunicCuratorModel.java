package fr.runevestiges.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

public final class RunicCuratorModel extends EntityModel<VillagerRenderState> {
   private static final String MODEL_RESOURCE = "/assets/runevestiges/models/entity/runic_curator_trader.bbmodel";
   public static final ModelLayerLocation MODEL_LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath("runevestiges", "runic_curator_trader"), "main");
   private final ModelPart head;
   private final ModelPart rightArm;
   private final ModelPart leftArm;
   private final ModelPart rightLeg;
   private final ModelPart leftLeg;
   private final ModelPart cape;

   public RunicCuratorModel(ModelPart root) {
      super(root);
      this.head = root.getChild("head");
      this.rightArm = root.getChild("right_arm");
      this.leftArm = root.getChild("left_arm");
      this.rightLeg = root.getChild("right_leg");
      this.leftLeg = root.getChild("left_leg");
      this.cape = root.getChild("cape");
   }

   public static LayerDefinition createLayer() {
      try {
         LayerDefinition var23;
         try (InputStream stream = RunicCuratorModel.class.getResourceAsStream("/assets/runevestiges/models/entity/runic_curator_trader.bbmodel")) {
            if (stream == null) {
               throw new IllegalStateException("Missing runic curator model resource: /assets/runevestiges/models/entity/runic_curator_trader.bbmodel");
            }

            JsonObject model = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject resolution = model.getAsJsonObject("resolution");
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            Map<String, JsonObject> elementsById = new HashMap<>();

            for (JsonElement element : model.getAsJsonArray("elements")) {
               JsonObject cube = element.getAsJsonObject();
               elementsById.put(cube.get("uuid").getAsString(), cube);
            }

            for (JsonElement groupElement : model.getAsJsonArray("outliner")) {
               JsonObject group = groupElement.getAsJsonObject();
               String groupName = group.get("name").getAsString();
               float[] origin = vector(group.getAsJsonArray("origin"));
               requireNoRotation(group.getAsJsonArray("rotation"), groupName);
               CubeListBuilder cubes = CubeListBuilder.create();

               for (JsonElement child : group.getAsJsonArray("children")) {
                  String childId = child.getAsString();
                  JsonObject cube = elementsById.get(childId);
                  if (cube == null) {
                     throw new IllegalStateException("Missing cube " + childId + " in runic curator model group " + groupName);
                  }

                  if (!cube.get("type").getAsString().equals("cube")) {
                     throw new IllegalStateException("Unsupported element type in runic curator model: " + cube.get("type"));
                  }

                  requireNoRotation(cube.getAsJsonArray("rotation"), cube.get("name").getAsString());
                  if (!cube.get("box_uv").getAsBoolean()) {
                     throw new IllegalStateException("Unsupported UV layout in runic curator model cube " + cube.get("name"));
                  }

                  float[] from = vector(cube.getAsJsonArray("from"));
                  float[] to = vector(cube.getAsJsonArray("to"));
                  JsonArray uv = cube.getAsJsonArray("uv_offset");
                  cubes = cubes.texOffs(uv.get(0).getAsInt(), uv.get(1).getAsInt())
                     .addBox(
                        cube.get("name").getAsString(),
                        from[0] - origin[0],
                        origin[1] - to[1],
                        from[2] - origin[2],
                        to[0] - from[0],
                        to[1] - from[1],
                        to[2] - from[2],
                        new CubeDeformation(cube.get("inflate").getAsFloat())
                     );
               }

               root.addOrReplaceChild(groupName, cubes, PartPose.offset(origin[0], 24.0F - origin[1], origin[2]));
            }

            var23 = LayerDefinition.create(mesh, resolution.get("width").getAsInt(), resolution.get("height").getAsInt());
         }

         return var23;
      } catch (IOException var21) {
         throw new IllegalStateException("Failed to load the runic curator model", var21);
      }
   }

   public void setupAnim(VillagerRenderState state) {
      this.root().getAllParts().forEach(ModelPart::resetPose);
      this.head.xRot = state.xRot * (float) (Math.PI / 180.0);
      this.head.yRot = state.yRot * (float) (Math.PI / 180.0);
      this.rightArm.xRot = Mth.cos(state.walkAnimationPos * 0.6662F + (float) Math.PI) * 1.4F * state.walkAnimationSpeed;
      this.leftArm.xRot = Mth.cos(state.walkAnimationPos * 0.6662F) * 1.4F * state.walkAnimationSpeed;
      this.rightLeg.xRot = Mth.cos(state.walkAnimationPos * 0.6662F) * 1.2F * state.walkAnimationSpeed;
      this.leftLeg.xRot = Mth.cos(state.walkAnimationPos * 0.6662F + (float) Math.PI) * 1.2F * state.walkAnimationSpeed;
      this.cape.xRot = 0.12F + Mth.cos(state.ageInTicks * 0.08F) * 0.035F + state.walkAnimationSpeed * 0.08F;
      this.cape.zRot = Mth.sin(state.walkAnimationPos * 0.6662F) * 0.025F * state.walkAnimationSpeed;
   }

   private static float[] vector(JsonArray values) {
      return new float[]{values.get(0).getAsFloat(), values.get(1).getAsFloat(), values.get(2).getAsFloat()};
   }

   private static void requireNoRotation(JsonArray rotation, String name) {
      if (rotation != null && (rotation.get(0).getAsFloat() != 0.0F || rotation.get(1).getAsFloat() != 0.0F || rotation.get(2).getAsFloat() != 0.0F)) {
         throw new IllegalStateException("Rotated geometry is not supported in runic curator model element " + name);
      }
   }
}
