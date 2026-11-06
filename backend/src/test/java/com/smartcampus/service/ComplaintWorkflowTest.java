package com.smartcampus.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import com.smartcampus.model.*;
import com.smartcampus.repository.ComplaintRepository;
import com.smartcampus.config.ForbiddenException;

class ComplaintWorkflowTest {
  ComplaintRepository repo=mock(ComplaintRepository.class);
  ComplaintService service=new ComplaintService(repo,mock(MongoTemplate.class));
  UserAccount owner=new UserAccount(),other=new UserAccount(),staff=new UserAccount();
  Complaint complaint=new Complaint();

  @BeforeEach void setup(){
    owner.setEmail("owner@example.com");owner.setName("Owner");owner.setRole("STUDENT");owner.setSchoolName("School A");
    other.setEmail("other@example.com");other.setName("Other");other.setRole("STUDENT");other.setSchoolName("School A");
    staff.setEmail("staff@example.com");staff.setName("Staff");staff.setRole("STAFF");staff.setSchoolName("School A");
    complaint.setId(10L);complaint.setTitle("Test case");complaint.setDescription("Private description");
    complaint.setSubmittedEmail(owner.getEmail());complaint.setSubmittedBy(owner.getName());complaint.setSchoolName("School A");
    complaint.setCategory("ICT");complaint.setLocation("Lab");complaint.setStatus("RESOLVED");
    complaint.setResolvedAt(Instant.parse("2026-11-01T12:00:00Z"));
    when(repo.findById(10L)).thenReturn(Optional.of(complaint));
    when(repo.save(any(Complaint.class))).thenAnswer(i->i.getArgument(0));
  }
  @Test void anonymousAndOtherStudentCannotReadPrivateDetails(){
    for(UserAccount user:Arrays.asList(null,other)){
      Map<?,?> view=(Map<?,?>)service.view(complaint,user);
      assertEquals(10L,view.get("id"));assertEquals(false,view.get("privateDetails"));
      for(String key:List.of("submittedEmail","submittedBy","description","evidenceData","comments","auditTrail","mongoId"))assertFalse(view.containsKey(key));
    }
    assertSame(complaint,service.view(complaint,owner));assertSame(complaint,service.view(complaint,staff));
    staff.setSchoolName("School B");assertInstanceOf(Map.class,service.view(complaint,staff));
  }
  @Test void onlyOwnerCanRateOrReopen(){
    assertThrows(ForbiddenException.class,()->service.feedback(10L,5,"",other));
    assertThrows(ForbiddenException.class,()->service.reopen(10L,"Still broken",staff));
    verify(repo,never()).save(any());
  }
  @Test void ratingsValidateRangeAndResolvedState(){
    assertThrows(IllegalArgumentException.class,()->service.feedback(10L,6,"",owner));
    complaint.setStatus("IN_PROGRESS");
    assertThrows(IllegalArgumentException.class,()->service.feedback(10L,5,"",owner));
  }
  @Test void feedbackThenReopenResetsResolutionAndRating(){
    service.feedback(10L,4,"Good",owner);assertEquals(4,complaint.getRating());
    assertThrows(IllegalArgumentException.class,()->service.feedback(10L,5,"",owner));
    service.reopen(10L,"Issue returned",owner);
    assertEquals("IN_PROGRESS",complaint.getStatus());assertNull(complaint.getRating());assertNull(complaint.getResolvedAt());
    assertTrue(complaint.getDueAt().isAfter(Instant.now()));
  }
  @Test void commentsRequireParticipationAndSafeTimelineDelimiters(){
    assertThrows(ForbiddenException.class,()->service.comment(10L,"Hello",other));
    service.comment(10L,"A | B",owner);
    assertEquals(3,complaint.getComments().get(0).split("\\|").length);
    assertThrows(IllegalArgumentException.class,()->service.comment(10L," ",owner));
  }
  @Test void closingPreservesOriginalResolutionTime(){
    Instant original=complaint.getResolvedAt();
    service.status(10L,"CLOSED",staff);assertEquals(original,complaint.getResolvedAt());
    assertThrows(IllegalArgumentException.class,()->service.status(10L,"IN_PROGRESS",staff));
  }
  @Test void createRejectsInvalidCoordinatesAndNullCategory(){
    complaint.setPriority("LOW");complaint.setMapX(101.0);complaint.setMapY(10.0);
    assertThrows(IllegalArgumentException.class,()->service.create(complaint,owner));
    complaint.setMapX(null);complaint.setMapY(null);complaint.setCategory(null);
    assertThrows(IllegalArgumentException.class,()->service.create(complaint,owner));
  }
}
