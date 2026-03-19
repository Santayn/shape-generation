package app;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;

public enum ScalarType {
    INT8("char", 1),
    UINT8("uchar", 1),
    INT16("short", 2),
    UINT16("ushort", 2),
    INT32("int", 4),
    UINT32("uint", 4),
    FLOAT32("float", 4),
    FLOAT64("double", 8);

    private final String plyName;
    private final int size;

    ScalarType(String plyName, int size) {
        this.plyName = plyName;
        this.size = size;
    }

    public String plyName() {
        return plyName;
    }

    public int size() {
        return size;
    }

    public static ScalarType fromName(String name) {
        return switch (name) {
            case "char", "int8" -> INT8;
            case "uchar", "uint8" -> UINT8;
            case "short", "int16" -> INT16;
            case "ushort", "uint16" -> UINT16;
            case "int", "int32" -> INT32;
            case "uint", "uint32" -> UINT32;
            case "float", "float32" -> FLOAT32;
            case "double", "float64" -> FLOAT64;
            default -> throw new IllegalArgumentException("Неподдерживаемый тип: " + name);
        };
    }

    public double readAsDouble(ByteBuffer bb) {
        return switch (this) {
            case INT8 -> bb.get();
            case UINT8 -> Byte.toUnsignedInt(bb.get());
            case INT16 -> bb.getShort();
            case UINT16 -> Short.toUnsignedInt(bb.getShort());
            case INT32 -> bb.getInt();
            case UINT32 -> Integer.toUnsignedLong(bb.getInt());
            case FLOAT32 -> bb.getFloat();
            case FLOAT64 -> bb.getDouble();
        };
    }

    public long readAsLong(InputStream in) throws IOException {
        byte[] bytes = in.readNBytes(size);
        if (bytes.length != size) {
            throw new EOFException("Недостаточно данных для чтения PLY.");
        }
        ByteBuffer bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        return switch (this) {
            case INT8 -> bb.get();
            case UINT8 -> Byte.toUnsignedInt(bb.get());
            case INT16 -> bb.getShort();
            case UINT16 -> Short.toUnsignedInt(bb.getShort());
            case INT32 -> bb.getInt();
            case UINT32 -> Integer.toUnsignedLong(bb.getInt());
            case FLOAT32, FLOAT64 -> throw new IOException("Тип индекса грани должен быть целочисленным.");
        };
    }

    public void write(OutputStream out, double value) throws IOException {
        ByteBuffer bb = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
        switch (this) {
            case INT8 -> bb.put((byte) Math.round(value));
            case UINT8 -> bb.put((byte) ((int) Math.round(value) & 0xFF));
            case INT16 -> bb.putShort((short) Math.round(value));
            case UINT16 -> bb.putShort((short) ((int) Math.round(value) & 0xFFFF));
            case INT32 -> bb.putInt((int) Math.round(value));
            case UINT32 -> bb.putInt((int) Math.round(value));
            case FLOAT32 -> bb.putFloat((float) value);
            case FLOAT64 -> bb.putDouble(value);
        }
        out.write(bb.array());
    }

    public String formatAscii(double value) {
        return switch (this) {
            case INT8, UINT8, INT16, UINT16, INT32, UINT32 -> Long.toString(Math.round(value));
            case FLOAT32, FLOAT64 -> String.format(Locale.US, "%.9g", value);
        };
    }
}
