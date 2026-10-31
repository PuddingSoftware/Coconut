package net.minestom.server.entity.ai.controls;

import net.minestom.server.entity.ai.controls.types.JumpControl;
import net.minestom.server.entity.ai.controls.types.LookControl;
import net.minestom.server.entity.ai.controls.types.MoveControl;
import org.jetbrains.annotations.NotNull;

public final class MobController {

    private final MoveControl moveControl;
    private final JumpControl jumpControl;
    private final LookControl lookControl;

    public MobController(@NotNull MoveControl moveControl,
                         @NotNull JumpControl jumpControl,
                         @NotNull LookControl lookControl) {
        this.moveControl = moveControl;
        this.jumpControl = jumpControl;
        this.lookControl = lookControl;
    }
}