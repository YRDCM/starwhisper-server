package com.starwhisper.server.service.impl;

import com.starwhisper.server.entity.FortuneDaily;
import com.starwhisper.server.entity.Sign;
import com.starwhisper.server.repository.SignRepository;
import com.starwhisper.server.service.FortuneGenerator;
import com.starwhisper.server.service.FortuneProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 万维易源 ShowAPI 星座运势数据源（apiCode 872）
 * 注意：该接口只提供"今天"的运势，且没有健康分、没有宜忌 ——
 * 宜忌用本地生成器补齐（混合模式），健康分留 null，前端按可空处理
 */
@Component
public class ShowapiFortuneProvider implements FortuneProvider {

  private static final Logger log = LoggerFactory.getLogger(ShowapiFortuneProvider.class);

  private static final String API_URL = "https://route.showapi.com/872-1";

  // 接口要求的星座拼音参数（坑：天秤是 tiancheng 不是 tianping）
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

  // 模拟模式用到的文案池
  private static final String[] DIRECTIONS = {"正东", "东南", "正南", "西南", "正西", "西北", "正北", "东北"};
  private static final String[] MOCK_COLORS = {"樱花粉", "天空蓝", "奶白色", "葡萄紫", "柠檬黄", "墨绿色", "香槟金"};
  private static final String[] MOCK_TIMES = {"07:00-09:00", "09:00-11:00", "13:00-15:00", "15:00-17:00", "19:00-21:00"};

  // 接口响应统一按 Map 解析，避免依赖具体 JSON 库的类型
  private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
      new ParameterizedTypeReference<>() {
      };

  private final SignRepository signRepository;
  private final FortuneGenerator fortuneGenerator;
  private final RestClient restClient;
  private final String appKey;
  private final boolean mock;

  public ShowapiFortuneProvider(SignRepository signRepository,
                                FortuneGenerator fortuneGenerator,
                                @Value("${showapi.appKey:}") String appKey,
                                @Value("${showapi.mock:false}") boolean mock) {
    this.signRepository = signRepository;
    this.fortuneGenerator = fortuneGenerator;
    this.appKey = appKey;
    this.mock = mock;

    // 官方建议超时：连接 15s / 读取 15s，这里连接收紧到 5s（连不上基本就是网络问题，没必要干等）
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(5000);
    factory.setReadTimeout(15000);
    this.restClient = RestClient.builder().requestFactory(factory).build();
  }

  /**
   * 数据源是否启用：mock 模式直接启用；否则要配了 appKey 才启用
   */
  public boolean isActive() {
    return mock || StringUtils.hasText(appKey);
  }

  @Override
  public FortuneDaily fetch(Sign sign, LocalDate date) {
    Map<String, Object> day = mock ? mockDay(sign, date) : callApi(sign);
    return mapToEntity(day, sign, date);
  }

  /**
   * 真实调用 ShowAPI：POST 表单，star=拼音，appKey 鉴权
   * 任何一步不正常都抛异常，交给上层回退本地生成
   */
  @SuppressWarnings("unchecked")
  private Map<String, Object> callApi(Sign sign) {
    String pinyin = PINYIN.get(sign.getNameEn().toLowerCase());
    if (pinyin == null) {
      throw new IllegalStateException("没有该星座的拼音映射：" + sign.getNameEn());
    }

    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("star", pinyin);
    form.add("appKey", appKey);

    Map<String, Object> root = restClient.post()
        .uri(API_URL)
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .body(form)
        .retrieve()
        .body(MAP_TYPE);

    // 外层信封：showapi_res_code == 0 才是成功
    Integer resCode = root == null ? null : scoreValue(root.get("showapi_res_code"));
    if (resCode == null || resCode != 0) {
      throw new IllegalStateException("ShowAPI 调用失败：" + (root == null ? "空响应" : root.get("showapi_res_error")));
    }
    // 内层业务码：ret_code == "0" 才取到数据
    Object bodyObj = root.get("showapi_res_body");
    if (!(bodyObj instanceof Map)) {
      throw new IllegalStateException("ShowAPI 响应里没有 showapi_res_body");
    }
    Map<String, Object> body = (Map<String, Object>) bodyObj;
    if (!"0".equals(String.valueOf(body.get("ret_code")))) {
      throw new IllegalStateException("ShowAPI 业务返回失败，ret_code=" + body.get("ret_code"));
    }
    Object dayObj = body.get("day");
    // 注意：OpenAPI 文档说 day 是数组，但线上实测返回的是单个对象（文档与实际不符）。
    // 两种形态都兼容：是 Map 就直接用，是 List 就取第一个元素
    Map<String, Object> day;
    if (dayObj instanceof Map) {
      day = (Map<String, Object>) dayObj;
    } else if (dayObj instanceof List && !((List<?>) dayObj).isEmpty()) {
      day = (Map<String, Object>) ((List<?>) dayObj).get(0);
    } else {
      throw new IllegalStateException("ShowAPI 响应里没有 day 数据");
    }
    return day;
  }

