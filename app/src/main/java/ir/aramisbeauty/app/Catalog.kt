package ir.aramisbeauty.app

/**
 * Shared shop catalog for the in-app features (quiz + chat advisor).
 *
 * Every entry was verified live on the site (InStock, price, photo URL):
 * the 17 quiz products on 2026-11-13, the 22 advisor extras the same
 * evening. Prices here are the live prices at verification time; the
 * quiz re-checks them live at result time, and the advisor re-checks
 * availability in the background after showing a reply.
 *
 * Categories: base (face) | eyes | lips | care (hair + skin care) | acc
 * (accessories). Tags drive the quiz/occasion scoring; keywords drive
 * the chat advisor's matching. Keywords are written without ZWNJ; the
 * engine normalizes both sides before matching.
 */
object Catalog {

    data class ShopProduct(
        val id: Int,
        val name: String,
        val priceToman: Int,
        val imageUrl: String,
        val category: String,
        val tags: Set<String>,
        val keywords: Set<String>
    ) {
        val url: String get() = "https://aramis-beauty.ir/product/$id"
    }

    private fun p(
        id: Int, name: String, price: Int, image: String,
        category: String, tags: Set<String>, keywords: Set<String>
    ) = ShopProduct(
        id, name, price,
        "https://aramis-beauty.ir/shop/pic/product/aramis-beauty/$image",
        category, tags, keywords
    )

