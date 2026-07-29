package com.rcacopilot.workflow;

import com.google.gson.*;
import java.lang.reflect.Type;
import java.util.Map;

public class ActionDeserializer implements JsonDeserializer<Action> {

    @Override
    public Action deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();
        
        if (!jsonObject.has("type")) {
            throw new JsonParseException("Missing 'type' field in Action definition: " + json);
        }
        
        String type = jsonObject.get("type").getAsString();
        String id = jsonObject.get("id").getAsString();

        switch (type.toUpperCase()) {
            case "QUERY":
                String querySource = jsonObject.get("querySource").getAsString();
                String defaultNextActionId = jsonObject.has("defaultNextActionId") && !jsonObject.get("defaultNextActionId").isJsonNull()
                        ? jsonObject.get("defaultNextActionId").getAsString() : null;
                QueryAction queryAction = new QueryAction(id, querySource, defaultNextActionId);
                
                if (jsonObject.has("keywordRoutes")) {
                    JsonObject routes = jsonObject.getAsJsonObject("keywordRoutes");
                    for (Map.Entry<String, JsonElement> entry : routes.entrySet()) {
                        queryAction.addKeywordRoute(entry.getKey(), entry.getValue().getAsString());
                    }
                }
                return queryAction;

            case "SCOPE_SWITCH":
                String newScope = jsonObject.get("newScope").getAsString();
                String targetResource = jsonObject.has("targetResource") && !jsonObject.get("targetResource").isJsonNull()
                        ? jsonObject.get("targetResource").getAsString() : null;
                String nextActionId = jsonObject.has("nextActionId") && !jsonObject.get("nextActionId").isJsonNull()
                        ? jsonObject.get("nextActionId").getAsString() : null;
                return new ScopeSwitchAction(id, newScope, targetResource, nextActionId);

            case "MITIGATION":
                String recommendation = jsonObject.get("mitigationRecommendation").getAsString();
                return new MitigationAction(id, recommendation);

            default:
                throw new JsonParseException("Unknown action type: " + type);
        }
    }
}
