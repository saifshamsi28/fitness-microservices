package com.saif.fitness.aiservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saif.fitness.aiservice.model.Activity;
import com.saif.fitness.aiservice.model.Recommendation;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Service
@Slf4j
@AllArgsConstructor
public class ActivityAIService {

    private final GeminiService geminiService;

    public Recommendation generateRecommendation(Activity activity){
//        try {
            String prompt = createPromptForAiRecommendation(activity);
            String aiResponse = geminiService.getRecommendations(prompt);
            return processAiResponse(activity, aiResponse);
//        } catch (Exception e) {
//            log.error("AI processing failed, skipping message to avoid retry loop", e);
//        }
    }

    private Recommendation processAiResponse(Activity activity, String aiResponse) {
        try {
            ObjectMapper mapper=new ObjectMapper();
            JsonNode rootNode=mapper.readTree(aiResponse);
            JsonNode textNode=rootNode.path("candidates")
                    .get(0)
                    .path("content")
                    .get("parts")
                    .get(0)
                    .path("text");

            String jsonContent=textNode.asText()
                    .replaceAll("```json\\n","")
                    .replaceAll("\\n```","")
                    .trim();

            JsonNode analysisJson=mapper.readTree(jsonContent);
            JsonNode analysisNode=analysisJson.path("analysis");

            StringBuilder fullAnalysis=new StringBuilder();
            addAnalysisSection(fullAnalysis, analysisNode, "overall", "Overall:");
            addAnalysisSection(fullAnalysis, analysisNode, "pace", "Pace:");
            addAnalysisSection(fullAnalysis, analysisNode, "heartRate", "Heart Rate:");
            addAnalysisSection(fullAnalysis, analysisNode, "caloriesBurned", "Calories Burned:");

            List<String> improvements= extractImprovements(analysisNode.path("improvements"));
            List<String> suggestions= extractSuggestions(analysisNode.path("suggestions"));
            List<String> safetyGuidelines= extractSafetyGuidelines(analysisNode.path("safety"));

            return Recommendation.builder()
                    .activityId(activity.getId())
                    .userId(activity.getUserId())
                    .activityType(activity.getActivityType().toString())
                    .recommendation(fullAnalysis.toString().trim())
                    .improvements(improvements)
                    .suggestions(suggestions)
                    .safety(safetyGuidelines)
                    .createdAt(LocalDateTime.now())
                    .build();

        }catch (Exception e){
            e.printStackTrace();
            return createFallbackRecommendation(activity);
        }
    }


    private List<String> extractSafetyGuidelines(JsonNode safetyNode) {
        List<String> safetyGuidelines =new ArrayList<>();
        if (safetyNode.isArray()){
            safetyNode.forEach(item-> safetyGuidelines.add(item.asText()));
        }
        return safetyGuidelines.isEmpty() ?
                Collections.singletonList("Follow the basic Safety Guidelines") :
                safetyGuidelines;
    }

    private List<String> extractSuggestions(JsonNode suggestionsNode) {
        List<String> suggestions =new ArrayList<>();
        if (suggestionsNode.isArray()){
            suggestionsNode.forEach(suggestion ->{
                String workout = suggestion.path("workout").asText();
                String description = suggestion.path("description").asText();
                suggestions.add(String.format("%s: %s", workout, description));
            });
        }
        return suggestions.isEmpty() ?
                Collections.singletonList("No specific suggestion provided") :
                suggestions;
    }

    private List<String> extractImprovements(JsonNode improvementNode) {
        List<String> improvements=new ArrayList<>();
        if (improvementNode.isArray()){
            improvementNode.forEach(improvement->{
                String area=improvement.path("area").asText();
                String recommendation=improvement.path("recommendation").asText();
                improvements.add(String.format("%s: %s",area, recommendation));
            });
        }
        return improvements.isEmpty() ?
                Collections.singletonList("No specific improvement needed") :
                improvements;

    }

    private void addAnalysisSection(StringBuilder fullAnalysis, JsonNode analysisNode, String key, String prefix) {
        if(analysisNode.path(key).isMissingNode()){
            fullAnalysis.append(prefix)
                    .append(analysisNode.path(key).asText())
                    .append("\n\n");
        }
    }


    private String createPromptForAiRecommendation(Activity activity) {
        return String.format("""
        Analyze this fitness activity and provide detailed recommendations in the following EXACT JSON format:
        {
          "analysis": {
            "overall": "Overall analysis here",
            "pace": "Pace analysis here",
            "heartRate": "Heart rate analysis here",
            "caloriesBurned": "Calories analysis here"
          },
          "improvements": [
            {
              "area": "Area name",
              "recommendation": "Detailed recommendation"
            }
          ],
          "suggestions": [
            {
              "workout": "Workout name",
              "description": "Detailed workout description"
            }
          ],
          "safety": [
            "Safety point 1",
            "Safety point 2"
          ]
        }

        Analyze this activity:
        Activity Type: %s
        Duration: %d minutes
        Calories Burned: %d
        Additional Metrics: %s
        
        Provide detailed analysis focusing on performance, improvements, next workout suggestions, and safety guidelines.
        Ensure the response follows the EXACT JSON format shown above.
        """,
                activity.getActivityType(),
                activity.getDuration(),
                activity.getCaloriesBurned(),
                activity.getAdditionalMetrics()
        );
    }

    public Recommendation createFallbackRecommendation(Activity activity) {
        return Recommendation.builder()
                .activityId(activity.getId())
                .userId(activity.getUserId())
                .activityType(activity.getActivityType().toString())
                .recommendation("Unable to generate detailed recommendations")
                .improvements(Collections.singletonList("Continue with your daily routines"))
                .suggestions(Collections.singletonList("Consider consulting a fitness consultant"))
                .safety(Arrays.asList(
                        "Always warm-up before exercise",
                        "Stay hydrated",
                        "Listen to your body"
                ))
                .createdAt(LocalDateTime.now())
                .build();
    }
}
