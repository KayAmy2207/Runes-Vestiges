package fr.runevestiges.client;

import fr.runevestiges.content.RunicCurator;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.npc.WanderingTrader;

public final class RunicCuratorModel extends VillagerModel<WanderingTrader> {
	private static final String MODEL_RESOURCE =
		"/assets/runevestiges/models/entity/runic_curator_trader.bbmodel";
	public static final ModelLayerLocation MODEL_LAYER = new ModelLayerLocation(
		ResourceLocation.fromNamespaceAndPath("runevestiges", "runic_curator_trader"),
		"main"
	);

	private final VillagerModel<WanderingTrader> vanillaModel;
	private final ModelPart curatorRoot;
	private final ModelPart head;
	private final ModelPart rightArm;
	private final ModelPart leftArm;
	private final ModelPart rightLeg;
	private final ModelPart leftLeg;
	private final ModelPart cape;
	private boolean renderingCurator;

	public RunicCuratorModel(VillagerModel<WanderingTrader> vanillaModel, ModelPart curatorRoot) {
		super(vanillaModel.root());
		this.vanillaModel = vanillaModel;
		this.curatorRoot = curatorRoot;
		head = curatorRoot.getChild("head");
		rightArm = curatorRoot.getChild("right_arm");
		leftArm = curatorRoot.getChild("left_arm");
		rightLeg = curatorRoot.getChild("right_leg");
		leftLeg = curatorRoot.getChild("left_leg");
		cape = curatorRoot.getChild("cape");
	}

	public static boolean isCurator(WanderingTrader trader) {
		return RunicCurator.isCurator(trader);
	}

	public static LayerDefinition createLayer() {
		try (InputStream stream = RunicCuratorModel.class.getResourceAsStream(MODEL_RESOURCE)) {
			if (stream == null) {
				throw new IllegalStateException("Missing runic curator model resource: " + MODEL_RESOURCE);
			}
			JsonObject model = JsonParser.parseReader(
				new InputStreamReader(stream, StandardCharsets.UTF_8)
			).getAsJsonObject();
			JsonObject resolution = model.getAsJsonObject("resolution");
			int textureWidth = resolution.get("width").getAsInt();
			int textureHeight = resolution.get("height").getAsInt();
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
						throw new IllegalStateException(
							"Missing cube " + childId + " in runic curator model group " + groupName
						);
					}
					if (!cube.get("type").getAsString().equals("cube")) {
						throw new IllegalStateException(
							"Unsupported element type in runic curator model: " + cube.get("type")
						);
					}
					requireNoRotation(cube.getAsJsonArray("rotation"), cube.get("name").getAsString());
					if (!cube.get("box_uv").getAsBoolean()) {
						throw new IllegalStateException(
							"Unsupported UV layout in runic curator model cube " + cube.get("name")
						);
					}
					float[] from = vector(cube.getAsJsonArray("from"));
					float[] to = vector(cube.getAsJsonArray("to"));
					JsonArray uv = cube.getAsJsonArray("uv_offset");
					cubes = cubes.texOffs(uv.get(0).getAsInt(), uv.get(1).getAsInt()).addBox(
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
				root.addOrReplaceChild(
					groupName,
					cubes,
					PartPose.offset(origin[0], 24.0F - origin[1], origin[2])
				);
			}

			return LayerDefinition.create(mesh, textureWidth, textureHeight);
		} catch (IOException exception) {
			throw new IllegalStateException("Failed to load the runic curator model", exception);
		}
	}

	@Override
	public ModelPart root() {
		return renderingCurator ? curatorRoot : vanillaModel.root();
	}

	@Override
	public ModelPart getHead() {
		return renderingCurator ? head : vanillaModel.getHead();
	}

	@Override
	public void setupAnim(
		WanderingTrader trader,
		float limbSwing,
		float limbSwingAmount,
		float ageInTicks,
		float netHeadYaw,
		float headPitch
	) {
		renderingCurator = isCurator(trader);
		if (!renderingCurator) {
			vanillaModel.setupAnim(trader, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
			return;
		}

		curatorRoot.getAllParts().forEach(ModelPart::resetPose);
		head.xRot = headPitch * (Mth.PI / 180.0F);
		head.yRot = netHeadYaw * (Mth.PI / 180.0F);
		rightArm.xRot = Mth.cos(limbSwing * 0.6662F + Mth.PI) * 1.4F * limbSwingAmount;
		leftArm.xRot = Mth.cos(limbSwing * 0.6662F) * 1.4F * limbSwingAmount;
		rightLeg.xRot = Mth.cos(limbSwing * 0.6662F) * 1.2F * limbSwingAmount;
		leftLeg.xRot = Mth.cos(limbSwing * 0.6662F + Mth.PI) * 1.2F * limbSwingAmount;
		cape.xRot = 0.12F + Mth.cos(ageInTicks * 0.08F) * 0.035F + limbSwingAmount * 0.08F;
		cape.zRot = Mth.sin(limbSwing * 0.6662F) * 0.025F * limbSwingAmount;
	}

	@Override
	public void renderToBuffer(
		PoseStack poseStack,
		VertexConsumer vertexConsumer,
		int packedLight,
		int packedOverlay,
		int color
	) {
		if (renderingCurator) {
			curatorRoot.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
		} else {
			vanillaModel.renderToBuffer(poseStack, vertexConsumer, packedLight, packedOverlay, color);
		}
	}

	private static float[] vector(JsonArray values) {
		return new float[] {
			values.get(0).getAsFloat(),
			values.get(1).getAsFloat(),
			values.get(2).getAsFloat()
		};
	}

	private static void requireNoRotation(JsonArray rotation, String name) {
		if (rotation != null
			&& (rotation.get(0).getAsFloat() != 0.0F
				|| rotation.get(1).getAsFloat() != 0.0F
				|| rotation.get(2).getAsFloat() != 0.0F)) {
			throw new IllegalStateException(
				"Rotated geometry is not supported in runic curator model element " + name
			);
		}
	}
}
