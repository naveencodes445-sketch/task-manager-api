package com.taskmanager;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.*;

public class TaskHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final DynamoDbClient dynamoDB = DynamoDbClient.builder()
            .region(Region.US_EAST_2)
            .build();

    private final String TABLE_NAME = "tasks";
    private final Gson gson = new Gson();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {

        String httpMethod = event.getHttpMethod();
        String path = event.getPath();

        try {
            // POST /tasks
            if (httpMethod.equals("POST") && path.equals("/tasks")) {
                return createTask(event);
            }
            // GET /tasks
            else if (httpMethod.equals("GET") && path.equals("/tasks")) {
                return getAllTasks();
            }
            // GET /tasks/{id}
            else if (httpMethod.equals("GET") && path.startsWith("/tasks/")) {
                String id = event.getPathParameters().get("id");
                return getTaskById(id);
            }
            // DELETE /tasks/{id}
            else if (httpMethod.equals("DELETE") && path.startsWith("/tasks/")) {
                String id = event.getPathParameters().get("id");
                return deleteTask(id);
            }
            else {
                return response(404, "{\"error\": \"Route not found\"}");
            }

        } catch (Exception e) {
            return response(500, "{\"error\": \"" + e.getMessage() + "\"}");
        }
    }

    // POST /tasks
    private APIGatewayProxyResponseEvent createTask(APIGatewayProxyRequestEvent event) {
        Map<String, String> body = gson.fromJson(event.getBody(), Map.class);

        if (body == null || !body.containsKey("title")) {
            return response(400, "{\"error\": \"title is required\"}");
        }

        String id = UUID.randomUUID().toString();
        String title = body.get("title");
        String status = body.getOrDefault("status", "pending");
        String createdAt = new Date().toString();

        Map<String, AttributeValue> item = new HashMap<>();
        item.put("id",        AttributeValue.builder().s(id).build());
        item.put("title",     AttributeValue.builder().s(title).build());
        item.put("status",    AttributeValue.builder().s(status).build());
        item.put("createdAt", AttributeValue.builder().s(createdAt).build());

        dynamoDB.putItem(PutItemRequest.builder()
                .tableName(TABLE_NAME)
                .item(item)
                .build());

        Map<String, String> result = new HashMap<>();
        result.put("id", id);
        result.put("title", title);
        result.put("status", status);
        result.put("createdAt", createdAt);

        return response(201, gson.toJson(result));
    }

    // GET /tasks
    private APIGatewayProxyResponseEvent getAllTasks() {
        ScanResponse scanResponse = dynamoDB.scan(ScanRequest.builder()
                .tableName(TABLE_NAME)
                .build());

        List<Map<String, String>> tasks = new ArrayList<>();
        for (Map<String, AttributeValue> item : scanResponse.items()) {
            Map<String, String> task = new HashMap<>();
            item.forEach((k, v) -> task.put(k, v.s()));
            tasks.add(task);
        }

        return response(200, gson.toJson(tasks));
    }

    // GET /tasks/{id}
    private APIGatewayProxyResponseEvent getTaskById(String id) {
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("id", AttributeValue.builder().s(id).build());

        GetItemResponse result = dynamoDB.getItem(GetItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(key)
                .build());

        if (!result.hasItem()) {
            return response(404, "{\"error\": \"Task not found\"}");
        }

        Map<String, String> task = new HashMap<>();
        result.item().forEach((k, v) -> task.put(k, v.s()));

        return response(200, gson.toJson(task));
    }

    // DELETE /tasks/{id}
    private APIGatewayProxyResponseEvent deleteTask(String id) {
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("id", AttributeValue.builder().s(id).build());

        dynamoDB.deleteItem(DeleteItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(key)
                .build());

        return response(200, "{\"message\": \"Task deleted successfully\"}");
    }

    // Helper
    private APIGatewayProxyResponseEvent response(int statusCode, String body) {
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(statusCode)
                .withHeaders(Map.of("Content-Type", "application/json"))
                .withBody(body);
    }
}
