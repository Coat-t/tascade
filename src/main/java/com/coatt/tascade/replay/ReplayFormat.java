package com.coatt.tascade.replay;

/**
 * the layout of a Tascade replay file (".tcr").
 *
 * <pre>
 * header   int MAGIC, short VERSION, short 0, long createdAtEpochMs
 * blocks   byte BLOCK_TAG, int rawLength, int compressedLength, int firstRecordTime, deflated bytes
 * footer   byte FOOTER_TAG, int metaLength, UTF-8 JSON metadata,
 *          int blockCount, then per block: long fileOffset, int rawLength, int firstRecordTime
 * trailer  long footerOffset, int END_MAGIC
 * </pre>
 *
 * Each block inflates to a run of whole records, so any record can be read by inflating just its
 * block. A record is {@code int time (ms since the recording started), byte kind, varint length,
 * payload}. A recording cut off by a crash has no footer, its blocks can still be found by
 * scanning from the header
 */
public final class ReplayFormat {
    public static final int MAGIC = 0x54435250; // "TCRP"
    public static final int END_MAGIC = 0x54435245; // "TCRE"
    public static final short VERSION = 1;
    public static final int HEADER_SIZE = 16;
    public static final byte BLOCK_TAG = (byte) 0xB1;
    public static final byte FOOTER_TAG = (byte) 0xF0;
    public static final int BLOCK_HEADER_SIZE = 13;
    public static final int TRAILER_SIZE = 12;
    public static final int BLOCK_TARGET_SIZE = 256 * 1024;
    // also closed once this old, so a crash loses at most this much
    public static final long BLOCK_MAX_AGE_MS = 5_000;
    public static final String EXTENSION = ".tcr";
    public static final String PARTIAL_EXTENSION = ".tcr.part";

    // record kinds. PACKET holds a vanilla play packet as the server sent it (varint id, then its
    // fields), the rest are what only the client knew, written by ClientSampler

    public static final int PACKET = 0;
    /** the camera as drawn, up to 60 times a second: double x, y, z, float yaw, pitch, fov, byte F5 perspective */
    public static final int CAMERA = 1;
    public static final int PLAYER = 2;
    public static final int VITALS = 3;
    /** changed player inventory slots (0-35 main, 36-39 armor, 40 offhand) */
    public static final int INVENTORY = 4;
    public static final int SCREEN = 5;
    public static final int SCREEN_SLOTS = 6;
    public static final int CURSOR = 7;
    public static final int CLICK = 8;
    public static final int SWING = 9;
    public static final int CHAT = 10;
    public static final int ACTION_BAR = 11;
    public static final int CHAT_CLEAR = 12;
    public static final int SETTINGS = 13;
    public static final int PIE_CHART = 14;
    public static final int MARKER = 15;
    public static final int META = 16;
    public static final int SOUND = 17;
    public static final int BLOCK_PARTICLES = 18;

    private ReplayFormat() {}
}
