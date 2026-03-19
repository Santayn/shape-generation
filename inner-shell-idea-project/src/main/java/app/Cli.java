package app;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class Cli {
    private Cli() {
    }

    public static void run(String[] args) throws Exception {
        Map<String, String> values = new HashMap<>();
        boolean closeShell = true;
        for (int i = 0; i < args.length; i++) {
            if ("--no-close-shell".equals(args[i])) {
                closeShell = false;
            } else if (args[i].startsWith("--") && i + 1 < args.length) {
                values.put(args[i], args[++i]);
            }
        }

        String input = values.get("--input");
        String output = values.get("--output");
        String offsetText = values.get("--offset");

        if (input == null || output == null || offsetText == null) {
            System.out.println("Использование: --input file.ply --output result.ply --offset 0.1 [--no-close-shell]");
            return;
        }

        Report report = MeshProcessor.process(Path.of(input), Path.of(output), Double.parseDouble(offsetText), closeShell);
        System.out.println(report.text());
        System.out.println();
        System.out.println("Файл сохранён: " + report.outputPath());
    }
}
