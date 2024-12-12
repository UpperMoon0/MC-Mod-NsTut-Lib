package com.nstut.nstutlib.views;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.nstut.nstutlib.NsTutLib;
import com.nstut.nstutlib.blocks.MachineBlock;
import com.nstut.nstutlib.blocks.MachineBlockEntity;
import com.nstut.nstutlib.models.MultiblockBlock;
import com.nstut.nstutlib.models.MultiblockPattern;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class SmartHammerScreen extends Screen {

    private static final Logger LOGGER = Logger.getLogger(SmartHammerScreen.class.getName());

    private static final ResourceLocation TEXTURE = new ResourceLocation(NsTutLib.MOD_ID, "textures/gui/smart_hammer.png");

    private static final String SCRIPT_OUTPUT_PATH = FMLPaths.GAMEDIR.get().resolve("nstut_script_output").toString();

    private final Level level;

    private EditBox firstCornerX;
    private EditBox firstCornerY;
    private EditBox firstCornerZ;
    private EditBox secondCornerX;
    private EditBox secondCornerY;
    private EditBox secondCornerZ;

    public SmartHammerScreen(Level level) {
        super(Component.literal("Debug Structure Analyzer"));
        this.level = level;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // First Corner
        this.firstCornerX = new EditBox(this.font, centerX - 70, centerY - 40, 40, 15, Component.literal("X"));
        this.firstCornerY = new EditBox(this.font, centerX - 20, centerY - 40, 40, 15, Component.literal("Y"));
        this.firstCornerZ = new EditBox(this.font, centerX + 30, centerY - 40, 40, 15, Component.literal("Z"));
        this.addRenderableWidget(this.firstCornerX);
        this.addRenderableWidget(this.firstCornerY);
        this.addRenderableWidget(this.firstCornerZ);

        // Second Corner
        this.secondCornerX = new EditBox(this.font, centerX - 70, centerY + 10, 40, 15, Component.literal("X"));
        this.secondCornerY = new EditBox(this.font, centerX - 20, centerY + 10, 40, 15, Component.literal("Y"));
        this.secondCornerZ = new EditBox(this.font, centerX + 30, centerY + 10, 40, 15, Component.literal("Z"));
        this.addRenderableWidget(this.secondCornerX);
        this.addRenderableWidget(this.secondCornerY);
        this.addRenderableWidget(this.secondCornerZ);

        // Save Button
        Button saveButton = Button.builder(Component.literal("Save"), this::onSave)
                .pos(centerX - 50, centerY + 50)
                .size(100, 20)
                .build();
        this.addRenderableWidget(saveButton);

        // Labels
        StringWidget firstCornerLabel = new StringWidget(centerX - 89, centerY - 60, 100, 20, Component.literal("First Corner"), this.font);
        StringWidget secondCornerLabel = new StringWidget(centerX - 84, centerY - 10, 100, 20, Component.literal("Second Corner"), this.font);
        this.addRenderableWidget(firstCornerLabel);
        this.addRenderableWidget(secondCornerLabel);
    }

    private void onSave(Button button) {
        int x1 = Integer.parseInt(this.firstCornerX.getValue());
        int y1 = Integer.parseInt(this.firstCornerY.getValue());
        int z1 = Integer.parseInt(this.firstCornerZ.getValue());
        int x2 = Integer.parseInt(this.secondCornerX.getValue());
        int y2 = Integer.parseInt(this.secondCornerY.getValue());
        int z2 = Integer.parseInt(this.secondCornerZ.getValue());

        int minX = Math.min(x1, x2);
        int minY = Math.min(y1, y2);
        int minZ = Math.min(z1, z2);
        int maxX = Math.max(x1, x2);
        int maxY = Math.max(y1, y2);
        int maxZ = Math.max(z1, z2);

        // Initialize pattern and mapping
        List<List<String>> pattern = new ArrayList<>();
        Map<String, MultiblockBlock> mapping = new HashMap<>();
        char currentChar = 'b'; // Reserved "a" for air

        // Capture the controller facing (default to NORTH)
        Direction controllerFacing = Direction.NORTH;

        // Calculate the rotation offset to align the controller to SOUTH
        int rotationOffset = getRotationOffset(controllerFacing);

        // Iterate over the area to capture block data
        for (int y = maxY; y >= minY; y--) {
            List<String> layer = new ArrayList<>();
            for (int z = minZ; z <= maxZ; z++) { // Iterate in the correct order
                StringBuilder row = new StringBuilder();
                for (int x = maxX; x >= minX; x--) { // Reverse the order here to fix the rotation
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    String blockName = Objects.requireNonNull(ForgeRegistries.BLOCKS.getKey(state.getBlock())).toString();

                    // Capture block states
                    Map<String, String> stateMap = state.getProperties().stream()
                            .collect(Collectors.toMap(
                                    Property::getName,
                                    property -> {
                                        if (property instanceof DirectionProperty directionProperty &&
                                                directionProperty.getName().equals("facing") &&
                                                directionProperty.getPossibleValues().contains(Direction.NORTH)) {
                                            // Adjust the facing property based on the rotation
                                            Direction originalFacing = state.getValue(directionProperty);
                                            Direction newFacing = rotateDirection(originalFacing, rotationOffset);
                                            return newFacing.getName();
                                        } else {
                                            // Preserve all other properties as-is
                                            return state.getValue(property).toString();
                                        }
                                    }
                            ));

                    if (blockName.equals("minecraft:air")) {
                        row.append(" ");
                    } else {
                        MultiblockBlock multiblockBlock = new MultiblockBlock(state.getBlock(), stateMap);
                        String symbol = mapping.entrySet().stream()
                                .filter(entry -> entry.getValue().equals(multiblockBlock))
                                .map(Map.Entry::getKey)
                                .findFirst()
                                .orElse(null);

                        if (symbol == null) {
                            symbol = String.valueOf(currentChar);
                            mapping.put(symbol, multiblockBlock);
                            currentChar++;
                        }

                        row.append(symbol);
                    }
                }
                layer.add(row.toString()); // Add the reversed row to the layer
            }
            pattern.add(0, layer); // Add the layer in reverse order to fix Y-axis flipping
        }

        // Create a MultiblockPattern object from the collected pattern
        MultiblockBlock[][][] blockArray = new MultiblockBlock[pattern.size()][][];
        for (int y = 0; y < pattern.size(); y++) {
            List<String> layer = pattern.get(y);
            MultiblockBlock[][] layerArray = new MultiblockBlock[layer.size()][];
            for (int z = 0; z < layer.size(); z++) {
                String row = layer.get(z);
                MultiblockBlock[] rowArray = new MultiblockBlock[row.length()];
                for (int x = 0; x < row.length(); x++) {
                    String symbol = String.valueOf(row.charAt(x));
                    if (" ".equals(symbol)) {
                        rowArray[x] = null;
                    } else {
                        rowArray[x] = mapping.get(symbol);
                    }
                }
                layerArray[z] = rowArray;
            }
            blockArray[y] = layerArray;
        }

        // Rotate the pattern to face SOUTH based on the controller's initial facing
        MultiblockPattern multiblockPattern = new MultiblockPattern(blockArray);
        multiblockPattern.rotate(rotationOffset);

        // Write to files using the new MultiblockPattern object
        writeJson(multiblockPattern);
        writeTxt(multiblockPattern);
    }

    private int getRotationOffset(Direction controllerFacing) {
        return switch (controllerFacing) {
            case SOUTH -> 0; // No rotation needed, already aligned to SOUTH
            case WEST -> 1;  // 90 degrees clockwise to SOUTH
            case NORTH -> 2; // 180 degrees to SOUTH
            case EAST -> 3;  // 270 degrees clockwise to SOUTH
            default -> 0;    // Default to no rotation (shouldn't happen)
        };
    }

    private Direction rotateDirection(Direction original, int steps) {
        if (!original.getAxis().isHorizontal()) {
            return original; // Only rotate horizontal directions
        }

        Direction[] horizontalDirections = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        int index = (original.get2DDataValue() + steps) % horizontalDirections.length;
        return horizontalDirections[index];
    }

    private void writeJson(MultiblockPattern multiblockPattern) {
        // Extract pattern and mapping from the MultiblockPattern instance
        MultiblockBlock[][][] pattern = multiblockPattern.getPattern();

        // Convert the pattern to a list of string layers for JSON format
        List<List<String>> jsonPattern = new ArrayList<>();
        Map<String, MultiblockBlock> mapping = new LinkedHashMap<>();
        char currentChar = 'b';

        for (int y = pattern.length - 1; y >= 0; y--) {
            List<String> layer = new ArrayList<>();
            for (int z = 0; z < pattern[y].length; z++) {
                StringBuilder row = new StringBuilder();
                for (int x = 0; x < pattern[y][z].length; x++) {
                    MultiblockBlock block = pattern[y][z][x];
                    if (block == null) {
                        row.append(" ");
                    } else {
                        String symbol = mapping.entrySet().stream()
                                .filter(entry -> entry.getValue().equals(block))
                                .map(Map.Entry::getKey)
                                .findFirst()
                                .orElse(null);

                        if (symbol == null) {
                            symbol = String.valueOf(currentChar++);
                            mapping.put(symbol, block);
                        }

                        row.append(symbol);
                    }
                }
                layer.add(row.toString());
            }
            jsonPattern.add(layer);
        }

        // Create the JSON structure
        Map<String, Object> jsonData = new HashMap<>();
        jsonData.put("type", "patchouli:multiblock");

        Map<String, Object> multiblockData = new HashMap<>();
        multiblockData.put("pattern", jsonPattern);
        multiblockData.put("mapping", mapping.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> {
                    Map<String, Object> blockData = new HashMap<>();
                    blockData.put("block", Objects.requireNonNull(ForgeRegistries.BLOCKS.getKey(entry.getValue().getBlock())).toString());
                    blockData.put("states", entry.getValue().getStates());
                    return blockData;
                }
        )));
        multiblockData.put("symmetrical", true);
        jsonData.put("multiblock", multiblockData);

        // Serialize to JSON and save to file
        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        try (FileWriter writer = new FileWriter(SCRIPT_OUTPUT_PATH + "\\structure_patchouli.json")) {
            gson.toJson(jsonData, writer);
        } catch (IOException e) {
            LOGGER.severe("Failed to write JSON file: " + e.getMessage());
        }
    }

    private void writeTxt(MultiblockPattern multiblockPattern) {
        MultiblockBlock[][][] pattern = multiblockPattern.getPattern();
        Map<String, String> filteredMapping = new LinkedHashMap<>();
        Map<String, MultiblockBlock> mapping = new LinkedHashMap<>();
        char currentChar = 'b';

        // Populate the mapping
        for (MultiblockBlock[][] layer : pattern) {
            for (MultiblockBlock[] row : layer) {
                for (MultiblockBlock block : row) {
                    if (block != null && !mapping.containsValue(block)) {
                        mapping.put(String.valueOf(currentChar++), block);
                    }
                }
            }
        }

        // Filter and map unique blocks to variable names
        for (Map.Entry<String, MultiblockBlock> entry : mapping.entrySet()) {
            MultiblockBlock block = entry.getValue();
            String blockName = Objects.requireNonNull(ForgeRegistries.BLOCKS.getKey(block.getBlock())).toString();
            if (!filteredMapping.containsValue(blockName)) {
                filteredMapping.put(entry.getKey(), blockName);
            }
        }

        // Start writing the multiblock pattern
        try (FileWriter writer = new FileWriter(SCRIPT_OUTPUT_PATH + "\\structure_pattern.txt")) {
            writer.write("@Override\n");
            writer.write("public MultiblockPattern getMultiblockPattern() {\n");

            // Write block declarations
            writer.write("    MultiblockBlock ");
            List<String> declarations = new ArrayList<>();
            for (Map.Entry<String, MultiblockBlock> entry : mapping.entrySet()) {
                MultiblockBlock block = entry.getValue();
                String blockName = ("Blocks." + block.getBlock().toString())
                        .replace("Block{minecraft:", "")
                        .replace("}", "")
                        .toUpperCase()
                        .replace(":", "_");
                String attributes = block.getStates().entrySet().stream()
                        .map(e -> String.format("\"%s\", \"%s\"", e.getKey(), e.getValue()))
                        .collect(Collectors.joining(", "));
                String blockDecl = String.format("%s = new MultiblockBlock(%s, Map.of(%s))",
                        entry.getKey(), blockName, attributes);
                declarations.add(blockDecl);
            }
            writer.write(String.join(",\n    ", declarations) + ";\n\n");

            // Write the pattern array
            writer.write("    MultiblockBlock[][][] blockArray = new MultiblockBlock[][][] {\n");
            for (int y = pattern.length - 1; y >= 0; y--) {
                writer.write("        {\n");
                for (MultiblockBlock[] row : pattern[y]) {
                    String formattedRow = Arrays.stream(row)
                            .map(block -> block == null ? "null" : mapping.entrySet().stream()
                                    .filter(entry -> entry.getValue().equals(block))
                                    .map(Map.Entry::getKey)
                                    .findFirst()
                                    .orElse("null"))
                            .collect(Collectors.joining(", "));
                    writer.write("            {" + formattedRow + "},\n");
                }
                writer.write("        },\n");
            }
            writer.write("    };\n\n");

            // Return the multiblock pattern
            writer.write("    return new MultiblockPattern(blockArray, false);\n");
            writer.write("}\n");
        } catch (IOException e) {
            LOGGER.severe("Failed to write TXT file: " + e.getMessage());
        }
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics);
        RenderSystem.setShaderTexture(0, TEXTURE);
        int screenHeight = 166;
        int screenWidth = 176;
        graphics.blit(TEXTURE, (this.width - screenWidth) / 2, (this.height - screenHeight) / 2, 0, 0, screenWidth, screenHeight);
        super.render(graphics, mouseX, mouseY, partialTicks);
        this.firstCornerX.render(graphics, mouseX, mouseY, partialTicks);
        this.firstCornerY.render(graphics, mouseX, mouseY, partialTicks);
        this.firstCornerZ.render(graphics, mouseX, mouseY, partialTicks);
        this.secondCornerX.render(graphics, mouseX, mouseY, partialTicks);
        this.secondCornerY.render(graphics, mouseX, mouseY, partialTicks);
        this.secondCornerZ.render(graphics, mouseX, mouseY, partialTicks);
    }
}
