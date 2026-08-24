package com.quanwei.gogo.agent.agent.core;

/**
 * 模型输出里抠 JSON 的工具。
 *
 * <p>提示词里写了「不要用代码块包裹」，但模型并不总听话 —— 尤其加了思考开关之后，
 * 前面挂一段说明、后面裹一层 ```json 都很常见。为这点格式噪音让整轮对话降级不值得，
 * 所以解析前统一先抠出第一个完整的 JSON 对象。
 *
 * <p>只做定位不做修复：抠出来的东西还是不是合法 JSON，交给真正的解析器判断。
 * 在这里试图「智能修补」残缺 JSON 只会把错误藏得更深。
 */
public final class LlmJsonUtils {

    private LlmJsonUtils() {
    }

    /**
     * 从模型输出里抠出最外层的 JSON 对象。
     *
     * @return 抠出来的片段；找不到成对的花括号时返回 null
     */
    public static String extractJsonObject(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String text = raw.trim();

        // 去掉 markdown 围栏。只去围栏本身，里面的内容原样保留
        if (text.startsWith("```")) {
            int firstLineEnd = text.indexOf('\n');
            if (firstLineEnd > 0) {
                text = text.substring(firstLineEnd + 1);
            }
            int fenceEnd = text.lastIndexOf("```");
            if (fenceEnd >= 0) {
                text = text.substring(0, fenceEnd);
            }
            text = text.trim();
        }

        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        return text.substring(start, end + 1);
    }
}
