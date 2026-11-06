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
import java.util.Base64;
import java.util.stream.Collectors;

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
    "SUBMITTED","UNDER_REVIEW","ASSIGNED","IN_PROGRESS","AWAITING_STUDENT","RESOLVED","CLOSED","REJECTED"
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

  @SuppressWarnings("unchecked")
  public Map<String,Object> publicView(long id) { return (Map<String,Object>)view(one(id),null); }

  public List<Complaint> mine(UserAccount u) {
    requireStudent(u);
    return repo.findBySubmittedEmailIgnoreCaseOrderByCreatedAtDesc(u.getEmail());
  }

  // Public tracking exposes status, never student identity, evidence or conversations.
  public Object view(Complaint c, UserAccount u) {
    if(canReadPrivate(c,u)) return c;
    Map<String,Object> result = new LinkedHashMap<>();
    result.put("id",c.getId()); result.put("title",c.getTitle());
    result.put("category",c.getCategory()); result.put("location",c.getLocation());
    result.put("status",c.getStatus()); result.put("priority",c.getPriority());
    result.put("department",c.getDepartment()); result.put("createdAt",c.getCreatedAt());
    result.put("updatedAt",c.getUpdatedAt()); result.put("dueAt",c.getDueAt());
    result.put("schoolName",c.getSchoolName()); result.put("campusName",c.getCampusName());
    result.put("privateDetails",false);
    return result;
  }

  public List<Object> recentViews(UserAccount u) {
    return recent().stream().map(c -> view(c,u)).collect(Collectors.toList());
  }

  public List<Object> duplicates(String category, String location, String title, UserAccount u) {
    if(title==null || title.trim().length()<3) return List.of();
    String query=title.trim().toLowerCase();
    return repo.findBySchoolNameIgnoreCaseOrderByCreatedAtDesc(u.getSchoolName()).stream()
      .filter(c -> Objects.equals(category,c.getCategory()) && Objects.equals(location,c.getLocation()))
      .filter(c -> !List.of("RESOLVED","CLOSED","REJECTED").contains(c.getStatus()))
      .filter(c -> c.getTitle()!=null && (c.getTitle().toLowerCase().contains(query) || query.contains(c.getTitle().toLowerCase())))
      .limit(5).map(c -> view(c,u)).collect(Collectors.toList());
  }

  public List<Map<String,Object>> notifications(UserAccount u) {
    return ("STUDENT".equals(u.getRole()) ? mine(u) : forStaff(u)).stream().limit(20).map(c -> {
      Map<String,Object> item=new LinkedHashMap<>();
      item.put("id",c.getId()); item.put("complaintId",c.getId());
      item.put("message","Complaint #"+c.getId()+": "+c.getStatus().replace('_',' '));
      item.put("createdAt",c.getUpdatedAt()); item.put("readFlag",true);
      return item;
    }).collect(Collectors.toList());
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
    if(!Double.isFinite(c.getMapX()) || !Double.isFinite(c.getMapY()) || c.getMapX() < 0 || c.getMapX() > 100 || c.getMapY() < 0 || c.getMapY() > 100)
      throw new IllegalArgumentException("Campus map coordinates are invalid");
    if(mapLabel.isBlank()) mapLabel = location;
    if(mapLabel.length() > 120)
      throw new IllegalArgumentException("Map label must be 120 characters or fewer");

    if(c.getEvidenceData()!=null && !c.getEvidenceData().isBlank()) {
      String data=c.getEvidenceData();
      if(data.length()>2800000 || !data.matches("^data:image/(png|jpeg|webp|gif);base64,[A-Za-z0-9+/=]+$"))
        throw new IllegalArgumentException("Attach a PNG, JPEG, WebP or GIF image smaller than 2 MB");
      try {
        if(Base64.getDecoder().decode(data.substring(data.indexOf(',')+1)).length>2000000)
          throw new IllegalArgumentException("Image must be smaller than 2 MB");
      } catch(IllegalArgumentException e) { throw new IllegalArgumentException("Invalid image attachment or image exceeds 2 MB"); }
    }
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
    c.setMongoId(null); c.setAssignedTo(null); c.setResolvedAt(null); c.setRating(null); c.setFeedback(null);
    c.setComments(new ArrayList<>());
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
    c.setAuditTrail(new ArrayList<>()); activity(c,u,"Complaint submitted");
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
    activity(c,u,"Status changed from "+current+" to "+status);
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
    activity(c,u,"Assigned to "+assignee);
    return repo.save(c);
  }

  public Complaint comment(long id, String text, UserAccount u) {
    Complaint c=one(id);
    if(!canReadPrivate(c,u)) throw new ForbiddenException("This complaint belongs to another account or institution");
    String value=requiredText(text,"Comment",2000);
    if(c.getComments()==null) c.setComments(new ArrayList<>());
    c.getComments().add(Instant.now()+"|"+safe(u.getName())+"|"+safe(value));
    c.setUpdatedAt(Instant.now());
    return repo.save(c);
  }

  public Complaint feedback(long id, Integer rating, String feedback, UserAccount u) {
    Complaint c=one(id); requireOwner(c,u);
    if(!List.of("RESOLVED","CLOSED").contains(c.getStatus())) throw new IllegalArgumentException("Only resolved complaints can be rated");
    if(rating==null || rating<1 || rating>5) throw new IllegalArgumentException("Rating must be between 1 and 5");
    if(c.getRating()!=null) throw new IllegalArgumentException("This complaint has already been rated");
    if(feedback!=null && feedback.length()>2000) throw new IllegalArgumentException("Feedback must be 2000 characters or fewer");
    c.setRating(rating); c.setFeedback(feedback==null ? "" : feedback.trim());
    c.setUpdatedAt(Instant.now()); activity(c,u,"Resolution rated "+rating+"/5");
    return repo.save(c);
  }

  public Complaint reopen(long id, String reason, UserAccount u) {
    Complaint c=one(id); requireOwner(c,u);
    if(!List.of("RESOLVED","CLOSED").contains(c.getStatus())) throw new IllegalArgumentException("Only resolved complaints can be reopened");
    String value=requiredText(reason,"Reopen reason",1000);
    c.setStatus("IN_PROGRESS"); c.setResolvedAt(null); c.setRating(null); c.setFeedback(null);
    c.setUpdatedAt(Instant.now()); c.setDueAt(Instant.now().plus(24,ChronoUnit.HOURS));
    activity(c,u,"Complaint reopened: "+value);
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
    Map<String,Long> byLocation = new LinkedHashMap<>();
    all.forEach(c -> byLocation.merge(c.getLocation()==null ? "Unspecified" : c.getLocation(),1L,Long::sum));
    long overdue=all.stream().filter(c -> c.getDueAt()!=null && c.getDueAt().isBefore(Instant.now()) && !List.of("RESOLVED","CLOSED","REJECTED").contains(c.getStatus())).count();

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
      "byCategory",byCategory,"byLocation",byLocation,"overdue",overdue
    );
  }

  private boolean isAllowedTransition(String current,String next) {
    if("SUBMITTED".equals(current)) return List.of("UNDER_REVIEW","ASSIGNED","REJECTED").contains(next);
    if("UNDER_REVIEW".equals(current)) return List.of("ASSIGNED","REJECTED").contains(next);
    if("ASSIGNED".equals(current)) return List.of("IN_PROGRESS","REJECTED").contains(next);
    if("IN_PROGRESS".equals(current)) return List.of("AWAITING_STUDENT","RESOLVED","REJECTED").contains(next);
    if("AWAITING_STUDENT".equals(current)) return List.of("IN_PROGRESS","RESOLVED","REJECTED").contains(next);
    if("RESOLVED".equals(current)) return "CLOSED".equals(next);
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
      if(u.getSchoolName()==null || c.getSchoolName()==null || !c.getSchoolName().equalsIgnoreCase(u.getSchoolName()))
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
    return value == null ? "" : value.replace('|','¦');
  }
  private boolean canReadPrivate(Complaint c, UserAccount u) {
    if(u==null) return false;
    if(u.getEmail()!=null && u.getEmail().equalsIgnoreCase(c.getSubmittedEmail())) return true;
    if(!List.of("STAFF","ADMIN").contains(u.getRole())) return false;
    return ("ADMIN".equals(u.getRole()) && (u.getSchoolName()==null || u.getSchoolName().isBlank())) ||
      (u.getSchoolName()!=null && !u.getSchoolName().isBlank() && u.getSchoolName().equalsIgnoreCase(c.getSchoolName()));
  }

  private void requireOwner(Complaint c, UserAccount u) {
    if(u==null || u.getEmail()==null || !u.getEmail().equalsIgnoreCase(c.getSubmittedEmail()))
      throw new ForbiddenException("Only the student who submitted this complaint can perform this action");
  }

  private String requiredText(String text,String label,int max) {
    if(text==null || text.isBlank() || text.trim().length()>max) throw new IllegalArgumentException(label+" is required and must be "+max+" characters or fewer");
    return text.trim();
  }

  private void activity(Complaint c,UserAccount u,String message) {
    if(c.getAuditTrail()==null) c.setAuditTrail(new ArrayList<>());
    c.getAuditTrail().add(Instant.now()+"|"+safe(u.getName())+"|"+safe(message));
  }
}
