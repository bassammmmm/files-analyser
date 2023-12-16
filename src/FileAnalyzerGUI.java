import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

class MainThread extends Thread {
    private List<File> files;
    private DefaultTableModel tableModel;

    public MainThread(List<File> files, DefaultTableModel tableModel) {
        this.files = files;
        this.tableModel = tableModel;
    }

    @Override
    public void run() {
        tableModel.setRowCount(0);
        for (File file : files) {
            int rowIndex = tableModel.getRowCount();
            tableModel.addRow(new Object[]{file.getName()});
            FileAnalyzerGUI.fileNames.add(file.getName());
        }
    }
}

class FileProcessor extends Thread {
    private File file;
    private DefaultTableModel tableModel;

    public FileProcessor(File file, DefaultTableModel tableModel) {
        this.file = file;
        this.tableModel = tableModel;
    }

    @Override
    public void run() {
        try {
            String text = Files.readString(Paths.get(file.getAbsolutePath()));
            if (text != null && !text.isEmpty()) {
                String[] words = text.split("\\s+");

                int wordsCount = words.length;
                int isCount = countOccurrences(words, "is");
                int areCount = countOccurrences(words, "are");
                int youCount = countOccurrences(words, "you");
                String longestWord = findLongestWord(words);
                String shortestWord = findShortestWord(words);

                Object[] rowData = new Object[]{
                        wordsCount, isCount, areCount, youCount, longestWord, shortestWord
                };

                int rowIndex = FileAnalyzerGUI.fileNames.indexOf(file.getName());
                if (rowIndex != -1) {
                    for (int i = 1; i < rowData.length + 1; i++) {
                        tableModel.setValueAt(rowData[i - 1], rowIndex, i);
                    }
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private int countOccurrences(String[] words, String target) {
        int count = 0;
        for (String word : words) {
            if (word.equalsIgnoreCase(target)) {
                count++;
            }
        }
        return count;
    }

    private String findLongestWord(String[] words) {
        String longest = "";
        for (String word : words) {
            if (word.length() > longest.length()) {
                longest = word;
            }
        }
        return longest;
    }

    private String findShortestWord(String[] words) {
        String shortest = words[0];
        for (String word : words) {
            if (word.length() < shortest.length()) {
                shortest = word;
            }
        }
        return shortest;
    }
    
}

public class FileAnalyzerGUI extends JFrame {
    private JTable outputTable;
    private JButton selectDirectoryButton;
    private JCheckBox includeSubdirectoriesCheckBox;
    private JButton startProcessingButton;
    private DefaultTableModel tableModel;
    private String selectedDirectory;
    private boolean includeSubdirectories;
    public static List<String> fileNames = new ArrayList<>();
    private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);
    private ScheduledFuture scheduledFuture;
    private JLabel longestLabel;
    private JLabel shortestLabel;
    private String longestWordAcrossFiles = "";
    private String shortestWordAcrossFiles = "";
    public FileAnalyzerGUI() {
        super("File Analyzer");

        tableModel = new DefaultTableModel(new String[]{"Files", "#Words", "#is", "#are", "#you", "Longest", "Shortest"}, 0);
        outputTable = new JTable(tableModel);
        outputTable.setPreferredScrollableViewportSize(new Dimension(600, 400));
        outputTable.setFillsViewportHeight(true);

        JScrollPane scrollPane = new JScrollPane(outputTable);

        selectDirectoryButton = new JButton("Select Directory");
        includeSubdirectoriesCheckBox = new JCheckBox("Include Subdirectories");
        startProcessingButton = new JButton("Start Processing");

        selectDirectoryButton.addActionListener(e -> selectedDirectory = getSelectedDirectory());

        startProcessingButton.addActionListener(e -> {
            includeSubdirectories = includeSubdirectoriesCheckBox.isSelected();
            if (selectedDirectory != null) {
                processDirectory(selectedDirectory, includeSubdirectories);
            } else {
                JOptionPane.showMessageDialog(FileAnalyzerGUI.this, "Select directory.");
            }
        });

        longestLabel = new JLabel("Longest word: ");
        shortestLabel = new JLabel("Shortest word: ");

        JPanel controlPanel = new JPanel();
        controlPanel.add(selectDirectoryButton);
        controlPanel.add(includeSubdirectoriesCheckBox);
        controlPanel.add(startProcessingButton);

        JPanel labelPanel = new JPanel();
        labelPanel.add(longestLabel);
        labelPanel.add(shortestLabel);

        JPanel contentPanel = new JPanel(new BorderLayout());
        contentPanel.add(controlPanel, BorderLayout.NORTH);
        contentPanel.add(scrollPane, BorderLayout.CENTER);
        contentPanel.add(labelPanel, BorderLayout.SOUTH);

        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.setContentPane(contentPanel);
        this.pack();
        this.setLocationRelativeTo(null);
        this.setVisible(true);
    }

    private String getSelectedDirectory() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

        int option = fileChooser.showOpenDialog(FileAnalyzerGUI.this);

        if (option == JFileChooser.APPROVE_OPTION) {
            File directory = fileChooser.getSelectedFile();
            return directory.getAbsolutePath();
        }

        return null;
    }

    private void processDirectory(String directoryPath, boolean includeSubdirectories) {
        fileNames.clear();
        
        if (scheduledFuture != null) {
            scheduledFuture.cancel(true);
        }

        Runnable task = () -> {
            List<File> filesDir = listFilePaths(directoryPath, includeSubdirectories);
            MainThread mainThread = new MainThread(filesDir, tableModel);
            mainThread.start();

            // Additional processing for finding longest and shortest words across all files
            String longestWord = "";
            String shortestWord = "";

            for (File file : filesDir) {
                FileProcessor fileProcessor = new FileProcessor(file, tableModel);
                fileProcessor.start();

                try {
                    String text = Files.readString(Paths.get(file.getAbsolutePath()));
                    if (text != null && !text.isEmpty()) {
                        String[] words = text.split("\\s+");

                        for (String word : words) {
                            if (longestWord.isEmpty() || word.length() > longestWord.length()) {
                                longestWord = word;
                            }
                            if (shortestWord.isEmpty() || word.length() < shortestWord.length()) {
                                shortestWord = word;
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            longestWordAcrossFiles = longestWord;
            shortestWordAcrossFiles = shortestWord;

            longestLabel.setText("Longest word: " + longestWordAcrossFiles);
            shortestLabel.setText("Shortest word: " + shortestWordAcrossFiles);

        };

        scheduledFuture = executorService.scheduleAtFixedRate(task, 0, 1, TimeUnit.SECONDS);
    }

    private List<File> listFilePaths(String path, boolean includeSubdirectories) {
        List<File> filesList = new ArrayList<>();

        File directory = new File(path);
        if (directory.exists() && directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile() && file.getName().endsWith(".txt")) {
                        filesList.add(file);
                    } else if (file.isDirectory() && includeSubdirectories) {
                        filesList.addAll(listFilePaths(file.getAbsolutePath(), true));
                    }
                }
            }
        }

        return filesList;
    }

    public static void main(String[] args) {

        FileAnalyzerGUI fileAnalyzerGUI = new FileAnalyzerGUI();

    }
}
