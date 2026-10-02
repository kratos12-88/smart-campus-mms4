# Smart Campus Service & Complaint Management System

Smart Campus is a full-stack campus complaint management system built from technologies covered in NIIT Semesters 1–4.

## Course-aligned stack

- **Frontend:** React, HTML5, CSS3
- **Backend:** Java 12-compatible source with Spring Boot
- **Database:** MongoDB / NoSQL
- **Testing:** JUnit 5
- **Deployment:** Docker
- **Semester 4 CI/Kubernetes practice:** GitHub Actions + Minikube workflow

## Architecture

React + HTML5 + CSS3 → Spring Boot REST API → MongoDB

JUnit 5 tests the Java backend. Docker packages the application for consistent deployment.

## Run frontend for development

```bash
cd frontend
npm install
npm start
```

## Run backend for development

Start MongoDB, then:

```bash
cd backend
mvn spring-boot:run
```

## Run the complete project with Docker

```bash
docker compose up --build
```

Open:

```text
http://localhost:8080
```

## GitHub Actions

The repository includes:

```text
.github/workflows/kubernetes-secret.yaml
```

It starts Minikube on GitHub Actions and creates a Kubernetes secret named `db-vault` using the repository secret `DB_PASSWORD`.

Before relying on the secret value, add `DB_PASSWORD` under:

**GitHub repository → Settings → Secrets and variables → Actions → New repository secret**

## Defense summary

> Smart Campus uses React with HTML5 and CSS3 for the responsive frontend. Java and Spring Boot provide REST APIs and business logic. MongoDB stores users and complaint records as NoSQL documents. JUnit 5 is used for backend testing, and Docker is used to package the application. The GitHub Actions workflow demonstrates the Kubernetes/Minikube concepts covered in Semester 4.
