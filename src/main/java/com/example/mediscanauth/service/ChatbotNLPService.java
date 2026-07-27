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
            // 1. ChÃ o há»i & XÃ£ giao
            new IntentRule("GREETING",
                    Arrays.asList("xin chao", "chao", "hello", "hi", "hey", "chao bac si"),
                    "Xin chÃ o! ðŸ‘‹ TÃ´i lÃ  <strong>Trá»£ lÃ½ AI MediScan (PhiÃªn báº£n NLP tá»± phÃ¡t triá»ƒn)</strong>.<br><br>TÃ´i cÃ³ thá»ƒ há»— trá»£ báº¡n:<br>ðŸ‘‰ TÆ° váº¥n triá»‡u chá»©ng xÆ°Æ¡ng khá»›p<br>ðŸ‘‰ Äáº·t lá»‹ch khÃ¡m<br>ðŸ‘‰ Giáº£i Ä‘Ã¡p thÃ´ng tin vá» phÃ²ng khÃ¡m<br><br>Báº¡n Ä‘ang gáº·p váº¥n Ä‘á» gÃ¬?",
                    null),
            new IntentRule("THANKS",
                    Arrays.asList("cam on", "thanks", "ok", "hieu roi", "tot", "tuyet", "da ro", "da hieu"),
                    "KhÃ´ng cÃ³ gÃ¬! Ráº¥t vui khi Ä‘Æ°á»£c há»— trá»£ báº¡n. ðŸ˜Š<br><br>ðŸ’¡ <em>LÆ°u Ã½: ThÃ´ng tin tá»« chatbot chá»‰ mang tÃ­nh tham kháº£o. Náº¿u cÃ³ triá»‡u chá»©ng náº·ng, báº¡n nÃªn Ä‘áº¿n phÃ²ng khÃ¡m ngay nhÃ©!</em>",
                    null),
            new IntentRule("GOODBYE",
                    Arrays.asList("tam biet", "bye", "thoat", "hen gap lai", "chao tam biet"),
                    "Táº¡m biá»‡t báº¡n! ðŸ‘‹ ChÃºc báº¡n luÃ´n cÃ³ má»™t há»‡ xÆ°Æ¡ng khá»›p khá»e máº¡nh.",
                    null),

            // 2. Chá»©c nÄƒng há»‡ thá»‘ng
            new IntentRule("BOOKING",
                    Arrays.asList("dat lich", "kham benh", "dang ky", "dat cho", "hen kham", "toi muon kham", "dang ky kham", "muon kham"),
                    "Tuyá»‡t vá»i! Há»‡ thá»‘ng sáº½ giÃºp báº¡n Ä‘áº·t lá»‹ch khÃ¡m ngay bÃ¢y giá».",
                    "triggerBooking"),
            new IntentRule("XRAY_AI",
                    Arrays.asList("chup xquang", "chup x quang", "ai phan tich", "phan tich anh", "ket qua xquang", "doc phim", "gui anh xquang"),
                    "<strong>ðŸ¤– PhÃ¢n tÃ­ch X-Quang báº±ng AI</strong><br><br>Há»‡ thá»‘ng MediScan cÃ³ tÃ­ch há»£p TrÃ­ tuá»‡ NhÃ¢n táº¡o Ä‘á»ƒ tá»± Ä‘á»™ng phÃ¢n tÃ­ch phim X-Quang cá»§a báº¡n.<br>ðŸ‘‰ Truy cáº­p má»¥c <strong>Há»“ sÆ¡ Y táº¿ -> Táº£i áº£nh X-Quang</strong> Ä‘á»ƒ AI nháº­n diá»‡n vÃ¹ng tá»•n thÆ°Æ¡ng, Ä‘á»™ tin cáº­y vÃ  cáº£nh bÃ¡o sá»›m cÃ¡c dáº¥u hiá»‡u báº¥t thÆ°á»ng.",
                    null),
            new IntentRule("CLINIC_INFO",
                    Arrays.asList("gio lam viec", "dia chi", "o dau", "lich lam viec", "gia kham", "chi phi", "bao nhieu tien"),
                    "<strong>ðŸ¥ ThÃ´ng tin PhÃ²ng khÃ¡m MediScan</strong><br><br>ðŸ”¸ <strong>Giá» lÃ m viá»‡c:</strong> 07:00 - 17:00 (Tá»« Thá»© 2 Ä‘áº¿n Chá»§ nháº­t)<br>ðŸ”¸ <strong>GiÃ¡ khÃ¡m cÆ¡ báº£n:</strong> 150.000Ä‘ - 300.000Ä‘ (ChÆ°a bao gá»“m phÃ­ chá»¥p X-Quang).<br>ðŸ‘‰ Báº¡n cÃ³ thá»ƒ thao tÃ¡c Äáº·t lá»‹ch Ä‘á»ƒ Ä‘Æ°á»£c giá»¯ chá»— trÆ°á»›c nhÃ©!",
                    null),

            // 3. Kiáº¿n thá»©c Y khoa - Bá»‡nh lÃ½ CÆ¡ XÆ°Æ¡ng Khá»›p
            new IntentRule("BONE_FRACTURE",
                    Arrays.asList("gay", "gay xuong", "vo xuong", "nut xuong", "nghi gay", "te nga"),
                    "<strong>âš ï¸ Nghi ngá» gÃ£y xÆ°Æ¡ng - Xá»­ lÃ½ kháº©n cáº¥p</strong><br><br><strong>Dáº¥u hiá»‡u:</strong> Äau dá»¯ dá»™i Ä‘á»™t ngá»™t, sÆ°ng báº§m lan rá»™ng, biáº¿n dáº¡ng chi, láº¡o xáº¡o xÆ°Æ¡ng.<br><strong>SÆ¡ cá»©u:</strong><br>ðŸ”¸ Giá»¯ náº¡n nhÃ¢n báº¥t Ä‘á»™ng, khÃ´ng cá»‘ náº¯n chá»‰nh.<br>ðŸ”¸ DÃ¹ng náº¹p cá»©ng (gá»—, bÃ¬a carton) Ä‘á»ƒ cá»‘ Ä‘á»‹nh táº¡m thá»i.<br>ðŸ‘‰ <strong>Cáº§n chá»¥p X-Quang ngay láº­p tá»©c.</strong>",
                    null),
            new IntentRule("SWELLING_SPRAIN",
                    Arrays.asList("sung", "bam tim", "tu mau", "bong gan", "trat khop", "cang co", "gian day chang", "lat somi", "lat so mi"),
                    "<strong>ðŸ§Š Bong gÃ¢n / SÆ°ng báº§m tÃ­m</strong><br><br><strong>PhÆ°Æ¡ng phÃ¡p R.I.C.E (48 giá» Ä‘áº§u):</strong><br>ðŸ”¸ <strong>R (Rest):</strong> Ngá»«ng váº­n Ä‘á»™ng, nghá»‰ ngÆ¡i.<br>ðŸ”¸ <strong>I (Ice):</strong> ChÆ°á»m láº¡nh 15-20p, cÃ¡ch nhau 2h (Tuyá»‡t Ä‘á»‘i KHÃ”NG chÆ°á»m nÃ³ng hay xoa dáº§u nÃ³ng).<br>ðŸ”¸ <strong>C (Compression):</strong> DÃ¹ng bÄƒng thun bÄƒng Ã©p nháº¹ nhÃ ng.<br>ðŸ”¸ <strong>E (Elevation):</strong> KÃª cao vÃ¹ng bá»‹ thÆ°Æ¡ng.",
                    null),
            new IntentRule("OSTEOPOROSIS",
                    Arrays.asList("loang xuong", "xuong yeu", "mat xuong", "canxi", "nhuc xuong", "moi xuong", "thieu canxi"),
                    "<strong>ðŸ¦´ LoÃ£ng xÆ°Æ¡ng (SÃ¡t thá»§ tháº§m láº·ng)</strong><br><br><strong>Triá»‡u chá»©ng:</strong> Äau má»i mÆ¡ há»“ dá»c cÃ¡c xÆ°Æ¡ng dÃ i, giáº£m chiá»u cao, gÃ¹ lÆ°ng, dá»… gÃ£y xÆ°Æ¡ng dÃ¹ va cháº¡m nháº¹.<br>ðŸ’¡ <strong>Lá»i khuyÃªn:</strong> Phá»¥ ná»¯ sau mÃ£n kinh hoáº·c ngÆ°á»i lá»›n tuá»•i nÃªn Ä‘i Ä‘o <strong>Máº­t Ä‘á»™ xÆ°Æ¡ng (DEXA)</strong> Ä‘á»‹nh ká»³ 6 thÃ¡ng/láº§n.",
                    null),
            new IntentRule("ARTHRITIS",
                    Arrays.asList("viem khop", "thoai hoa", "xuong khop", "cung khop", "dau khop", "nhuc khop", "kho cu dong", "luc cuc"),
                    "<strong>ðŸ”¥ ViÃªm / ThoÃ¡i hÃ³a khá»›p</strong><br><br>ðŸ”¸ <strong>ThoÃ¡i hÃ³a khá»›p:</strong> Äau tÄƒng khi váº­n Ä‘á»™ng, giáº£m khi nghá»‰ ngÆ¡i. Khá»›p cÃ³ tiáº¿ng láº¡o xáº¡o (lá»¥c cá»¥c) khi cá»­ Ä‘á»™ng.<br>ðŸ”¸ <strong>ViÃªm khá»›p dáº¡ng tháº¥p:</strong> Cá»©ng khá»›p buá»•i sÃ¡ng kÃ©o dÃ i hÆ¡n 1 tiáº¿ng, sÆ°ng nÃ³ng Ä‘á» Ä‘au nhiá»u khá»›p Ä‘á»‘i xá»©ng.<br>ðŸ‘‰ Cáº§n Ä‘i khÃ¡m Ä‘á»ƒ bÃ¡c sÄ© chá»‰ Ä‘á»‹nh thuá»‘c, trÃ¡nh tá»± Ã½ mua thuá»‘c giáº£m Ä‘au dáº¡ dÃ y.",
                    null),
            new IntentRule("HAND_PAIN",
                    Arrays.asList("dau ban tay", "dau co tay", "te ban tay", "ong co tay", "ngon tay", "cung ngon tay"),
                    "<strong>🖐 Đau Bàn tay / Cổ tay</strong><br><br>Có thể bạn đang gặp Hội chứng ống cổ tay (chèn ép thần kinh) hoặc Viêm khớp ngón tay.<br>🔸 Tránh gõ phím/cầm chuột liên tục.<br>🔸 Nếu đau kèm kẹt ngón tay (ngón tay cò súng), hãy đi khám sớm.",
                    null),
            new IntentRule("BACK_PAIN",
                    Arrays.asList("dau lung", "thoat vi", "dia dem", "than kinh toa", "te chan", "dau cot song", "dau that lung"),
                    "<strong>âš¡ Äau lÆ°ng / ThoÃ¡t vá»‹ Ä‘Ä©a Ä‘á»‡m</strong><br><br>Náº¿u báº¡n bá»‹ <strong>Ä‘au tháº¯t lÆ°ng lan xuá»‘ng mÃ´ng vÃ  chÃ¢n, kÃ¨m theo tÃª bÃ¬</strong>, Ä‘Ã³ cÃ³ thá»ƒ lÃ  dáº¥u hiá»‡u chÃ¨n Ã©p rá»… tháº§n kinh (Tháº§n kinh tá»a).<br>ðŸ”¸ TrÃ¡nh khom lÆ°ng nháº¥c váº­t náº·ng.<br>ðŸ”¸ KhÃ´ng náº±m ná»‡m quÃ¡ má»m.<br>ðŸ‘‰ BÃ¡c sÄ© cÃ³ thá»ƒ yÃªu cáº§u chá»¥p MRI Ä‘á»ƒ xem rÃµ Ä‘Ä©a Ä‘á»‡m (X-Quang chá»‰ tháº¥y xÆ°Æ¡ng, khÃ´ng tháº¥y Ä‘Ä©a Ä‘á»‡m).",
                    null),
            new IntentRule("NECK_PAIN",
                    Arrays.asList("dau co", "vai gay", "moi co", "cung co", "dau vai", "te tay", "chong mat"),
                    "<strong>ðŸ’» Äau má»i vai gÃ¡y / Cá»™t sá»‘ng cá»•</strong><br><br>Ráº¥t phá»• biáº¿n á»Ÿ dÃ¢n vÄƒn phÃ²ng. Náº¿u Ä‘au vai gÃ¡y kÃ¨m <strong>tÃª lan xuá»‘ng cÃ¡nh tay, ngÃ³n tay</strong> hoáº·c <strong>hay chÃ³ng máº·t Ä‘au Ä‘áº§u</strong>, Ä‘Ã³ cÃ³ thá»ƒ lÃ  thoÃ¡i hÃ³a Ä‘á»‘t sá»‘ng cá»• gÃ¢y chÃ¨n Ã©p máº¡ch mÃ¡u/tháº§n kinh.<br>ðŸ”¸ HÃ£y táº­p vÆ°Æ¡n vai, xoay cá»• nháº¹ nhÃ ng má»—i 45 phÃºt lÃ m viá»‡c.",
                    null),
            new IntentRule("GOUT",
                    Arrays.asList("gout", "gut", "axit uric", "acid uric", "sung ngon chan", "dau ngon chan"),
                    "<strong>ðŸ”¥ Bá»‡nh Gout (Thá»‘ng phong)</strong><br><br>Biá»ƒu hiá»‡n Ä‘áº·c trÆ°ng lÃ  <strong>sÆ°ng, nÃ³ng, Ä‘á», Ä‘au dá»¯ dá»™i</strong> thÆ°á»ng báº¯t Ä‘áº§u á»Ÿ khá»›p ngÃ³n chÃ¢n cÃ¡i vÃ o ban Ä‘Ãªm.<br>ðŸ”¸ <strong>KiÃªng:</strong> RÆ°á»£u bia, háº£i sáº£n, ná»™i táº¡ng Ä‘á»™ng váº­t, thá»‹t Ä‘á».<br>ðŸ”¸ <strong>NÃªn lÃ m:</strong> Uá»‘ng nhiá»u nÆ°á»›c, Ä‘i xÃ©t nghiá»‡m ná»“ng Ä‘á»™ Acid Uric trong mÃ¡u.",
                    null),
            new IntentRule("KNEE_PAIN",
                    Arrays.asList("dau dau goi", "dau goi", "tran dich", "dut day chang", "cheo truoc", "cheo sau"),
                    "<strong>ðŸ¦µ Cháº¥n thÆ°Æ¡ng / Äau khá»›p gá»‘i</strong><br><br>Khá»›p gá»‘i chá»‹u toÃ n bá»™ trá»ng lÆ°á»£ng cÆ¡ thá»ƒ. <br>ðŸ”¸ Náº¿u lá»ng gá»‘i, cáº£m giÃ¡c sá»¥m gá»‘i: Nguy cÆ¡ Ä‘á»©t dÃ¢y cháº±ng chÃ©o (thÆ°á»ng do thá»ƒ thao).<br>ðŸ”¸ Náº¿u gá»‘i sÆ°ng to, cÄƒng tá»©c: CÃ³ thá»ƒ bá»‹ trÃ n dá»‹ch khá»›p gá»‘i.<br>ðŸ‘‰ Báº¡n nÃªn háº¡n cháº¿ Ä‘i láº¡i, dÃ¹ng náº¡ng vÃ  Ä‘áº¿n phÃ²ng khÃ¡m chuyÃªn khoa sá»›m.",
                    null),
            new IntentRule("NUTRITION",
                    Arrays.asList("an gi", "dinh duong", "kieng an", "thuc pham", "sua", "thuc an", "thuoc bo", "an uong"),
                    "<strong>ðŸ¥¦ Dinh dÆ°á»¡ng cho XÆ°Æ¡ng Khá»›p</strong><br><br>ðŸ”¸ <strong>Tá»‘t cho xÆ°Æ¡ng:</strong> Thá»±c pháº©m giÃ u Canxi (sá»¯a, sá»¯a chua, cÃ¡ nhá» Äƒn cáº£ xÆ°Æ¡ng), Vitamin D (táº¯m náº¯ng sÃ¡ng sá»›m), Omega-3 (cÃ¡ há»“i, háº¡t Ã³c chÃ³) giÃºp giáº£m viÃªm.<br>ðŸ”¸ <strong>Cáº§n trÃ¡nh:</strong> Thá»©c Äƒn quÃ¡ máº·n (lÃ m Ä‘Ã o tháº£i canxi), nÆ°á»›c ngá»t cÃ³ gas, Ä‘á»“ ngá»t nhiá»u Ä‘Æ°á»ng (tÄƒng pháº£n á»©ng viÃªm).",
                    null)
    );

    public ChatResponse processMessage(String input) {
        if (input == null || input.trim().isEmpty()) {
            return new ChatResponse("Vui lÃ²ng nháº­p cÃ¢u há»i cá»§a báº¡n.", null);
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
                        if (score < 5) score = 0; // Tránh cộng điểm cho từ quá chung chung
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

        return new ChatResponse("Xin lá»—i, tÃ´i chÆ°a hiá»ƒu rÃµ Ã½ cá»§a báº¡n. Äá»ƒ tÃ´i há»— trá»£ tá»‘t nháº¥t, báº¡n cÃ³ thá»ƒ nÃ³i rÃµ hÆ¡n vá» <strong>triá»‡u chá»©ng bá»‡nh lÃ½</strong> (VD: Ä‘au lÆ°ng, nhá»©c gá»‘i, bong gÃ¢n...) hoáº·c yÃªu cáº§u <strong>Ä‘áº·t lá»‹ch khÃ¡m</strong> nhÃ©!", null);
    }

    private String normalizeText(String text) {
        String nfdNormalizedString = Normalizer.normalize(text.toLowerCase(), Normalizer.Form.NFD); 
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        String noAccents = pattern.matcher(nfdNormalizedString).replaceAll("");
        noAccents = noAccents.replaceAll("Ä‘", "d").replaceAll("Ä", "D");
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