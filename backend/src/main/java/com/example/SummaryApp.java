package com.example;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.UploadedFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.bson.types.ObjectId;
import io.github.cdimascio.dotenv.Dotenv;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;

public class SummaryApp {
    private static final ObjectMapper mapper = new ObjectMapper();
    private static MongoCollection<Document> collection;
    private static final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final HttpClient httpClient = HttpClient.newHttpClient();
    private static final String FLASK_AI_URL = "http://localhost:5000";

    public static void main(String[] args) {
        // Load environment variables
        Dotenv dotenv = Dotenv.configure().load();
        String mongoUri = dotenv.get("MONGO_URI", "mongodb://localhost:27017");

        // Initialize MongoDB
        MongoClient mongoClient = MongoClients.create(mongoUri);
        MongoDatabase database = mongoClient.getDatabase("Summary_history");
        collection = database.getCollection("details");

        // Configure Javalin
        Javalin app = Javalin.create(config -> {
            config.plugins.enableCors(cors -> {
                cors.add(corsConfig -> {
                    corsConfig.anyHost();
                });
            });
        }).start(5001);  // Different port from Flask

        // API Endpoints
        app.post("/video-summary", ctx -> handleMedia(ctx, "video"));
        app.post("/audio-summary", ctx -> handleMedia(ctx, "audio"));
        app.post("/text-summary", SummaryApp::handleText);
        app.get("/summaries", SummaryApp::getSummaries);
    }

    private static void handleMedia(Context ctx, String mediaType) {
        Path tempFile = null;
        try {
            UploadedFile file = ctx.uploadedFile("file");
            if (file == null) {
                ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "No file provided"));
                return;
            }

            // Create temp file
            tempFile = Files.createTempFile("summary-", mediaType.equals("video") ? ".mp4" : ".wav");
            try (InputStream inputStream = file.content()) {
                Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }

            // Build multipart form data
            String boundary = "----JavaBoundary" + System.currentTimeMillis();
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(byteArrayOutputStream));

            // Add file part
            writer.append("--").append(boundary).append("\r\n");
            writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"").append(tempFile.getFileName().toString()).append("\"\r\n");
            writer.append("Content-Type: ").append(file.contentType()).append("\r\n\r\n");
            writer.flush();
            byteArrayOutputStream.write(Files.readAllBytes(tempFile));
            writer.append("\r\n").flush();

            // Add form fields
            String[] formFields = {"date", "meeting_title", "attendees"};
            for (String field : formFields) {
                String value = ctx.formParam(field);
                if (value != null) {
                    writer.append("--").append(boundary).append("\r\n");
                    writer.append("Content-Disposition: form-data; name=\"").append(field).append("\"\r\n\r\n");
                    writer.append(value).append("\r\n").flush();
                }
            }

            // End boundary
            writer.append("--").append(boundary).append("--\r\n");
            writer.close();

            // Call Flask AI service
            String flaskEndpoint = FLASK_AI_URL + (mediaType.equals("video") ? "/video-summary" : "/audio-summary");
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(flaskEndpoint))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(byteArrayOutputStream.toByteArray()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("AI service error: " + response.body());
            }

            // Parse and store response
            Map<String, Object> aiResponse = mapper.readValue(response.body(), 
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            storeMeetingData(ctx, aiResponse);
            ctx.json(aiResponse);

        } catch (Exception e) {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(Map.of("error", e.getMessage()));
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException e) {
                    System.err.println("Failed to delete temp file: " + e.getMessage());
                }
            }
        }
    }

    private static void handleText(Context ctx) {
        try {
            Map<String, String> requestData = mapper.readValue(ctx.body(), 
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {});

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(FLASK_AI_URL + "/text-summary"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(ctx.body()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("AI service error: " + response.body());
            }

            Map<String, Object> aiResponse = mapper.readValue(response.body(),
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            storeMeetingData(ctx, aiResponse);
            ctx.json(aiResponse);

        } catch (Exception e) {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(Map.of("error", e.getMessage()));
        }
    }

    private static void storeMeetingData(Context ctx, Map<String, Object> aiResponse) {
        try {
            String meetingDateStr = ctx.formParam("date") != null ? 
                    ctx.formParam("date") : (String) aiResponse.get("date");
            LocalDate meetingDate = LocalDate.parse(meetingDateStr, dateFormatter);
            String meetingDay = meetingDate.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH);

            Document meetingData = new Document()
                    .append("meeting_title", ctx.formParam("meeting_title") != null ? 
                            ctx.formParam("meeting_title") : aiResponse.get("meeting_title"))
                    .append("meeting_date", meetingDateStr)
                    .append("meeting_day", meetingDay)
                    .append("transcription", aiResponse.get("transcription"))
                    .append("summary", aiResponse.get("summary"))
                    .append("attendees", Arrays.asList(
                            ctx.formParam("attendees") != null ? 
                            ctx.formParam("attendees").split("\n") : 
                            ((String) aiResponse.getOrDefault("attendees", "")).split("\n")))
                    .append("timelines", aiResponse.get("timelines"));

            collection.insertOne(meetingData);

        } catch (Exception e) {
            throw new RuntimeException("Failed to store meeting data: " + e.getMessage());
        }
    }

    private static void getSummaries(Context ctx) {
        try {
            Document query = new Document();
            String searchMethod = ctx.queryParam("search_method");

            if ("By Date Range".equals(searchMethod)) {
                String startDate = ctx.queryParam("start_date");
                String endDate = ctx.queryParam("end_date");
                if (startDate != null && endDate != null) {
                    query.append("meeting_date", new Document()
                            .append("$gte", startDate)
                            .append("$lte", endDate));
                }
            } else if ("By Meeting Title".equals(searchMethod)) {
                String title = ctx.queryParam("title");
                if (title != null) {
                    query.append("meeting_title", new Document()
                            .append("$regex", title)
                            .append("$options", "i"));
                }
            }

            List<Document> results = new ArrayList<>();
            collection.find(query)
                    .sort(new Document("meeting_date", -1))
                    .forEach(results::add);

            results.forEach(doc -> doc.put("_id", doc.getObjectId("_id").toString()));
            ctx.json(results);

        } catch (Exception e) {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(Map.of("error", e.getMessage()));
        }
    }
}