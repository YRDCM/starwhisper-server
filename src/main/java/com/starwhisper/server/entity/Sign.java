package com.starwhisper.server.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 星座
 */
@Entity                          // 新增：这个类对应一张数据库表
@Table(name = "signs")           // 新增：表名叫 signs
public class Sign {

  @Id                          // 新增：主键
  @GeneratedValue(strategy = GenerationType.IDENTITY)  // 新增：自增（数据库自动生成）
  private Long id;

  private String name;
  private String nameEn;
  private String emoji;      // 符号：♈
  private String element;    // 象：火象/土象/风象/水象
  private String dateRange;  // 日期范围展示：03.21-04.19
  private Integer startMonth; // 起始月（算星座用）
  private Integer startDay;
  private Integer endMonth;   // 结束月
  private Integer endDay;
  // ... 其余字段和 getter/setter 不动

  public Sign() {
  }

  public Sign(Long id, String name, String nameEn, String emoji, String element, String dateRange, Integer startMonth, Integer startDay, Integer endMonth, Integer endDay) {
    this.id = id;
    this.name = name;
    this.nameEn = nameEn;
    this.emoji = emoji;
    this.element = element;
    this.dateRange = dateRange;
    this.startMonth = startMonth;
    this.startDay = startDay;
    this.endMonth = endMonth;
    this.endDay = endDay;
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getNameEn() {
    return nameEn;
  }

  public void setNameEn(String nameEn) {
    this.nameEn = nameEn;
  }

  public String getEmoji() {
    return emoji;
  }

  public void setEmoji(String emoji) {
    this.emoji = emoji;
  }

  public String getElement() {
    return element;
  }

  public void setElement(String element) {
    this.element = element;
  }

  public String getDateRange() {
    return dateRange;
  }

  public void setDateRange(String dateRange) {
    this.dateRange = dateRange;
  }

  public Integer getStartMonth() {
    return startMonth;
  }

  public void setStartMonth(Integer startMonth) {
    this.startMonth = startMonth;
  }

  public Integer getStartDay() {
    return startDay;
  }

  public void setStartDay(Integer startDay) {
    this.startDay = startDay;
  }

  public Integer getEndMonth() {
    return endMonth;
  }

  public void setEndMonth(Integer endMonth) {
    this.endMonth = endMonth;
  }

  public Integer getEndDay() {
    return endDay;
  }

  public void setEndDay(Integer endDay) {
    this.endDay = endDay;
  }
}