  /**
   * 模拟模式：不发任何 HTTP 请求，编一份"长得像真的"的数据，
   * 用和本地生成器同款种子混洗，保证同一星座同一天结果固定（前端联调可复现）
   */
  private Map<String, Object> mockDay(Sign sign, LocalDate date) {
    Random random = fortuneGenerator.newSeededRandom(sign.getId(), date, 100);

    // 贵人星座：确定性挑一个不是自己
    List<Sign> signs = signRepository.findAllByOrderByIdAsc();
    int index = (int) (sign.getId() % signs.size());
    Sign pair = signs.get((index + 1 + random.nextInt(signs.size() - 1)) % signs.size());

    Map<String, Object> node = new HashMap<>();
    node.put("summary_star", 2 + random.nextInt(4));
    node.put("love_star", 2 + random.nextInt(4));
    node.put("money_star", 2 + random.nextInt(4));
    node.put("work_star", 2 + random.nextInt(4));
    node.put("grxz", pair.getName());
    node.put("lucky_num", String.valueOf(1 + random.nextInt(99)));
    node.put("lucky_time", MOCK_TIMES[random.nextInt(MOCK_TIMES.length)]);
    node.put("lucky_direction", DIRECTIONS[random.nextInt(DIRECTIONS.length)]);
    node.put("lucky_color", MOCK_COLORS[random.nextInt(MOCK_COLORS.length)]);
    node.put("day_notice", "【模拟数据】" + sign.getName() + "今日提醒：出门看黄历，遇事莫急躁。");
    node.put("general_txt", "【模拟数据】" + sign.getName() + "今日整体运势平稳中带惊喜，保持节奏即可。");
    node.put("love_txt", "【模拟数据】感情方面多主动一点，单身者有望遇到聊得来的人。");
    node.put("work_txt", "【模拟数据】工作上适合处理积压任务，效率比预期高。");
    node.put("money_txt", "【模拟数据】财运一般，大额支出建议再观望两天。");
    return node;
  }

  /**
   * 把接口 day[0]（或模拟数据）映射成实体
   */
  private FortuneDaily mapToEntity(Map<String, Object> day, Sign sign, LocalDate date) {
    FortuneDaily fortune = new FortuneDaily();
    fortune.setSignId(sign.getId());
    fortune.setFortuneDate(date);

    // 评分：接口满星 5；没有健康分，留 null
    // 注意：文档说评分是数字，线上实测全是字符串（如 "4"），所以必须防御性解析，
    // 解析不出来就给 null，绝不能因为上游格式抽风把请求搞挂
    fortune.setOverallScore(scoreValue(day.get("summary_star")));
    fortune.setLoveScore(scoreValue(day.get("love_star")));
    fortune.setCareerScore(scoreValue(day.get("work_star")));
    fortune.setWealthScore(scoreValue(day.get("money_star")));
    fortune.setHealthScore(null);

    fortune.setLuckyColor(textValue(day.get("lucky_color")));
    fortune.setLuckyNumber(parseLuckyNumber(textValue(day.get("lucky_num"))));
    fortune.setLuckyTime(textValue(day.get("lucky_time")));
    fortune.setLuckyDirection(textValue(day.get("lucky_direction")));

    fortune.setSummary(textValue(day.get("general_txt")));
    fortune.setDayNotice(textValue(day.get("day_notice")));
    fortune.setLoveTxt(textValue(day.get("love_txt")));
    fortune.setWorkTxt(textValue(day.get("work_txt")));
    fortune.setMoneyTxt(textValue(day.get("money_txt")));
    // healthTxt 暂无数据源，保持 null

    // 宜忌：接口没有，用本地生成器的文案池补齐（确定性，重启不变）
    fortune.setDoText(fortuneGenerator.generateDoText(sign.getId(), date));
    fortune.setDontText(fortuneGenerator.generateDontText(sign.getId(), date));

    // 贵人星座（中文名）→ 速配星座 id，查不到就 null，不阻断主流程
    String grxz = textValue(day.get("grxz"));
    if (grxz != null) {
      fortune.setPairSignId(signRepository.findByName(grxz).map(Sign::getId).orElse(null));
    }

    fortune.setSource("SHOWAPI");
    return fortune;
  }

  /**
   * 幸运数字：接口返回的是字符串，解析失败就兜底成 7（经典幸运数字，且确定性）
   */
  private Integer parseLuckyNumber(String luckyNum) {
    if (luckyNum == null) {
      return 7;
    }
    try {
      return Integer.parseInt(luckyNum.trim());
    } catch (NumberFormatException e) {
      return 7;
    }
  }

  /**
   * 评分字段解析：线上实测是字符串（"4"），文档说是数字 —— 两种都收，
   * null/空白/非法值一律返回 null（评分是可空字段，不该抛异常）
   */
  private Integer scoreValue(Object value) {
    if (value instanceof Number) {
      return ((Number) value).intValue();
    }
    if (value != null) {
      String text = value.toString().trim();
      if (!text.isEmpty()) {
        try {
          return Integer.parseInt(text);
        } catch (NumberFormatException ignored) {
          // 上游格式异常，落 null 即可
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
