package com.smartcampus.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import com.smartcampus.model.Complaint;
import com.smartcampus.model.Counter;
import com.smartcampus.model.UserAccount;
import com.smartcampus.repository.ComplaintRepository;

class ComplaintServiceTest {
  @Mock private ComplaintRepository repo;
  @Mock private MongoTemplate mongo;

  private ComplaintService service;
  private UserAccount student;
  private UserAccount staff;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    service = new ComplaintService(repo, mongo);

    student = new UserAccount();
    student.setName("Student One");
    student.setEmail("student@example.com");
    student.setRole("STUDENT");
    student.setSchoolName("Example University");
    student.setCampusName("Main Campus");

    staff = new UserAccount();
    staff.setName("Staff One");
    staff.setEmail("staff@example.com");
    staff.setRole("STAFF");
    staff.setSchoolName("Example University");
    staff.setCampusName("Main Campus");
  }

  @Test
  void createPopulatesWorkflowFieldsAndSla() {
    Counter counter = new Counter();
    counter.setSeq(42);
    when(mongo.findAndModify(
      any(Query.class),
      any(Update.class),
      any(FindAndModifyOptions.class),
      eq(Counter.class)
    )).thenReturn(counter);
    when(repo.save(any(Complaint.class))).thenAnswer(invocation -> invocation.getArgument(0));

    Complaint input = new Complaint();
    input.setTitle("No power");
    input.setDescription("Power outage in laboratory");
    input.setLocation("Science Block");
    input.setCategory("ELECTRICITY");
    input.setPriority("HIGH");

    Complaint created = service.create(input, student);

    assertEquals(Long.valueOf(42), created.getId());
    assertEquals("SUBMITTED", created.getStatus());
    assertEquals("Electrical Unit", created.getDepartment());
    assertEquals("student@example.com", created.getSubmittedEmail());
    assertEquals("Example University", created.getSchoolName());
    assertEquals("HIGH", created.getPriority());
    assertNotNull(created.getDueAt());
    assertFalse(created.getAuditTrail().isEmpty());
  }

  @Test
  void createRejectsMissingDescription() {
    Complaint input = new Complaint();
    input.setTitle("No power");
    input.setLocation("Science Block");

    assertThrows(IllegalArgumentException.class, () -> service.create(input, student));
  }

  @Test
  void assignmentMovesSubmittedComplaintToAssigned() {
    Complaint c = complaint(7L, "SUBMITTED", "Example University");
    when(repo.findById(7L)).thenReturn(Optional.of(c));
    when(repo.save(any(Complaint.class))).thenAnswer(invocation -> invocation.getArgument(0));

    Complaint updated = service.assign(7L, "Electrical Team", staff);

    assertEquals("ASSIGNED", updated.getStatus());
    assertEquals("Electrical Team", updated.getAssignedTo());
  }

  @Test
  void assignmentRejectsBlankAssignee() {
    Complaint c = complaint(8L, "SUBMITTED", "Example University");
    when(repo.findById(8L)).thenReturn(Optional.of(c));

    assertThrows(IllegalArgumentException.class, () -> service.assign(8L, " ", staff));
  }

  @Test
  void statusRejectsInvalidWorkflowState() {
    Complaint c = complaint(9L, "ASSIGNED", "Example University");
    when(repo.findById(9L)).thenReturn(Optional.of(c));

    assertThrows(IllegalArgumentException.class, () -> service.status(9L, "DONE", staff));
  }

  @Test
  void staffCannotChangeComplaintFromAnotherInstitution() {
    Complaint c = complaint(11L, "SUBMITTED", "Other University");
    when(repo.findById(11L)).thenReturn(Optional.of(c));

    assertThrows(IllegalArgumentException.class, () -> service.assign(11L, "Team A", staff));
  }

  @Test
  void analyticsCalculatesAverageResolutionTime() {
    Complaint resolved = complaint(15L, "RESOLVED", "Example University");
    resolved.setCategory("ICT");
    resolved.setCreatedAt(Instant.parse("2026-11-01T10:00:00Z"));
    resolved.setResolvedAt(Instant.parse("2026-11-01T12:30:00Z"));

    when(repo.findBySchoolNameIgnoreCaseOrderByCreatedAtDesc("Example University"))
      .thenReturn(List.of(resolved));

    Map<String,Object> analytics = service.analytics(staff);

    assertEquals(1, analytics.get("total"));
    assertEquals(2.5, (Double) analytics.get("averageResolutionHours"), 0.001);
  }

  @Test
  void studentCannotReadStaffComplaintsOrAnalytics() {
    assertThrows(IllegalArgumentException.class, () -> service.forStaff(student));
    assertThrows(IllegalArgumentException.class, () -> service.analytics(student));
    verifyNoInteractions(repo);
  }

  @Test
  void staffListIsScopedToInstitution() {
    when(repo.findBySchoolNameIgnoreCaseOrderByCreatedAtDesc("Example University")).thenReturn(List.of());
    assertTrue(service.forStaff(staff).isEmpty());
    verify(repo).findBySchoolNameIgnoreCaseOrderByCreatedAtDesc("Example University");
    verify(repo, never()).findTop100ByOrderByCreatedAtDesc();
  }

  @Test
  void staffWithoutInstitutionIsRejected() {
    staff.setSchoolName(" ");
    assertThrows(IllegalArgumentException.class, () -> service.forStaff(staff));
    verifyNoInteractions(repo);
  }

  @Test
  void unscopedAdminCanReadStaffComplaints() {
    staff.setRole("ADMIN");
    staff.setSchoolName("");
    when(repo.findTop100ByOrderByCreatedAtDesc()).thenReturn(List.of());
    assertTrue(service.forStaff(staff).isEmpty());
    verify(repo).findTop100ByOrderByCreatedAtDesc();
  }

  private Complaint complaint(Long id, String status, String schoolName) {
    Complaint c = new Complaint();
    c.setId(id);
    c.setStatus(status);
    c.setSchoolName(schoolName);
    c.setAuditTrail(new java.util.ArrayList<>());
    return c;
  }
}
