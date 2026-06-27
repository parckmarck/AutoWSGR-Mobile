package org.openwsgr.autowsgrmobile;

import java.util.Locale;

/**
 * 决战「方案 C」文字识别 PoC —— 数字模板识别（费用/资源/阶段号）。
 *
 * <p>用模板匹配替代通用 OCR：把一个数字单元归一化为 {@code CELL_W×CELL_H} 灰度网格(0..255)，
 * 与 0-9 的参考网格逐一比相似度（复用 {@code vision/TemplateMatcher.similarityAt} 同款
 * 「均值绝对色距 → [0,1]」打分），取最高且过阈值者；否则未知。
 *
 * <p>本类是<b>纯算法</b>：只在 {@code int[]} 网格上运算，不依赖 {@code android.graphics.Bitmap}，
 * 因此可离机做契约测试（设备侧负责把 Bitmap 区域采样成网格再调用本类）。这证明方案 C
 * 在裸 javac 构建下可行——为后续决战识别铺第一块砖。详见 docs/decisive-text-recognition-design.md。
 */
final class DigitTemplateRecognizer {
    static final int CELL_W = 8;
    static final int CELL_H = 12;
    static final int CELL_SIZE = CELL_W * CELL_H;

    private DigitTemplateRecognizer() {
    }

    /**
     * 识别单个数字单元。
     *
     * @param cell      归一化为 {@link #CELL_SIZE} 长度的灰度网格(0..255)。
     * @param templates {@code int[10][CELL_SIZE]}，索引即数字 0-9。
     * @param minScore  最低相似度阈值(0..1)。
     * @return 识别到的数字 0-9；不可识别返回 -1。
     */
    static int recognizeDigit(int[] cell, int[][] templates, double minScore) {
        if (cell == null || cell.length != CELL_SIZE || templates == null) {
            return -1;
        }
        int best = -1;
        double bestScore = -1.0;
        for (int d = 0; d < templates.length; d++) {
            int[] template = templates[d];
            if (template == null || template.length != CELL_SIZE) {
                continue;
            }
            double score = similarity(cell, template);
            if (score > bestScore) {
                bestScore = score;
                best = d;
            }
        }
        if (best < 0 || bestScore < minScore) {
            return -1;
        }
        return best;
    }

    /**
     * 逐单元识别一串数字。
     *
     * @return 识别出的数字串；某位不可识别用 {@code '?'} 占位。
     */
    static String recognizeNumber(int[][] cells, int[][] templates, double minScore) {
        if (cells == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (int[] cell : cells) {
            int d = recognizeDigit(cell, templates, minScore);
            out.append(d < 0 ? '?' : Character.forDigit(d, 10));
        }
        return out.toString();
    }

    /** 把一串数字解析为整数；含 {@code '?'} 或空串返回 -1。 */
    static int parseNumber(String digits) {
        if (digits == null || digits.length() == 0 || digits.indexOf('?') >= 0) {
            return -1;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** 归一化相似度：1 - 均值绝对差/255，落在 [0,1]。与 vision/TemplateMatcher 一致。 */
    static double similarity(int[] a, int[] b) {
        if (a == null || b == null || a.length == 0 || a.length != b.length) {
            return 0.0;
        }
        long total = 0L;
        for (int i = 0; i < a.length; i++) {
            total += Math.abs((a[i] & 0xff) - (b[i] & 0xff));
        }
        double mean = total / (double) a.length;
        double score = 1.0 - (mean / 255.0);
        if (score < 0.0) {
            return 0.0;
        }
        if (score > 1.0) {
            return 1.0;
        }
        return score;
    }

    static String describe(int[][] templates) {
        int count = templates == null ? 0 : templates.length;
        return String.format(Locale.US, "digitTemplates=%d, cell=%dx%d", count, CELL_W, CELL_H);
    }
}
