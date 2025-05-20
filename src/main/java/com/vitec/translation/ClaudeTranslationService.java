package com.vitec.translation;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Palveluluokka, joka käsittelee kielenkäännöksiä Claude API:n avulla.
 */
public class ClaudeTranslationService {
    
    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private final String apiKey;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String model;
    
    /**
     * Alustaa kääntäjäpalvelun annetulla API-avaimella.
     * 
     * @param apiKey Claude API-avain
     * @param model  Käytettävä malli (esim. "claude-3-7-sonnet-20250219")
     */
    public ClaudeTranslationService(String apiKey, String model) {
        this.apiKey = apiKey;
        this.model = model;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * Kääntää annetun tekstin lähdekielestä kohdekieleen.
     * 
     * @param sourceText Käännettävä teksti
     * @param sourceLanguage Lähdekieli (esim. "suomi", "englanti")
     * @param targetLanguage Kohdekieli (esim. "ruotsi", "ranska")
     * @param context Lisäkonteksti kääntämistä varten (toimiala, tyyli, jne.)
     * @return Käännetty teksti
     * @throws IOException Jos API-kutsussa tapahtuu virhe
     * @throws InterruptedException Jos API-kutsu keskeytyy
     */
    public String translateText(String sourceText, String sourceLanguage, 
                               String targetLanguage, String context) 
                               throws IOException, InterruptedException {
        
        // Rakennetaan tehokas prompt kääntämistä varten
        String prompt = buildTranslationPrompt(sourceText, sourceLanguage, targetLanguage, context);
        
        // Luodaan API-pyyntö JSON
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("max_tokens", 1000);
        
        // API odottaa messages-arrayn
        Map<String, String> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", prompt);
        
        requestBody.put("messages", new Object[]{message});
        
        String requestBodyJson = objectMapper.writeValueAsString(requestBody);
        
        // Debug-tulostus
        System.out.println("Lähetetään pyyntö Claude API:lle...");
        System.out.println("URL: " + API_URL);
        System.out.println("API-avain (ensimmäiset 5 merkkiä): " + 
                apiKey.substring(0, Math.min(5, apiKey.length())) + "...");
        
        // Luodaan HTTP-pyyntö
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(requestBodyJson))
                .build();
        
        try {
            // Lähetetään pyyntö ja käsitellään vastaus
            System.out.println("Odotetaan vastausta...");
            HttpResponse<String> response = httpClient.send(request, 
                    HttpResponse.BodyHandlers.ofString());
            
            // Debug-tulostus
            System.out.println("API vastaus (status): " + response.statusCode());
            
            if (response.statusCode() == 200) {
                String responseBody = response.body();
                System.out.println("API vastaus vastaanotettu.");
                
                try {
                    // Jäsennä vastaus
                    Map<String, Object> responseMap = objectMapper.readValue(responseBody, Map.class);
                    
                    // Debug: näytä vastauksen rakenne
                    System.out.println("API vastauksen rakenne (avaimet): " + responseMap.keySet());
                    
                    // Tarkistetaan "content" avain
                    if (responseMap.containsKey("content")) {
                        List<?> contentList = (List<?>) responseMap.get("content");
                        System.out.println("Content listan koko: " + contentList.size());
                        
                        if (!contentList.isEmpty()) {
                            Object contentItem = contentList.get(0);
                            
                            // Debug: tarkistetaan contentItem:in tyyppi
                            System.out.println("Content item tyyppi: " + contentItem.getClass().getName());
                            
                            // Käsitellään eri tyypit
                            if (contentItem instanceof Map) {
                                Map<?, ?> contentMap = (Map<?, ?>) contentItem;
                                System.out.println("Content item avaimet: " + contentMap.keySet());
                                
                                if (contentMap.containsKey("text")) {
                                    Object textObj = contentMap.get("text");
                                    
                                    if (textObj instanceof String) {
                                        return (String) textObj;
                                    } else {
                                        System.out.println("Text ei ole String vaan: " + textObj.getClass().getName());
                                        return textObj.toString();
                                    }
                                }
                            } else if (contentItem instanceof String) {
                                // Jos content item on suoraan String
                                return (String) contentItem;
                            }
                        }
                    }
                    
                    // Jos emme voineet jäsentää vastausta odotetulla tavalla, näytetään raakadata
                    System.out.println("Raaka API vastaus: " + responseBody);
                    
                    // Yksinkertainen keino ekstraktoida vastaus JSON:sta, jos muu ei toimi
                    if (responseBody.contains("\"text\"")) {
                        int textStart = responseBody.indexOf("\"text\"") + 8; // "text":"
                        int textEnd = responseBody.indexOf("\"", textStart);
                        if (textStart > 0 && textEnd > textStart) {
                            return responseBody.substring(textStart, textEnd);
                        }
                    }
                    
                    return "Virhe vastauksen käsittelyssä. Tarkista API-kutsu ja vastausrakenne.";
                    
                } catch (Exception e) {
                    System.err.println("Virhe vastauksen käsittelyssä: " + e.getMessage());
                    System.out.println("Raaka API vastaus: " + responseBody);
                    e.printStackTrace();
                    return "Virhe vastauksen käsittelyssä: " + e.getMessage();
                }
            } else {
                System.err.println("API-kutsu epäonnistui: " + response.statusCode() + " - " + response.body());
                return "API-kutsu epäonnistui: " + response.statusCode() + " - " + response.body();
            }
        } catch (Exception e) {
            System.err.println("Virhe API-kutsussa: " + e.getMessage());
            e.printStackTrace();
            return "Virhe API-kutsussa: " + e.getMessage();
        }
    }
    
    /**
     * Rakentaa tehokkaan prompt-tekstin kääntämistä varten.
     */
    private String buildTranslationPrompt(String sourceText, String sourceLanguage, 
                                         String targetLanguage, String context) {
        StringBuilder promptBuilder = new StringBuilder();
        
        // Peruskäännösohje
        promptBuilder.append("Käännä seuraava teksti kielestä ")
                .append(sourceLanguage)
                .append(" kieleen ")
                .append(targetLanguage)
                .append(".\n\n");
        
        // Lisäkonteksti, jos annettu
        if (context != null && !context.isEmpty()) {
            promptBuilder.append("Konteksti: ").append(context).append("\n\n");
            promptBuilder.append("Käytä käännöksessä tähän toimialaan sopivaa terminologiaa. ");
            promptBuilder.append("Säilytä alkuperäisen tekstin sävy ja tyyli. ");
            promptBuilder.append("Kaikki erikoistermit tulee kääntää tarkasti.\n\n");
        }
        
        // Käännettävä teksti
        promptBuilder.append("Teksti käännettäväksi:\n").append(sourceText).append("\n\n");
        
        // Ohjeet vastauksen muodolle
        promptBuilder.append("Anna vain käännetty teksti vastauksena ilman ylimääräisiä selityksiä tai kommentteja.");
        
        return promptBuilder.toString();
    }
}