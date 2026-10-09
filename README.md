# SwiftRoute Courier Management

SwiftRoute is a courier operations portal backed by a Spring Boot REST API and MySQL. The feature-complete portal is served by Spring Boot, so its frontend and `/api/*` requests use one origin.

## Run the Single Portal

Start MySQL, configure the database environment variables below, then run `com.courier.CourierApplication` from Eclipse as a Spring Boot application. Open:

http://localhost:8080

Alternatively, run from `backend/` with Maven:

```powershell
mvn spring-boot:run
```

The portal served from `backend/src/main/resources/static/` is the integrated application and retains the existing operational menus and workflows.

## Deploy the Portal to Vercel

The static portal can be deployed to Vercel from the project root using the included `vercel.json`. Its `/api/*` requests are forwarded by `api/[...path].js` to a separately hosted Spring Boot backend. Deploy the backend and MySQL database to a Java-capable hosting provider first, then set the Vercel project environment variable `BACKEND_URL` to the backend's base URL (for example, `https://your-backend.example.com`) for each deployment environment. Do not include `/api` at the end of this value.

In Vercel, import this repository, keep the project root as the Root Directory, and deploy. The backend must be publicly reachable over HTTPS and configured with its database connection environment variables. Vercel hosts the frontend and API proxy; it does not run this Spring Boot application or host its MySQL database.

## Deploy the Backend to Render

The repository includes a Render Blueprint in `render.yaml` and a Java 21 Docker build in `backend/Dockerfile`. Push the repository to GitHub, create a Blueprint in Render for that repository, and configure `MYSQL_URL`, `MYSQL_USERNAME`, and `MYSQL_PASSWORD` for the service. `MYSQL_URL` must be a JDBC URL for a separately hosted, reachable MySQL database, such as `jdbc:mysql://<host>:3306/courier_db?useSSL=true&serverTimezone=Asia/Kolkata`; follow the database provider's required TLS and connection settings. The database account needs permission to create or update the application schema.

After Render deploys the backend, set Vercel's `BACKEND_URL` to the Render service's public base URL. Render does not provide a managed MySQL service in this Blueprint; choose a MySQL host separately. The current Spring Security configuration permits all requests, so the application must not be exposed publicly with real customer or shipment data until authentication and authorization are secured.

The master administrator manages district logins from **Districts** and creates region logins from **Regions** by selecting a district login. District and state details are filled from the selected district; area and contact are optional, and a supplied contact number must contain exactly 10 digits.

## Database Setup

The backend connects to MySQL at `localhost:3306` and uses the `courier_db` database. It requests database creation if missing, so the configured MySQL account must have permission. Set credentials in PowerShell before starting the backend:

```powershell
$env:MYSQL_USERNAME="root"
$env:MYSQL_PASSWORD="your_mysql_password"
```

Database connection settings are in `backend/src/main/resources/application.properties`. Do not commit local database credentials.

## Technology

- Java 21
- Spring Boot 3.5.6, Spring MVC, Spring Data JPA, and Spring Security
- MySQL 8 with Hibernate
- Maven

## Project Structure

```text
backend/
	src/main/java/com/courier/       Spring Boot controllers, entities, repositories, and services
	src/main/resources/              Application configuration and integrated static portal
	src/test/                        Backend tests
	pom.xml                          Maven project configuration
database.sql                       Database reference SQL
```

## API Overview

- `/api/auth` - login and account operations
- `/api/shipments` - shipment listing, booking, tracking status, and related operations
- `/api/regions` - region administration

Some operations require role and operating-region headers. The controllers under `backend/src/main/java/com/courier/controller/` define the route-specific behavior.

## Verification

Run backend tests from `backend/`:

```powershell
mvn test
```
