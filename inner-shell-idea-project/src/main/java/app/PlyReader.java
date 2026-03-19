package app;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class PlyReader {
    private PlyReader() {
    }

    public static PlyMesh read(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            Header header = readHeader(in);
            Map<String, double[]> vertexValues = new LinkedHashMap<>();
            for (VertexProperty prop : header.vertexProperties()) {
                vertexValues.put(prop.name(), new double[header.vertexCount()]);
            }
            if (header.format().equals("ascii")) {
                readAsciiBody(in, header, vertexValues);
            } else {
                readBinaryBody(in, header, vertexValues);
            }
            return new PlyMesh(
                    header.format(),
                    header.vertexProperties(),
                    vertexValues,
                    header.vertexCount(),
                    header.faces().toArray(),
                    header.faces().size() / 3
            );
        }
    }

    private static void readAsciiBody(InputStream in, Header header, Map<String, double[]> vertexValues) throws IOException {
        for (int i = 0; i < header.vertexCount(); i++) {
            String line = readLine(in);
            if (line == null) {
                throw new IOException("Файл оборван в секции vertex.");
            }
            String[] parts = line.trim().split("\\s+");
            if (parts.length != header.vertexProperties().size()) {
                throw new IOException("Некорректная строка vertex.");
            }
            for (int p = 0; p < header.vertexProperties().size(); p++) {
                VertexProperty prop = header.vertexProperties().get(p);
                vertexValues.get(prop.name())[i] = Double.parseDouble(parts[p]);
            }
        }

        for (int i = 0; i < header.faceCount(); i++) {
            String line = readLine(in);
            if (line == null) {
                throw new IOException("Файл оборван в секции face.");
            }
            String[] parts = line.trim().split("\\s+");
            int count = Integer.parseInt(parts[0]);
            if (count < 3) {
                continue;
            }
            int[] idx = new int[count];
            for (int j = 0; j < count; j++) {
                idx[j] = Integer.parseInt(parts[j + 1]);
            }
            triangulate(idx, header.faces());
        }
    }

    private static void readBinaryBody(InputStream in, Header header, Map<String, double[]> vertexValues) throws IOException {
        int vertexStride = 0;
        for (VertexProperty prop : header.vertexProperties()) {
            vertexStride += prop.type().size();
        }

        byte[] vertexBytes = in.readNBytes(vertexStride * header.vertexCount());
        if (vertexBytes.length != vertexStride * header.vertexCount()) {
            throw new IOException("Файл оборван в секции vertex.");
        }

        ByteBuffer vb = ByteBuffer.wrap(vertexBytes).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < header.vertexCount(); i++) {
            for (VertexProperty prop : header.vertexProperties()) {
                vertexValues.get(prop.name())[i] = prop.type().readAsDouble(vb);
            }
        }

        for (int i = 0; i < header.faceCount(); i++) {
            int count = (int) header.faceCountType().readAsLong(in);
            int[] idx = new int[count];
            for (int j = 0; j < count; j++) {
                idx[j] = (int) header.faceIndexType().readAsLong(in);
            }
            if (count >= 3) {
                triangulate(idx, header.faces());
            }
        }
    }

    private static Header readHeader(InputStream in) throws IOException {
        String first = readLine(in);
        if (first == null || !first.equals("ply")) {
            throw new IOException("Файл не является PLY.");
        }

        String format = null;
        int vertexCount = 0;
        int faceCount = 0;
        ScalarType faceCountType = null;
        ScalarType faceIndexType = null;
        String element = null;
        List<VertexProperty> vertexProperties = new ArrayList<>();
        IntArrayList faces = new IntArrayList();

        while (true) {
            String line = readLine(in);
            if (line == null) {
                throw new IOException("PLY-заголовок оборван.");
            }
            line = line.trim();
            if (line.isEmpty() || line.startsWith("comment")) {
                continue;
            }
            if (line.equals("end_header")) {
                break;
            }
            String[] parts = line.split("\\s+");
            switch (parts[0]) {
                case "format" -> format = parts[1];
                case "element" -> {
                    element = parts[1];
                    if (element.equals("vertex")) {
                        vertexCount = Integer.parseInt(parts[2]);
                    }
                    if (element.equals("face")) {
                        faceCount = Integer.parseInt(parts[2]);
                    }
                }
                case "property" -> {
                    if ("vertex".equals(element)) {
                        vertexProperties.add(new VertexProperty(parts[2], ScalarType.fromName(parts[1])));
                    } else if ("face".equals(element)) {
                        if (!parts[1].equals("list")) {
                            throw new IOException("Поддерживаются только face list properties.");
                        }
                        faceCountType = ScalarType.fromName(parts[2]);
                        faceIndexType = ScalarType.fromName(parts[3]);
                    }
                }
                default -> {
                }
            }
        }

        if (!Objects.equals(format, "ascii") && !Objects.equals(format, "binary_little_endian")) {
            throw new IOException("Поддерживаются только ascii и binary_little_endian.");
        }

        boolean hasX = false;
        boolean hasY = false;
        boolean hasZ = false;
        for (VertexProperty p : vertexProperties) {
            hasX |= p.name().equals("x");
            hasY |= p.name().equals("y");
            hasZ |= p.name().equals("z");
        }
        if (!(hasX && hasY && hasZ)) {
            throw new IOException("Вершины должны содержать x, y, z.");
        }

        return new Header(format, vertexCount, faceCount, vertexProperties, faceCountType, faceIndexType, faces);
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') {
                break;
            }
            if (b != '\r') {
                out.write(b);
            }
        }
        if (b == -1 && out.size() == 0) {
            return null;
        }
        return out.toString(StandardCharsets.US_ASCII);
    }

    private static void triangulate(int[] idx, IntArrayList faces) {
        for (int i = 1; i < idx.length - 1; i++) {
            faces.add(idx[0]);
            faces.add(idx[i]);
            faces.add(idx[i + 1]);
        }
    }

    private record Header(
            String format,
            int vertexCount,
            int faceCount,
            List<VertexProperty> vertexProperties,
            ScalarType faceCountType,
            ScalarType faceIndexType,
            IntArrayList faces
    ) {
    }
}
