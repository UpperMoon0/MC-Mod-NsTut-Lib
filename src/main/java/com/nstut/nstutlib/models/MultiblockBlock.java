package com.nstut.nstutlib.models;

import lombok.Data;
import net.minecraft.world.level.block.Block;

import java.util.Map;

@Data
public class MultiblockBlock {

    private Block block;
    Map<String, String> states;

    public MultiblockBlock(Block block, Map<String, String> states) {
        this.block = block;
        this.states = states;
    }
}
