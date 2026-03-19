package app;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.nio.file.Path;

public final class MeshProcessor {
    private MeshProcessor() {
    }

    public static Path buildDefaultOutputPath(Path inputPath, double offset) {
        String offsetText = String.valueOf(offset).replace('.', '_');
        String fileName = inputPath.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String stem = dot >= 0 ? fileName.substring(0, dot) : fileName;
        return inputPath.resolveSibling(stem + "_inner_offset_" + offsetText + ".ply");
    }

    public static Report process(Path input, Path output, double offset, boolean closeShell) throws Exception {
        if (!input.toString().toLowerCase(Locale.ROOT).endsWith(".ply")) {
            throw new IllegalArgumentException("Поддерживается только формат .ply");
        }
        PlyMesh mesh = PlyReader.read(input);
        PlyMesh result = buildOffsetShell(mesh, offset, closeShell);
        PlyWriter.write(result, output, mesh.format());
        String report = "Исходная модель:\n" + summary(mesh) + "\n\nРезультат:\n" + summary(result) + "\n\nФайл сохранён:\n" + output.toAbsolutePath();
        return new Report(output.toAbsolutePath().toString(), report);
    }

    private static String summary(PlyMesh mesh) {
        int boundaryCount = getBoundaryEdges(mesh.faces()).length / 2;
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;

        for (int i = 0; i < mesh.vertexCount(); i++) {
            double x = mesh.vertexValues().get("x")[i];
            double y = mesh.vertexValues().get("y")[i];
            double z = mesh.vertexValues().get("z")[i];
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }

        return "Вершин: " + mesh.vertexCount() + "\n" +
                "Треугольников: " + mesh.faceCount() + "\n" +
                "Герметичная сетка: " + (boundaryCount == 0 ? "да" : "нет") + "\n" +
                "Открытых граничных рёбер: " + boundaryCount + "\n" +
                String.format(Locale.US, "Габариты: %.4f × %.4f × %.4f", maxX - minX, maxY - minY, maxZ - minZ);
    }

    private static PlyMesh buildOffsetShell(PlyMesh mesh, double offset, boolean closeShell) {
        if (!(offset > 0.0) || Double.isNaN(offset) || Double.isInfinite(offset)) {
            throw new IllegalArgumentException("Величина отступа должна быть положительным числом.");
        }

        double[] xyz = mesh.xyz();
        validateOffset(xyz, mesh.vertexCount(), offset);
        int[] outerFaces = orientFacesOutward(xyz, mesh.faces(), mesh.faceCount());
        double[] vertexNormals = computeVertexNormals(xyz, mesh.vertexCount(), outerFaces, mesh.faceCount());

        int vertexCount = mesh.vertexCount();
        int[] innerFaces = new int[outerFaces.length];
        for (int i = 0; i < mesh.faceCount(); i++) {
            innerFaces[i * 3] = outerFaces[i * 3 + 2] + vertexCount;
            innerFaces[i * 3 + 1] = outerFaces[i * 3 + 1] + vertexCount;
            innerFaces[i * 3 + 2] = outerFaces[i * 3] + vertexCount;
        }

        Map<String, double[]> combinedValues = new LinkedHashMap<>();
        for (VertexProperty prop : mesh.vertexProperties()) {
            double[] source = mesh.vertexValues().get(prop.name());
            double[] combined = new double[vertexCount * 2];
            System.arraycopy(source, 0, combined, 0, vertexCount);
            if (prop.name().equals("x") || prop.name().equals("y") || prop.name().equals("z")) {
                int axis = prop.name().equals("x") ? 0 : prop.name().equals("y") ? 1 : 2;
                for (int i = 0; i < vertexCount; i++) {
                    combined[vertexCount + i] = xyz[i * 3 + axis] - vertexNormals[i * 3 + axis] * offset;
                }
            } else {
                System.arraycopy(source, 0, combined, vertexCount, vertexCount);
            }
            combinedValues.put(prop.name(), combined);
        }

        int[] boundary = getBoundaryEdges(outerFaces);
        int sideFacesCount = closeShell ? boundary.length : 0;
        int[] faces = new int[outerFaces.length + innerFaces.length + sideFacesCount * 3];
        System.arraycopy(outerFaces, 0, faces, 0, outerFaces.length);
        System.arraycopy(innerFaces, 0, faces, outerFaces.length, innerFaces.length);
        int index = outerFaces.length + innerFaces.length;

        if (closeShell) {
            for (int i = 0; i < boundary.length; i += 2) {
                int a = boundary[i];
                int b = boundary[i + 1];
                int aInner = a + vertexCount;
                int bInner = b + vertexCount;
                faces[index++] = a;
                faces[index++] = b;
                faces[index++] = bInner;
                faces[index++] = a;
                faces[index++] = bInner;
                faces[index++] = aInner;
            }
        }

        return new PlyMesh(mesh.format(), mesh.vertexProperties(), combinedValues, vertexCount * 2, faces, faces.length / 3);
    }

    private static void validateOffset(double[] xyz, int vertexCount, double offset) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;

        for (int i = 0; i < vertexCount; i++) {
            double x = xyz[i * 3];
            double y = xyz[i * 3 + 1];
            double z = xyz[i * 3 + 2];
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }

