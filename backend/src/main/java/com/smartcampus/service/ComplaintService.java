package com.smartcampus.service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Base64;
import java.util.stream.Collectors;

import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.smartcampus.model.Complaint;
import com.smartcampus.model.Counter;
import com.smartcampus.model.UserAccount;
import com.smartcampus.repository.ComplaintRepository;
import com.smartcampus.config.ForbiddenException;

@Service
public class ComplaintService {
  private final ComplaintRepository repo;
  private final MongoTemplate mongo;

  public ComplaintService(ComplaintRepository repo, MongoTemplate mongo) {
    this.repo = repo;
    this.mongo = mongo;
  }

  public List<Complaint> recent() { return repo.findTop100ByOrderByCreatedAtDesc(); }
  public Complaint one(long id) { return repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Complaint not found")); }
  public List<Complaint> mine(UserAccount u) { return repo.findBySubmittedEmailIgnoreCaseOrderByCreatedAtDesc(u.getEmail()); }

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
    return mine(u).stream().limit(20).map(c -> {
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
    if(c.getTitle()==null || c.getTitle().isBlank())
      throw new IllegalArgumentException("Complaint title is required");
    if(c.getDescription()==null || c.getDescription().isBlank())
      throw new IllegalArgumentException("Complaint description is required");
    if(c.getLocation()==null || c.getLocation().isBlank())
      throw new IllegalArgumentException("Complaint location is required");

    if(c.getTitle().trim().length()>160 || c.getDescription().trim().length()>5000 || c.getLocation().trim().length()>120)
      throw new IllegalArgumentException("Title, description or location is too long");
    if(c.getCategory()==null || c.getPriority()==null)
      throw new IllegalArgumentException("Category and priority are required");
    if((c.getMapX()==null)!=(c.getMapY()==null) || (c.getMapX()!=null &&
      (!Double.isFinite(c.getMapX()) || !Double.isFinite(c.getMapY()) || c.getMapX()<0 || c.getMapX()>100 || c.getMapY()<0 || c.getMapY()>100)))
      throw new IllegalArgumentException("Map coordinates must be between 0 and 100");
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
    String cat = c.getCategory();
    if(!sla.containsKey(cat)) throw new IllegalArgumentException("Invalid complaint category");
    String priority = c.getPriority();
    if(!List.of("LOW","MEDIUM","HIGH","URGENT").contains(priority)) throw new IllegalArgumentException("Invalid priority");
    Instant now = Instant.now();

    c.setId(nextId());
    c.setMongoId(null);
    c.setTitle(c.getTitle().trim()); c.setDescription(c.getDescription().trim()); c.setLocation(c.getLocation().trim());
    c.setAssignedTo(null); c.setResolvedAt(null); c.setRating(null); c.setFeedback(null);
    c.setComments(new ArrayList<>());
    c.setCategory(cat);
    c.setPriority(priority);
    c.setStatus("SUBMITTED");
    c.setDepartment(teams.get(cat));
    c.setSubmittedBy(u.getName());
    c.setSubmittedEmail(u.getEmail());
    c.setSchoolName(u.getSchoolName());
    c.setCampusName(u.getCampusName());
    c.setCreatedAt(now);
    c.setUpdatedAt(now);
    c.setDueAt(now.plus(sla.get(cat), ChronoUnit.HOURS));
    c.setAuditTrail(new ArrayList<>());
    activity(c,u,"Complaint submitted");
    return repo.save(c);
  }

  public Complaint status(long id, String status, UserAccount u) {
    Complaint c = one(id);
    checkStaffAccess(c,u);
    if(status==null || !List.of("SUBMITTED","UNDER_REVIEW","ASSIGNED","IN_PROGRESS","AWAITING_STUDENT","RESOLVED","CLOSED","REJECTED").contains(status))
      throw new IllegalArgumentException("Invalid status");
    if(status.equals(c.getStatus())) return c;
    if(List.of("RESOLVED","CLOSED","REJECTED").contains(c.getStatus()) && !List.of("RESOLVED","CLOSED").contains(status))
      throw new IllegalArgumentException("Use the reopen action for a resolved complaint");
    c.setStatus(status);
    c.setUpdatedAt(Instant.now());
    if(List.of("RESOLVED","CLOSED").contains(status) && c.getResolvedAt()==null) c.setResolvedAt(Instant.now());
    activity(c,u,"Status changed to "+status);
    return repo.save(c);
  }

  public Complaint assign(long id, String assignedTo, UserAccount u) {
    Complaint c = one(id);
    checkStaffAccess(c,u);
    if(assignedTo == null || assignedTo.isBlank() || assignedTo.trim().length()>120)
      throw new IllegalArgumentException("Assigned staff or team is required");
    c.setAssignedTo(assignedTo.trim());
    if("SUBMITTED".equals(c.getStatus())) c.setStatus("ASSIGNED");
    c.setUpdatedAt(Instant.now());
    activity(c,u,"Assigned to "+assignedTo.trim());
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

  private void requireStaff(UserAccount u) {
    if(u == null || !List.of("STAFF","ADMIN").contains(u.getRole()))
      throw new ForbiddenException("Staff access required");
    if("STAFF".equals(u.getRole()) && (u.getSchoolName()==null || u.getSchoolName().isBlank()))
      throw new IllegalArgumentException("Staff institution is required");
  }

  private void checkStaffAccess(Complaint c, UserAccount u) {
    requireStaff(u);
    if(!"ADMIN".equals(u.getRole()) || (u.getSchoolName()!=null && !u.getSchoolName().isBlank())) {
      if(!Objects.equals(c.getSchoolName(),u.getSchoolName()))
        throw new ForbiddenException("Complaint belongs to another institution");
    }
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

  private String safe(String value) { return value==null ? "" : value.replace('|','¦'); }

  private void activity(Complaint c,UserAccount u,String message) {
    if(c.getAuditTrail()==null) c.setAuditTrail(new ArrayList<>());
    c.getAuditTrail().add(Instant.now()+"|"+safe(u.getName())+"|"+safe(message));
  }
}
