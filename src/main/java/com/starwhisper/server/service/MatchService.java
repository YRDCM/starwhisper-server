package com.starwhisper.server.service;

import com.starwhisper.server.dto.MatchVO;
import com.starwhisper.server.entity.Sign;
import com.starwhisper.server.repository.SignRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.Map;
import java.util.Random;

/**
 * 星座配对业务层
 * 优先走 ShowAPI 872-2 真实配对数据；任何失败（没配 key、超时、错误码）
 * 都回退到本地确定性生成，绝不让外部接口挂掉拖垮用户请求
 */
@Service
public class MatchService {

  private static final Logger log = LoggerFactory.getLogger(MatchService.class);

  private static final String API_URL = "https://route.showapi.com/872-2";

  // 接口要求的星座拼音（与运势接口 872-1 同款映射，天秤是 tiancheng）
  private static final Map<String, String> PINYIN = Map.ofEntries(
      Map.entry("aries", "baiyang"),
      Map.entry("taurus", "jinniu"),
      Map.entry("gemini", "shuangzi"),
      Map.entry("cancer", "juxie"),
      Map.entry("leo", "shizi"),
      Map.entry("virgo", "chunv"),
      Map.entry("libra", "tiancheng"),
      Map.entry("scorpio", "tianxie"),
      Map.entry("sagittarius", "sheshou"),
      Map.entry("capricorn", "mojie"),
      Map.entry("aquarius", "shuiping"),
      Map.entry("pisces", "shuangyu")
  );

  // 本地兜底的文案池
  private static final String[] SUGGEST_POOL = {
      "多制造一些共同话题，感情会在细水长流中升温。",
      "给彼此留一点独处空间，距离产生美。",
      "重要决定一起做，参与感是安全感的来源。",
      "偶尔来点小惊喜，平淡的日子需要仪式感。",
      "吵架时先处理情绪，再处理事情。",
      "多肯定对方的付出，赞美是最便宜的浪漫。",
      "培养一个共同爱好，让相处不止于吃饭逛街。",
      "坦诚说出需求，别让对方猜，猜久了会累。",
      "记住对方的忌口和小习惯，细节最能打动人。"
  };
  private static final String[] ATTENTION_POOL = {
      "别把最坏的情绪留给最亲的人。",
      "冷战解决不了问题，主动破冰不丢人。",
      "翻旧账是感情的头号杀手，过去的事让它过去。",
      "经济观不一致要趁早谈开，别等爆发。",
      "不要在气头上做任何决定。",
      "尊重对方的社交圈，信任是相互的。",
      "比较是毒药，别看别人家的对象。",
      "再忙也要留出专属的二人时间。",
      "承诺说出口就要兑现，信用破产很难重建。"
  };
  private static final String[] REVIEW_POOL = {
      "一对需要磨合但潜力不小的组合。",
      "性格互补，长久相处的关键在沟通。",
      "热情有余耐心不足，慢一点会更稳。",
      "缘分不浅，懂得珍惜才能走得长远。",
      "相似的价值观是这段关系最大的底牌。"
  };

  private final SignRepository signRepository;
  private final FortuneGenerator fortuneGenerator;
  private final RestClient restClient;
  private final String appKey;

  public MatchService(SignRepository signRepository,
                      FortuneGenerator fortuneGenerator,
                      @Value("${showapi.appKey:}") String appKey) {
    this.signRepository = signRepository;
    this.fortuneGenerator = fortuneGenerator;
    this.appKey = appKey;

    // 超时与运势接口一致：连接 5s / 读取 15s
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(5000);
    factory.setReadTimeout(15000);
    this.restClient = RestClient.builder().requestFactory(factory).build();
  }

  /**
   * 配对主入口：能走 ShowAPI 就走，失败自动回退本地生成
   */
  public MatchVO match(String query1, String query2, int gender1, int gender2) {
    Sign star1 = resolveSign(query1);
    Sign star2 = resolveSign(query2);

    if (StringUtils.hasText(appKey)) {
      try {
        return fetchFromShowapi(star1, star2, gender1, gender2);
      } catch (Exception e) {
        log.warn("ShowAPI 配对接口调用失败，回退本地生成：{} vs {}, 原因={}",
            star1.getNameEn(), star2.getNameEn(), e.getMessage());
      }
    }
    return localMatch(star1, star2, gender1, gender2);
  }

