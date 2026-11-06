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

import com.smartcampus.config.ForbiddenException;
import com.smartcampus.config.NotFoundException;
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

    Complaint input = validComplaint();
    input.setTitle("No power in laboratory");
    input.setDescription("Power outage in the science laboratory since morning.");
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
  void createRejectsShortDescription() {
    Complaint input = validComplaint();
    input.setDescription("Too short");
    assertThrows(IllegalArgumentException.class, () -> service.create(input, student));
  }

  @Test
  void createRejectsMissingMapTag() {
    Complaint input = validComplaint();
    input.setMapX(null);
    input.setMapY(null);
    assertThrows(IllegalArgumentException.class, () -> service.create(input, student));
  }

  @Test
  void createRejectsInvalidCategoryAndPriority() {
    Complaint badCategory = validComplaint();
    badCategory.setCategory("FAKE");
    assertThrows(IllegalArgumentException.class, () -> service.create(badCategory, student));

    Complaint badPriority = validComplaint();
    badPriority.setPriority("CRITICAL");
    assertThrows(IllegalArgumentException.class, () -> service.create(badPriority, student));
  }

  @Test
  void nonStudentCannotCreateComplaint() {
    assertThrows(ForbiddenException.class, () -> service.create(validComplaint(), staff));
  }

  @Test
  void complaintReferenceMustExist() {
    when(repo.findById(999L)).thenReturn(Optional.empty());
    assertThrows(NotFoundException.class, () -> service.one(999L));
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
  void assignmentRejectsBlankOrClosedComplaint() {
    Complaint c = complaint(8L, "SUBMITTED", "Example University");
    when(repo.findById(8L)).thenReturn(Optional.of(c));
    assertThrows(IllegalArgumentException.class, () -> service.assign(8L, " ", staff));

    Complaint closed = complaint(81L, "CLOSED", "Example University");
    when(repo.findById(81L)).thenReturn(Optional.of(closed));
    assertThrows(IllegalArgumentException.class, () -> service.assign(81L, "Team A", staff));
  }

  @Test
  void statusAllowsOnlyValidTransitions() {
    Complaint c = complaint(9L, "ASSIGNED", "Example University");
    when(repo.findById(9L)).thenReturn(Optional.of(c));
    when(repo.save(any(Complaint.class))).thenAnswer(invocation -> invocation.getArgument(0));

    Complaint updated = service.status(9L, "IN_PROGRESS", staff);
    assertEquals("IN_PROGRESS", updated.getStatus());
  }

  @Test
  void statusRejectsSkippingWorkflow() {
    Complaint c = complaint(10L, "SUBMITTED", "Example University");
    when(repo.findById(10L)).thenReturn(Optional.of(c));

    IllegalArgumentException ex = assertThrows(
      IllegalArgumentException.class,
      () -> service.status(10L, "RESOLVED", staff)
    );
    assertTrue(ex.getMessage().contains("Invalid status transition"));
  }

  @Test
  void statusRejectsUnknownState() {
    Complaint c = complaint(12L, "ASSIGNED", "Example University");
    when(repo.findById(12L)).thenReturn(Optional.of(c));

    assertThrows(IllegalArgumentException.class, () -> service.status(12L, "DONE", staff));
  }

  @Test
  void staffCannotChangeComplaintFromAnotherInstitution() {
    Complaint c = complaint(11L, "SUBMITTED", "Other University");
    when(repo.findById(11L)).thenReturn(Optional.of(c));

    assertThrows(ForbiddenException.class, () -> service.assign(11L, "Team A", staff));
  }

  @Test
  void publicTrackingViewDoesNotExposeStudentIdentity() {
    Complaint c = complaint(20L, "SUBMITTED", "Example University");
    c.setTitle("Wi-Fi outage");
    c.setDescription("Wi-Fi is unavailable in the main library.");
    c.setLocation("Main Library");
    c.setCategory("ICT");
    c.setPriority("MEDIUM");
    c.setDepartment("ICT Services");
    c.setSubmittedBy("Private Student");
    c.setSubmittedEmail("private@example.com");
    when(repo.findById(20L)).thenReturn(Optional.of(c));

    Map<String,Object> view = service.publicView(20L);

    assertEquals(20L, view.get("id"));
    assertFalse(view.containsKey("submittedEmail"));
    assertFalse(view.containsKey("submittedBy"));
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
    assertThrows(ForbiddenException.class, () -> service.forStaff(student));
    assertThrows(ForbiddenException.class, () -> service.analytics(student));
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
    assertThrows(ForbiddenException.class, () -> service.forStaff(staff));
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

  private Complaint validComplaint() {
    Complaint c = new Complaint();
    c.setTitle("Broken Wi-Fi access point");
    c.setDescription("The wireless access point has been offline since this morning.");
    c.setLocation("Main Library");
    c.setCategory("ICT");
    c.setPriority("MEDIUM");
    c.setMapLabel("Main Library");
    c.setMapX(50.0);
    c.setMapY(50.0);
    return c;
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
