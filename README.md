# Smart Campus Service & Complaint Management System

Smart Campus is a full-stack campus complaint management system built from technologies covered in NIIT Semesters 1–4.

## Course-aligned stack

- **Frontend:** React, HTML5, CSS3
- **Backend:** Java 12-compatible source with Spring Boot 2.7
- **Database:** MongoDB / NoSQL
- **Testing:** JUnit 5
- **Deployment:** Docker
- **CI/Kubernetes:** GitHub Actions + Minikube + Kubernetes manifests

## Architecture

```text
React + HTML5 + CSS3
        |
        v
Spring Boot REST API
        |
        v
MongoDB
```

The frontend and backend are packaged together by the root Dockerfile. MongoDB stores users, authentication sessions, complaint records, and counters.

## Core application flow

### Students
1. Register and sign in.
2. Select institution/campus.
3. Submit a complaint with category, description, and schematic campus location.
4. Receive a complaint reference.
5. Track complaint progress.

### Staff
1. View complaints for their institution.
2. Assign complaints.
3. Move complaints through workflow states.

### Administrators
1. Review service activity.
2. View complaint analytics.
3. Monitor open, resolved, and overdue cases.

> Location tagging in this project is schematic campus tagging. It is not a Google Maps or live geospatial integration.

## Run locally

### Frontend development

```bash
cd frontend
npm install
npm start
```

### Backend development

Start MongoDB, then:

```bash
cd backend
mvn spring-boot:run
```

### Complete Docker environment

```bash
docker compose up --build
```

Open:

```text
http://localhost:8080
```

## Tests

Run backend tests with:

```bash
cd backend
mvn test
```

The test suite includes unit coverage for authentication/session behavior and complaint workflow behavior. It is still a focused project test suite, not comprehensive production coverage.

## Kubernetes

Kubernetes manifests are stored in:

```text
k8s/
  mongodb.yaml
  deployment.yaml
  service.yaml
```

The CI workflow creates a local Minikube cluster, builds the Smart Campus Docker image inside Minikube, creates the `db-vault` Kubernetes secret, deploys MongoDB and Smart Campus, waits for both deployments, and calls the database-backed `/api/dashboard/summary` endpoint.

## GitHub Actions

Workflow:

```text
.github/workflows/kubernetes-secret.yaml
```

The workflow performs:

1. Checkout.
2. JUnit backend tests.
3. Minikube startup.
4. Docker image build.
5. Kubernetes secret creation.
6. MongoDB deployment.
7. Smart Campus deployment.
8. Rollout verification.
9. Database-backed API verification.

## Render deployment

Render service:

```text
https://smart-campus-mms4-api.onrender.com
```

The service is configured to deploy the root Dockerfile. Production requires:

```text
MONGODB_URI=mongodb+srv://<user>:<password>@<cluster>/smart_campus?... 
```

Set the real Atlas connection string in Render under **Environment** as `MONGODB_URI`. Do not commit database credentials to this repository.

At the time this documentation was updated, the previous Render build succeeded but the runtime deployment exited with a non-zero status. The production deployment should not be described as fully operational until a deployment succeeds and a database-backed endpoint such as `/api/dashboard/summary` responds successfully.

## Defence summary

> Smart Campus uses React with HTML5 and CSS3 for the responsive frontend. Java and Spring Boot provide the REST APIs and business logic. MongoDB stores users, sessions, complaints, and counters as NoSQL documents. JUnit 5 tests authentication and complaint workflow behavior. Docker packages the complete application. GitHub Actions starts Minikube, deploys the Kubernetes manifests, and verifies a database-backed API request. Render is configured for production deployment, with the MongoDB Atlas `MONGODB_URI` required for the final live deployment.
