# Veyzo Media API

Veyzo is a Spring Boot-based REST API service built for processing video and media files asynchronously. It utilizes FFmpeg to perform I/O-intensive operations such as video trimming, compression, audio extraction, and GIF conversion without blocking the main server threads.

## Architecture & Technologies
- **Backend:** Java 21, Spring Boot 4
- **Database:** PostgreSQL, Spring Data JPA
- **Media Processing:** FFmpeg
- **Security:** Spring Security, JWT (HttpOnly, SameSite=Strict)
- **Deployment:** Docker & Docker Compose

## Core Features
- **Asynchronous Processing:** Heavy FFmpeg tasks are isolated in background threads to ensure API responsiveness.
- **Secure File Uploads:** Validates incoming files using "Magic Bytes" checks rather than relying solely on file extensions, preventing malicious or fake file uploads.
- **Centralized Exception Handling:** Client-facing errors are filtered via a `GlobalExceptionHandler`. Internal system errors, database exceptions, and stack traces are never leaked to the client.
- **Authentication:** Stateless, CSRF-protected, and HttpOnly cookie-based JWT session management.
- **Batch Operations:** Supports server-side ZIP compression, allowing users to download multiple processed files in a single request.

## Installation

### Prerequisites
- Java 21+
- PostgreSQL
- FFmpeg (must be installed and added to the system `PATH`)

### Running the Application
1. Configure your `application.properties` or `.env` file:
   ```properties
   spring.datasource.url=jdbc:postgresql://localhost:5432/veyzo_db
   spring.datasource.username=your_db_user
   spring.datasource.password=your_db_password
   
   application.security.jwt.secret-key=your_secret_key_here
