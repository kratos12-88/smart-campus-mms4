package com.smartcampus.controller;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

import com.smartcampus.model.Complaint;
import com.smartcampus.model.UserAccount;
import com.smartcampus.service.AuthService;
import com.smartcampus.service.ComplaintService;

@RestController
@RequestMapping("/api")
public class ComplaintController {
  private final ComplaintService complaints;
  private final AuthService auth;

  public ComplaintController(ComplaintService complaints, AuthService auth){
    this.complaints=complaints; this.auth=auth;
  }

  @GetMapping("/health")
  public Map<String,Object> health(){return Map.of("ok",true,"database","mongodb");}

  @GetMapping("/complaints")
  public List<Object> recent(@RequestHeader(value="Authorization",required=false) String h){return complaints.recentViews(optionalUser(h));}

  @GetMapping("/complaints/{id}")
  public Object one(@PathVariable long id,@RequestHeader(value="Authorization",required=false) String h){return complaints.view(complaints.one(id),optionalUser(h));}

  @GetMapping("/complaints/duplicates/check")
  public List<Object> duplicates(@RequestParam String category,@RequestParam String location,@RequestParam String title,@RequestHeader(value="Authorization",required=false) String h){
    return complaints.duplicates(category,location,title,auth.require(h));
  }

  @GetMapping("/auth/notifications")
  public List<Map<String,Object>> notifications(@RequestHeader(value="Authorization",required=false) String h){return complaints.notifications(auth.require(h));}

  @PostMapping("/complaints/{id}/comments")
  public Complaint comment(@PathVariable long id,@RequestBody Map<String,String> body,@RequestHeader(value="Authorization",required=false) String h){return complaints.comment(id,body.get("text"),auth.require(h));}

  @PostMapping("/complaints/{id}/feedback")
  public Complaint feedback(@PathVariable long id,@RequestBody Map<String,Object> body,@RequestHeader(value="Authorization",required=false) String h){
    Object value=body.get("rating");
    if(!(value instanceof Number) || ((Number)value).doubleValue()!=((Number)value).intValue()) throw new IllegalArgumentException("Rating must be a whole number between 1 and 5");
    Object text=body.get("feedback");
    if(text!=null && !(text instanceof String)) throw new IllegalArgumentException("Feedback must be text");
    return complaints.feedback(id,((Number)value).intValue(),(String)text,auth.require(h));
  }

  @PostMapping("/complaints/{id}/reopen")
  public Complaint reopen(@PathVariable long id,@RequestBody Map<String,String> body,@RequestHeader(value="Authorization",required=false) String h){return complaints.reopen(id,body.get("reason"),auth.require(h));}

  @GetMapping("/complaints/mine")
  public List<Complaint> mine(@RequestHeader(value="Authorization",required=false) String h){return complaints.mine(auth.require(h));}

  @PostMapping("/complaints")
  public Complaint create(@RequestBody Complaint c,@RequestHeader(value="Authorization",required=false) String h){return complaints.create(c,auth.require(h));}

  @GetMapping("/staff/complaints")
  public List<Complaint> staff(@RequestHeader(value="Authorization",required=false) String h){return complaints.forStaff(auth.require(h));}

  @PutMapping("/complaints/{id}/status")
  public Complaint status(@PathVariable long id,@RequestBody Map<String,String> b,@RequestHeader(value="Authorization",required=false) String h){
    return complaints.status(id,b.get("status"),auth.require(h));
  }

  @PutMapping("/complaints/{id}/assignment")
  public Complaint assign(@PathVariable long id,@RequestBody Map<String,String> b,@RequestHeader(value="Authorization",required=false) String h){
    return complaints.assign(id,b.getOrDefault("assignedTo",""),auth.require(h));
  }

  @GetMapping("/dashboard/summary")
  public Map<String,Object> summary(){return complaints.summary();}

  @GetMapping("/dashboard/analytics")
  public Map<String,Object> analytics(@RequestHeader(value="Authorization",required=false) String h){return complaints.analytics(auth.require(h));}

  private UserAccount optionalUser(String header) {return header==null || header.isBlank() ? null : auth.require(header);}
}
