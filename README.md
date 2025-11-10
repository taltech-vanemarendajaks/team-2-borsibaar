# Iti0302-2025

## Project Overview

Borsibaar is a fullstack web application with a Spring Boot backend and Next.js frontend. The system appears to be a stock exchange/trading platform ("börsibaari" in Estonian means "stock bar").

## Architecture

* **Backend**: Spring Boot 3.5.5 with Java 21, PostgreSQL database, Spring Security with OAuth2, JWT authentication
* **Frontend**: Next.js with TypeScript, Tailwind CSS, Radix UI components
* **Database**: PostgreSQL with Liquibase migrations
* **Containerization**: Docker Compose for development environment

## Development Commands

### Backend (Spring Boot)

```bash
# Run backend with Maven wrapper
cd backend && ./mvnw spring-boot:run

# Build backend
cd backend && ./mvnw clean package

# Run tests
cd backend && ./mvnw test
```

### Frontend (Next.js)

```bash
# Development server with Turbopack
cd frontend && npm run dev

# Build for production
cd frontend && npm run build

# Start production server
cd frontend && npm start

# Lint code
cd frontend && npm run lint
```

### Docker Development

```bash
# Start full development environment
docker-compose up

# Start specific services
docker-compose up postgres backend
```

## Key Backend Architecture

The Spring Boot backend follows a layered architecture:

* **Controllers** (`controller/`): REST API endpoints
* **Services** (`service/`): Business logic layer
* **Repositories** (`repository/`): Data access layer using Spring Data JPA
* **Entities** (`entity/`): JPA entities mapping to database tables
* **DTOs** (`dto/`): Request/Response data transfer objects
* **Mappers** (`mapper/`): MapStruct mappers for entity-DTO conversion
* **Config** (`config/`): Spring configuration classes

Key technologies:

* Spring Security with OAuth2 client
* JWT tokens for authentication
* Liquibase for database migrations
* MapStruct for object mapping
* Lombok for reducing boilerplate

## Frontend Structure

Next.js 15 application using the App Router:

* **Pages**: `app/page.tsx` (landing), `app/dashboard/`, `app/login/`, `app/onboarding/`
* **API Routes**: `app/api/` for backend integration
* **Styling**: Tailwind CSS with custom components using Radix UI
* **TypeScript**: Fully typed with strict configuration

## Database

PostgreSQL database configured via Docker Compose. Environment variables are loaded from `.env` and `backend/.env` files.

## Environment Setup

1. Copy `.sample.env` to `.env` and configure database credentials
2. Set up backend environment in `backend/.env`
3. Use Docker Compose for local development: `docker-compose up`

### Sample `.env` (root)

```env
POSTGRES_DB=
POSTGRES_USER=
POSTGRES_PASSWORD=

SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/{pane siia POSTGRES_DB nimi}
SPRING_DATASOURCE_USERNAME=
SPRING_DATASOURCE_PASSWORD=

GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=

JWT_SECRET="" # openssl rand -base64 32
```

## Sample Spring configuration (application.properties)

`backend/src/main/resources/application.properties`

```properties
spring.application.name=Borsibaar

spring.datasource.url=${SPRING_DATASOURCE_URL}
spring.datasource.username=${SPRING_DATASOURCE_USERNAME}
spring.datasource.password=${SPRING_DATASOURCE_PASSWORD}

spring.security.oauth2.client.registration.google.client-id=${GOOGLE_CLIENT_ID}
spring.security.oauth2.client.registration.google.client-secret=${GOOGLE_CLIENT_SECRET}
spring.security.oauth2.client.registration.google.scope=openid,profile,email
spring.security.oauth2.client.registration.google.redirect-uri={baseUrl}/login/oauth2/code/{registrationId}
spring.security.oauth2.client.registration.google.client-name=Google

spring.jpa.hibernate.ddl-auto=validate
spring.jpa.open-in-view=false

spring.liquibase.change-log=classpath:db/changelog/db.changelog-master.yaml
spring.liquibase.enabled=true
spring.sql.init.mode=never

jwt.secret=${JWT_SECRET}
app.cors.allowed-origins=http://localhost:3000,http://127.0.0.1:3000
app.frontend.url=http://localhost:3000

server.forward-headers-strategy=framework
```

## Key Dependencies

### Backend

* Spring Boot Starter (Web, Data JPA, Security, OAuth2 Client)
* PostgreSQL driver and Liquibase
* JWT libraries (jjwt)
* MapStruct and Lombok
* Spring DotEnv for environment configuration

### Frontend

* Next.js 15 with Turbopack
* React 19
* Tailwind CSS v4
* Radix UI components
* TypeScript


## TODOs

### Backend

- Define relationships (OneToOne, ManyToOne, OneToMany etc) in all models and migrate away from setId/getId
    - id getters can be read only, id setters must not be used
    - example: (getProduct, setProduct) Inventory <-> Product (getInventory, setInventory)
    - frequently queried relationships should be loaded eagerly (product always needs inventory, transactions need inventory)
    - Fix code by removing extra repository queries, get inventory/transactions/categories etc directly from product object
- Remove duplicate organization column in inventory
  - Fix queries using organization_id from inventory table
- Create public item transaction history endpoint (needs login for now)
- Create price increase/decrease amount columns for org (0.05€ default)
- Create price correction setting column (how often, lookback window)
- Skip price correction if not enabled for drink
- Fix CategoryService org TODOs
- ...

### Frontend
- Public view queries data from all organizations
  - Need to login for transaction history
  - Should supply specific organization id in URL (or keep login)
  - Should fetch all drinks in 1 request
  - Compact view so everything fits on screen (3 columns + wide chart?)
- Min/max prices in create view (currently do not exist?)
- sorting in create view (newest first?)
- Set current price in stock view (adjusted_price in inventory table)
- Enable/disable price correction checkbox for drinks
- Increase/decrease price amount in org settings
- Check if API returns not logged in response, reload app instead of error "<!doctype "... is not valid JSON
- ...