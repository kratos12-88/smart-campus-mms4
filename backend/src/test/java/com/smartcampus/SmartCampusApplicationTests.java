package com.smartcampus;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import com.smartcampus.model.Complaint;

class SmartCampusApplicationTests {
  @Test
  void complaintModelStoresCoreWorkflowData() {
    Complaint complaint = new Complaint();
    complaint.setId(1L);
    complaint.setStatus("SUBMITTED");
    complaint.setCategory("ICT");

    assertEquals(Long.valueOf(1), complaint.getId());
    assertEquals("SUBMITTED", complaint.getStatus());
    assertEquals("ICT", complaint.getCategory());
  }
}
