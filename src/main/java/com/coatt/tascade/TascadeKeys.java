package com.coatt.tascade;

import com.coatt.tascade.mixin.access.KeyBindingAccessor;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public final class TascadeKeys {
    private TascadeKeys() {}

    public static final String CATEGORY = "key.categories.tascade";

    static {
        // Controls has no place for an unknown category
        KeyBindingAccessor.getCategoryOrderMap().putIfAbsent(CATEGORY, 100);
    }

    public static final KeyBinding LEAVE_QUEUE =
            new KeyBinding("key.tascade.leaveQueue", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_Z, CATEGORY);

    public static KeyBinding[] all() {
        return new KeyBinding[]{LEAVE_QUEUE};
    }
}
