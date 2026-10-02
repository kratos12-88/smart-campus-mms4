package com.smartcampus.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("complaints")
public class Complaint {
  @Id private String mongoId;
  @Indexed(unique=true) private Long id;
  private String title,description,location,category,priority,status,department,assignedTo,submittedBy,submittedEmail,schoolName,campusName,mapLabel;
  private Double mapX,mapY;
  private Instant createdAt,updatedAt,dueAt,resolvedAt;
  private Integer rating;
  private String feedback;
  private List<String> auditTrail=new ArrayList<>();

  public String getMongoId(){return mongoId;} public void setMongoId(String v){mongoId=v;}
  public Long getId(){return id;} public void setId(Long v){id=v;}
  public String getTitle(){return title;} public void setTitle(String v){title=v;}
  public String getDescription(){return description;} public void setDescription(String v){description=v;}
  public String getLocation(){return location;} public void setLocation(String v){location=v;}
  public String getCategory(){return category;} public void setCategory(String v){category=v;}
  public String getPriority(){return priority;} public void setPriority(String v){priority=v;}
  public String getStatus(){return status;} public void setStatus(String v){status=v;}
  public String getDepartment(){return department;} public void setDepartment(String v){department=v;}
  public String getAssignedTo(){return assignedTo;} public void setAssignedTo(String v){assignedTo=v;}
  public String getSubmittedBy(){return submittedBy;} public void setSubmittedBy(String v){submittedBy=v;}
  public String getSubmittedEmail(){return submittedEmail;} public void setSubmittedEmail(String v){submittedEmail=v;}
  public String getSchoolName(){return schoolName;} public void setSchoolName(String v){schoolName=v;}
  public String getCampusName(){return campusName;} public void setCampusName(String v){campusName=v;}
  public String getMapLabel(){return mapLabel;} public void setMapLabel(String v){mapLabel=v;}
  public Double getMapX(){return mapX;} public void setMapX(Double v){mapX=v;}
  public Double getMapY(){return mapY;} public void setMapY(Double v){mapY=v;}
  public Instant getCreatedAt(){return createdAt;} public void setCreatedAt(Instant v){createdAt=v;}
  public Instant getUpdatedAt(){return updatedAt;} public void setUpdatedAt(Instant v){updatedAt=v;}
  public Instant getDueAt(){return dueAt;} public void setDueAt(Instant v){dueAt=v;}
  public Instant getResolvedAt(){return resolvedAt;} public void setResolvedAt(Instant v){resolvedAt=v;}
  public Integer getRating(){return rating;} public void setRating(Integer v){rating=v;}
  public String getFeedback(){return feedback;} public void setFeedback(String v){feedback=v;}
  public List<String> getAuditTrail(){return auditTrail;} public void setAuditTrail(List<String> v){auditTrail=v;}
}
