import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public class LLMService {
    private static final String OLLAMA_API_URL = "http://localhost:11434/api/generate";
    private final ReentrantLock lock = new ReentrantLock();

    public String getAIResponse(String prompt, List<String> conversationHistory) throws IOException {
        lock.lock();
        try {
            StringBuilder context = new StringBuilder(prompt + "\n\nConversation History:\n");
            for (String message : conversationHistory) {
                context.append(message).append("\n");
            }
            context.append("\nResponse:");

            String jsonInput = String.format("""
                {
                    "model": "llama2",
                    "prompt": "%s",
                    "stream": false
                }
                """, escapeJson(context.toString()));

            HttpURLConnection connection = (HttpURLConnection) new URL(OLLAMA_API_URL).openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);

            try (OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream())) {
                writer.write(jsonInput);
            }

            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream()))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    return parseAIResponse(response.toString());
                }
            } else {
                throw new IOException("LLM request failed with code: " + connection.getResponseCode());
            }
        } finally {
            lock.unlock();
        }
    }

    private String escapeJson(String input) {
        return input.replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }

    private String parseAIResponse(String jsonResponse) {
        int startIdx = jsonResponse.indexOf("\"response\":\"") + 12;
        int endIdx = jsonResponse.indexOf("\"", startIdx);
        return jsonResponse.substring(startIdx, endIdx);
    }
}