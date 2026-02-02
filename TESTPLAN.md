# Testing Plan

## 1. Testing Objectives

- Ensure core business logic (sales, dynamic pricing, inventory management) works correctly
- Validate multi-tenant data isolation between organizations
- Verify authentication/authorization flows (OAuth2 + JWT)
- Confirm frontend UI workflows function end-to-end
- Establish regression safety nets for future development
- Validate the scheduled price correction job behavior

## 2. Testing Levels

### Unit Testing
**Backend:** Service and controller tests with Mockito (18 test files already exist)  
**Frontend:** Component tests with Vitest + React Testing Library (none exist yet - need setup)

### Integration Testing
**Backend:** Spring Boot @SpringBootTest with H2 or Testcontainers for full workflow tests  
**Frontend:** API route proxy tests, page-level integration tests

### System/E2E Testing
**Backend:** Full Docker Compose environment with Playwright or Cypress driving the UI through complete user flows  
**Frontend:** End-to-end user flow testing

## 3. Test Scope

### In Scope

- **Backend services:** SalesService, InventoryService, ProductService, CategoryService, BarStationService, OrganizationService, AuthService, JwtService
- **Backend controllers:** All 9 REST controllers
- **Scheduled jobs:** PriceCorrectionJob
- **Security:** JWT filter, role-based access (USER/ADMIN), multi-tenant isolation
- **Frontend pages:** Login, onboarding, dashboard, inventory, POS, client view
- **Frontend components:** POS workflow (product selection, cart, checkout), inventory management forms
- **API integration:** Frontend-to-backend proxy routes

### Out of Scope

- OAuth2 provider (Google) internals
- Third-party library internals (Radix UI, D3.js)
- Infrastructure/deployment (Docker image builds, CI/CD pipeline itself)

## 4. Test Approach

### Backend (Java/Spring Boot)

- **Unit tests:** Mockito-based, mock repositories and dependent services. Already largely in place for services and controllers.
- **Integration tests:** Use @SpringBootTest with H2 database to test complete request-response cycles including security filters, validation, and database operations.
- **Key gaps to fill:** PriceCorrectionJob test, multi-tenant isolation tests, authorization rule tests, repository custom query tests.

### Frontend (Next.js/TypeScript)

- **Setup required:** Install Vitest, React Testing Library, MSW (Mock Service Worker)
- **Component tests:** Test individual components (ProductCard, CartSidebar, StationCard) in isolation
- **Page tests:** Test page-level behavior with mocked API responses
- **E2E tests (optional/future):** Playwright against Docker Compose environment

### Test Design Techniques

- Equivalence partitioning for pricing logic (below min, at min, normal, at max, above max)
- Boundary value analysis for stock quantities (0, 1, max)
- Decision table testing for dynamic pricing rules
- Negative testing for authorization (user accessing admin endpoints, cross-org access)

## 5. Test Environment

| Environment | Purpose | Stack |
|------------|---------|-------|
| Local dev | Unit + integration tests | `./mvnw test` (H2 in-memory DB), `npm run test` (Vitest) |
| Docker Compose | System/E2E tests | PostgreSQL + Spring Boot + Next.js via `docker-compose up` |
| CI (future) | Automated regression | GitHub Actions running both backend and frontend test suites |

### Prerequisites

- Java 21, Node.js, Docker
- `.env` configured with test database credentials
- H2 available as test dependency (already in pom.xml)

## 6. Entry and Exit Criteria

### Entry Criteria

- Code compiles without errors (`./mvnw clean package`, `npm run build`)
- Test environment is operational (Docker Compose starts successfully)
- All test dependencies are installed

### Exit Criteria

- All unit tests pass
- All integration tests pass
- No critical or high-severity defects remain open
- Backend test coverage >= 70% on service and controller layers
- Frontend has tests for all critical user flows (login, POS sale, inventory management)

## 7. Roles and Responsibilities

| Role | Responsibility |
|------|----------------|
| Developers | Write and maintain unit tests, fix defects, ensure tests pass before merging |
| QA/Team Lead | Define test cases for integration and E2E tests, review test coverage, manage defect triage |
| All team members | Run full test suite before submitting PRs, report bugs |

## 8. Risks and Assumptions

### Risks

| Risk | Impact | Mitigation |
|------|--------|------------|
| Frontend has zero test infrastructure | High - no regression safety | Prioritize setting up Vitest + React Testing Library |
| Dynamic pricing logic is complex | High - subtle bugs in price calculations | Thorough unit tests with boundary values |
| Multi-tenant data leaks | Critical - security vulnerability | Dedicated integration tests for cross-org isolation |
| PriceCorrectionJob has no tests | Medium - scheduled job could silently break | Add unit test with mocked repositories |
| H2 vs PostgreSQL behavior differences | Medium - tests pass but prod fails | Consider Testcontainers for critical integration tests |

### Assumptions

- OAuth2 provider (Google) is available and functional
- H2 is sufficient for most integration tests
- Team has access to Docker for running the full environment
- Current existing backend tests are passing

## 9. Test Deliverables

1. This testing plan document
2. **Backend unit tests** - Expand existing 18 test files, add PriceCorrectionJob tests
3. **Backend integration tests** - New test class(es) for end-to-end API workflows
4. **Frontend test setup** - Vitest + React Testing Library configuration
5. **Frontend component/page tests** - Tests for critical flows
6. **Test coverage report** - Generated via Maven Surefire/JaCoCo
7. **Defect log** - Tracked via GitHub Issues

## Already Implemented

**Test Plan Item:** Backend unit tests (service + controller) with Mockito  
**Status:** 17 test files exist (8 service, 9 controller)
---
**Test Plan Item:** JwtAuthenticationFilter test  
**Status:** Exists in `config/`
---
**Test Plan Item:** H2 in-memory DB for test environment  
**Status:** Configured in `pom.xml` and `application.properties`
---
**Test Plan Item:** Test application properties  
**Status:** Configured with H2 in PostgreSQL mode