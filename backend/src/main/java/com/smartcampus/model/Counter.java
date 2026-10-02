package com.smartcampus.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("counters")
public class Counter {
  @Id private String id;
  private long seq;
  public String getId(){return id;} public void setId(String v){id=v;}
  public long getSeq(){return seq;} public void setSeq(long v){seq=v;}
}
