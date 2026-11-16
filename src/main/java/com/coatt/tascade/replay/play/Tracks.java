package com.coatt.tascade.replay.play;

import com.coatt.tascade.replay.RecordCodec;
import com.coatt.tascade.replay.ReplayFormat;
import io.netty.buffer.Unpooled;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.profiler.ProfilerTiming;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class Tracks {
    // CAMERA, up to 60 Hz
    private int cameraCount;
    private int[] cameraTime = new int[1024];
    private double[] cameraX = new double[1024];
    private double[] cameraY = new double[1024];
    private double[] cameraZ = new double[1024];
    private float[] cameraYaw = new float[1024];
    private float[] cameraPitch = new float[1024];
    private float[] cameraFov = new float[1024];
    private byte[] cameraPerspective = new byte[1024];

    // PLAYER, every tick
    private int playerCount;
    private int[] playerTime = new int[1024];
    private double[] playerX = new double[1024];
    private double[] playerY = new double[1024];
    private double[] playerZ = new double[1024];
    private float[] playerYaw = new float[1024];
    private float[] playerPitch = new float[1024];
    private float[] playerHeadYaw = new float[1024];
    private float[] playerBodyYaw = new float[1024];
    private byte[] playerPose = new byte[1024];
    private byte[] playerFlags = new byte[1024];
    private short[] playerInput = new short[1024];

    final TimedList<RecordCodec.Vitals> vitals = new TimedList<>();
    final TimedList<ItemStack[]> inventory = new TimedList<>();
    final TimedList<ScreenState> screens = new TimedList<>();
    final TimedList<float[]> cursor = new TimedList<>();
    final TimedList<int[]> clicks = new TimedList<>();
    final TimedList<Integer> swings = new TimedList<>();
    final TimedList<ChatLine> chat = new TimedList<>();
    final TimedList<Boolean> chatClears = new TimedList<>();
    final TimedList<ActionBar> actionBar = new TimedList<>();
    final TimedList<JSONObject> settings = new TimedList<>();
    final TimedList<PieChart> pieCharts = new TimedList<>();
    final List<Marker> markers = new ArrayList<>();
    final TimedList<RecordCodec.Sound> sounds = new TimedList<>();
    final TimedList<long[]> blockParticles = new TimedList<>();
    private boolean hasBlockParticles;
    JSONObject streamMeta;

    public static final class ScreenState {
        public final RecordCodec.ScreenInfo info;
        public final int serial;
        public final int openedAt;
        public final ItemStack[] slots;
        public final ItemStack cursorStack;
        public final int[] properties;

        ScreenState(RecordCodec.ScreenInfo info, int serial, int openedAt, ItemStack[] slots, ItemStack cursorStack, int[] properties) {
            this.info = info;
            this.serial = serial;
            this.openedAt = openedAt;
            this.slots = slots;
            this.cursorStack = cursorStack;
            this.properties = properties;
        }
    }

    public static final class ChatLine {
        public final Text text;
        public final int messageId;

        ChatLine(Text text, int messageId) {
            this.text = text;
            this.messageId = messageId;
        }
    }

    public static final class ActionBar {
        public final Text text;
        public final boolean tinted;

        ActionBar(Text text, boolean tinted) {
            this.text = text;
            this.tinted = tinted;
        }
    }

    public static final class PieChart {
        public final boolean shown;
        public final String path;
        public final List<ProfilerTiming> timings;

        PieChart(boolean shown, String path, List<ProfilerTiming> timings) {
            this.shown = shown;
            this.path = path;
            this.timings = timings;
        }
    }

    public static final class Marker {
        public final int time;
        public final String type;
        public final String name;
        public final JSONObject extra;

        Marker(int time, String type, String name, JSONObject extra) {
            this.time = time;
            this.type = type;
            this.name = name;
            this.extra = extra;
        }
    }

    private int screenSerial = 0;

    void add(int kind, int time, byte[] data, int offset, int length) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.wrappedBuffer(data, offset, length));
        switch (kind) {
            case ReplayFormat.CAMERA: addCamera(time, buf); break;
            case ReplayFormat.PLAYER: addPlayer(time, buf); break;
            case ReplayFormat.VITALS: vitals.add(time, RecordCodec.readVitals(buf)); break;
            case ReplayFormat.INVENTORY: addInventory(time, buf); break;
            case ReplayFormat.SCREEN: addScreen(time, buf); break;
            case ReplayFormat.SCREEN_SLOTS: addScreenSlots(time, buf); break;
            case ReplayFormat.CURSOR: cursor.add(time, new float[] {buf.readFloat(), buf.readFloat()}); break;
            case ReplayFormat.CLICK: clicks.add(time, new int[] {buf.readVarInt(), buf.readShort(), buf.readByte(), buf.readByte()}); break;
            case ReplayFormat.SWING: swings.add(time, (int) buf.readByte()); break;
            case ReplayFormat.CHAT: {
                int id = buf.readVarInt();
                chat.add(time, new ChatLine(RecordCodec.readText(buf), id));
                break;
            }
            case ReplayFormat.ACTION_BAR: actionBar.add(time, new ActionBar(RecordCodec.readText(buf), buf.readBoolean())); break;
            case ReplayFormat.CHAT_CLEAR: chatClears.add(time, Boolean.TRUE); break;
            case ReplayFormat.SETTINGS: settings.add(time, new JSONObject(RecordCodec.readString(buf))); break;
            case ReplayFormat.PIE_CHART: addPieChart(time, buf); break;
            case ReplayFormat.SOUND:
                sounds.add(time, RecordCodec.readSound(buf));
                break;
            case ReplayFormat.BLOCK_PARTICLES: {
                long[] record = {buf.readByte(), buf.readLong(), buf.readVarInt()};
                if (record[0] != RecordCodec.PARTICLES_CRACK) hasBlockParticles = true;
                blockParticles.add(time, record);
                break;
            }
            case ReplayFormat.META:
                streamMeta = new JSONObject(RecordCodec.readString(buf));
                break;
            case ReplayFormat.MARKER: {
                String type = RecordCodec.readString(buf);
                String name = RecordCodec.readString(buf);
                JSONObject extra;
                try {
                    extra = new JSONObject(RecordCodec.readString(buf));
                } catch (RuntimeException e) {
                    extra = new JSONObject();
                }
                markers.add(new Marker(time, type, name, extra));
                break;
            }
            default:
                break;
        }
    }

    private void addCamera(int time, PacketByteBuf buf) {
        if (cameraCount == cameraTime.length) {
            int n = cameraCount * 2;
            cameraTime = Arrays.copyOf(cameraTime, n);
            cameraX = Arrays.copyOf(cameraX, n);
            cameraY = Arrays.copyOf(cameraY, n);
            cameraZ = Arrays.copyOf(cameraZ, n);
            cameraYaw = Arrays.copyOf(cameraYaw, n);
            cameraPitch = Arrays.copyOf(cameraPitch, n);
            cameraFov = Arrays.copyOf(cameraFov, n);
            cameraPerspective = Arrays.copyOf(cameraPerspective, n);
        }
        int i = cameraCount++;
        cameraTime[i] = time;
        cameraX[i] = buf.readDouble();
        cameraY[i] = buf.readDouble();
        cameraZ[i] = buf.readDouble();
        cameraYaw[i] = buf.readFloat();
        cameraPitch[i] = buf.readFloat();
        cameraFov[i] = buf.readFloat();
        cameraPerspective[i] = buf.isReadable() ? buf.readByte() : -1;
    }

    private void addPlayer(int time, PacketByteBuf buf) {
        if (playerCount == playerTime.length) {
            int n = playerCount * 2;
            playerTime = Arrays.copyOf(playerTime, n);
            playerX = Arrays.copyOf(playerX, n);
            playerY = Arrays.copyOf(playerY, n);
            playerZ = Arrays.copyOf(playerZ, n);
            playerYaw = Arrays.copyOf(playerYaw, n);
            playerPitch = Arrays.copyOf(playerPitch, n);
            playerHeadYaw = Arrays.copyOf(playerHeadYaw, n);
            playerBodyYaw = Arrays.copyOf(playerBodyYaw, n);
            playerPose = Arrays.copyOf(playerPose, n);
            playerFlags = Arrays.copyOf(playerFlags, n);
            playerInput = Arrays.copyOf(playerInput, n);
        }
        int i = playerCount++;
        playerTime[i] = time;
        playerX[i] = buf.readDouble();
        playerY[i] = buf.readDouble();
        playerZ[i] = buf.readDouble();
        playerYaw[i] = buf.readFloat();
        playerPitch[i] = buf.readFloat();
        playerHeadYaw[i] = buf.readFloat();
        playerBodyYaw[i] = buf.readFloat();
        playerPose[i] = buf.readByte();
        playerFlags[i] = buf.readByte();
        playerInput[i] = buf.readShort();
    }

    private void addInventory(int time, PacketByteBuf buf) {
        ItemStack[] previous = inventory.last();
        ItemStack[] slots = previous != null ? previous.clone() : emptyStacks(RecordCodec.INVENTORY_SIZE);
        int count = buf.readVarInt();
        for (int i = 0; i < count; i++) {
            int slot = buf.readByte();
            ItemStack stack = RecordCodec.readItem(buf);
            if (slot >= 0 && slot < slots.length) slots[slot] = stack;
        }
        inventory.add(time, slots);
    }

    private void addScreen(int time, PacketByteBuf buf) {
        RecordCodec.ScreenInfo info = RecordCodec.readScreen(buf);
        screens.add(time, new ScreenState(info, ++screenSerial, time, emptyStacks(info.slotCount), ItemStack.EMPTY, new int[0]));
    }

    private void addScreenSlots(int time, PacketByteBuf buf) {
        ScreenState previous = screens.last();
        if (previous == null) return;
        ItemStack[] slots = previous.slots.clone();
        int changed = buf.readVarInt();
        for (int i = 0; i < changed; i++) {
            int slot = buf.readVarInt();
            ItemStack stack = RecordCodec.readItem(buf);
            if (slot >= 0 && slot < slots.length) slots[slot] = stack;
        }
        ItemStack cursorStack = buf.readBoolean() ? RecordCodec.readItem(buf) : previous.cursorStack;
        int[] properties = previous.properties;
        int propertiesChanged = buf.readVarInt();
        if (propertiesChanged > 0) {
            properties = properties.clone();
            for (int i = 0; i < propertiesChanged; i++) {
                int index = buf.readVarInt();
                int value = buf.readVarInt();
                if (index < 0 || index > 64) continue;
                if (index >= properties.length) properties = Arrays.copyOf(properties, index + 1);
                properties[index] = value;
            }
        }
        screens.add(time, new ScreenState(previous.info, previous.serial, previous.openedAt, slots, cursorStack, properties));
    }

    private void addPieChart(int time, PacketByteBuf buf) {
        if (!buf.readBoolean()) {
            pieCharts.add(time, new PieChart(false, "", Collections.emptyList()));
            return;
        }
        String path = RecordCodec.readString(buf);
        int count = buf.readVarInt();
        List<ProfilerTiming> timings = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String name = RecordCodec.readString(buf);
            double ofParent = buf.readDouble();
            double ofTotal = buf.readDouble();
            timings.add(new ProfilerTiming(name, ofParent, ofTotal, 0));
        }
        pieCharts.add(time, new PieChart(true, path, timings));
    }

    private static ItemStack[] emptyStacks(int count) {
        ItemStack[] stacks = new ItemStack[count];
        Arrays.fill(stacks, ItemStack.EMPTY);
        return stacks;
    }

    // lookups

    boolean hasCamera() {
        return cameraCount > 0;
    }

    boolean camera(double time, double[] out) {
        if (cameraCount == 0) return false;
        int i = TimedList.lastAtOrBefore(cameraTime, cameraCount, (int) Math.floor(time));
        if (i < 0) i = 0;
        int j = Math.min(i + 1, cameraCount - 1);
        double f = fraction(cameraTime[i], cameraTime[j], time);
        // no sliding between first and third person, or through a teleport
        double dx = cameraX[j] - cameraX[i];
        double dy = cameraY[j] - cameraY[i];
        double dz = cameraZ[j] - cameraZ[i];
        if (cameraPerspective[i] != cameraPerspective[j] || dx * dx + dy * dy + dz * dz > 64) f = 0;
        out[0] = MathHelper.lerp(f, cameraX[i], cameraX[j]);
        out[1] = MathHelper.lerp(f, cameraY[i], cameraY[j]);
        out[2] = MathHelper.lerp(f, cameraZ[i], cameraZ[j]);
        out[3] = MathHelper.lerp(f, cameraYaw[i], cameraYaw[j]);
        out[4] = MathHelper.lerp(f, cameraPitch[i], cameraPitch[j]);
        out[5] = MathHelper.lerp(f, cameraFov[i], cameraFov[j]);
        return true;
    }

    boolean hasBlockParticles() {
        return hasBlockParticles;
    }

    int cameraPerspective(double time) {
        if (cameraCount == 0) return -1;
        int i = TimedList.lastAtOrBefore(cameraTime, cameraCount, (int) Math.floor(time));
        return cameraPerspective[Math.max(i, 0)];
    }

    boolean hasPlayer() {
        return playerCount > 0;
    }

    int player(double time, double[] out) {
        if (playerCount == 0) return -1;
        int i = TimedList.lastAtOrBefore(playerTime, playerCount, (int) Math.floor(time));
        if (i < 0) i = 0;
        int j = Math.min(i + 1, playerCount - 1);
        double f = fraction(playerTime[i], playerTime[j], time);
        // a jump of more than a few blocks is a teleport: don't slide through it
        double dx = playerX[j] - playerX[i];
        double dy = playerY[j] - playerY[i];
        double dz = playerZ[j] - playerZ[i];
        if (dx * dx + dy * dy + dz * dz > 64) f = f < 1 ? 0 : 1;
        out[0] = MathHelper.lerp(f, playerX[i], playerX[j]);
        out[1] = MathHelper.lerp(f, playerY[i], playerY[j]);
        out[2] = MathHelper.lerp(f, playerZ[i], playerZ[j]);
        out[3] = MathHelper.lerp(f, playerYaw[i], playerYaw[j]);
        out[4] = MathHelper.lerp(f, playerPitch[i], playerPitch[j]);
        out[5] = MathHelper.lerp(f, playerHeadYaw[i], playerHeadYaw[j]);
        out[6] = MathHelper.lerp(f, playerBodyYaw[i], playerBodyYaw[j]);
        return f >= 0.5 ? j : i;
    }

    int firstPlayerNear(int from, int until, double x, double y, double z, double distance) {
        for (int i = TimedList.lastAtOrBefore(playerTime, playerCount, from - 1) + 1; i < playerCount; i++) {
            if (playerTime[i] > until) break;
            double dx = playerX[i] - x;
            double dy = playerY[i] - y;
            double dz = playerZ[i] - z;
            if (dx * dx + dy * dy + dz * dz <= distance * distance) return playerTime[i];
        }
        return -1;
    }

    int playerPose(int sample) {
        return playerPose[sample];
    }

    int playerFlags(int sample) {
        return playerFlags[sample] & 0xFF;
    }

    int playerInput(int sample) {
        return playerInput[sample] & 0xFFFF;
    }

    int firstPlayerTime() {
        return playerCount > 0 ? playerTime[0] : 0;
    }

    private static double fraction(int from, int to, double time) {
        if (to <= from) return 0;
        return MathHelper.clamp((time - from) / (to - from), 0, 1);
    }
}
