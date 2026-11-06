package com.smartcampus.service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.smartcampus.config.ForbiddenException;
import com.smartcampus.config.NotFoundException;
import com.smartcampus.model.Complaint;
import com.smartcampus.model.Counter;
import com.smartcampus.model.UserAccount;
import com.smartcampus.repository.ComplaintRepository;

@Service
public class ComplaintService {
  private static final Set<String> CATEGORIES = Set.of(
    "SECURITY","ICT","ELECTRICITY","WATER","FACILITIES","HOSTEL","ACADEMIC","OTHER"
  );
  private static final Set<String> PRIORITIES = Set.of("LOW","MEDIUM","HIGH","URGENT");
  private static final Set<String> STATUSES = Set.of(
    "SUBMITTED","ASSIGNED","IN_PROGRESS","RESOLVED","CLOSED","REJECTED"
  );

  private final ComplaintRepository repo;
  private final MongoTemplate mongo;

  public ComplaintService(ComplaintRepository repo, MongoTemplate mongo) {
    this.repo = repo;
    this.mongo = mongo;
  }

  public List<Complaint> recent() { return repo.findTop100ByOrderByCreatedAtDesc(); }

  public Complaint one(long id) {
    if(id <= 0) throw new IllegalArgumentException("Complaint reference must be a positive number");
    return repo.findById(id).orElseThrow(() -> new NotFoundException("Complaint not found"));
  }

  public Map<String,Object> publicView(long id) {
    Complaint c = one(id);
    Map<String,Object> view = new LinkedHashMap<>();
    view.put("id", c.getId());
    view.put("title", safe(c.getTitle()));
    view.put("description", safe(c.getDescription()));
    view.put("location", safe(c.getLocation()));
    view.put("category", safe(c.getCategory()));
    view.put("priority", safe(c.getPriority()));
    view.put("status", safe(c.getStatus()));
    view.put("department", safe(c.getDepartment()));
    view.put("assignedTo", safe(c.getAssignedTo()));
    view.put("createdAt", c.getCreatedAt()==null ? "" : c.getCreatedAt().toString());
    view.put("updatedAt", c.getUpdatedAt()==null ? "" : c.getUpdatedAt().toString());
    return view;
  }

  public List<Complaint> mine(UserAccount u) {
    requireStudent(u);
    return repo.findBySubmittedEmailIgnoreCaseOrderByCreatedAtDesc(u.getEmail());
  }

  public List<Complaint> forStaff(UserAccount u) {
    requireStaff(u);
    if("ADMIN".equals(u.getRole()) && (u.getSchoolName()==null || u.getSchoolName().isBlank())) return recent();
    return repo.findBySchoolNameIgnoreCaseOrderByCreatedAtDesc(u.getSchoolName());
  }

  public Complaint create(Complaint c, UserAccount u) {
    requireStudent(u);
    if(c == null) throw new IllegalArgumentException("Complaint details are required");

    String title = clean(c.getTitle());
    String description = clean(c.getDescription());
    String location = clean(c.getLocation());
    String category = clean(c.getCategory()).toUpperCase();
    String priority = clean(c.getPriority()).toUpperCase();
    String mapLabel = clean(c.getMapLabel());

    if(title.length() < 5 || title.length() > 120)
      throw new IllegalArgumentException("Issue title must be between 5 and 120 characters");
    if(description.length() < 10 || description.length() > 2000)
      throw new IllegalArgumentException("Description must be between 10 and 2000 characters");
    if(location.length() < 2 || location.length() > 120)
      throw new IllegalArgumentException("Complaint location is required");
    if(!CATEGORIES.contains(category))
      throw new IllegalArgumentException("Select a valid complaint category");
    if(!PRIORITIES.contains(priority))
      throw new IllegalArgumentException("Select a valid priority");
    if(c.getMapX()==null || c.getMapY()==null)
      throw new IllegalArgumentException("Tag the issue location on the campus map");
    if(c.getMapX() < 0 || c.getMapX() > 100 || c.getMapY() < 0 || c.getMapY() > 100)
      throw new IllegalArgumentException("Campus map coordinates are invalid");
    if(mapLabel.isBlank()) mapLabel = location;
    if(mapLabel.length() > 120)
      throw new IllegalArgumentException("Map label must be 120 characters or fewer");

    Map<String,Integer> sla = Map.of(
      "SECURITY",4,"ICT",24,"ELECTRICITY",24,"WATER",24,
      "FACILITIES",48,"HOSTEL",48,"ACADEMIC",48,"OTHER",72
    );
    Map<String,String> teams = Map.of(
      "SECURITY","Campus Security","ICT","ICT Services","ELECTRICITY","Electrical Unit",
      "WATER","Works & Maintenance","FACILITIES","Facilities","HOSTEL","Student Affairs",
      "ACADEMIC","Academic Affairs","OTHER","General Services"
    );
    Instant now = Instant.now();

    c.setId(nextId());
    c.setTitle(title);
    c.setDescription(description);
    c.setLocation(location);
    c.setMapLabel(mapLabel);
    c.setCategory(category);
    c.setPriority(priority);
    c.setStatus("SUBMITTED");
    c.setDepartment(teams.get(category));
    c.setSubmittedBy(u.getName());
    c.setSubmittedEmail(u.getEmail());
    c.setSchoolName(u.getSchoolName());
    c.setCampusName(u.getCampusName());
    c.setCreatedAt(now);
    c.setUpdatedAt(now);
    c.setDueAt(now.plus(sla.get(category), ChronoUnit.HOURS));
    c.setAuditTrail(new ArrayList<>(List.of("Complaint submitted by "+u.getName())));
    return repo.save(c);
  }

