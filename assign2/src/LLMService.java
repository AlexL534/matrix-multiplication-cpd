import java.io.*;
import java.net.*;
import java.util.*;

public class LLMService {
    private static final String OLLAMA_URL = "http://localhost:11434/api/generate";
    private String model = "llama3";
        private static final int MAX_HISTORY_LENGTH = 4000;
        private static final int MAX_READ_TIMEOUT = 120000; // 2 minutes


    public String getAIResponse(String prompt, List<String> conversationHistory, String initialPrompt) throws IOException {
        if (!testConnection()) {
            throw new IOException("Cannot connect to Ollama server");
        }

        List<String> limitedHistory = limitHistorySize(conversationHistory);

        String jsonInput = String.format(
            "{\"model\":\"%s\",\"prompt\":\"%s\",\"stream\":false}",
            model,
            escapeJson(buildFullPrompt(prompt, conversationHistory, initialPrompt))
        );
        
        HttpURLConnection connection = (HttpURLConnection) new URL(OLLAMA_URL).openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        connection.setRequestProperty("Accept", "application/json");
        connection.setConnectTimeout(10000); // 10 secs
        connection.setReadTimeout(MAX_READ_TIMEOUT);  
        connection.setDoOutput(true);

        try (OutputStream os = connection.getOutputStream();
                OutputStreamWriter writer = new OutputStreamWriter(os, "UTF-8")) {
            writer.write(jsonInput);
            writer.flush();
        }

        int responseCode = connection.getResponseCode();
        System.out.println("Ollama response code: " + responseCode);
        
        if (responseCode != 200) {
            StringBuilder errorResponse = new StringBuilder();
            try (BufferedReader err = new BufferedReader(
                new InputStreamReader(connection.getErrorStream()))) {
                String errorLine;
                while ((errorLine = err.readLine()) != null) {
                    errorResponse.append(errorLine);
                }
            }
            System.err.println("Full error response: " + errorResponse.toString());
            throw new IOException("HTTP error: " + responseCode + " - " + errorResponse.toString());
        }

        try (BufferedReader in = new BufferedReader(
            new InputStreamReader(connection.getInputStream()))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                response.append(line);
            }
            return parseAIResponse(response.toString());
        }
    }

    private List<String> limitHistorySize(List<String> conversationHistory) {
        if (conversationHistory == null || conversationHistory.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> limitedHistory = new ArrayList<>();
        int totalLength = 0;

        for (int i = conversationHistory.size() - 1; i >= 0; i--) {
            String msg = conversationHistory.get(i);
            if (totalLength + msg.length() > MAX_HISTORY_LENGTH) {
                break;
            }
            limitedHistory.add(0, msg);
            totalLength += msg.length();
        }

        return limitedHistory;
    }

    private boolean testConnection() {
        try {
            HttpURLConnection testConn = (HttpURLConnection) new URL(OLLAMA_URL)
                .openConnection();
            testConn.setRequestMethod("POST");
            testConn.setRequestProperty("Content-Type", "application/json");
            testConn.setConnectTimeout(3000);
            // send empty JSON to test the endpoint
            testConn.setDoOutput(true);
            try (OutputStream os = testConn.getOutputStream()) {
                os.write("{}".getBytes());
            }
            int responseCode = testConn.getResponseCode();
            System.out.println("Connection test response: " + responseCode);
            return responseCode < 500; // accept any non-server-error code
        } catch (Exception e) {
            System.err.println("Connection test failed: " + e.getMessage());
            return false;
        }
    }

    private String escapeJson(String input) {
        return input.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }

    private String parseAIResponse(String jsonResponse) {
        try {
            int startIdx = jsonResponse.indexOf("\"response\":\"") + 12;
            if (startIdx < 12) {
                throw new IllegalArgumentException("Invalid JSON response - missing 'response' field");
            }
            
            StringBuilder response = new StringBuilder();
            boolean escape = false;
            
            for (int i = startIdx; i < jsonResponse.length(); i++) {
                char c = jsonResponse.charAt(i);
                
                if (escape) {
                    if (c == 'n') {
                        response.append('\n'); 
                    } else if (c == 'r') {
                        response.append('\r'); 
                    } else if (c == 't') {
                        response.append('\t'); 
                    } else {
                        response.append(c);
                    }
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    if (i + 1 < jsonResponse.length()) {
                        char nextChar = jsonResponse.charAt(i + 1);
                        if (nextChar == ',' || nextChar == '}') {
                            break;
                        }
                    }
                    response.append(c);
                } else {
                    response.append(c);
                }
            }
            return response.toString();
        } catch (Exception e) {
            System.err.println("Failed to parse response: " + e.getMessage());
            System.err.println("Response content: " + jsonResponse);
            return "Error parsing AI response: " + e.getMessage();
        }
    }

    private String buildFullPrompt(String prompt, List<String> conversationHistory, String initialPrompt) {
        StringBuilder fullPrompt = new StringBuilder(prompt);

        if (initialPrompt != null && !initialPrompt.isEmpty()) {
            fullPrompt.append(initialPrompt).append("\n\n");
        } else {
            // default prompt if none is specified
            fullPrompt.append("You are a helpful chat assistant. Respond naturally and conversationally.\n\n");
        }

        if (!conversationHistory.isEmpty()) {
            fullPrompt.append("\n\nContext:");
            for (String msg : conversationHistory) {
                fullPrompt.append(msg).append("\n");
            }
        }

        fullPrompt.append("\nUser: ").append(prompt);
        fullPrompt.append("\nAssistant:");

        String fullPromptStr = fullPrompt.toString();
        if (fullPromptStr.length() > MAX_HISTORY_LENGTH) {
            fullPromptStr = fullPromptStr.substring(fullPromptStr.length() - MAX_HISTORY_LENGTH);
            
        }

        return fullPrompt.toString();
    }
}