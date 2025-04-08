package org.example;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Objects;

import org.apache.commons.io.IOUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.router.Route;

import elemental.json.Json;

@Route("")
public class MainView extends VerticalLayout {
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final String BACKEND_URL = "http://localhost:5001";
    private final Grid<MeetingSummary> summaryGrid = new Grid<>(MeetingSummary.class);

    public MainView() {
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        H1 header = new H1("Meeting Summary App");
        add(header);

        // Create tabs
        Tab videoTab = new Tab("Video Summary");
        Tab audioTab = new Tab("Audio Summary");
        Tab textTab = new Tab("Text Summary");
        Tab viewTab = new Tab("View Summaries");
        Tabs tabs = new Tabs(videoTab, audioTab, textTab, viewTab);
        tabs.setWidthFull();

        // Create content for each tab
        VerticalLayout videoContent = createVideoUploadForm();
        VerticalLayout audioContent = createAudioUploadForm();
        VerticalLayout textContent = createTextForm();
        VerticalLayout viewContent = createSummaryView();

        // Show only the selected tab's content
        tabs.addSelectedChangeListener(event -> {
            videoContent.setVisible(tabs.getSelectedTab() == videoTab);
            audioContent.setVisible(tabs.getSelectedTab() == audioTab);
            textContent.setVisible(tabs.getSelectedTab() == textTab);
            viewContent.setVisible(tabs.getSelectedTab() == viewTab);
            
            if (tabs.getSelectedTab() == viewTab) {
                refreshSummaries();
            }
        });

        add(tabs);
        add(videoContent, audioContent, textContent, viewContent);

        // Initially show video tab
        videoContent.setVisible(true);
        audioContent.setVisible(false);
        textContent.setVisible(false);
        viewContent.setVisible(false);
    }

    private VerticalLayout createVideoUploadForm() {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);
        layout.setSpacing(true);

        DatePicker datePicker = new DatePicker("Meeting Date");
        datePicker.setRequiredIndicatorVisible(true);

        TextField titleField = new TextField("Meeting Title");
        titleField.setRequiredIndicatorVisible(true);

        TextArea attendeesArea = new TextArea("Attendees");
        attendeesArea.setPlaceholder("Enter one attendee per line");
        attendeesArea.setRequiredIndicatorVisible(true);

        MemoryBuffer buffer = new MemoryBuffer();
        Upload upload = new Upload(buffer);
        upload.setAcceptedFileTypes(".mp4", ".mov", ".avi");
        upload.setDropLabel(new Span("Drop video file here or click to browse"));
        upload.setUploadButton(new Button("Upload Video"));

        Button submitButton = new Button("Submit Summary");
        submitButton.setEnabled(false);

        // Store filename when upload succeeds
        final String[] videoFileName = new String[1];
        upload.addSucceededListener(event -> {
            videoFileName[0] = event.getFileName();
            submitButton.setEnabled(true);
            Notification.show("Video ready for upload");
        });