        double minDimension = Math.min(maxX - minX, Math.min(maxY - minY, maxZ - minZ));
        if (offset >= minDimension / 2.0) {
            throw new IllegalArgumentException("Отступ слишком большой относительно размеров модели.");
        }
    }

    private static int[] orientFacesOutward(double[] xyz, int[] faces, int faceCount) {
        int[] oriented = Arrays.copyOf(faces, faces.length);
        double cx = 0;
        double cy = 0;
        double cz = 0;
        int vertexCount = xyz.length / 3;

        for (int i = 0; i < vertexCount; i++) {
            cx += xyz[i * 3];
            cy += xyz[i * 3 + 1];
            cz += xyz[i * 3 + 2];
        }

        cx /= vertexCount;
        cy /= vertexCount;
        cz /= vertexCount;

        double sum = 0;
        for (int i = 0; i < faceCount; i++) {
            int a = faces[i * 3];
            int b = faces[i * 3 + 1];
            int c = faces[i * 3 + 2];
            double ax = xyz[a * 3];
            double ay = xyz[a * 3 + 1];
            double az = xyz[a * 3 + 2];
            double bx = xyz[b * 3];
            double by = xyz[b * 3 + 1];
            double bz = xyz[b * 3 + 2];
            double cxv = xyz[c * 3];
            double cyv = xyz[c * 3 + 1];
            double czv = xyz[c * 3 + 2];
            double ux = bx - ax;
            double uy = by - ay;
            double uz = bz - az;
            double vx = cxv - ax;
            double vy = cyv - ay;
            double vz = czv - az;
            double nx = uy * vz - uz * vy;
            double ny = uz * vx - ux * vz;
            double nz = ux * vy - uy * vx;
            double centerX = (ax + bx + cxv) / 3.0;
            double centerY = (ay + by + cyv) / 3.0;
            double centerZ = (az + bz + czv) / 3.0;
            sum += (centerX - cx) * nx + (centerY - cy) * ny + (centerZ - cz) * nz;
        }

        if (sum < 0) {
            for (int i = 0; i < faceCount; i++) {
                int tmp = oriented[i * 3];
                oriented[i * 3] = oriented[i * 3 + 2];
                oriented[i * 3 + 2] = tmp;
            }
        }
        return oriented;
    }

    private static double[] computeVertexNormals(double[] xyz, int vertexCount, int[] faces, int faceCount) {
        double[] normals = new double[vertexCount * 3];
        for (int i = 0; i < faceCount; i++) {
            int a = faces[i * 3];
            int b = faces[i * 3 + 1];
            int c = faces[i * 3 + 2];
            double ax = xyz[a * 3];
            double ay = xyz[a * 3 + 1];
            double az = xyz[a * 3 + 2];
            double bx = xyz[b * 3];
            double by = xyz[b * 3 + 1];
            double bz = xyz[b * 3 + 2];
            double cx = xyz[c * 3];
            double cy = xyz[c * 3 + 1];
            double cz = xyz[c * 3 + 2];
            double ux = bx - ax;
            double uy = by - ay;
            double uz = bz - az;
            double vx = cx - ax;
            double vy = cy - ay;
            double vz = cz - az;
            double nx = uy * vz - uz * vy;
            double ny = uz * vx - ux * vz;
            double nz = ux * vy - uy * vx;
            normals[a * 3] += nx;
            normals[a * 3 + 1] += ny;
            normals[a * 3 + 2] += nz;
            normals[b * 3] += nx;
            normals[b * 3 + 1] += ny;
            normals[b * 3 + 2] += nz;
            normals[c * 3] += nx;
            normals[c * 3 + 1] += ny;
            normals[c * 3 + 2] += nz;
        }

        for (int i = 0; i < vertexCount; i++) {
            double nx = normals[i * 3];
            double ny = normals[i * 3 + 1];
            double nz = normals[i * 3 + 2];
            double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len <= 1e-12) {
                throw new IllegalArgumentException("У части вершин не удалось вычислить корректные нормали.");
            }
            normals[i * 3] /= len;
            normals[i * 3 + 1] /= len;
            normals[i * 3 + 2] /= len;
        }
        return normals;
    }

    private static int[] getBoundaryEdges(int[] faces) {
        Map<Long, Integer> counts = new HashMap<>();
        Map<Long, int[]> oriented = new HashMap<>();
        int faceCount = faces.length / 3;
        for (int i = 0; i < faceCount; i++) {
            int a = faces[i * 3];
            int b = faces[i * 3 + 1];
            int c = faces[i * 3 + 2];
            addEdge(a, b, counts, oriented);
            addEdge(b, c, counts, oriented);
            addEdge(c, a, counts, oriented);
        }

        IntArrayList result = new IntArrayList();
        for (Map.Entry<Long, Integer> entry : counts.entrySet()) {
            if (entry.getValue() == 1) {
                int[] edge = oriented.get(entry.getKey());
                result.add(edge[0]);
                result.add(edge[1]);
            }
        }
        return result.toArray();
    }

    private static void addEdge(int a, int b, Map<Long, Integer> counts, Map<Long, int[]> oriented) {
        int min = Math.min(a, b);
        int max = Math.max(a, b);
        long key = (((long) min) << 32) | (max & 0xffffffffL);
        counts.put(key, counts.getOrDefault(key, 0) + 1);
        oriented.putIfAbsent(key, new int[]{a, b});
    }
}
