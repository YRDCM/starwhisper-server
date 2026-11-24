package com.starwhisper.server.dto;

import java.util.List;

/**
 * 起卦结果视图对象：本卦 + 之卦（有变爻时）+ 每爻明细
 */
public class CastVO {

  private HexagramVO primary;            // 本卦
  private HexagramVO changed;            // 之卦（变卦），无变爻时为 null
  private List<Integer> changingLines;   // 变爻位置列表（1-6，自下而上）

  /**
   * 每一爻的明细
   */
  public static class LineDetail {
    private int position;     // 爻位 1-6，自下而上（1=初爻）
    private String label;     // 老阳/少阳/少阴/老阴
    private boolean yang;     // 阳爻 true / 阴爻 false
    private boolean changing; // 是否变爻（老阳老阴为变爻）

    public LineDetail(int position, String label, boolean yang, boolean changing) {
      this.position = position;
      this.label = label;
      this.yang = yang;
      this.changing = changing;
    }

    public int getPosition() {
      return position;
    }

    public String getLabel() {
      return label;
    }

    public boolean isYang() {
      return yang;
    }

    public boolean isChanging() {
      return changing;
    }
  }

  private List<LineDetail> linesDetail;  // 每爻明细，自下而上

  public static CastVO of(HexagramVO primary, HexagramVO changed,
                          List<Integer> changingLines, List<LineDetail> linesDetail) {
    CastVO vo = new CastVO();
    vo.primary = primary;
    vo.changed = changed;
    vo.changingLines = changingLines;
    vo.linesDetail = linesDetail;
    return vo;
  }

  public HexagramVO getPrimary() {
    return primary;
  }

  public HexagramVO getChanged() {
    return changed;
  }

  public List<Integer> getChangingLines() {
    return changingLines;
  }

  public List<LineDetail> getLinesDetail() {
    return linesDetail;
  }
}
