# Smart Campus Final QA & Defence Checklist

Use this checklist before calling the project complete.

## 1. Student flow
- [ ] Register a new student account.
- [ ] Confirm duplicate email registration is rejected.
- [ ] Sign in with the new account.
- [ ] Confirm the portal shows the correct school and campus.
- [ ] Submit an ICT complaint.
- [ ] Submit an electricity complaint.
- [ ] Confirm complaint title, description and location are required.
- [ ] Tag a location on the schematic campus map.
- [ ] Confirm a complaint reference number is generated.
- [ ] Open the complaint from the student portal.
- [ ] Track the complaint by reference.
- [ ] Sign out and sign back in.

## 2. Staff / operations flow
- [ ] Sign in with a STAFF or ADMIN account.
- [ ] Confirm operations only shows complaints for the allowed institution.
- [ ] Assign a complaint to a staff member or team.
- [ ] Confirm blank assignment is rejected.
- [ ] Move a complaint through SUBMITTED → ASSIGNED → IN_PROGRESS → RESOLVED.
- [ ] Confirm an invalid status is rejected.
- [ ] Confirm staff cannot modify a complaint from another institution.

## 3. Admin flow
- [ ] Sign in with an ADMIN account.
- [ ] Open the analytics page.
- [ ] Confirm total complaint count is correct.
- [ ] Confirm category aggregation is correct.
- [ ] Resolve at least one complaint and confirm average resolution hours changes.

## 4. Database verification
- [ ] Open MongoDB Atlas or local MongoDB.
- [ ] Confirm collections exist for users, sessions, complaints and counters.
- [ ] Confirm a newly registered user creates a users document.
- [ ] Confirm login creates a sessions document.
- [ ] Confirm a submitted complaint creates a complaints document.
- [ ] Confirm the counter increments when a complaint is created.

## 5. Backend / API
- [ ] GET /api/health responds.
- [ ] GET /api/dashboard/summary returns real database-backed values.
- [ ] Registration works.
- [ ] Login works.
- [ ] Complaint creation works.
- [ ] Student complaint list works.
- [ ] Staff complaint list works.
- [ ] Status update works.
- [ ] Assignment works.
- [ ] Analytics works.
- [ ] Invalid requests return a useful error response.

## 6. Automated tests
- [ ] GitHub Actions backend test step passes.
- [ ] AuthServiceTest passes.
- [ ] ComplaintServiceTest passes.
- [ ] SmartCampusApplicationTests passes.
- [ ] Do not describe this as comprehensive production test coverage.

## 7. Docker
- [ ] docker compose up --build succeeds locally.
- [ ] Frontend opens at http://localhost:8080.
- [ ] Application connects to the Docker MongoDB service.
- [ ] Restart containers and confirm persisted complaints remain.

## 8. Kubernetes / CI
- [ ] k8s/mongodb.yaml exists.
- [ ] k8s/deployment.yaml exists.
- [ ] k8s/service.yaml exists.
- [ ] GitHub Actions starts Minikube.
- [ ] CI builds the application image.
- [ ] CI creates db-vault.
- [ ] CI deploys MongoDB and Smart Campus.
- [ ] Both rollouts complete.
- [ ] CI calls /api/dashboard/summary successfully.

## 9. Render production deployment
- [ ] Set MONGODB_URI in Render Environment.
- [ ] Atlas Network Access allows the Render service to reach the cluster.
- [ ] Deploy completes successfully.
- [ ] Open https://smart-campus-mms4-api.onrender.com.
- [ ] Call /api/dashboard/summary and confirm a successful database-backed response.
- [ ] Register a test student against the Render deployment.
- [ ] Verify the test user appears in Atlas.
- [ ] Submit a complaint and verify it appears in Atlas.

## 10. Defence evidence
Capture screenshots of:
- [ ] Frontend home page.
- [ ] Report complaint screen.
- [ ] Tracking / student portal.
- [ ] Staff operations screen.
- [ ] Admin analytics.
- [ ] GitHub repository root.
- [ ] frontend root.
- [ ] backend root.
- [ ] k8s manifests.
- [ ] JUnit test files.
- [ ] Green GitHub Actions run.
- [ ] Dockerfile.
- [ ] Render successful deployment.
- [ ] MongoDB complaint record.

## Claims to avoid
Do not claim:
- Google Maps or live geospatial mapping.
- Comprehensive automated test coverage.
- A successful Render production deployment until it actually succeeds.
- Database connectivity based only on /api/health.
- Kubernetes functionality unless the current Actions run is green.
