package com.smartcampus.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("users")
public class UserAccount {
  @Id private String id;
  private String name;
  @Indexed(unique=true) private String email;
  private String passwordHash;
  private String role;
  private String department;
  private String schoolName;
  private String campusName;
  private String schoolType;
  private String schoolState;

  public String getId(){return id;} public void setId(String v){id=v;}
  public String getName(){return name;} public void setName(String v){name=v;}
  public String getEmail(){return email;} public void setEmail(String v){email=v;}
  public String getPasswordHash(){return passwordHash;} public void setPasswordHash(String v){passwordHash=v;}
  public String getRole(){return role;} public void setRole(String v){role=v;}
  public String getDepartment(){return department;} public void setDepartment(String v){department=v;}
  public String getSchoolName(){return schoolName;} public void setSchoolName(String v){schoolName=v;}
  public String getCampusName(){return campusName;} public void setCampusName(String v){campusName=v;}
  public String getSchoolType(){return schoolType;} public void setSchoolType(String v){schoolType=v;}
  public String getSchoolState(){return schoolState;} public void setSchoolState(String v){schoolState=v;}
}
