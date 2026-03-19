package app;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.nio.file.Path;

public class InnerShellApp extends JFrame {
    private final JTextField inputField = new JTextField();
    private final JTextField outputField = new JTextField();
    private final JTextField offsetField = new JTextField("0.1");
    private final JCheckBox closeShellCheck = new JCheckBox("Замыкать оболочку по открытым краям", true);
    private final JTextArea reportArea = new JTextArea();
    private final JLabel statusLabel = new JLabel("Готово к работе");

    public InnerShellApp() {
        super("Inner Shell Generator");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(820, 600));
        setSize(920, 680);
        setLocationRelativeTo(null);
        buildUi();
    }

    private void buildUi() {
        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(new EmptyBorder(14, 14, 14, 14));
        setContentPane(root);

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Генерация внутренней поверхности с равномерным отступом");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        header.add(title);
        header.add(Box.createVerticalStrut(6));

        JLabel subtitle = new JLabel("Поддерживается PLY. Внутренняя поверхность строится по нормалям, а не масштабированием.");
        header.add(subtitle);

        root.add(header, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(12, 12));
        root.add(center, BorderLayout.CENTER);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createTitledBorder("Параметры"));
        center.add(form, BorderLayout.NORTH);

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.fill = GridBagConstraints.HORIZONTAL;

        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 0;
        form.add(new JLabel("Исходный файл:"), c);
        c.gridx = 1;
        c.weightx = 1;
        form.add(inputField, c);
        c.gridx = 2;
        c.weightx = 0;
        JButton chooseInput = new JButton("Выбрать...");
        chooseInput.addActionListener(e -> chooseInputFile());
        form.add(chooseInput, c);

        c.gridx = 0;
        c.gridy = 1;
        c.weightx = 0;
        form.add(new JLabel("Выходной файл:"), c);
        c.gridx = 1;
        c.weightx = 1;
        form.add(outputField, c);
        c.gridx = 2;
        c.weightx = 0;
        JButton chooseOutput = new JButton("Сохранить как...");
        chooseOutput.addActionListener(e -> chooseOutputFile());
        form.add(chooseOutput, c);

        c.gridx = 0;
        c.gridy = 2;
        c.weightx = 0;
        form.add(new JLabel("Отступ внутрь:"), c);
        c.gridx = 1;
        c.weightx = 1;
        form.add(offsetField, c);

        c.gridx = 0;
        c.gridy = 3;
        c.gridwidth = 3;
        form.add(closeShellCheck, c);
        c.gridwidth = 1;

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JButton build = new JButton("Построить");
        build.addActionListener(e -> processMesh());
        JButton clear = new JButton("Очистить");
        clear.addActionListener(e -> clearForm());
        JButton openFolder = new JButton("Открыть папку результата");
        openFolder.addActionListener(e -> openOutputFolder());
        buttons.add(build);
        buttons.add(clear);
        buttons.add(openFolder);

        c.gridx = 0;
        c.gridy = 4;
        c.gridwidth = 3;
        form.add(buttons, c);

        reportArea.setEditable(false);
        reportArea.setLineWrap(true);
        reportArea.setWrapStyleWord(true);
        JScrollPane reportPane = new JScrollPane(reportArea);
        reportPane.setBorder(BorderFactory.createTitledBorder("Отчёт"));
        center.add(reportPane, BorderLayout.CENTER);

        root.add(statusLabel, BorderLayout.SOUTH);
    }

    private void chooseInputFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Выберите PLY-файл");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            inputField.setText(file.getAbsolutePath());
            try {
                double offset = Double.parseDouble(offsetField.getText().replace(',', '.'));
                outputField.setText(MeshProcessor.buildDefaultOutputPath(file.toPath(), offset).toString());
            } catch (Exception ignored) {
                outputField.setText(MeshProcessor.buildDefaultOutputPath(file.toPath(), 0.1).toString());
            }
            statusLabel.setText("Исходный файл выбран");
        }
    }

    private void chooseOutputFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Куда сохранить результат");
        chooser.setSelectedFile(new File(outputField.getText().isBlank() ? "result.ply" : outputField.getText()));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            outputField.setText(chooser.getSelectedFile().getAbsolutePath());
            statusLabel.setText("Путь сохранения выбран");
        }
    }

    private void clearForm() {
        inputField.setText("");
        outputField.setText("");
        offsetField.setText("0.1");
        closeShellCheck.setSelected(true);
        reportArea.setText("");
        statusLabel.setText("Форма очищена");
    }

    private void openOutputFolder() {
        if (outputField.getText().isBlank()) {
            JOptionPane.showMessageDialog(this, "Сначала укажите путь выходного файла.", "Нет пути", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        try {
            File folder = Path.of(outputField.getText()).toAbsolutePath().getParent().toFile();
            Desktop.getDesktop().open(folder);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void processMesh() {
        String input = inputField.getText().trim();
        String output = outputField.getText().trim();
        if (input.isBlank()) {
            JOptionPane.showMessageDialog(this, "Выберите исходный файл модели.", "Ошибка", JOptionPane.ERROR_MESSAGE);
            return;
        }
        try {
            double offset = Double.parseDouble(offsetField.getText().trim().replace(',', '.'));
            if (output.isBlank()) {
                output = MeshProcessor.buildDefaultOutputPath(Path.of(input), offset).toString();
                outputField.setText(output);
            }
            statusLabel.setText("Идёт обработка...");
            Report report = MeshProcessor.process(Path.of(input), Path.of(output), offset, closeShellCheck.isSelected());
            reportArea.setText(report.text());
            statusLabel.setText("Готово");
            JOptionPane.showMessageDialog(this, "Файл сохранён:\n" + report.outputPath(), "Успешно", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            statusLabel.setText("Ошибка обработки");
            reportArea.setText("Ошибка:\n" + ex.getMessage());
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            Cli.run(args);
            return;
        }
        SwingUtilities.invokeLater(() -> new InnerShellApp().setVisible(true));
    }
}
