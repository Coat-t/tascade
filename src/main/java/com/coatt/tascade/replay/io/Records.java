package com.coatt.tascade.replay.io;

import io.netty.buffer.ByteBuf;

public final class Records {
    private Records() {}

    public static byte[] frame(int time, int kind, ByteBuf payload) {
        int length = payload.readableBytes();
        int lengthSize = varIntSize(length);
        byte[] out = new byte[5 + lengthSize + length];
        out[0] = (byte) (time >>> 24);
        out[1] = (byte) (time >>> 16);
        out[2] = (byte) (time >>> 8);
        out[3] = (byte) time;
        out[4] = (byte) kind;
        int p = 5;
        int value = length;
        while ((value & ~0x7F) != 0) {
            out[p++] = (byte) (value & 0x7F | 0x80);
            value >>>= 7;
        }
        out[p++] = (byte) value;
        payload.getBytes(payload.readerIndex(), out, p, length);
        return out;
    }

    public static int varIntSize(int value) {
        int size = 1;
        while ((value & ~0x7F) != 0) {
            size++;
            value >>>= 7;
        }
        return size;
    }

    public static final class Cursor {
        private final byte[] data;
        private final int end;
        private int position;
        public int time;
        public int kind;
        public int payloadOffset;
        public int payloadLength;

        public Cursor(byte[] data, int length) {
            this.data = data;
            this.end = length;
        }

        public boolean next() {
            if (position + 6 > end) return false;
            time = (data[position] & 0xFF) << 24 | (data[position + 1] & 0xFF) << 16
                    | (data[position + 2] & 0xFF) << 8 | (data[position + 3] & 0xFF);
            kind = data[position + 4] & 0xFF;
            int p = position + 5;
            int length = 0;
            int shift = 0;
            byte b;
            do {
                if (p >= end) return false;
                b = data[p++];
                length |= (b & 0x7F) << shift;
                shift += 7;
            } while ((b & 0x80) != 0 && shift < 35);
            if (length < 0 || p + length > end) return false;
            payloadOffset = p;
            payloadLength = length;
            position = p + length;
            return true;
        }
    }
}
