package com.terminalfoam.envenom.mixin;

import com.terminalfoam.envenom.EnvenomTestFunctions;
import java.util.function.Consumer;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.BuiltinTestFunctions;
import net.minecraft.gametest.framework.GameTestHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 内置注册表冻结前把 Envenom 的测试函数写进去。 */
@Mixin(BuiltinTestFunctions.class)
public abstract class BuiltinTestFunctionsMixin {
    @Inject(method = "bootstrap", at = @At("RETURN"))
    private static void envenom$registerTests(
            Registry<Consumer<GameTestHelper>> registry, CallbackInfoReturnable<Consumer<GameTestHelper>> cir) {
        EnvenomTestFunctions.bootstrap(registry);
    }
}
