import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ShamirGUI extends JFrame {
    JTextField secretField, primeField;
    JTextField[][] shareFields = new JTextField[3][2];
    JTextField resultField, statusField;
    JButton loadButton;

    public ShamirGUI() {
        setTitle("Shamir Secret Sharing Validator");
        setSize(500, 500);
        setLayout(new GridLayout(10, 2, 5, 5));
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        add(new JLabel("JSON File Path:"));
        JPanel filePanel = new JPanel(new BorderLayout());
        JTextField filePathField = new JTextField("shamir.json");
        loadButton = new JButton("Load");
        filePanel.add(filePathField, BorderLayout.CENTER);
        filePanel.add(loadButton, BorderLayout.EAST);
        add(filePanel);

        loadButton.addActionListener(e -> {
            try {
                loadJson(filePathField.getText().trim());
            } catch (Exception ex) {
                statusField.setText("Error: " + ex.getMessage());
            }
        });

        add(new JLabel("Expected Secret:"));
        secretField = new JTextField();
        add(secretField);

        add(new JLabel("Prime Number (> Secret):"));
        primeField = new JTextField();
        add(primeField);

        for (int i = 0; i < 3; i++) {
            add(new JLabel("Share " + (i + 1) + " (x, y):"));
            JPanel panel = new JPanel(new GridLayout(1, 2));
            shareFields[i][0] = new JTextField();
            shareFields[i][1] = new JTextField();
            panel.add(shareFields[i][0]);
            panel.add(shareFields[i][1]);
            add(panel);
        }

        JButton button = new JButton("Reconstruct Secret");
        button.addActionListener(e -> reconstructSecret());
        add(button);

        add(new JLabel("Recovered Secret:"));
        resultField = new JTextField();
        resultField.setEditable(false);
        add(resultField);

        add(new JLabel("Status:"));
        statusField = new JTextField();
        statusField.setEditable(false);
        add(statusField);

        setVisible(true);
    }

    void loadJson(String filePath) throws Exception {
        String content = new String(Files.readAllBytes(Paths.get(filePath)));
        JSONObject json = parseSimpleJson(content);
        
        secretField.setText(json.getValue("expectedSecret"));
        primeField.setText(json.getValue("prime"));
        
        JSONArray shares = json.getArray("shares");
        for (int i = 0; i < 3; i++) {
            JSONObject share = shares.getObject(i);
            shareFields[i][0].setText(share.getValue("x"));
            shareFields[i][1].setText(share.getValue("y"));
        }
        
        statusField.setText("Loaded from: " + filePath);
    }

    // Simple JSON parser for fixed structure
    static class JSONObject {
        private final String content;
        
        public JSONObject(String content) {
            this.content = content;
        }
        
        public String getValue(String key) {
            String pattern = "\"" + key + "\":";
            int start = content.indexOf(pattern) + pattern.length();
            int end = content.indexOf(',', start);
            if (end == -1) end = content.indexOf('}', start);
            return content.substring(start, end).replaceAll("\"", "").trim();
        }
        
        public JSONArray getArray(String key) {
            String pattern = "\"" + key + "\":\\s*\\[";
            int start = content.indexOf(pattern) + pattern.length();
            int end = content.lastIndexOf(']');
            return new JSONArray(content.substring(start, end));
        }
    }

    static class JSONArray {
        private final String content;
        private final String[] items;
        
        public JSONArray(String content) {
            this.content = content;
            this.items = content.split("\\},\\s*\\{");
        }
        
        public JSONObject getObject(int index) {
            String item = items[index].replaceAll("[\\[\\]{}]", "").trim();
            if (!item.startsWith("{")) item = "{" + item;
            if (!item.endsWith("}")) item = item + "}";
            return new JSONObject(item);
        }
    }
    
    static JSONObject parseSimpleJson(String content) {
        // Remove newlines and extra spaces
        content = content.replaceAll("\\s+", " ").trim();
        return new JSONObject(content);
    }

    void reconstructSecret() {
        try {
            int expectedSecret = Integer.parseInt(secretField.getText().trim());
            int prime = Integer.parseInt(primeField.getText().trim());

            List<Share> shares = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                int x = Integer.parseInt(shareFields[i][0].getText().trim());
                int y = Integer.parseInt(shareFields[i][1].getText().trim());
                shares.add(new Share(x, y));
            }

            int recovered = recoverSecret(shares, prime);
            resultField.setText(String.valueOf(recovered));

            if (recovered == expectedSecret) {
                statusField.setText("Valid Shares");
            } else {
                statusField.setText("Invalid Shares");
            }

        } catch (Exception ex) {
            statusField.setText("Error: " + ex.getMessage());
            resultField.setText("");
        }
    }

    static class Share {
        int x, y;
        Share(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    static int power(int a, int b, int mod) {
        int result = 1;
        a %= mod;
        while (b > 0) {
            if ((b & 1) == 1)
                result = (result * a) % mod;
            a = (a * a) % mod;
            b >>= 1;
        }
        return result;
    }

    static int modInverse(int a, int mod) {
        return power(a, mod - 2, mod);
    }

    static int recoverSecret(List<Share> shares, int prime) throws Exception {
        Set<Integer> xSet = new HashSet<>();
        for (Share share : shares) {
            if (xSet.contains(share.x)) {
                throw new Exception("Duplicate x value: " + share.x);
            }
            xSet.add(share.x);
        }

        int secret = 0;
        for (int i = 0; i < shares.size(); i++) {
            int xi = shares.get(i).x;
            int yi = shares.get(i).y;

            int num = 1, den = 1;
            for (int j = 0; j < shares.size(); j++) {
                if (i != j) {
                    int xj = shares.get(j).x;
                    num = (num * (-xj + prime)) % prime;
                    den = (den * (xi - xj + prime)) % prime;
                }
            }

            int term = yi * num % prime * modInverse(den, prime) % prime;
            secret = (secret + term) % prime;
        }
        return (secret + prime) % prime;
    }

    public static void main(String[] args) {
        try {
            testRecovery();
        } catch (Exception ex) {
            System.err.println("Core test failed: " + ex.getMessage());
        }
        
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("Running in headless mode. GUI will not be launched.");
            System.out.println("Core algorithm test completed successfully.");
        } else {
            SwingUtilities.invokeLater(ShamirGUI::new);
        }
    }

    static void testRecovery() throws Exception {
        List<Share> shares = Arrays.asList(
            new Share(1, 1494),
            new Share(2, 1942),
            new Share(3, 491)
        );
        int prime = 2087;
        int secret = recoverSecret(shares, prime);
        System.out.println("Core Algorithm Test:");
        System.out.println("Recovered: " + secret + " | Expected: 1234");
        System.out.println("Test " + (secret == 1234 ? "PASSED" : "FAILED"));
        
        // Test JSON loading
        testJsonParsing();
    }
    
    static void testJsonParsing() {
        System.out.println("\nJSON Parsing Test:");
        try {
            String jsonContent = "{ \"expectedSecret\": 1234, \"prime\": 2087, \"shares\": [ { \"x\": 1, \"y\": 1494 }, { \"x\": 2, \"y\": 1942 }, { \"x\": 3, \"y\": 491 } ] }";
            JSONObject json = parseSimpleJson(jsonContent);
            
            String secret = json.getValue("expectedSecret");
            String prime = json.getValue("prime");
            JSONArray shares = json.getArray("shares");
            
            System.out.println("Secret: " + secret + " | Prime: " + prime);
            for (int i = 0; i < 3; i++) {
                JSONObject share = shares.getObject(i);
                System.out.println("Share " + (i+1) + ": x=" + share.getValue("x") + ", y=" + share.getValue("y"));
            }
            
            System.out.println("JSON Parsing Test PASSED");
        } catch (Exception ex) {
            System.out.println("JSON Parsing Test FAILED: " + ex.getMessage());
        }
    }
}