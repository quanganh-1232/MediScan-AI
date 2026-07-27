package com.example.mediscanauth.service;

import com.example.mediscanauth.dto.response.ChatResponse;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class ChatbotNLPService {

    private static class IntentRule {
        String name;
        List<String> keywords;
        String reply;
        String action;

        public IntentRule(String name, List<String> keywords, String reply, String action) {
            this.name = name;
            this.keywords = keywords;
            this.reply = reply;
            this.action = action;
        }
    }

    private final List<IntentRule> knowledgeBase = Arrays.asList(
            new IntentRule("GREETING",
                    Arrays.asList("xin chao", "chao", "hello", "hi", "hey"),
                    "Xin chào! 👋 Tôi là <strong>Trợ lý AI MediScan (Phiên bản NLP tự phát triển)</strong>.<br><br>Tôi có thể hỗ trợ bạn:<br>👉 Tư vấn triệu chứng xương khớp<br>👉 Đặt lịch khám<br><br>Bạn đang gặp vấn đề gì?",
                    null),
            new IntentRule("THANKS",
                    Arrays.asList("cam on", "thanks", "ok", "hieu roi", "tot", "tuyet"),
                    "Không có gì! Rất vui khi được hỗ trợ bạn. 😊<br><br>💡 <em>Lưu ý: Thông tin từ chatbot chỉ mang tính tham khảo.</em>",
                    null),
            new IntentRule("GOODBYE",
                    Arrays.asList("tam biet", "bye", "thoat"),
                    "Tạm biệt bạn! 👋 Chúc bạn sức khỏe tốt.",
                    null),
            new IntentRule("BOOKING",
                    Arrays.asList("dat lich", "kham benh", "dang ky", "dat cho", "hen kham", "toi muon kham"),
                    "Tuyệt vời! Hệ thống sẽ giúp bạn đặt lịch khám ngay bây giờ.",
                    "triggerBooking"),
            new IntentRule("BONE_FRACTURE",
                    Arrays.asList("gay", "gay xuong", "vo xuong", "nut xuong", "nghi gay"),
                    "<strong>⚠️ Nghi ngờ gãy xương - Xử lý khẩn cấp</strong><br><br><strong>Dấu hiệu nhận biết:</strong><br>🔸 Đau dữ dội đột ngột sau chấn thương<br>🔸 Sưng và bầm tím lan rộng<br>🔸 Biến dạng chi<br>🔸 <em>Trong khi chờ cấp cứu: Giữ nạn nhân bất động.</em>",
                    null),
            new IntentRule("SWELLING",
                    Arrays.asList("sung", "bam tim", "tu mau", "bong gan", "trat khop"),
                    "<strong>🧊 Sưng và bầm tím sau chấn thương</strong><br><br><strong>Phương pháp RICE (48 giờ đầu):</strong><br>🔸 <strong>R</strong>est (Nghỉ ngơi)<br>🔸 <strong>I</strong>ce (Chườm lạnh 15-20p)<br>🔸 <strong>C</strong>ompression (Băng ép)<br>🔸 <strong>E</strong>levation (Kê cao chi)",
                    null),
            new IntentRule("OSTEOPOROSIS",
                    Arrays.asList("loang xuong", "xuong yeu", "mat xuong", "canxi", "nhuc xuong", "moi xuong"),
                    "<strong>🦴 Loãng xương (Osteoporosis)</strong><br><br><strong>Triệu chứng:</strong> Thường âm thầm, hay đau mỏi vùng lưng, giảm chiều cao.<br>💡 Nên kiểm tra mật độ xương (DEXA scan) nếu trên 65 tuổi hoặc dùng corticoid kéo dài.",
                    null),
            new IntentRule("ARTHRITIS",
                    Arrays.asList("viem khop", "thoai hoa", "xuong khop", "cung khop", "dau khop", "nhuc khop", "kho cu dong"),
                    "<strong>🔥 Viêm khớp / Thoái hóa khớp</strong><br><br>🔸 <strong>Thoái hóa:</strong> Đau tăng khi vận động, giảm khi nghỉ.<br>🔸 <strong>Viêm khớp dạng thấp:</strong> Cứng khớp buổi sáng kéo dài.<br>⚠️ Tránh tự ý dùng corticoid kéo dài.",
                    null)
    );

    public ChatResponse processMessage(String input) {
        if (input == null || input.trim().isEmpty()) {
            return new ChatResponse("Vui lòng nhập câu hỏi của bạn.", null);
        }

        String normalizedInput = normalizeText(input);
        String[] words = normalizedInput.split("\\s+");

        IntentRule bestMatch = null;
        int maxScore = 0;

        for (IntentRule rule : knowledgeBase) {
            int currentScore = 0;
            for (String keyword : rule.keywords) {
                String normalizedKeyword = normalizeText(keyword);
                if (normalizedInput.contains(normalizedKeyword)) {
                    currentScore += 10;
                } else {
                    String[] keyWords = normalizedKeyword.split("\\s+");
                    int matchCount = 0;
                    for (String kw : keyWords) {
                        for (String w : words) {
                            if (calculateSimilarity(w, kw) >= 0.75) {
                                matchCount++;
                                break;
                            }
                        }
                    }
                    if (matchCount == keyWords.length) {
                        currentScore += 8;
                    } else if (matchCount > 0) {
                        currentScore += matchCount;
                    }
                }
            }
            if (currentScore > maxScore) {
                maxScore = currentScore;
                bestMatch = rule;
            }
        }

        if (bestMatch != null && maxScore >= 2) {
            return new ChatResponse(bestMatch.reply, bestMatch.action);
        }

        return new ChatResponse("Xin lỗi, tôi chưa hiểu rõ ý của bạn. Bạn có thể nói rõ hơn về <strong>triệu chứng</strong> hoặc yêu cầu <strong>đặt lịch</strong> không?", null);
    }

    private String normalizeText(String text) {
        String nfdNormalizedString = Normalizer.normalize(text.toLowerCase(), Normalizer.Form.NFD); 
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        String noAccents = pattern.matcher(nfdNormalizedString).replaceAll("");
        noAccents = noAccents.replaceAll("đ", "d").replaceAll("Đ", "D");
        return noAccents.replaceAll("[^a-z0-9\\s]", "").trim();
    }

    private double calculateSimilarity(String s1, String s2) {
        if (s1.equals(s2)) return 1.0;
        int maxLength = Math.max(s1.length(), s2.length());
        if (maxLength == 0) return 1.0;
        return 1.0 - ((double) computeLevenshteinDistance(s1, s2) / maxLength);
    }

    private int computeLevenshteinDistance(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            for (int j = 0; j <= b.length(); j++) {
                if (i == 0) dp[i][j] = j;
                else if (j == 0) dp[i][j] = i;
                else {
                    int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
                    dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
                }
            }
        }
        return dp[a.length()][b.length()];
    }
}