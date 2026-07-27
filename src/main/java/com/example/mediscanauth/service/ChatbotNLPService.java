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
            // 1. ChÃƒÂ o hÃ¡Â»Âi & XÃƒÂ£ giao
            new IntentRule("GREETING",
                    Arrays.asList("xin chao", "chao", "hello", "hi", "hey", "chao bac si"),
                    "Xin chÃƒÂ o! Ã°Å¸â€˜â€¹ TÃƒÂ´i lÃƒÂ  <strong>TrÃ¡Â»Â£ lÃƒÂ½ AI MediScan (PhiÃƒÂªn bÃ¡ÂºÂ£n NLP tÃ¡Â»Â± phÃƒÂ¡t triÃ¡Â»Æ’n)</strong>.<br><br>TÃƒÂ´i cÃƒÂ³ thÃ¡Â»Æ’ hÃ¡Â»â€” trÃ¡Â»Â£ bÃ¡ÂºÂ¡n:<br>Ã°Å¸â€˜â€° TÃ†Â° vÃ¡ÂºÂ¥n triÃ¡Â»â€¡u chÃ¡Â»Â©ng xÃ†Â°Ã†Â¡ng khÃ¡Â»â€ºp<br>Ã°Å¸â€˜â€° Ã„ÂÃ¡ÂºÂ·t lÃ¡Â»â€¹ch khÃƒÂ¡m<br>Ã°Å¸â€˜â€° GiÃ¡ÂºÂ£i Ã„â€˜ÃƒÂ¡p thÃƒÂ´ng tin vÃ¡Â»Â phÃƒÂ²ng khÃƒÂ¡m<br><br>BÃ¡ÂºÂ¡n Ã„â€˜ang gÃ¡ÂºÂ·p vÃ¡ÂºÂ¥n Ã„â€˜Ã¡Â»Â gÃƒÂ¬?",
                    null),
            new IntentRule("THANKS",
                    Arrays.asList("cam on", "thanks", "ok", "hieu roi", "tot", "tuyet", "da ro", "da hieu"),
                    "KhÃƒÂ´ng cÃƒÂ³ gÃƒÂ¬! RÃ¡ÂºÂ¥t vui khi Ã„â€˜Ã†Â°Ã¡Â»Â£c hÃ¡Â»â€” trÃ¡Â»Â£ bÃ¡ÂºÂ¡n. Ã°Å¸ËœÅ <br><br>Ã°Å¸â€™Â¡ <em>LÃ†Â°u ÃƒÂ½: ThÃƒÂ´ng tin tÃ¡Â»Â« chatbot chÃ¡Â»â€° mang tÃƒÂ­nh tham khÃ¡ÂºÂ£o. NÃ¡ÂºÂ¿u cÃƒÂ³ triÃ¡Â»â€¡u chÃ¡Â»Â©ng nÃ¡ÂºÂ·ng, bÃ¡ÂºÂ¡n nÃƒÂªn Ã„â€˜Ã¡ÂºÂ¿n phÃƒÂ²ng khÃƒÂ¡m ngay nhÃƒÂ©!</em>",
                    null),
            new IntentRule("GOODBYE",
                    Arrays.asList("tam biet", "bye", "thoat", "hen gap lai", "chao tam biet"),
                    "TÃ¡ÂºÂ¡m biÃ¡Â»â€¡t bÃ¡ÂºÂ¡n! Ã°Å¸â€˜â€¹ ChÃƒÂºc bÃ¡ÂºÂ¡n luÃƒÂ´n cÃƒÂ³ mÃ¡Â»â„¢t hÃ¡Â»â€¡ xÃ†Â°Ã†Â¡ng khÃ¡Â»â€ºp khÃ¡Â»Âe mÃ¡ÂºÂ¡nh.",
                    null),

            // 2. ChÃ¡Â»Â©c nÃ„Æ’ng hÃ¡Â»â€¡ thÃ¡Â»â€˜ng
            new IntentRule("BOOKING",
                    Arrays.asList("dat lich", "kham benh", "dang ky", "dat cho", "hen kham", "toi muon kham", "dang ky kham", "muon kham"),
                    "TuyÃ¡Â»â€¡t vÃ¡Â»Âi! HÃ¡Â»â€¡ thÃ¡Â»â€˜ng sÃ¡ÂºÂ½ giÃƒÂºp bÃ¡ÂºÂ¡n Ã„â€˜Ã¡ÂºÂ·t lÃ¡Â»â€¹ch khÃƒÂ¡m ngay bÃƒÂ¢y giÃ¡Â»Â.",
                    "triggerBooking"),
            new IntentRule("XRAY_AI",
                    Arrays.asList("chup xquang", "chup x quang", "ai phan tich", "phan tich anh", "ket qua xquang", "doc phim", "gui anh xquang"),
                    "<strong>Ã°Å¸Â¤â€“ PhÃƒÂ¢n tÃƒÂ­ch X-Quang bÃ¡ÂºÂ±ng AI</strong><br><br>HÃ¡Â»â€¡ thÃ¡Â»â€˜ng MediScan cÃƒÂ³ tÃƒÂ­ch hÃ¡Â»Â£p TrÃƒÂ­ tuÃ¡Â»â€¡ NhÃƒÂ¢n tÃ¡ÂºÂ¡o Ã„â€˜Ã¡Â»Æ’ tÃ¡Â»Â± Ã„â€˜Ã¡Â»â„¢ng phÃƒÂ¢n tÃƒÂ­ch phim X-Quang cÃ¡Â»Â§a bÃ¡ÂºÂ¡n.<br>Ã°Å¸â€˜â€° Truy cÃ¡ÂºÂ­p mÃ¡Â»Â¥c <strong>HÃ¡Â»â€œ sÃ†Â¡ Y tÃ¡ÂºÂ¿ -> TÃ¡ÂºÂ£i Ã¡ÂºÂ£nh X-Quang</strong> Ã„â€˜Ã¡Â»Æ’ AI nhÃ¡ÂºÂ­n diÃ¡Â»â€¡n vÃƒÂ¹ng tÃ¡Â»â€¢n thÃ†Â°Ã†Â¡ng, Ã„â€˜Ã¡Â»â„¢ tin cÃ¡ÂºÂ­y vÃƒÂ  cÃ¡ÂºÂ£nh bÃƒÂ¡o sÃ¡Â»â€ºm cÃƒÂ¡c dÃ¡ÂºÂ¥u hiÃ¡Â»â€¡u bÃ¡ÂºÂ¥t thÃ†Â°Ã¡Â»Âng.",
                    null),
            new IntentRule("CLINIC_INFO",
                    Arrays.asList("gio lam viec", "dia chi", "o dau", "lich lam viec", "gia kham", "chi phi", "bao nhieu tien"),
                    "<strong>Ã°Å¸ÂÂ¥ ThÃƒÂ´ng tin PhÃƒÂ²ng khÃƒÂ¡m MediScan</strong><br><br>Ã°Å¸â€Â¸ <strong>GiÃ¡Â»Â lÃƒÂ m viÃ¡Â»â€¡c:</strong> 07:00 - 17:00 (TÃ¡Â»Â« ThÃ¡Â»Â© 2 Ã„â€˜Ã¡ÂºÂ¿n ChÃ¡Â»Â§ nhÃ¡ÂºÂ­t)<br>Ã°Å¸â€Â¸ <strong>GiÃƒÂ¡ khÃƒÂ¡m cÃ†Â¡ bÃ¡ÂºÂ£n:</strong> 150.000Ã„â€˜ - 300.000Ã„â€˜ (ChÃ†Â°a bao gÃ¡Â»â€œm phÃƒÂ­ chÃ¡Â»Â¥p X-Quang).<br>Ã°Å¸â€˜â€° BÃ¡ÂºÂ¡n cÃƒÂ³ thÃ¡Â»Æ’ thao tÃƒÂ¡c Ã„ÂÃ¡ÂºÂ·t lÃ¡Â»â€¹ch Ã„â€˜Ã¡Â»Æ’ Ã„â€˜Ã†Â°Ã¡Â»Â£c giÃ¡Â»Â¯ chÃ¡Â»â€” trÃ†Â°Ã¡Â»â€ºc nhÃƒÂ©!",
                    null),

            // 3. KiÃ¡ÂºÂ¿n thÃ¡Â»Â©c Y khoa - BÃ¡Â»â€¡nh lÃƒÂ½ CÃ†Â¡ XÃ†Â°Ã†Â¡ng KhÃ¡Â»â€ºp
            new IntentRule("BONE_FRACTURE",
                    Arrays.asList("gay", "gay xuong", "vo xuong", "nut xuong", "nghi gay", "te nga"),
                    "<strong>Ã¢Å¡Â Ã¯Â¸Â Nghi ngÃ¡Â»Â gÃƒÂ£y xÃ†Â°Ã†Â¡ng - XÃ¡Â»Â­ lÃƒÂ½ khÃ¡ÂºÂ©n cÃ¡ÂºÂ¥p</strong><br><br><strong>DÃ¡ÂºÂ¥u hiÃ¡Â»â€¡u:</strong> Ã„Âau dÃ¡Â»Â¯ dÃ¡Â»â„¢i Ã„â€˜Ã¡Â»â„¢t ngÃ¡Â»â„¢t, sÃ†Â°ng bÃ¡ÂºÂ§m lan rÃ¡Â»â„¢ng, biÃ¡ÂºÂ¿n dÃ¡ÂºÂ¡ng chi, lÃ¡ÂºÂ¡o xÃ¡ÂºÂ¡o xÃ†Â°Ã†Â¡ng.<br><strong>SÃ†Â¡ cÃ¡Â»Â©u:</strong><br>Ã°Å¸â€Â¸ GiÃ¡Â»Â¯ nÃ¡ÂºÂ¡n nhÃƒÂ¢n bÃ¡ÂºÂ¥t Ã„â€˜Ã¡Â»â„¢ng, khÃƒÂ´ng cÃ¡Â»â€˜ nÃ¡ÂºÂ¯n chÃ¡Â»â€°nh.<br>Ã°Å¸â€Â¸ DÃƒÂ¹ng nÃ¡ÂºÂ¹p cÃ¡Â»Â©ng (gÃ¡Â»â€”, bÃƒÂ¬a carton) Ã„â€˜Ã¡Â»Æ’ cÃ¡Â»â€˜ Ã„â€˜Ã¡Â»â€¹nh tÃ¡ÂºÂ¡m thÃ¡Â»Âi.<br>Ã°Å¸â€˜â€° <strong>CÃ¡ÂºÂ§n chÃ¡Â»Â¥p X-Quang ngay lÃ¡ÂºÂ­p tÃ¡Â»Â©c.</strong>",
                    null),
            new IntentRule("SWELLING_SPRAIN",
                    Arrays.asList("sung", "bam tim", "tu mau", "bong gan", "trat khop", "cang co", "gian day chang", "lat somi", "lat so mi"),
                    "<strong>Ã°Å¸Â§Å  Bong gÃƒÂ¢n / SÃ†Â°ng bÃ¡ÂºÂ§m tÃƒÂ­m</strong><br><br><strong>PhÃ†Â°Ã†Â¡ng phÃƒÂ¡p R.I.C.E (48 giÃ¡Â»Â Ã„â€˜Ã¡ÂºÂ§u):</strong><br>Ã°Å¸â€Â¸ <strong>R (Rest):</strong> NgÃ¡Â»Â«ng vÃ¡ÂºÂ­n Ã„â€˜Ã¡Â»â„¢ng, nghÃ¡Â»â€° ngÃ†Â¡i.<br>Ã°Å¸â€Â¸ <strong>I (Ice):</strong> ChÃ†Â°Ã¡Â»Âm lÃ¡ÂºÂ¡nh 15-20p, cÃƒÂ¡ch nhau 2h (TuyÃ¡Â»â€¡t Ã„â€˜Ã¡Â»â€˜i KHÃƒâ€NG chÃ†Â°Ã¡Â»Âm nÃƒÂ³ng hay xoa dÃ¡ÂºÂ§u nÃƒÂ³ng).<br>Ã°Å¸â€Â¸ <strong>C (Compression):</strong> DÃƒÂ¹ng bÃ„Æ’ng thun bÃ„Æ’ng ÃƒÂ©p nhÃ¡ÂºÂ¹ nhÃƒÂ ng.<br>Ã°Å¸â€Â¸ <strong>E (Elevation):</strong> KÃƒÂª cao vÃƒÂ¹ng bÃ¡Â»â€¹ thÃ†Â°Ã†Â¡ng.",
                    null),
            new IntentRule("OSTEOPOROSIS",
                    Arrays.asList("loang xuong", "xuong yeu", "mat xuong", "canxi", "nhuc xuong", "moi xuong", "thieu canxi"),
                    "<strong>Ã°Å¸Â¦Â´ LoÃƒÂ£ng xÃ†Â°Ã†Â¡ng (SÃƒÂ¡t thÃ¡Â»Â§ thÃ¡ÂºÂ§m lÃ¡ÂºÂ·ng)</strong><br><br><strong>TriÃ¡Â»â€¡u chÃ¡Â»Â©ng:</strong> Ã„Âau mÃ¡Â»Âi mÃ†Â¡ hÃ¡Â»â€œ dÃ¡Â»Âc cÃƒÂ¡c xÃ†Â°Ã†Â¡ng dÃƒÂ i, giÃ¡ÂºÂ£m chiÃ¡Â»Âu cao, gÃƒÂ¹ lÃ†Â°ng, dÃ¡Â»â€¦ gÃƒÂ£y xÃ†Â°Ã†Â¡ng dÃƒÂ¹ va chÃ¡ÂºÂ¡m nhÃ¡ÂºÂ¹.<br>Ã°Å¸â€™Â¡ <strong>LÃ¡Â»Âi khuyÃƒÂªn:</strong> PhÃ¡Â»Â¥ nÃ¡Â»Â¯ sau mÃƒÂ£n kinh hoÃ¡ÂºÂ·c ngÃ†Â°Ã¡Â»Âi lÃ¡Â»â€ºn tuÃ¡Â»â€¢i nÃƒÂªn Ã„â€˜i Ã„â€˜o <strong>MÃ¡ÂºÂ­t Ã„â€˜Ã¡Â»â„¢ xÃ†Â°Ã†Â¡ng (DEXA)</strong> Ã„â€˜Ã¡Â»â€¹nh kÃ¡Â»Â³ 6 thÃƒÂ¡ng/lÃ¡ÂºÂ§n.",
                    null),
            new IntentRule("ARTHRITIS",
                    Arrays.asList("viem khop", "thoai hoa", "xuong khop", "cung khop", "dau khop", "nhuc khop", "kho cu dong", "luc cuc"),
                    "<strong>Ã°Å¸â€Â¥ ViÃƒÂªm / ThoÃƒÂ¡i hÃƒÂ³a khÃ¡Â»â€ºp</strong><br><br>Ã°Å¸â€Â¸ <strong>ThoÃƒÂ¡i hÃƒÂ³a khÃ¡Â»â€ºp:</strong> Ã„Âau tÃ„Æ’ng khi vÃ¡ÂºÂ­n Ã„â€˜Ã¡Â»â„¢ng, giÃ¡ÂºÂ£m khi nghÃ¡Â»â€° ngÃ†Â¡i. KhÃ¡Â»â€ºp cÃƒÂ³ tiÃ¡ÂºÂ¿ng lÃ¡ÂºÂ¡o xÃ¡ÂºÂ¡o (lÃ¡Â»Â¥c cÃ¡Â»Â¥c) khi cÃ¡Â»Â­ Ã„â€˜Ã¡Â»â„¢ng.<br>Ã°Å¸â€Â¸ <strong>ViÃƒÂªm khÃ¡Â»â€ºp dÃ¡ÂºÂ¡ng thÃ¡ÂºÂ¥p:</strong> CÃ¡Â»Â©ng khÃ¡Â»â€ºp buÃ¡Â»â€¢i sÃƒÂ¡ng kÃƒÂ©o dÃƒÂ i hÃ†Â¡n 1 tiÃ¡ÂºÂ¿ng, sÃ†Â°ng nÃƒÂ³ng Ã„â€˜Ã¡Â»Â Ã„â€˜au nhiÃ¡Â»Âu khÃ¡Â»â€ºp Ã„â€˜Ã¡Â»â€˜i xÃ¡Â»Â©ng.<br>Ã°Å¸â€˜â€° CÃ¡ÂºÂ§n Ã„â€˜i khÃƒÂ¡m Ã„â€˜Ã¡Â»Æ’ bÃƒÂ¡c sÃ„Â© chÃ¡Â»â€° Ã„â€˜Ã¡Â»â€¹nh thuÃ¡Â»â€˜c, trÃƒÂ¡nh tÃ¡Â»Â± ÃƒÂ½ mua thuÃ¡Â»â€˜c giÃ¡ÂºÂ£m Ã„â€˜au dÃ¡ÂºÂ¡ dÃƒÂ y.",
                    null),
            new IntentRule("HAND_PAIN",
                    Arrays.asList("dau ban tay", "dau co tay", "te ban tay", "ong co tay", "ngon tay", "cung ngon tay"),
                    "<strong>ðŸ– Äau BÃ n tay / Cá»• tay</strong><br><br>CÃ³ thá»ƒ báº¡n Ä‘ang gáº·p Há»™i chá»©ng á»‘ng cá»• tay (chÃ¨n Ã©p tháº§n kinh) hoáº·c ViÃªm khá»›p ngÃ³n tay.<br>ðŸ”¸ TrÃ¡nh gÃµ phÃ­m/cáº§m chuá»™t liÃªn tá»¥c.<br>ðŸ”¸ Náº¿u Ä‘au kÃ¨m káº¹t ngÃ³n tay (ngÃ³n tay cÃ² sÃºng), hÃ£y Ä‘i khÃ¡m sá»›m.",
                    null),
            new IntentRule("BACK_PAIN",
                    Arrays.asList("dau lung", "thoat vi", "dia dem", "than kinh toa", "te chan", "dau cot song", "dau that lung"),
                    "<strong>Ã¢Å¡Â¡ Ã„Âau lÃ†Â°ng / ThoÃƒÂ¡t vÃ¡Â»â€¹ Ã„â€˜Ã„Â©a Ã„â€˜Ã¡Â»â€¡m</strong><br><br>NÃ¡ÂºÂ¿u bÃ¡ÂºÂ¡n bÃ¡Â»â€¹ <strong>Ã„â€˜au thÃ¡ÂºÂ¯t lÃ†Â°ng lan xuÃ¡Â»â€˜ng mÃƒÂ´ng vÃƒÂ  chÃƒÂ¢n, kÃƒÂ¨m theo tÃƒÂª bÃƒÂ¬</strong>, Ã„â€˜ÃƒÂ³ cÃƒÂ³ thÃ¡Â»Æ’ lÃƒÂ  dÃ¡ÂºÂ¥u hiÃ¡Â»â€¡u chÃƒÂ¨n ÃƒÂ©p rÃ¡Â»â€¦ thÃ¡ÂºÂ§n kinh (ThÃ¡ÂºÂ§n kinh tÃ¡Â»Âa).<br>Ã°Å¸â€Â¸ TrÃƒÂ¡nh khom lÃ†Â°ng nhÃ¡ÂºÂ¥c vÃ¡ÂºÂ­t nÃ¡ÂºÂ·ng.<br>Ã°Å¸â€Â¸ KhÃƒÂ´ng nÃ¡ÂºÂ±m nÃ¡Â»â€¡m quÃƒÂ¡ mÃ¡Â»Âm.<br>Ã°Å¸â€˜â€° BÃƒÂ¡c sÃ„Â© cÃƒÂ³ thÃ¡Â»Æ’ yÃƒÂªu cÃ¡ÂºÂ§u chÃ¡Â»Â¥p MRI Ã„â€˜Ã¡Â»Æ’ xem rÃƒÂµ Ã„â€˜Ã„Â©a Ã„â€˜Ã¡Â»â€¡m (X-Quang chÃ¡Â»â€° thÃ¡ÂºÂ¥y xÃ†Â°Ã†Â¡ng, khÃƒÂ´ng thÃ¡ÂºÂ¥y Ã„â€˜Ã„Â©a Ã„â€˜Ã¡Â»â€¡m).",
                    null),
            new IntentRule("NECK_PAIN",
                    Arrays.asList("dau co", "vai gay", "moi co", "cung co", "dau vai", "te tay", "chong mat"),
                    "<strong>Ã°Å¸â€™Â» Ã„Âau mÃ¡Â»Âi vai gÃƒÂ¡y / CÃ¡Â»â„¢t sÃ¡Â»â€˜ng cÃ¡Â»â€¢</strong><br><br>RÃ¡ÂºÂ¥t phÃ¡Â»â€¢ biÃ¡ÂºÂ¿n Ã¡Â»Å¸ dÃƒÂ¢n vÃ„Æ’n phÃƒÂ²ng. NÃ¡ÂºÂ¿u Ã„â€˜au vai gÃƒÂ¡y kÃƒÂ¨m <strong>tÃƒÂª lan xuÃ¡Â»â€˜ng cÃƒÂ¡nh tay, ngÃƒÂ³n tay</strong> hoÃ¡ÂºÂ·c <strong>hay chÃƒÂ³ng mÃ¡ÂºÂ·t Ã„â€˜au Ã„â€˜Ã¡ÂºÂ§u</strong>, Ã„â€˜ÃƒÂ³ cÃƒÂ³ thÃ¡Â»Æ’ lÃƒÂ  thoÃƒÂ¡i hÃƒÂ³a Ã„â€˜Ã¡Â»â€˜t sÃ¡Â»â€˜ng cÃ¡Â»â€¢ gÃƒÂ¢y chÃƒÂ¨n ÃƒÂ©p mÃ¡ÂºÂ¡ch mÃƒÂ¡u/thÃ¡ÂºÂ§n kinh.<br>Ã°Å¸â€Â¸ HÃƒÂ£y tÃ¡ÂºÂ­p vÃ†Â°Ã†Â¡n vai, xoay cÃ¡Â»â€¢ nhÃ¡ÂºÂ¹ nhÃƒÂ ng mÃ¡Â»â€”i 45 phÃƒÂºt lÃƒÂ m viÃ¡Â»â€¡c.",
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
                    "<strong>Ã°Å¸Â¥Â¦ Dinh dÃ†Â°Ã¡Â»Â¡ng cho XÃ†Â°Ã†Â¡ng KhÃ¡Â»â€ºp</strong><br><br>Ã°Å¸â€Â¸ <strong>TÃ¡Â»â€˜t cho xÃ†Â°Ã†Â¡ng:</strong> ThÃ¡Â»Â±c phÃ¡ÂºÂ©m giÃƒÂ u Canxi (sÃ¡Â»Â¯a, sÃ¡Â»Â¯a chua, cÃƒÂ¡ nhÃ¡Â»Â Ã„Æ’n cÃ¡ÂºÂ£ xÃ†Â°Ã†Â¡ng), Vitamin D (tÃ¡ÂºÂ¯m nÃ¡ÂºÂ¯ng sÃƒÂ¡ng sÃ¡Â»â€ºm), Omega-3 (cÃƒÂ¡ hÃ¡Â»â€œi, hÃ¡ÂºÂ¡t ÃƒÂ³c chÃƒÂ³) giÃƒÂºp giÃ¡ÂºÂ£m viÃƒÂªm.<br>Ã°Å¸â€Â¸ <strong>CÃ¡ÂºÂ§n trÃƒÂ¡nh:</strong> ThÃ¡Â»Â©c Ã„Æ’n quÃƒÂ¡ mÃ¡ÂºÂ·n (lÃƒÂ m Ã„â€˜ÃƒÂ o thÃ¡ÂºÂ£i canxi), nÃ†Â°Ã¡Â»â€ºc ngÃ¡Â»Ât cÃƒÂ³ gas, Ã„â€˜Ã¡Â»â€œ ngÃ¡Â»Ât nhiÃ¡Â»Âu Ã„â€˜Ã†Â°Ã¡Â»Âng (tÃ„Æ’ng phÃ¡ÂºÂ£n Ã¡Â»Â©ng viÃƒÂªm).",
                    null)
    );

    public ChatResponse processMessage(String input) {
        if (input == null || input.trim().isEmpty()) {
            return new ChatResponse("Vui lÃƒÂ²ng nhÃ¡ÂºÂ­p cÃƒÂ¢u hÃ¡Â»Âi cÃ¡Â»Â§a bÃ¡ÂºÂ¡n.", null);
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

        return new ChatResponse("Xin lÃ¡Â»â€”i, tÃƒÂ´i chÃ†Â°a hiÃ¡Â»Æ’u rÃƒÂµ ÃƒÂ½ cÃ¡Â»Â§a bÃ¡ÂºÂ¡n. Ã„ÂÃ¡Â»Æ’ tÃƒÂ´i hÃ¡Â»â€” trÃ¡Â»Â£ tÃ¡Â»â€˜t nhÃ¡ÂºÂ¥t, bÃ¡ÂºÂ¡n cÃƒÂ³ thÃ¡Â»Æ’ nÃƒÂ³i rÃƒÂµ hÃ†Â¡n vÃ¡Â»Â <strong>triÃ¡Â»â€¡u chÃ¡Â»Â©ng bÃ¡Â»â€¡nh lÃƒÂ½</strong> (VD: Ã„â€˜au lÃ†Â°ng, nhÃ¡Â»Â©c gÃ¡Â»â€˜i, bong gÃƒÂ¢n...) hoÃ¡ÂºÂ·c yÃƒÂªu cÃ¡ÂºÂ§u <strong>Ã„â€˜Ã¡ÂºÂ·t lÃ¡Â»â€¹ch khÃƒÂ¡m</strong> nhÃƒÂ©!", null);
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