  /**
   * 调 ShowAPI 872-2：表单提交 star1/star2（拼音）+ gender1/gender2（1男0女）
   * 响应体 showapi_res_body 直接就是配对结果对象（不是数组），各字段全是字符串
   */
  private MatchVO fetchFromShowapi(Sign star1, Sign star2, int gender1, int gender2) {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("star1", PINYIN.get(star1.getNameEn().toLowerCase()));
    form.add("star2", PINYIN.get(star2.getNameEn().toLowerCase()));
    form.add("gender1", String.valueOf(gender1));
    form.add("gender2", String.valueOf(gender2));
    form.add("appKey", appKey);

    Map<String, Object> root = restClient.post()
        .uri(API_URL)
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .body(form)
        .retrieve()
        .body(new ParameterizedTypeReference<>() {
        });

    // 外层信封：showapi_res_code == 0 才成功
    Integer resCode = root == null ? null : scoreValue(root.get("showapi_res_code"));
    if (resCode == null || resCode != 0) {
      throw new IllegalStateException("ShowAPI 调用失败：" + (root == null ? "空响应" : root.get("showapi_res_error")));
    }
    // 内层业务码：ret_code == "0"，body 本身就是结果对象
    Object bodyObj = root.get("showapi_res_body");
    if (!(bodyObj instanceof Map)) {
      throw new IllegalStateException("ShowAPI 响应里没有 showapi_res_body");
    }
    @SuppressWarnings("unchecked")
    Map<String, Object> body = (Map<String, Object>) bodyObj;
    if (!"0".equals(String.valueOf(body.get("ret_code")))) {
      throw new IllegalStateException("ShowAPI 业务返回失败，ret_code=" + body.get("ret_code"));
    }

    MatchVO vo = baseVO(star1, star2, "SHOWAPI");
    MatchVO.Scores scores = new MatchVO.Scores();
    // 线上实测：match 是 "90分" 带单位字符串，其余分项是 1-5 星值，
    // 前端契约是 0-100，所以分项星值 ×20 归一（≤5 认为是星值，>5 认为已是百分制）
    scores.setOverall(scoreValue(body.get("match")));            // 综合指数
    scores.setLove(starScoreTo100(body.get("love")));            // 爱情
    scores.setFriendship(starScoreTo100(body.get("friendship"))); // 友情
    scores.setMarriage(starScoreTo100(body.get("married")));     // 婚姻
    scores.setForever(starScoreTo100(body.get("forever")));      // 天长地久
    scores.setLqxy(starScoreTo100(body.get("lqxy")));            // 两情相悦
    scores.setAffection(starScoreTo100(body.get("affection")));  // 亲情
    vo.setScores(scores);

    vo.setProportion(textValue(body.get("proportion")));
    vo.setSuggest(textValue(body.get("suggest")));
    vo.setPredestination(textValue(body.get("predestination")));
    vo.setMatchCase(textValue(body.get("match_case")));
    vo.setAttention(textValue(body.get("attention")));
    vo.setReview(textValue(body.get("review")));
    return vo;
  }

  /**
   * 本地兜底：确定性伪随机（同一对组合同一天结果固定），指数 40-99 不给低分添堵
   */
  private MatchVO localMatch(Sign star1, Sign star2, int gender1, int gender2) {
    // 种子混合双方星座和性别，盐值 300 与其他模块隔离（运势=0/1/2，塔罗=100，易经=200）
    long pairSeed = star1.getId() * 13 + star2.getId() * 7 + (long) gender1 * 3 + gender2;
    Random random = fortuneGenerator.newSeededRandom(pairSeed, LocalDate.now(), 300);

    MatchVO vo = baseVO(star1, star2, "LOCAL");
    MatchVO.Scores scores = new MatchVO.Scores();
    scores.setOverall(40 + random.nextInt(60));
    scores.setLove(40 + random.nextInt(60));
    scores.setFriendship(40 + random.nextInt(60));
    scores.setMarriage(40 + random.nextInt(60));
    scores.setForever(40 + random.nextInt(60));
    scores.setLqxy(40 + random.nextInt(60));
    scores.setAffection(40 + random.nextInt(60));
    vo.setScores(scores);

    int left = 40 + random.nextInt(21); // 比重在 40:60 ~ 60:40 之间，不会太极端
    vo.setProportion(left + ":" + (100 - left));
    vo.setSuggest(SUGGEST_POOL[random.nextInt(SUGGEST_POOL.length)]);
    vo.setAttention(ATTENTION_POOL[random.nextInt(ATTENTION_POOL.length)]);
    vo.setReview(REVIEW_POOL[random.nextInt(REVIEW_POOL.length)]);
    vo.setPredestination(star1.getName() + "与" + star2.getName() + "的相遇，是星轨交错的一次必然。");
    vo.setMatchCase(star1.getName() + " × " + star2.getName());
    return vo;
  }

  /**
   * 组装骨架 VO（星座双方 + 来源标记）
   */
  private MatchVO baseVO(Sign star1, Sign star2, String source) {
    MatchVO vo = new MatchVO();
    vo.setStar1(MatchVO.SignBrief.of(star1));
    vo.setStar2(MatchVO.SignBrief.of(star2));
    vo.setSource(source);
    return vo;
  }

  /**
   * 解析星座参数：先试英文名（忽略大小写），再试中文名
   */
  private Sign resolveSign(String signQuery) {
    if (signQuery == null || signQuery.isBlank()) {
      throw new IllegalArgumentException("缺少星座参数（star1/star2）");
    }
    String query = signQuery.trim();
    return signRepository.findByNameEnIgnoreCase(query)
        .or(() -> signRepository.findByName(query))
        .orElseThrow(() -> new IllegalArgumentException("不认识的星座：" + query + "（支持英文名或中文名，如 aries / 白羊座）"));
  }

  /**
   * 分项星值归一到百分制：接口给的是 1-5 星，契约是 0-100
   * ≤5 认为是星值 ×20；>5 认为已是百分制原样返回
   */
  private Integer starScoreTo100(Object value) {
    Integer score = scoreValue(value);
    if (score == null) {
      return null;
    }
    return score <= 5 ? score * 20 : score;
  }

  /**
   * 指数解析：线上实测是数字字符串，综合指数还带单位（"90分"），
   * 抠出数字部分解析，非法值给 null 不抛异常
   */
  private Integer scoreValue(Object value) {
    if (value instanceof Number) {
      return ((Number) value).intValue();
    }
    if (value != null) {
      String digits = value.toString().replaceAll("[^0-9]", "");
      if (!digits.isEmpty()) {
        try {
          return Integer.parseInt(digits);
        } catch (NumberFormatException ignored) {
          // 上游格式异常，落 null
        }
      }
    }
    return null;
  }

  private String textValue(Object value) {
    if (value == null) {
      return null;
    }
    String text = value.toString();
    return text.isBlank() ? null : text;
  }
}