    val products = listOf(
        // --- Quiz set (17) ---
        p(466, "کرم پودر شیشه‌ای دوسه", 550000, "466.webp", "base",
            setOf("daily", "party"),
            setOf("کرم پودر", "فوندیشن", "پایه", "صورت")),
        p(491, "کانسیلر مایع پرنسس لیدی", 535000, "491.webp", "base",
            setOf("daily", "party"),
            setOf("کانسیلر", "پوشاننده", "صورت")),
        p(497, "کانسیلر مایع رمانتیک", 285000, "497.webp", "base",
            setOf("daily", "natural"),
            setOf("کانسیلر", "پوشاننده", "صورت")),
        p(484, "ریمل آرسلار R01 (بلندکننده)", 650000, "484.webp", "eyes",
            setOf("party", "bold", "daily"),
            setOf("ریمل", "مژه", "بلند", "حجم")),
        p(485, "ریمل آرسلار R02 (پرپشت‌کننده)", 650000, "485.webp", "eyes",
            setOf("party", "bold"),
            setOf("ریمل", "مژه", "حجم", "پرپشت")),
        p(486, "ریمل ستاره آبی شیدوس", 395000, "486.webp", "eyes",
            setOf("daily", "natural"),
            setOf("ریمل", "مژه", "ضد آب")),
        p(501, "ژل ابرو مورلی آرگان‌دار", 220000, "501.webp", "eyes",
            setOf("natural", "daily"),
            setOf("ژل ابرو", "ابرو", "آرگان")),
        p(495, "ژل لیفت ابرو مشکی مورلی", 235000, "495.webp", "eyes",
            setOf("bold", "party", "daily"),
            setOf("ژل ابرو", "ابرو", "مشکی", "لیفت")),
        p(483, "رژ خیس کره‌ای GAGK", 345000, "483.webp", "lips",
            setOf("natural", "daily", "glossy"),
            setOf("رژ", "لب", "خیس", "کره ای")),
        p(493, "روغن لب شفاف پینک کی", 250000, "493.webp", "lips",
            setOf("natural", "daily", "glossy", "dry"),
            setOf("روغن لب", "لیپ اویل", "لب")),
        p(494, "برق لب مجیک کوارلیا", 195000, "494.webp", "lips",
            setOf("party", "bold", "glossy"),
            setOf("برق لب", "لیپ گلاس", "مجیک", "لب")),
        p(496, "پیلاف لب اولیبولا", 485000, "496.webp", "lips",
            setOf("party", "bold", "matte"),
            setOf("پیلاف", "رژ استخری", "لب")),
        p(498, "رژ لب روغنی جی‌ان‌وی", 180000, "498.webp", "lips",
            setOf("natural", "daily"),
            setOf("رژ", "روغن", "لب")),
        p(469, "اسپری فیکس آلوئه‌ورا کاریته", 590000, "469.webp", "care",
            setOf("oily", "daily", "natural"),
            setOf("اسپری فیکس", "فیکس", "آرایش", "ماندگاری", "آلوئه")),
        p(470, "اسپری فیکس شیگلم", 379200, "470.webp", "care",
            setOf("daily", "party", "oily"),
            setOf("اسپری فیکس", "فیکس", "آرایش", "ماندگاری", "شیگلم")),
        p(471, "اسپری فیکس کیس بیوتی", 630000, "471.webp", "care",
            setOf("party", "bold"),
            setOf("اسپری فیکس", "فیکس", "آرایش", "ماندگاری")),
        p(34, "روغن آرگان و کراتین انزو", 745000, "34.jpg", "care",
            setOf("dry", "natural", "daily"),
            setOf("روغن آرگان", "مو", "کراتین", "انزو")),
        // --- Advisor extras (22, verified live 2026-11-13) ---
        p(467, "پالت سایه چشم و رژگونه ۱۴ رنگ سون کول", 580000, "467.webp", "eyes",
            setOf("party", "bold", "daily"),
            setOf("سایه", "پالت", "چشم", "رژگونه", "رژ گونه")),
        p(468, "وزگیر مو استیکی تیجی TIGI", 320000, "468.webp", "care",
            setOf("daily", "natural"),
            setOf("وزگیر", "مو", "استیکی", "تیجی", "چسب مو")),
        p(472, "برس مو طرح‌دار فانتزی", 285000, "472.webp", "care",
            setOf("daily", "natural"),
            setOf("برس", "مو", "شانه", "طرح دار")),
        p(473, "تافت مو ویتروس اولترا استرانگ ۲۵۰ میل", 325000, "473.webp", "care",
            setOf("daily", "party"),
            setOf("تافت", "مو", "اسپری مو", "حالت دهنده")),
        p(481, "پد تخم مرغی", 95000, "481.webp", "base",
            setOf("daily", "natural"),
            setOf("پد", "اسفنج", "تخم مرغی", "کرم پودر", "بلندر")),
        p(482, "شامپو رنگ مو بابل موس رنگ مشکی اصل", 2450000, "482.webp", "care",
            setOf("party", "bold"),
            setOf("شامپو", "رنگ مو", "مشکی", "بابل", "موس")),
        p(474, "گیره انبری استیکر مدل جوجه چاقو به دست", 60000, "474.webp", "acc",
            setOf("daily", "natural"),
            setOf("گیره", "استیکر", "مو", "انبری", "اموجی", "جوجه")),
        p(475, "گیره انبری استیکر مدل برگ پاییزی", 60000, "475.webp", "acc",
            setOf("daily", "natural"),
            setOf("گیره", "استیکر", "مو", "انبری", "اموجی", "برگ")),
        p(476, "گیره انبری استیکر مدل پروانه آبی", 60000, "476.webp", "acc",
            setOf("daily", "natural"),
            setOf("گیره", "استیکر", "مو", "انبری", "اموجی", "پروانه", "آبی")),
        p(477, "گیره انبری استیکر مدل پاپیون صورتی", 60000, "477.webp", "acc",
            setOf("daily", "natural"),
            setOf("گیره", "استیکر", "مو", "انبری", "اموجی", "پاپیون", "صورتی")),
        p(478, "گیره انبری استیکر جوجه تخم مرغی", 60000, "478.webp", "acc",
            setOf("daily", "natural"),
            setOf("گیره", "استیکر", "مو", "انبری", "اموجی", "جوجه")),
        p(479, "گیره انبری استیکر مدل پروانه قهوه‌ای", 60000, "479.webp", "acc",
            setOf("daily", "natural"),
            setOf("گیره", "استیکر", "مو", "انبری", "اموجی", "پروانه", "قهوه ای")),
        p(480, "گیره انبری استیکر مدل پاپیون طلایی", 60000, "480.webp", "acc",
            setOf("daily", "natural"),
            setOf("گیره", "استیکر", "مو", "انبری", "اموجی", "پاپیون", "طلایی")),
        p(487, "پیرسینگ ناف تیتانیوم طرح پاپیون", 675000, "487.webp", "acc",
            setOf("party", "bold"),
            setOf("پیرسینگ", "ناف", "تیتانیوم", "پاپیون")),
        p(488, "پیرسینگ ناف تیتانیوم طرح گل نگین‌دار", 675000, "488.webp", "acc",
            setOf("party", "bold"),
            setOf("پیرسینگ", "ناف", "تیتانیوم", "گل", "نگین")),
        p(489, "پیرسینگ ناف تیتانیوم طرح لنگر", 665000, "489.webp", "acc",
            setOf("party", "bold"),
            setOf("پیرسینگ", "ناف", "تیتانیوم", "لنگر")),
        p(490, "پیرسینگ ناف تیتانیوم طرح گل با آویز اشکی", 695000, "490.webp", "acc",
            setOf("party", "bold"),
            setOf("پیرسینگ", "ناف", "تیتانیوم", "گل", "آویز")),
        p(499, "برس اکستنشن مو", 550000, "499.webp", "care",
            setOf("daily"),
            setOf("برس", "اکستنشن", "مو")),
        p(500, "دستگاه وکس سیلیکونی", 1150000, "500.webp", "care",
            setOf("daily", "natural"),
            setOf("وکس", "دستگاه", "مو", "اپیلاسیون", "سیلیکونی")),
        p(502, "براش سر تخت کرم پودر", 205000, "502.jpg", "base",
            setOf("daily", "natural"),
            setOf("براش", "کرم پودر", "تخت", "آرایش")),
        p(503, "شانه شپش", 15000, "503.webp", "care",
            setOf("daily"),
            setOf("شانه", "شپش")),
        p(411, "فر کننده مو فیلیپس 9 میلی‌متری سرامیکی", 5485000, "411.webp", "care",
            setOf("party", "bold"),
            setOf("فر کننده", "مو", "فیلیپس", "بابلیس", "سرامیکی"))
    )
}
