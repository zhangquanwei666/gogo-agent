package com.quanwei.gogo.agent.agent.intent.rule;

import com.quanwei.gogo.agent.agent.enums.IntentCategory;
import com.quanwei.gogo.agent.agent.enums.IntentLevelEnum;
import com.quanwei.gogo.agent.agent.intent.IntentRecognitionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * L1 规则匹配器：只覆盖「意图清晰 + 表达高度模板化」的高频场景 —— 寒暄、报销、政策查询、查询类。
 *
 * <p>规则写在代码里而不是走 intent_seed.yml 的关键词：
 * 模板化的话术（「报销审批到哪一步了」「出差住宿标准是多少」「你好」）靠 contains 词组区分不开，
 * 得有语序和邻近约束才判得准，而这类约束只能用正则表达。
 * 种子文件那份关键词留给 L2 的样本做伴，长尾表达一律交给 L2/L3。
 *
 * <p>意图类别与 prompt/intent-recognition-agent-system.md 的映射表严格对齐，
 * 覆盖范围和刻意留白的部分见 {@link #RULES} 上的说明。
 *
 * <p>本类无任何注入依赖，{@code new IntentRuleMatcher()} 就能单测，
 * 和 {@code IntentRecognitionRouter} 保持一致的可测试性约定。
 */
@Slf4j
@Component
public class IntentRuleMatcher {

    /**
     * 规则命中给的置信度档位。
     *
     * <p>能被这张表命中的都是措辞高度模板化的句子，正则还带了语序和邻近约束
     * （如「审批」后 8 字内出现「到哪了」），判错的概率很低，配得上 HIGH。
     * 拿不准的表达根本进不了这张表，会落到 L2/L3。
     */
    private static final IntentRecognitionResult.Confidence RULE_CONFIDENCE =
            IntentRecognitionResult.Confidence.HIGH;

    /**
     * 子句切分符：标点 + 带时序或递进的连词。
     *
     * <p>不切「和」「跟」「与」—— 这些多数时候连接的是并列实体（「北京和上海的机票」），
     * 切了会把一个意图拆成两个，反而制造出假的歧义信号。
     */
    private static final Pattern CLAUSE_DELIMITER = Pattern.compile(
            "[，。！？；、,.!?;~\\s]+|然后|接着|之后|另外|此外|同时|并且|以及|还要|还得|还想|顺便|再帮|再订|再查|再看|再给");

    /** 子句短于它就不参与歧义计票，两个字以内基本是语气词残片 */
    private static final int MIN_CLAUSE_LENGTH = 3;

    /**
     * 规则表，<b>顺序即优先级</b>，靠前的先判。意图类别与系统提示词的映射表严格对齐。
     *
     * <p>排序原则：<b>越具体越靠前</b>。
     * 「报销审批到哪一步了」既含「报销」也含「审批」，必须让 REIMBURSEMENT 抢在 APPROVAL_QUERY 前面，
     * 否则会被出差审批那条吃掉，路由到管差旅单的智能体去查报销单。
     *
     * <p><b>提示词里这几类刻意不覆盖</b>：
     * <ul>
     *   <li>{@code travel_application} / {@code itinerary_planning} —— 提示词规则 2 要求
     *       「历史中是否已存在该次出差的差旅单」才能定夺，同一句「帮我安排出差行程」在有无审批时
     *       分属两个意图。规则是无状态的，看不到历史，这里判等于瞎猜；</li>
     *   <li>{@code booking} / {@code travel_cancel} / {@code travel_modify} —— 都会改数据。
     *       查错了用户再问一遍，订错退错要走取消和退改，误判代价不对称，不值得用浅层正则去赌。</li>
     * </ul>
     */
    private static final List<Rule> RULES = List.of(

            /* ---------- 寒暄：masterAgent ---------- */
            // 只认「整句都是客套」的情况。用 ^...$ 锚定，是为了让「你好，帮我查报销进度」
            // 落不到这条上 —— 那是一个业务请求，前面那句「你好」不构成意图。
            new Rule(IntentCategory.GREETING,
                    "^(你好|您好|哈喽|哈啰|hello|hi|hey|嗨|早|早上好|中午好|下午好|晚上好|在吗|在么|在不在"
                            + "|你是谁|你叫什么|你是什么|你能做什么|你会做什么|你会什么|能干什么|能帮我做什么"
                            + "|有什么功能|怎么用|谢谢|谢啦|多谢|感谢|好的|辛苦了|再见|拜拜|bye)"
                            + "[\\s呀吗嘛啊哦呢的了~!！。？?，,、]*$",
                    "整句寒暄或问能力"),

            /* ---------- 政策咨询：infoAgent ---------- */
            // 压在报销规则前面：「报销标准是多少」问的是制度，归政策；
            // 「帮我报销这张发票」才是要办事，归报销。含标准/额度/能不能报这类问法的一律先走政策。
            new Rule(IntentCategory.POLICY_QUERY,
                    "(差旅|出差|因公|公务|报销|餐补|餐标|住宿|房费|酒店|机票|舱位|高铁|火车|市内交通|补贴)"
                            + ".{0,8}(标准|额度|政策|规定|上限|限额|要求|规则|能坐|可以坐|能住|可以住|坐什么|住什么)"
                            + "|(能不能报|能报销吗|可以报吗|报不报|哪些能报|什么能报|不能报|能报多少)"
                            + "|(签证|入境|出入境|护照|免签|落地签)",
                    "差旅政策、餐标、住宿标准、签证入境"),

            /* ---------- 报销：reimbursementAgent ---------- */
            // 提示词把发票、报销单、报销进度合并成一个 reimbursement，不再细分动作 ——
            // 反正都由同一个子智能体接手，细分只会在这里制造出无意义的边界判断。
            new Rule(IntentCategory.REIMBURSEMENT,
                    "(报销|报账|发票|专票|普票|电子票|抬头|税号|开票|费用单)",
                    "报销、发票、报销单"),

            /* ---------- 差旅审批与差旅单：itineraryManageAgent ---------- */
            new Rule(IntentCategory.APPROVAL_QUERY,
                    "(审批|审核|申请单|出差申请|差旅申请).{0,8}(进度|状态|结果|到哪|到第几|通过没|通过了没"
                            + "|批没|批了没|批下来|过了没|怎么样了)"
                            + "|(查|查下|查一下|看下|看一下|看看).{0,4}(审批|审核)",
                    "审批进度、状态、结果查询"),
            new Rule(IntentCategory.TRAVEL_ORDER_QUERY,
                    "(差旅单|出差单|行程单|差旅行程|出差记录|订单|我的行程).{0,8}"
                            + "(查|查询|查下|看下|看看|状态|详情|有哪些|列表|记录|到哪)"
                            + "|(查|查下|查一下|看下|看一下|看看|我的).{0,6}(差旅单|出差单|行程单|差旅行程|订单|行程)",
                    "差旅单详情、状态查询"),

            /* ---------- 交通与住宿查询：itineraryPlanAgent ---------- */
            new Rule(IntentCategory.FLIGHT_SEARCH,
                    "(查|查下|查一下|看下|看一下|看看|搜|有没有|有哪些|多少钱|什么价|价格|几点).{0,12}(机票|航班|飞机|航线)"
                            + "|(机票|航班|飞机).{0,10}(多少钱|什么价|价格|贵不贵|有哪些|有没有|几点|时刻|查一下|查下)",
                    "查航班"),
            new Rule(IntentCategory.TRAIN_SEARCH,
                    "(查|查下|查一下|看下|看一下|看看|搜|有没有|有哪些|多少钱|什么价|价格|几点).{0,12}(火车票|火车|高铁|动车|车次)"
                            + "|(火车票|火车|高铁|动车|车次).{0,10}(多少钱|什么价|价格|有哪些|有没有|几点|时刻|查一下|查下)",
                    "查火车、高铁"),
            new Rule(IntentCategory.HOTEL_SEARCH,
                    "(查|查下|查一下|看下|看一下|看看|搜|有没有|有哪些|推荐|多少钱|什么价|价格|住哪).{0,12}(酒店|宾馆|住宿|房间|房源)"
                            + "|(酒店|宾馆|住宿).{0,10}(多少钱|什么价|价格|贵不贵|有哪些|有没有|推荐|干净吗)"
                            + "|住哪(里|儿)?(比较)?(好|方便|合适)",
                    "查酒店"),

            /* ---------- 通用信息：infoAgent ---------- */
            new Rule(IntentCategory.ATTRACTIONS_QUERY,
                    "(有什么好玩|好玩的|有什么景点|景点|游玩|逛什么|打卡|必去|值得去|旅游攻略)",
                    "目的地景点、旅游信息"),
            new Rule(IntentCategory.GENERAL_INFO,
                    "(天气|气温|下雨|下雪|冷不冷|热不热|有什么新闻|最新消息)"
                            + "|(怎么去|怎么走|怎么到|多久到|多远|远不远).{0,10}(机场|车站|高铁站|火车站|酒店|公司|市区)",
                    "天气、交通等通用信息")
    );

    /**
     * 对输入文本执行 L1 规则匹配，返回完整裁决。
     *
     * <p>裁决分三态而不是「Optional 有值 / 空」，是为了让调用方能区分两件完全不同的事：
     * <ul>
     *   <li>{@link Verdict#MISS} —— 规则没覆盖到，<b>应当继续走 L2</b>，L2 很可能能识别；
     *   <li>{@link Verdict#AMBIGUOUS} —— 一句话里检出分属多个子智能体的意图，
     *       <b>L2 也帮不上忙</b>（向量检索同样只返回单一意图），应当连 L2 一起跳过直接进 L3。
     * </ul>
     * 只用 Optional 的话这两种情况都是「空」，调用方就只能一律往下顺次走，白花一次 embedding 调用。
     *
     * @param text 用户问题，通常是改写智能体的产出
     * @return 三态裁决，永不为 null
     */
    public Outcome evaluate(String text) {
        // 空输入无从判定，直接放行给 L2/L3
        if (text == null || text.isBlank()) {
            return Outcome.miss();
        }
        String raw = text.trim();
        // 小写化只影响 hello/hi 这类英文寒暄，对中文是空操作，所以可以放心全局做
        String normalized = raw.toLowerCase(Locale.ROOT);

        // 步骤 1：多意图守卫 —— 按标点/连词拆成子句，逐句匹配。
        // 注意这里只做「歧义否决」，不做「命中判定」：子句一个都没中不代表全文不中，
        // 拿它当 MISS 的前置条件会把「你好」这种短到被子句过滤掉的输入误杀。
        List<IntentCategory> clauseIntents = matchClauseIntents(normalized);

        // 命中类别横跨 ≥2 个目标子智能体，说明这是复合请求：规则匹配器只能返回单意图，
        // 硬选一个会丢掉另一半诉求。
        //
        // 按 targetAgent 计票而不是按意图计票：查机票和查酒店都归行程规划智能体，
        // 「查下机票再看看酒店」交给它一个人就能办完，不该因为是两个意图码就下沉 L3；
        // 而「查报销进度」和「查机票」分属两个智能体，那才是真的拆不开。
        long distinctAgents = clauseIntents.stream()
                .map(IntentCategory::getTargetAgent)
                .distinct()
                .count();
        if (distinctAgents >= 2) {
            log.info("[L1_RULE] 检出跨智能体多意图 {}，跳过 L1/L2 直接进 L3", clauseIntents);
            return Outcome.ambiguous(clauseIntents);
        }

        // 步骤 2：全文按优先级取首个命中规则。
        // 用全文而不是复用子句结果：子句是被切碎的，「帮我查一下」和「报销进度」分在两边时，
        // 单看任一子句都命中不了带邻近约束的正则，只有全文能匹配上。
        Rule hit = firstMatch(normalized);
        if (hit == null) {
            return Outcome.miss();
        }
        log.debug("[L1_RULE] 命中 {}（{}），输入「{}」", hit.intent().getCode(), hit.desc(), raw);
        return Outcome.hit(IntentRecognitionResult.single(
                IntentLevelEnum.L1,
                hit.intent(),
                RULE_CONFIDENCE,
                "L1 规则命中：「" + hit.desc() + "」",
                null));
    }

    /**
     * 逐子句匹配，返回去重后的命中意图，保持子句出现顺序。
     *
     * <p>寒暄子句被排除在外：「你好，帮我查下报销进度」是一个请求加一句客套，不是两个意图。
     * 把 GREETING 算进去的话，最常见的开场问法会全部被误判成歧义，白白下沉到 L3。
     */
    private List<IntentCategory> matchClauseIntents(String normalized) {
        Set<IntentCategory> intents = new LinkedHashSet<>();
        for (String clause : CLAUSE_DELIMITER.split(normalized)) {
            String trimmed = clause.trim();
            if (trimmed.length() < MIN_CLAUSE_LENGTH) {
                continue;
            }
            Rule rule = firstMatch(trimmed);
            if (rule != null && rule.intent() != IntentCategory.GREETING) {
                intents.add(rule.intent());
            }
        }
        return List.copyOf(intents);
    }

    /** 按 {@link #RULES} 的声明顺序取首个命中的规则，全不命中返回 null */
    private Rule firstMatch(String text) {
        for (Rule rule : RULES) {
            if (rule.pattern().matcher(text).find()) {
                return rule;
            }
        }
        return null;
    }

    /** 裁决类型 */
    public enum Verdict {

        /** 规则命中，可直接采信 */
        HIT,

        /** 规则未覆盖，继续走 L2 */
        MISS,

        /** 跨子智能体多意图，L2 同样无解，直接进 L3 */
        AMBIGUOUS
    }

    /**
     * 一条规则。
     *
     * @param intent  命中后判定的意图
     * @param pattern 匹配正则。静态表在类加载时一次性编完，运行时只有 find()，不重复编译
     * @param desc    规则说明，会拼进 reason 落库，排查误判时能一眼看出是哪条规则干的
     */
    private record Rule(IntentCategory intent, Pattern pattern, String desc) {

        Rule(IntentCategory intent, String regex, String desc) {
            this(intent, Pattern.compile(regex), desc);
        }
    }

    /**
     * L1 匹配结局。{@code result} 仅在 {@link Verdict#HIT} 时非空；
     * {@code ambiguousCategories} 仅在 {@link Verdict#AMBIGUOUS} 时非空，用于日志排障。
     */
    public record Outcome(Verdict verdict,
                          IntentRecognitionResult result,
                          List<IntentCategory> ambiguousCategories) {

        static Outcome hit(IntentRecognitionResult result) {
            return new Outcome(Verdict.HIT, result, List.of());
        }

        static Outcome ambiguous(List<IntentCategory> categories) {
            return new Outcome(Verdict.AMBIGUOUS, null, categories);
        }

        static Outcome miss() {
            return new Outcome(Verdict.MISS, null, List.of());
        }
    }
}
