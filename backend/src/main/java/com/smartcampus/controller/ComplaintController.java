package com.smartcampus.controller;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

import com.smartcampus.model.Complaint;
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
  public List<Complaint> recent(){return complaints.recent();}

  @GetMapping("/complaints/{id}")
  public Complaint one(@PathVariable long id){return complaints.one(id);}

  @GetMapping("/complaints/mine")
  public List<Complaint> mine(@RequestHeader("Authorization") String h){return complaints.mine(auth.require(h));}

  @PostMapping("/complaints")
  public Complaint create(@RequestBody Complaint c,@RequestHeader("Authorization") String h){return complaints.create(c,auth.require(h));}

  @GetMapping("/staff/complaints")
  public List<Complaint> staff(@RequestHeader("Authorization") String h){return complaints.forStaff(auth.require(h));}

  @PutMapping("/complaints/{id}/status")
  public Complaint status(@PathVariable long id,@RequestBody Map<String,String> b,@RequestHeader("Authorization") String h){
    return complaints.status(id,b.get("status"),auth.require(h));
  }

  @PutMapping("/complaints/{id}/assignment")
  public Complaint assign(@PathVariable long id,@RequestBody Map<String,String> b,@RequestHeader("Authorization") String h){
    return complaints.assign(id,b.getOrDefault("assignedTo",""),auth.require(h));
  }

  @GetMapping("/dashboard/summary")
  public Map<String,Object> summary(){return complaints.summary();}

  @GetMapping("/dashboard/analytics")
  public Map<String,Object> analytics(@RequestHeader("Authorization") String h){return complaints.analytics(auth.require(h));}
}