        submitButton.addClickListener(event -> {
            if (datePicker.isEmpty() || titleField.isEmpty() || attendeesArea.isEmpty()) {
                Notification.show("Please fill all required fields", 3000, Notification.Position.MIDDLE);
                return;
            }

            try {
                InputStream fileData = buffer.getInputStream();
                byte[] bytes = IOUtils.toByteArray(fileData);

                String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                PrintWriter writer = new PrintWriter(new OutputStreamWriter(byteArrayOutputStream, StandardCharsets.UTF_8), true);

                // Add file part
                writer.append("--").append(boundary).append("\r\n");
                writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"")
                     .append(videoFileName[0]).append("\"\r\n");
                writer.append("Content-Type: video/mp4\r\n\r\n");
                writer.flush();
                byteArrayOutputStream.write(bytes);
                writer.append("\r\n").flush();

                // Add form fields
                addFormField(writer, boundary, "date", Objects.requireNonNull(datePicker.getValue()).format(DateTimeFormatter.ISO_DATE));
                addFormField(writer, boundary, "meeting_title", titleField.getValue());
                addFormField(writer, boundary, "attendees", attendeesArea.getValue());

                // End boundary
                writer.append("--").append(boundary).append("--\r\n");
                writer.close();

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(BACKEND_URL + "/video-summary"))
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(byteArrayOutputStream.toByteArray()))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    Notification.show("Summary created successfully!");
                    datePicker.clear();
                    titleField.clear();
                    attendeesArea.clear();
                    submitButton.setEnabled(false);
                } else {
                    Notification.show("Error: " + response.body(), 5000, Notification.Position.MIDDLE);
                }
            } catch (Exception e) {
                Notification.show("Error: " + e.getMessage(), 5000, Notification.Position.MIDDLE);
                e.printStackTrace();
            }
        });

        layout.add(datePicker, titleField, attendeesArea, upload, submitButton);
        return layout;
    }

    private VerticalLayout createAudioUploadForm() {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);
        layout.setSpacing(true);

        DatePicker datePicker = new DatePicker("Meeting Date");
        datePicker.setRequiredIndicatorVisible(true);

        TextField titleField = new TextField("Meeting Title");
        titleField.setRequiredIndicatorVisible(true);

        TextArea attendeesArea = new TextArea("Attendees");
        attendeesArea.setPlaceholder("Enter one attendee per line");
        attendeesArea.setRequiredIndicatorVisible(true);

        MemoryBuffer buffer = new MemoryBuffer();
        Upload upload = new Upload(buffer);
        upload.setAcceptedFileTypes(".wav", ".mp3", ".ogg");
        upload.setDropLabel(new Span("Drop audio file here or click to browse"));
        upload.setUploadButton(new Button("Upload Audio"));

        Button submitButton = new Button("Submit Summary");
        submitButton.setEnabled(false);

        // Store filename when upload succeeds
        final String[] audioFileName = new String[1];
        upload.addSucceededListener(event -> {
            audioFileName[0] = event.getFileName();
            submitButton.setEnabled(true);
            Notification.show("Audio ready for upload");
        });

        submitButton.addClickListener(event -> {
            if (datePicker.isEmpty() || titleField.isEmpty() || attendeesArea.isEmpty()) {
                Notification.show("Please fill all required fields", 3000, Notification.Position.MIDDLE);
                return;
            }

            try {
                InputStream fileData = buffer.getInputStream();
                byte[] bytes = IOUtils.toByteArray(fileData);

                String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                PrintWriter writer = new PrintWriter(new OutputStreamWriter(byteArrayOutputStream, StandardCharsets.UTF_8), true);

                // Add file part
                writer.append("--").append(boundary).append("\r\n");
                writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"")
                     .append(audioFileName[0]).append("\"\r\n");
                writer.append("Content-Type: audio/wav\r\n\r\n");
                writer.flush();
                byteArrayOutputStream.write(bytes);
                writer.append("\r\n").flush();

                // Add form fields
                addFormField(writer, boundary, "date", Objects.requireNonNull(datePicker.getValue()).format(DateTimeFormatter.ISO_DATE));
                addFormField(writer, boundary, "meeting_title", titleField.getValue());
                addFormField(writer, boundary, "attendees", attendeesArea.getValue());

                // End boundary
                writer.append("--").append(boundary).append("--\r\n");
                writer.close();

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(BACKEND_URL + "/audio-summary"))
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(byteArrayOutputStream.toByteArray()))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    Notification.show("Summary created successfully!");
                    datePicker.clear();
                    titleField.clear();
                    attendeesArea.clear();
                    submitButton.setEnabled(false);
                } else {
                    Notification.show("Error: " + response.body(), 5000, Notification.Position.MIDDLE);
                }
            } catch (Exception e) {
                Notification.show("Error: " + e.getMessage(), 5000, Notification.Position.MIDDLE);
                e.printStackTrace();
            }
        });

        layout.add(datePicker, titleField, attendeesArea, upload, submitButton);
        return layout;
    }

    private VerticalLayout createTextForm() {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);
        layout.setSpacing(true);

        DatePicker datePicker = new DatePicker("Meeting Date");
        datePicker.setRequiredIndicatorVisible(true);

        TextField titleField = new TextField("Meeting Title");
        titleField.setRequiredIndicatorVisible(true);

        TextArea attendeesArea = new TextArea("Attendees");
        attendeesArea.setPlaceholder("Enter one attendee per line");
        attendeesArea.setRequiredIndicatorVisible(true);

        TextArea transcriptArea = new TextArea("Meeting Transcript");
        transcriptArea.setPlaceholder("Paste the meeting transcript here");
        transcriptArea.setRequiredIndicatorVisible(true);
        transcriptArea.setHeight("200px");

        Button submitButton = new Button("Generate Summary");

        submitButton.addClickListener(event -> {
            if (datePicker.isEmpty() || titleField.isEmpty() || attendeesArea.isEmpty() || transcriptArea.isEmpty()) {
                Notification.show("Please fill all required fields", 3000, Notification.Position.MIDDLE);
                return;
            }

            try {
                // Create JSON object properly
                elemental.json.JsonObject jsonObject = Json.createObject();
                jsonObject.put("text", transcriptArea.getValue());
                jsonObject.put("date", Objects.requireNonNull(datePicker.getValue()).format(DateTimeFormatter.ISO_DATE));
                jsonObject.put("meeting_title", titleField.getValue());
                jsonObject.put("attendees", attendeesArea.getValue());
                
                String requestBody = jsonObject.toJson();

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(BACKEND_URL + "/text-summary"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    Notification.show("Summary created successfully!");
                    datePicker.clear();
                    titleField.clear();
                    attendeesArea.clear();
                    transcriptArea.clear();
                } else {
                    Notification.show("Error: " + response.body(), 5000, Notification.Position.MIDDLE);
                }
            } catch (Exception e) {
                Notification.show("Error: " + e.getMessage(), 5000, Notification.Position.MIDDLE);
                e.printStackTrace();
            }
        });

        layout.add(datePicker);
        layout.add(titleField);
        layout.add(attendeesArea);
        layout.add(transcriptArea);
        layout.add(submitButton);
        
        return layout;
    }

    private VerticalLayout createSummaryView() {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);
        layout.setSpacing(true);
        layout.setSizeFull();

        // Search controls
        DatePicker startDate = new DatePicker("Start Date");
        DatePicker endDate = new DatePicker("End Date");
        TextField titleSearch = new TextField("Meeting Title");
        Button searchButton = new Button("Search");
        Button clearButton = new Button("Clear");

        // Configure grid
        summaryGrid.removeAllColumns();
        summaryGrid.addColumn(MeetingSummary::getMeetingTitle).setHeader("Title").setAutoWidth(true);
        summaryGrid.addColumn(MeetingSummary::getMeetingDate).setHeader("Date").setAutoWidth(true);
        summaryGrid.addColumn(MeetingSummary::getSummary).setHeader("Summary").setAutoWidth(true);
        summaryGrid.setHeight("500px");
        summaryGrid.setWidthFull();

        // Create search controls layout
        HorizontalLayout searchControls = new HorizontalLayout(
            startDate,
            endDate,
            titleSearch,
            searchButton,
            clearButton
        );
        searchControls.setSpacing(true);

        // Add components to main layout
        layout.add(searchControls);
        layout.add(summaryGrid);

        // Search button handler
        searchButton.addClickListener(event -> {
            try {
                StringBuilder url = new StringBuilder(BACKEND_URL + "/summaries?");
                
                if (!titleSearch.isEmpty()) {
                    url.append("search_method=By Meeting Title&title=")
                       .append(URLEncoder.encode(titleSearch.getValue(), StandardCharsets.UTF_8));
                } else if (!startDate.isEmpty() && !endDate.isEmpty()) {
                    url.append("search_method=By Date Range")
                       .append("&start_date=").append(Objects.requireNonNull(startDate.getValue()).format(DateTimeFormatter.ISO_DATE))
                       .append("&end_date=").append(Objects.requireNonNull(endDate.getValue()).format(DateTimeFormatter.ISO_DATE));
                } else {
                    url.append("search_method=By Date Range")
                       .append("&start_date=").append(LocalDate.now().minusMonths(1).format(DateTimeFormatter.ISO_DATE))
                       .append("&end_date=").append(LocalDate.now().format(DateTimeFormatter.ISO_DATE));
                }

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url.toString()))
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    MeetingSummary[] summaries = new ObjectMapper().readValue(response.body(), MeetingSummary[].class);
                    summaryGrid.setItems(Arrays.asList(summaries));
                } else {
                    Notification.show("Error fetching summaries: " + response.body(), 5000, Notification.Position.MIDDLE);
                }
            } catch (Exception e) {
                Notification.show("Error: " + e.getMessage(), 5000, Notification.Position.MIDDLE);
                e.printStackTrace();
            }
        });

        // Clear button handler
        clearButton.addClickListener(event -> {
            startDate.clear();
            endDate.clear();
            titleSearch.clear();
            refreshSummaries();
        });

        return layout;
    }

    private void refreshSummaries() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BACKEND_URL + "/summaries"))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                MeetingSummary[] summaries = new ObjectMapper().readValue(response.body(), MeetingSummary[].class);
                summaryGrid.setItems(Arrays.asList(summaries));
            } else {
                Notification.show("Error refreshing summaries: " + response.body(), 5000, Notification.Position.MIDDLE);
            }
        } catch (Exception e) {
            Notification.show("Error refreshing summaries: " + e.getMessage(), 5000, Notification.Position.MIDDLE);
            e.printStackTrace();
        }
    }

    private void addFormField(PrintWriter writer, String boundary, String name, String value) {
        writer.append("--").append(boundary).append("\r\n");
        writer.append("Content-Disposition: form-data; name=\"").append(name).append("\"\r\n\r\n");
        writer.append(value).append("\r\n").flush();
    }

    public static class MeetingSummary {
        private String id;
        private String meetingTitle;
        private String meetingDate;
        private String meetingDay;
        private String transcription;
        private String summary;
        private String[] attendees;
        private String[] timelines;

        // Getters and setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getMeetingTitle() { return meetingTitle; }
        public void setMeetingTitle(String meetingTitle) { this.meetingTitle = meetingTitle; }
        public String getMeetingDate() { return meetingDate; }
        public void setMeetingDate(String meetingDate) { this.meetingDate = meetingDate; }
        public String getMeetingDay() { return meetingDay; }
        public void setMeetingDay(String meetingDay) { this.meetingDay = meetingDay; }
        public String getTranscription() { return transcription; }
        public void setTranscription(String transcription) { this.transcription = transcription; }
        public String getSummary() { return summary; }
        public void setSummary(String summary) { this.summary = summary; }
        public String[] getAttendees() { return attendees; }
        public void setAttendees(String[] attendees) { this.attendees = attendees; }
        public String[] getTimelines() { return timelines; }
        public void setTimelines(String[] timelines) { this.timelines = timelines; }
    }
}