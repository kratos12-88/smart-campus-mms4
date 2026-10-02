package com.smartcampus.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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

  public List<Complaint> forStaff(UserAccount u) {
    if("ADMIN".equals(u.getRole()) && (u.getSchoolName()==null || u.getSchoolName().isBlank())) return recent();
    return repo.findBySchoolNameIgnoreCaseOrderByCreatedAtDesc(u.getSchoolName());
  }

  public Complaint create(Complaint c, UserAccount u) {
    Map<String,Integer> sla = Map.of(
      "SECURITY",4,"ICT",24,"ELECTRICITY",24,"WATER",24,
      "FACILITIES",48,"HOSTEL",48,"ACADEMIC",48,"OTHER",72
    );
    Map<String,String> teams = Map.of(
      "SECURITY","Campus Security","ICT","ICT Services","ELECTRICITY","Electrical Unit",
      "WATER","Works & Maintenance","FACILITIES","Facilities","HOSTEL","Student Affairs",
      "ACADEMIC","Academic Affairs","OTHER","General Services"
    );
    String cat = sla.containsKey(c.getCategory()) ? c.getCategory() : "OTHER";
    Instant now = Instant.now();

    c.setId(nextId());
    c.setCategory(cat);
    c.setStatus("SUBMITTED");
    c.setDepartment(teams.get(cat));
    c.setSubmittedBy(u.getName());
    c.setSubmittedEmail(u.getEmail());
    c.setSchoolName(u.getSchoolName());
    c.setCampusName(u.getCampusName());
    c.setCreatedAt(now);
    c.setUpdatedAt(now);
    c.setDueAt(now.plus(sla.get(cat), ChronoUnit.HOURS));
    c.setAuditTrail(new ArrayList<>(List.of("Complaint submitted by "+u.getName())));
    return repo.save(c);
  }

  public Complaint status(long id, String status, UserAccount u) {
    Complaint c = one(id);
    checkStaffAccess(c,u);
    if(!List.of("SUBMITTED","ASSIGNED","IN_PROGRESS","RESOLVED","CLOSED","REJECTED").contains(status))
      throw new IllegalArgumentException("Invalid status");
    c.setStatus(status);
    c.setUpdatedAt(Instant.now());
    c.setResolvedAt(List.of("RESOLVED","CLOSED").contains(status) ? Instant.now() : null);
    c.getAuditTrail().add("Status changed to "+status+" by "+u.getName());
    return repo.save(c);
  }

  public Complaint assign(long id, String assignedTo, UserAccount u) {
    Complaint c = one(id);
    checkStaffAccess(c,u);
    c.setAssignedTo(assignedTo);
    if("SUBMITTED".equals(c.getStatus())) c.setStatus("ASSIGNED");
    c.setUpdatedAt(Instant.now());
    c.getAuditTrail().add("Assigned to "+assignedTo+" by "+u.getName());
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
    double avg = all.stream().filter(c -> c.getRating()!=null).mapToInt(Complaint::getRating).average().orElse(0);
    return Map.of("total",all.size(),"averageResolutionHours",0,"averageRating",Math.round(avg*10.0)/10.0,"byCategory",byCategory);
  }

  private long nextId() {
    Counter c = mongo.findAndModify(
      Query.query(Criteria.where("_id").is("complaint")),
      new Update().inc("seq",1),
      FindAndModifyOptions.options().upsert(true).returnNew(true),
      Counter.class
    );
    return c.getSeq();
  }

  private void checkStaffAccess(Complaint c, UserAccount u) {
    if(!List.of("STAFF","ADMIN").contains(u.getRole()))
      throw new IllegalArgumentException("Staff access required");
    if(!"ADMIN".equals(u.getRole()) || (u.getSchoolName()!=null && !u.getSchoolName().isBlank())) {
      if(!Objects.equals(c.getSchoolName(),u.getSchoolName()))
        throw new IllegalArgumentException("Complaint belongs to another institution");
    }
  }
}
