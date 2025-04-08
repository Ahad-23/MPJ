
# Meeting Summary Application

## Overview

A full-stack application for summarizing meeting content with:

- **Backend**: Javalin (Java) for API services and MongoDB integration
- **Frontend**: Vaadin (Java) for the web interface

## Project Structure

```
meeting-summary-app/
├── backend/               # Javalin backend
│   ├── src/               # Source code
│   ├── pom.xml            # Maven configuration
│   └── .env.example       # Environment variables template
├── frontend/              # Vaadin frontend
│   ├── src/               # Source code  
│   └── pom.xml            # Maven configuration
├── .gitignore             # Global ignore rules
└── README.md              # This file
```

## Features

- Video/Audio meeting summarization
- Text transcript processing
- Timeline extraction
- Meeting history browser
- MongoDB data persistence

## Prerequisites

- Java 17+
- Maven 3.6+
- MongoDB (local or Atlas)

## Setup Instructions

### 1. Clone the Repository

```bash
git clone https://github.com/Ahad-23/MPJ.git
cd MPJ
```

### 2. Backend Configuration

```bash
cd backend
cp .env.example .env
```

Edit `.env` with your MongoDB credentials:

```
MONGO_URI=mongodb://localhost:27017
FLASK_AI_URL=http://localhost:5000  # If using AI service
```

### 3. Frontend Configuration

```bash
cd ../frontend
```

### 4. Install Dependencies

```bash
mvn clean install
```

## Running the Application

### Development Mode

Run in separate terminals:

**Backend**:

```bash
cd backend
mvn clean compile exec:java
```

**Frontend**:

```bash
cd frontend  
mvn spring-boot:run
```

Access the application at: `http://localhost:8080`

### Production Build

```bash
cd frontend
mvn clean package -Pproduction
java -jar target/vaadin-frontend-1.0.war
```

## API Endpoints

| Endpoint        | Method | Description              |
|----------------|--------|--------------------------|
| /video-summary | POST   | Process video meetings   |
| /audio-summary | POST   | Process audio meetings   |
| /text-summary  | POST   | Process text transcripts |
| /summaries     | GET    | Retrieve saved summaries |

## Environment Variables

**Backend**

- `MONGO_URI`: MongoDB connection string
- `FLASK_AI_URL`: URL of AI processing service (if used)

## Deployment

### Docker

```bash
docker-compose up --build
```

### Cloud Deployment

- Build frontend production bundle
- Deploy backend as a service
- Configure reverse proxy

## Development Tips

### Backend

- Use Postman to test API endpoints
- Enable debug logging with `-Dorg.slf4j.simpleLogger.defaultLogLevel=debug`

### Frontend

- Live reload during development
- Access Vaadin debug tools at `http://localhost:8080/?debug`

## Troubleshooting

**Port Conflicts**:

- Backend runs on 5001
- Frontend runs on 8080

**Database Issues**:

- Verify MongoDB connection string
- Check if MongoDB service is running

**Build Errors**:

- Clean and rebuild: `mvn clean install -U`
- Verify Java version matches `pom.xml`

## Contributing

- Fork the repository
- Create a feature branch
- Submit a pull request

## License

MIT License

> **Note**: For detailed API documentation, see `API_REFERENCE.md`
