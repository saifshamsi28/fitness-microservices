package com.saif.fitness.aiservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saif.fitness.aiservice.model.Activity;
import com.saif.fitness.aiservice.model.Recommendation;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Service
@Slf4j
@AllArgsConstructor
public class ActivityAIService {

    private final OpenRouterAIService openRouterAIService;

    public Mono<Recommendation> generateRecommendation(Activity activity){

        String prompt = createPromptForAiRecommendation(activity);

        return openRouterAIService.getRecommendationsAsync(prompt)
                .map(aiResponse -> processAiResponse(activity, aiResponse))
                .onErrorResume(e -> {
                    log.error("AI failed, returning fallback", e);
                    return Mono.just(createFallbackRecommendation(activity));
                });
    }


    private Recommendation processAiResponse(Activity activity, String aiResponse) {
        log.info("Received ai response in processAiResponse: {}", aiResponse);

        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(aiResponse);

            // ✅ Correct path for OpenRouter
            String rawContent = rootNode.path("choices")
                    .get(0)
                    .path("message")
                    .path("content")
                    .asText();

            log.info("Raw AI content: {}", rawContent);

            // Remove ```json fences if present
            String cleanedJson = rawContent
                    .replaceAll("```json", "")
                    .replaceAll("```", "")
                    .trim();

            log.info("Extracted inner JSON: {}", cleanedJson);

            // Now parse the actual recommendation JSON
            JsonNode analysisJson = mapper.readTree(cleanedJson);
            JsonNode analysisNode = analysisJson.path("analysis");

            StringBuilder fullAnalysis = new StringBuilder();
            addAnalysisSection(fullAnalysis, analysisNode, "overall", "Overall: ");
            addAnalysisSection(fullAnalysis, analysisNode, "pace", "Pace: ");
            addAnalysisSection(fullAnalysis, analysisNode, "heartRate", "Heart Rate: ");
            addAnalysisSection(fullAnalysis, analysisNode, "caloriesBurned", "Calories Burned: ");

            List<String> improvements = extractImprovements(analysisJson.path("improvements"));
            List<String> suggestions = extractSuggestions(analysisJson.path("suggestions"));
            List<String> safetyGuidelines = extractSafetyGuidelines(analysisJson.path("safety"));

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

        } catch (Exception e) {
            log.error("Parsing failed, returning fallback", e);
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
        if(!analysisNode.path(key).isMissingNode()){
            fullAnalysis.append(prefix)
                    .append(analysisNode.path(key).asText())
                    .append("\n\n");
        }
    }


    private String createPromptForAiRecommendation(Activity activity) {
        return String.format("""
        Analyze this fitness activity and provide detailed recommendations in the following EXACT JSON format. You MUST return ONLY valid JSON.
        No explanations. No markdown. No ```json blocks.

        EXACT required format:

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

        NOW ANALYZE THIS ACTIVITY:

        Activity Type: %s
        Duration: %d minutes
        Calories Burned: %d
        Additional Metrics: %s

        Provide:
        - Clear performance analysis
        - 2–3 concrete improvements
        - 2–3 next-workout suggestions
        - 2–3 safety guidelines

        Again: return ONLY the JSON above.
       \s""",
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
