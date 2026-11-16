package com.coatt.tascade.replay.play;

final class ByteReader {
    private final byte[] data;
    private int position;

    ByteReader(byte[] data, int offset) {
        this.data = data;
        this.position = offset;
    }

    int readVarInt() {
        int value = 0;
        int shift = 0;
        byte b;
        do {
            b = data[position++];
            value |= (b & 0x7F) << shift;
            shift += 7;
        } while ((b & 0x80) != 0 && shift < 35);
        return value;
    }

    int readInt() {
        int value = (data[position] & 0xFF) << 24 | (data[position + 1] & 0xFF) << 16
                | (data[position + 2] & 0xFF) << 8 | (data[position + 3] & 0xFF);
        position += 4;
        return value;
    }

    long readLong() {
        long high = readInt() & 0xFFFFFFFFL;
        long low = readInt() & 0xFFFFFFFFL;
        return high << 32 | low;
    }

    short readShort() {
        short value = (short) ((data[position] & 0xFF) << 8 | (data[position + 1] & 0xFF));
        position += 2;
        return value;
    }

    byte readByte() {
        return data[position++];
    }

    boolean readBoolean() {
        return data[position++] != 0;
    }

    double readDouble() {
        return Double.longBitsToDouble(readLong());
    }

    void skip(int bytes) {
        position += bytes;
    }
}