  public Complaint status(long id, String requestedStatus, UserAccount u) {
    Complaint c = one(id);
    checkStaffAccess(c,u);

    String status = clean(requestedStatus).toUpperCase();
    if(!STATUSES.contains(status))
      throw new IllegalArgumentException("Select a valid complaint status");

    String current = safe(c.getStatus()).toUpperCase();
    if(status.equals(current)) return c;
    if(!isAllowedTransition(current, status))
      throw new IllegalArgumentException("Invalid status transition from "+current+" to "+status);

    c.setStatus(status);
    c.setUpdatedAt(Instant.now());
    if(List.of("RESOLVED","CLOSED").contains(status)) {
      if(c.getResolvedAt()==null) c.setResolvedAt(Instant.now());
    } else {
      c.setResolvedAt(null);
    }
    audit(c).add("Status changed from "+current+" to "+status+" by "+u.getName());
    return repo.save(c);
  }

  public Complaint assign(long id, String assignedTo, UserAccount u) {
    Complaint c = one(id);
    checkStaffAccess(c,u);

    String assignee = clean(assignedTo);
    if(assignee.length() < 2 || assignee.length() > 80)
      throw new IllegalArgumentException("Assignee must be between 2 and 80 characters");
    if(List.of("RESOLVED","CLOSED","REJECTED").contains(c.getStatus()))
      throw new IllegalArgumentException("Resolved, closed, or rejected complaints cannot be assigned");

    c.setAssignedTo(assignee);
    if("SUBMITTED".equals(c.getStatus())) c.setStatus("ASSIGNED");
    c.setUpdatedAt(Instant.now());
    audit(c).add("Assigned to "+assignee+" by "+u.getName());
    return repo.save(c);
  }

  public Map<String,Object> summary() {
    List<Complaint> all = repo.findAll();
    long resolved = all.stream().filter(c -> List.of("RESOLVED","CLOSED").contains(c.getStatus())).count();
    long open = all.stream().filter(c -> !List.of("RESOLVED","CLOSED","REJECTED").contains(c.getStatus())).count();
    long overdue = all.stream().filter(c -> c.getDueAt()!=null && c.getDueAt().isBefore(Instant.now()) && !List.of("RESOLVED","CLOSED","REJECTED").contains(c.getStatus())).count();
    return Map.of("total",all.size(),"resolved",resolved,"open",open,"overdue",overdue);
  }

  public Map<String,Object> analytics(UserAccount u) {
    List<Complaint> all = forStaff(u);
    Map<String,Long> byCategory = new LinkedHashMap<>();
    all.forEach(c -> byCategory.merge(c.getCategory(),1L,Long::sum));

    double avgRating = all.stream()
      .filter(c -> c.getRating()!=null)
      .mapToInt(Complaint::getRating)
      .average()
      .orElse(0);

    double avgResolutionHours = all.stream()
      .filter(c -> c.getCreatedAt()!=null && c.getResolvedAt()!=null)
      .mapToLong(c -> Duration.between(c.getCreatedAt(), c.getResolvedAt()).toMinutes())
      .average()
      .orElse(0) / 60.0;

    return Map.of(
      "total",all.size(),
      "averageResolutionHours",Math.round(avgResolutionHours*10.0)/10.0,
      "averageRating",Math.round(avgRating*10.0)/10.0,
      "byCategory",byCategory
    );
  }

  private boolean isAllowedTransition(String current, String next) {
    if("SUBMITTED".equals(current)) return List.of("ASSIGNED","REJECTED").contains(next);
    if("ASSIGNED".equals(current)) return List.of("IN_PROGRESS","REJECTED").contains(next);
    if("IN_PROGRESS".equals(current)) return List.of("RESOLVED","REJECTED").contains(next);
    if("RESOLVED".equals(current)) return List.of("CLOSED","IN_PROGRESS").contains(next);
    return false;
  }

  private long nextId() {
    Counter c = mongo.findAndModify(
      Query.query(Criteria.where("_id").is("complaint")),
      new Update().inc("seq",1),
      FindAndModifyOptions.options().upsert(true).returnNew(true),
      Counter.class
    );
    if(c == null) throw new IllegalStateException("Unable to generate complaint reference");
    return c.getSeq();
  }

  private void requireStudent(UserAccount u) {
    if(u == null || !"STUDENT".equals(u.getRole()))
      throw new ForbiddenException("Student access required");
    if(u.getEmail()==null || u.getEmail().isBlank())
      throw new ForbiddenException("Student account email is required");
    if(u.getSchoolName()==null || u.getSchoolName().isBlank())
      throw new ForbiddenException("Student institution is required");
  }

  private void requireStaff(UserAccount u) {
    if(u == null || !List.of("STAFF","ADMIN").contains(u.getRole()))
      throw new ForbiddenException("Staff access required");
    if("STAFF".equals(u.getRole()) && (u.getSchoolName()==null || u.getSchoolName().isBlank()))
      throw new ForbiddenException("Staff institution is required");
  }

  private void checkStaffAccess(Complaint c, UserAccount u) {
    requireStaff(u);
    if(!"ADMIN".equals(u.getRole()) || (u.getSchoolName()!=null && !u.getSchoolName().isBlank())) {
      if(!Objects.equals(c.getSchoolName(),u.getSchoolName()))
        throw new ForbiddenException("Complaint belongs to another institution");
    }
  }

  private List<String> audit(Complaint c) {
    if(c.getAuditTrail()==null) c.setAuditTrail(new ArrayList<>());
    return c.getAuditTrail();
  }

  private String clean(String value) {
    return value == null ? "" : value.trim().replaceAll("\\s+", " ");
  }

  private String safe(String value) {
    return value == null ? "" : value;
  }
}
