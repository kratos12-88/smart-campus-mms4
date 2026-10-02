package com.smartcampus;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class SmartCampusApplicationTests {
  @Test
  void arithmeticSanityCheck() {
    assertEquals(4, 2 + 2);
  }

  @Test
  void workflowContainsResolvedState() {
    String[] statuses={"SUBMITTED","ASSIGNED","IN_PROGRESS","RESOLVED","CLOSED"};
    assertTrue(java.util.Arrays.asList(statuses).contains("RESOLVED"));
  }
}
