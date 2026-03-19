package app;

import java.util.List;
import java.util.Map;

public record PlyMesh(
        String format,
        List<VertexProperty> vertexProperties,
        Map<String, double[]> vertexValues,
        int vertexCount,
        int[] faces,
        int faceCount
) {
    public double[] xyz() {
        double[] xyz = new double[vertexCount * 3];
        double[] xs = vertexValues.get("x");
        double[] ys = vertexValues.get("y");
        double[] zs = vertexValues.get("z");
        for (int i = 0; i < vertexCount; i++) {
            xyz[i * 3] = xs[i];
            xyz[i * 3 + 1] = ys[i];
            xyz[i * 3 + 2] = zs[i];
        }
        return xyz;
    }
}
