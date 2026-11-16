package com.coatt.tascade.mixin;

import com.coatt.tascade.TascadeKeys;
import net.minecraft.client.options.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Map;

// keeps Tascade's combo bindings out of the key map so vanilla bindings on the same key still work
@Mixin(KeyBinding.class)
public abstract class KeyBindingMixin {
  @Redirect(method = "<init>(Ljava/lang/String;Lnet/minecraft/client/util/InputUtil$Type;ILjava/lang/String;)V",
      at = @At(value = "INVOKE", target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
          ordinal = 1))
  private Object tascade$skipOnCreate(Map<Object, Object> map, Object key, Object binding) {
    return tascade$put(map, key, binding);
  }

  @Redirect(method = "updateKeysByCode",
      at = @At(value = "INVOKE", target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"))
  private static Object tascade$skipOnUpdate(Map<Object, Object> map, Object key, Object binding) {
    return tascade$put(map, key, binding);
  }

  // checked by category to avoid loading TascadeKeys early
  private static Object tascade$put(Map<Object, Object> map, Object key, Object binding) {
    if (TascadeKeys.CATEGORY.equals(((KeyBinding) binding).getCategory())) return map.get(key);
    return map.put(key, binding);
  }
}
