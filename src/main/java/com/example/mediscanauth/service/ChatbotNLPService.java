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
            // 1. Chào hỏi & Xã giao
            new IntentRule("GREETING",
                    Arrays.asList("xin chao", "chao", "hello", "hi", "hey", "chao bac si"),
                    "Xin chào! 👋 Tôi là <strong>Trợ lý AI MediScan (Phiên bản NLP tự phát triển)</strong>.<br><br>Tôi có thể hỗ trợ bạn:<br>👉 Tư vấn triệu chứng xương khớp<br>👉 Đặt lịch khám<br>👉 Giải đáp thông tin về phòng khám<br><br>Bạn đang gặp vấn đề gì?",
                    null),
            new IntentRule("THANKS",
                    Arrays.asList("cam on", "thanks", "ok", "hieu roi", "tot", "tuyet", "da ro", "da hieu"),
                    "Không có gì! Rất vui khi được hỗ trợ bạn. 😊<br><br>💡 <em>Lưu ý: Thông tin từ chatbot chỉ mang tính tham khảo. Nếu có triệu chứng nặng, bạn nên đến phòng khám ngay nhé!</em>",
                    null),
            new IntentRule("GOODBYE",
                    Arrays.asList("tam biet", "bye", "thoat", "hen gap lai", "chao tam biet"),
                    "Tạm biệt bạn! 👋 Chúc bạn luôn có một hệ xương khớp khỏe mạnh.",
                    null),

            // 2. Chức năng hệ thống
            new IntentRule("BOOKING",
                    Arrays.asList("dat lich", "kham benh", "dang ky", "dat cho", "hen kham", "toi muon kham", "dang ky kham", "muon kham"),
                    "Tuyệt vời! Hệ thống sẽ giúp bạn đặt lịch khám ngay bây giờ.",
                    "triggerBooking"),
            new IntentRule("XRAY_AI",
                    Arrays.asList("chup xquang", "chup x quang", "ai phan tich", "phan tich anh", "ket qua xquang", "doc phim", "gui anh xquang"),
                    "<strong>🤖 Phân tích X-Quang bằng AI</strong><br><br>Hệ thống MediScan có tích hợp Trí tuệ Nhân tạo để tự động phân tích phim X-Quang của bạn.<br>👉 Truy cập mục <strong>Hồ sơ Y tế -> Tải ảnh X-Quang</strong> để AI nhận diện vùng tổn thương, độ tin cậy và cảnh báo sớm các dấu hiệu bất thường.",
                    null),
            new IntentRule("CLINIC_INFO",
                    Arrays.asList("gio lam viec", "dia chi", "o dau", "lich lam viec", "gia kham", "chi phi", "bao nhieu tien"),
                    "<strong>🏥 Thông tin Phòng khám MediScan</strong><br><br>🔸 <strong>Giờ làm việc:</strong> 07:00 - 17:00 (Từ Thứ 2 đến Chủ nhật)<br>🔸 <strong>Giá khám cơ bản:</strong> 150.000đ - 300.000đ (Chưa bao gồm phí chụp X-Quang).<br>👉 Bạn có thể thao tác Đặt lịch để được giữ chỗ trước nhé!",
                    null),

            // 3. Kiến thức Y khoa - Bệnh lý Cơ Xương Khớp
            new IntentRule("BONE_FRACTURE",
                    Arrays.asList("gay", "gay xuong", "vo xuong", "nut xuong", "nghi gay", "te nga"),
                    "<strong>⚠️ Nghi ngờ gãy xương - Xử lý khẩn cấp</strong><br><br><strong>Dấu hiệu:</strong> Đau dữ dội đột ngột, sưng bầm lan rộng, biến dạng chi, lạo xạo xương.<br><strong>Sơ cứu:</strong><br>🔸 Giữ nạn nhân bất động, không cố nắn chỉnh.<br>🔸 Dùng nẹp cứng (gỗ, bìa carton) để cố định tạm thời.<br>👉 <strong>Cần chụp X-Quang ngay lập tức.</strong>",
                    null),
            new IntentRule("SWELLING_SPRAIN",
                    Arrays.asList("sung", "bam tim", "tu mau", "bong gan", "trat khop", "cang co", "gian day chang", "lat somi", "lat so mi"),
                    "<strong>🧊 Bong gân / Sưng bầm tím</strong><br><br><strong>Phương pháp R.I.C.E (48 giờ đầu):</strong><br>🔸 <strong>R (Rest):</strong> Ngừng vận động, nghỉ ngơi.<br>🔸 <strong>I (Ice):</strong> Chườm lạnh 15-20p, cách nhau 2h (Tuyệt đối KHÔNG chườm nóng hay xoa dầu nóng).<br>🔸 <strong>C (Compression):</strong> Dùng băng thun băng ép nhẹ nhàng.<br>🔸 <strong>E (Elevation):</strong> Kê cao vùng bị thương.",
                    null),
            new IntentRule("OSTEOPOROSIS",
                    Arrays.asList("loang xuong", "xuong yeu", "mat xuong", "canxi", "nhuc xuong", "moi xuong", "thieu canxi"),
                    "<strong>🦴 Loãng xương (Sát thủ thầm lặng)</strong><br><br><strong>Triệu chứng:</strong> Đau mỏi mơ hồ dọc các xương dài, giảm chiều cao, gù lưng, dễ gãy xương dù va chạm nhẹ.<br>💡 <strong>Lời khuyên:</strong> Phụ nữ sau mãn kinh hoặc người lớn tuổi nên đi đo <strong>Mật độ xương (DEXA)</strong> định kỳ 6 tháng/lần.",
                    null),
            new IntentRule("ARTHRITIS",
                    Arrays.asList("viem khop", "thoai hoa", "xuong khop", "cung khop", "dau khop", "nhuc khop", "kho cu dong", "luc cuc"),
                    "<strong>🔥 Viêm / Thoái hóa khớp</strong><br><br>🔸 <strong>Thoái hóa khớp:</strong> Đau tăng khi vận động, giảm khi nghỉ ngơi. Khớp có tiếng lạo xạo (lục cục) khi cử động.<br>🔸 <strong>Viêm khớp dạng thấp:</strong> Cứng khớp buổi sáng kéo dài hơn 1 tiếng, sưng nóng đỏ đau nhiều khớp đối xứng.<br>👉 Cần đi khám để bác sĩ chỉ định thuốc, tránh tự ý mua thuốc giảm đau dạ dày.",
                    null),
            new IntentRule("HAND_PAIN",
                    Arrays.asList("dau ban tay", "dau co tay", "te ban tay", "ong co tay", "ngon tay", "cung ngon tay"),
                    "<strong>🤚 Đau Bàn tay / Cổ tay</strong><br><br>Có thể bạn đang gặp Hội chứng ống cổ tay (chèn ép thần kinh) hoặc Viêm khớp ngón tay.<br>🔸 Tránh gõ phím/cầm chuột liên tục.<br>🔸 Nếu đau kèm kẹt ngón tay (ngón tay cò súng), hãy đi khám sớm.",
                    null),
            new IntentRule("BACK_PAIN",
                    Arrays.asList("dau lung", "thoat vi", "dia dem", "than kinh toa", "te chan", "dau cot song", "dau that lung"),
                    "<strong>⚡ Đau lưng / Thoát vị đĩa đệm</strong><br><br>Nếu bạn bị <strong>đau thắt lưng lan xuống mông và chân, kèm theo tê bì</strong>, đó có thể là dấu hiệu chèn ép rễ thần kinh (Thần kinh tọa).<br>🔸 Tránh khom lưng nhấc vật nặng.<br>🔸 Không nằm nệm quá mềm.<br>👉 Bác sĩ có thể yêu cầu chụp MRI để xem rõ đĩa đệm (X-Quang chỉ thấy xương, không thấy đĩa đệm).",
                    null),
            new IntentRule("NECK_PAIN",
                    Arrays.asList("dau co", "vai gay", "moi co", "cung co", "dau vai", "te tay", "chong mat"),
                    "<strong>💻 Đau mỏi vai gáy / Cột sống cổ</strong><br><br>Rất phổ biến ở dân văn phòng. Nếu đau vai gáy kèm <strong>tê lan xuống cánh tay, ngón tay</strong> hoặc <strong>hay chóng mặt đau đầu</strong>, đó có thể là thoái hóa đốt sống cổ gây chèn ép mạch máu/thần kinh.<br>🔸 Hãy tập vươn vai, xoay cổ nhẹ nhàng mỗi 45 phút làm việc.",
                    null),
            new IntentRule("GOUT",
                    Arrays.asList("gout", "gut", "axit uric", "acid uric", "sung ngon chan", "dau ngon chan"),
                    "<strong>Ã°Å¸â€Â¥ BÃ¡Â»â€¡nh Gout (ThÃ¡Â»â€˜ng phong)</strong><br><br>BiÃ¡Â»Æ’u hiÃ¡Â»â€¡n Ã„â€˜Ã¡ÂºÂ·c trÃ†Â°ng lÃƒÂ  <strong>sÃ†Â°ng, nÃƒÂ³ng, Ã„â€˜Ã¡Â»Â, Ã„â€˜au dÃ¡Â»Â¯ dÃ¡Â»â„¢i</strong> thÃ†Â°Ã¡Â»Âng bÃ¡ÂºÂ¯t Ã„â€˜Ã¡ÂºÂ§u Ã¡Â»Å¸ khÃ¡Â»â€ºp ngÃƒÂ³n chÃƒÂ¢n cÃƒÂ¡i vÃƒÂ o ban Ã„â€˜ÃƒÂªm.<br>Ã°Å¸â€Â¸ <strong>KiÃƒÂªng:</strong> RÃ†Â°Ã¡Â»Â£u bia, hÃ¡ÂºÂ£i sÃ¡ÂºÂ£n, nÃ¡Â»â„¢i tÃ¡ÂºÂ¡ng Ã„â€˜Ã¡Â»â„¢ng vÃ¡ÂºÂ­t, thÃ¡Â»â€¹t Ã„â€˜Ã¡Â»Â.<br>Ã°Å¸â€Â¸ <strong>NÃƒÂªn lÃƒÂ m:</strong> UÃ¡Â»â€˜ng nhiÃ¡Â»Âu nÃ†Â°Ã¡Â»â€ºc, Ã„â€˜i xÃƒÂ©t nghiÃ¡Â»â€¡m nÃ¡Â»â€œng Ã„â€˜Ã¡Â»â„¢ Acid Uric trong mÃƒÂ¡u.",
                    null),
            new IntentRule("KNEE_PAIN",
                    Arrays.asList("dau dau goi", "dau goi", "tran dich", "dut day chang", "cheo truoc", "cheo sau"),
                    "<strong>Ã°Å¸Â¦Âµ ChÃ¡ÂºÂ¥n thÃ†Â°Ã†Â¡ng / Ã„Âau khÃ¡Â»â€ºp gÃ¡Â»â€˜i</strong><br><br>KhÃ¡Â»â€ºp gÃ¡Â»â€˜i chÃ¡Â»â€¹u toÃƒÂ n bÃ¡Â»â„¢ trÃ¡Â»Âng lÃ†Â°Ã¡Â»Â£ng cÃ†Â¡ thÃ¡Â»Æ’. <br>Ã°Å¸â€Â¸ NÃ¡ÂºÂ¿u lÃ¡Â»Âng gÃ¡Â»â€˜i, cÃ¡ÂºÂ£m giÃƒÂ¡c sÃ¡Â»Â¥m gÃ¡Â»â€˜i: Nguy cÃ†Â¡ Ã„â€˜Ã¡Â»Â©t dÃƒÂ¢y chÃ¡ÂºÂ±ng chÃƒÂ©o (thÃ†Â°Ã¡Â»Âng do thÃ¡Â»Æ’ thao).<br>Ã°Å¸â€Â¸ NÃ¡ÂºÂ¿u gÃ¡Â»â€˜i sÃ†Â°ng to, cÃ„Æ’ng tÃ¡Â»Â©c: CÃƒÂ³ thÃ¡Â»Æ’ bÃ¡Â»â€¹ trÃƒÂ n dÃ¡Â»â€¹ch khÃ¡Â»â€ºp gÃ¡Â»â€˜i.<br>Ã°Å¸â€˜â€° BÃ¡ÂºÂ¡n nÃƒÂªn hÃ¡ÂºÂ¡n chÃ¡ÂºÂ¿ Ã„â€˜i lÃ¡ÂºÂ¡i, dÃƒÂ¹ng nÃ¡ÂºÂ¡ng vÃƒÂ  Ã„â€˜Ã¡ÂºÂ¿n phÃƒÂ²ng khÃƒÂ¡m chuyÃƒÂªn khoa sÃ¡Â»â€ºm.",
                    null),
            new IntentRule("NUTRITION",
                    Arrays.asList("an gi", "dinh duong", "kieng an", "thuc pham", "sua", "thuc an", "thuoc bo", "an uong"),
                    "<strong>Ã°Å¸Â¥Â¦ Dinh dÃ†Â°Ã¡Â»Â¡ng cho XÃ†Â°Ã†Â¡ng KhÃ¡Â»â€ºp</strong><br><br>Ã°Å¸â€ Â¸ <strong>TÃ¡Â»â€˜t cho xÃ†Â°Ã†Â¡ng:</strong> ThÃ¡Â»Â±c phÃ¡ÂºÂ©m giÃƒÂ u Canxi (sÃ¡Â»Â¯a, sÃ¡Â»Â¯a chua, cÃƒÂ¡ nhÃ¡Â»Â  Ã„Æ’n cÃ¡ÂºÂ£ xÃ†Â°Ã†Â¡ng), Vitamin D (tÃ¡ÂºÂ¯m nÃ¡ÂºÂ¯ng sÃƒÂ¡ng sÃ¡Â»â€ºm), Omega-3 (cÃƒÂ¡ hÃ¡Â»â€œi, hÃ¡ÂºÂ¡t ÃƒÂ³c chÃƒÂ³) giÃƒÂºp giÃ¡ÂºÂ£m viÃƒÂªm.<br>Ã°Å¸â€ Â¸ <strong>CÃ¡ÂºÂ§n trÃƒÂ¡nh:</strong> ThÃ¡Â»Â©c Ã„Æ’n quÃƒÂ¡ mÃ¡ÂºÂ·n (lÃƒÂ m Ã„â€˜ÃƒÂ o thÃ¡ÂºÂ£i canxi), nÃ†Â°Ã¡Â»â€ºc ngÃ¡Â»Â t cÃƒÂ³ gas, Ã„â€˜Ã¡Â»â€œ ngÃ¡Â»Â t nhiÃ¡Â»Â u Ã„â€˜Ã†Â°Ã¡Â»Â ng (tÃ„Æ’ng phÃ¡ÂºÂ£n Ã¡Â»Â©ng viÃƒÂªm).",
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
            int maxKeywordScore = 0;
            for (String keyword : rule.keywords) {
                int score = 0;
                String normalizedKeyword = normalizeText(keyword);
                if (normalizedInput.contains(normalizedKeyword)) {
                    score = 10;
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
                        score = 8;
                    } else if (matchCount > 0) {
                        score = (matchCount * 10) / keyWords.length;
                        if (score < 5) score = 0; // TrÃ¡nh cá»™ng Ä‘iá»ƒm cho tá»« quÃ¡ chung chung
                    }
                }
                maxKeywordScore = Math.max(maxKeywordScore, score);
            }
            if (maxKeywordScore > maxScore) {
                maxScore = maxKeywordScore;
                bestMatch = rule;
            }
        }

        if (bestMatch != null && maxScore >= 6) {
            return new ChatResponse(bestMatch.reply, bestMatch.action);
        }

        return new ChatResponse("Xin lỗi, tôi chưa hiểu rõ ý của bạn. Để tôi hỗ trợ tốt nhất, bạn có thể nói rõ hơn về <strong>triệu chứng bệnh lý</strong> (VD: đau lưng, nhức gối, bong gân...) hoặc yêu cầu <strong>đặt lịch khám</strong> nhé!", null);
    }

    private String normalizeText(String text) {
        String nfdNormalizedString = Normalizer.normalize(text.toLowerCase(), Normalizer.Form.NFD); 
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        String noAccents = pattern.matcher(nfdNormalizedString).replaceAll("");
        noAccents = noAccents.replaceAll("Ã„â€˜", "d").replaceAll("Ã„Â", "D");
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