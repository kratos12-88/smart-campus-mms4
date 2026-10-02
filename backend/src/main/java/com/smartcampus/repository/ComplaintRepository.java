package com.smartcampus.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import com.smartcampus.model.Complaint;

public interface ComplaintRepository extends MongoRepository<Complaint,String> {
  Optional<Complaint> findById(Long id);
  List<Complaint> findTop100ByOrderByCreatedAtDesc();
  List<Complaint> findBySubmittedEmailIgnoreCaseOrderByCreatedAtDesc(String email);
  List<Complaint> findBySchoolNameIgnoreCaseOrderByCreatedAtDesc(String schoolName);
}
