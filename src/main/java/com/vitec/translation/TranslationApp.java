package com.vitec.translation;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Komentorivisovellus kielenkääntäjän testaamiseen.
 */
public class TranslationApp {
    
    private static final String CONFIG_FILE = "config.properties";
    private static ClaudeTranslationService translationService;
    private static BufferedReader reader;
    
    public static void main(String[] args) {
        reader = new BufferedReader(new InputStreamReader(System.in));
        
        try {
            // Tarkista ensin ympäristömuuttuja
            String apiKey = System.getenv("CLAUDE_API_KEY");
            String model = System.getenv("CLAUDE_MODEL");
            if (model == null || model.isEmpty()) {
                model = "claude-3-7-sonnet-20250219";
            }
            
            // Jos ympäristömuuttujaa ei ole, tarkista config-tiedosto
            if (apiKey == null || apiKey.isEmpty()) {
                Properties config = loadConfiguration();
                apiKey = config.getProperty("claude.api.key");
                
                if (model == null || model.isEmpty()) {
                    model = config.getProperty("claude.model", "claude-3-7-sonnet-20250219");
                }
                
                // Jos avain on yhä tyhjä, pyydä käyttäjää syöttämään se
                if (apiKey == null || apiKey.isEmpty()) {
                    System.out.println("API-avainta ei löydy. Syötä Claude API-avain:");
                    apiKey = reader.readLine().trim();
                    
                    // Tallennetaan API-avain myöhempää käyttöä varten
                    config.setProperty("claude.api.key", apiKey);
                    saveConfiguration(config);
                }
            }
            
            System.out.println("Käytetään mallia: " + model);
            
            // Alustetaan käännöspalvelu
            translationService = new ClaudeTranslationService(apiKey, model);
            
            // Testataan API-yhteyttä
            testApiConnection(apiKey);
            
            // Käynnistetään valikko
            showMenu();
            
        } catch (Exception e) {
            System.err.println("Virhe sovelluksen käynnistyksessä: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                reader.close();
            } catch (IOException e) {
                // Ignoroidaan sulkemisvirhe
            }
        }
    }
    
    /**
     * Näyttää päävalikon ja käsittelee käyttäjän syötteet.
     */
    private static void showMenu() throws IOException {
        while (true) {
            System.out.println("\n===== CLAUDE API KÄÄNTÄJÄ =====");
            System.out.println("1. Käännä teksti");
            System.out.println("2. Käännä tiedosto");
            System.out.println("3. Lopeta");
            System.out.print("\nValitse toiminto (1-3): ");
            
            String choice = reader.readLine();
            
            switch (choice) {
                case "1":
                    translateInteractive();
                    break;
                case "2":
                    translateFile();
                    break;
                case "3":
                    System.out.println("Sovellus suljetaan.");
                    return;
                default:
                    System.out.println("Virheellinen valinta. Kokeile uudelleen.");
            }
        }
    }
    
    /**
     * Käsittelee interaktiivisen tekstin kääntämisen.
     */
    private static void translateInteractive() throws IOException {
        try {
            System.out.println("\n----- TEKSTIN KÄÄNTÄMINEN -----");
            
            System.out.print("Syötä lähdekieli: ");
            String sourceLanguage = reader.readLine().trim();
            
            System.out.print("Syötä kohdekieli: ");
            String targetLanguage = reader.readLine().trim();
            
            System.out.print("Syötä toimialakonteksti (tai jätä tyhjäksi): ");
            String context = reader.readLine().trim();
            
            System.out.println("\nSyötä käännettävä teksti (tyhjä rivi lopettaa):");
            StringBuilder textBuilder = new StringBuilder();
            String line;
            
            // Luetaan tekstiä, kunnes käyttäjä antaa tyhjän rivin
            while (true) {
                line = reader.readLine();
                if (line == null || line.isEmpty()) {
                    break;
                }
                textBuilder.append(line).append("\n");
            }
            
            String sourceText = textBuilder.toString().trim();
            
            if (sourceText.isEmpty()) {
                System.out.println("Teksti on tyhjä. Käännöstä ei suoriteta.");
                return;
            }
            
            System.out.println("\nKäännetään tekstiä...");
            System.out.println("Lähetetään pyyntö Claude API:lle...");
            
            String translatedText = translationService.translateText(
                    sourceText, sourceLanguage, targetLanguage, context);
            
            System.out.println("\n----- KÄÄNNÖS -----");
            System.out.println(translatedText);
            
        } catch (Exception e) {
            System.err.println("Virhe käännöksessä: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Käsittelee tiedoston kääntämisen.
     */
    private static void translateFile() throws IOException {
        try {
            System.out.println("\n----- TIEDOSTON KÄÄNTÄMINEN -----");
            
            System.out.print("Syötä tiedoston polku: ");
            String filePath = reader.readLine().trim();
            
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                System.out.println("Tiedostoa ei löydy: " + filePath);
                return;
            }
            
            String sourceText = Files.readString(path);
            
            System.out.print("Syötä lähdekieli: ");
            String sourceLanguage = reader.readLine().trim();
            
            System.out.print("Syötä kohdekieli: ");
            String targetLanguage = reader.readLine().trim();
            
            System.out.print("Syötä toimialakonteksti (tai jätä tyhjäksi): ");
            String context = reader.readLine().trim();
            
            System.out.println("\nKäännetään tiedostoa...");
            System.out.println("Lähetetään pyyntö Claude API:lle...");
            
            String translatedText = translationService.translateText(
                    sourceText, sourceLanguage, targetLanguage, context);
            
            // Luodaan käännöstiedosto
            String originalFileName = path.getFileName().toString();
            String extension = "";
            int dotIndex = originalFileName.lastIndexOf('.');
            
            if (dotIndex > 0) {
                extension = originalFileName.substring(dotIndex);
                originalFileName = originalFileName.substring(0, dotIndex);
            }
            
            String translatedFileName = originalFileName + "_" + targetLanguage + extension;
            Path translatedPath = path.resolveSibling(translatedFileName);
            
            Files.writeString(translatedPath, translatedText);
            
            System.out.println("\nKäännös tallennettu tiedostoon: " + translatedPath.toString());
            
        } catch (Exception e) {
            System.err.println("Virhe tiedoston käännöksessä: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Testaa API-yhteyttä yksinkertaisella pyynnöllä
     */
    private static void testApiConnection(String apiKey) {
        try {
            System.out.println("Testataan API-yhteyttä...");
            
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
                
            Map<String, Object> testBody = new HashMap<>();
            testBody.put("model", "claude-3-7-sonnet-20250219");
            testBody.put("max_tokens", 100);
            testBody.put("messages", new Object[]{
                Map.of("role", "user", "content", "Say 'hello'")
            });
            
            String testJson = new ObjectMapper().writeValueAsString(testBody);
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.anthropic.com/v1/messages"))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(testJson))
                .build();
                
            HttpResponse<String> response = client.send(request, 
                HttpResponse.BodyHandlers.ofString());
                
            System.out.println("API-testi vastaus (status): " + response.statusCode());
            System.out.println("API-testi vastaus (ensimmäiset 200 merkkiä): " + 
                response.body().substring(0, Math.min(200, response.body().length())));
                
        } catch (Exception e) {
            System.err.println("API-testi epäonnistui: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Lataa sovelluksen asetukset tiedostosta.
     */
    private static Properties loadConfiguration() throws IOException {
        Properties config = new Properties();
        Path configPath = Paths.get(CONFIG_FILE);
        
        if (Files.exists(configPath)) {
            config.load(Files.newInputStream(configPath));
        }
        
        return config;
    }
    
    /**
     * Tallentaa sovelluksen asetukset tiedostoon.
     */
    private static void saveConfiguration(Properties config) throws IOException {
        Path configPath = Paths.get(CONFIG_FILE);
        config.store(Files.newOutputStream(configPath), "Claude API Configuration");
    }
}