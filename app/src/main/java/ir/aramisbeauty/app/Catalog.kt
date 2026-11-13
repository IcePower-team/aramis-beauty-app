package ir.aramisbeauty.app

/**
 * Shared shop catalog for the in-app features (quiz + chat advisor).
 *
 * Every entry was verified live on the site (InStock, price, photo URL):
 * the 17 quiz products on 2026-11-13, the 22 advisor extras the same
 * evening, plus 5 in-stock foundations (450, 451, 379, 388, 359) added
 * later that evening after the advisor recommended tools instead of
 * foundations for "کرم پودر", and 7 out-of-stock foundations (45, 47,
 * 101, 102, 115, 119, 140) added so the advisor can list all 13
 * foundations sorted by price, with the chat marking the unavailable
 * ones "ناموجود". Since 2026-11-13 the advisor searches the WHOLE site
 * (SiteProducts.kt: all 503 live products, keywords = name tokens);
 * the entries below are the curated overrides for the products the quiz
 * and the advisor know best.
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
        val keywords: Set<String>,
        /** What the product goes WITH, not what it IS (e.g. a sponge is
         * used with foundation but is not a foundation). Primary hits
         * always outrank related hits in the advisor. */
        val relatedKeywords: Set<String> = emptySet(),
        /** Bundled stock flag, live-verified when added. The chat's live
         * check corrects the badge/price at display time; the flag only
         * drives ORDER (in-stock first). Update it when stock changes. */
        val inStock: Boolean = true
    ) {
        val url: String get() = "https://aramis-beauty.ir/product/$id"
    }

    private fun p(
        id: Int, name: String, price: Int, image: String,
        category: String, tags: Set<String>, keywords: Set<String>,
        related: Set<String> = emptySet(),
        inStock: Boolean = true
    ) = ShopProduct(
        id, name, price,
        "https://aramis-beauty.ir/shop/pic/product/aramis-beauty/$image",
        category, tags, keywords, related, inStock
    )

    private val curatedProducts = listOf(
        // --- Quiz set (17) ---
        p(466, "کرم پودر شیشه‌ای دوسه", 550000, "466.webp", "base",
            setOf("daily", "party"),
            setOf("کرم پودر", "فوندیشن", "پایه", "صورت", "شیشه ای", "دوسه")),
        // --- Foundations (verified live 2026-11-13; the rest of the
        // site's foundations are OutOfStock) ---
        p(450, "کرم پودر آرسلار های کالر پرفکت | SPF 15 | ۳۰ میل", 1000000, "450.webp", "base",
            setOf("daily", "party"),
            setOf("کرم پودر", "فوندیشن", "آرسلار", "شیشه ای", "های کالر", "پرفکت")),
        p(451, "کرم پودر تیوپی آرسلار های کالر | ۳۰ میل | SPF 15", 780000, "451.webp", "base",
            setOf("daily", "party"),
            setOf("کرم پودر", "فوندیشن", "آرسلار", "تیوپی", "های کالر")),
        p(379, "کرم پودر تیوپی اوکالان اصل حاوی روغن آرگان", 525000, "379.jpg", "base",
            setOf("daily", "natural"),
            setOf("کرم پودر", "فوندیشن", "اوکالان", "تیوپی", "آرگان")),
        p(388, "کرم پودر پمپی مینی کویین Mini Queen مدل Nude", 492370, "388.jpg", "base",
            setOf("daily", "matte"),
            setOf("کرم پودر", "فوندیشن", "مینی کویین", "پمپی", "مات")),
        p(359, "کرم پودر CC لنکاور: پوشش بی‌نقص، مات و ماندگار برای پوست چرب و جوش‌دار", 2722000, "359_4.jpg", "base",
            setOf("oily", "matte", "daily"),
            setOf("کرم پودر", "فوندیشن", "لنکاور", "CC", "مات", "چرب")),
        // --- Out-of-stock foundations (verified live 2026-11-13) ---
        // Deliberately in the catalog: the advisor shows them too, sorted
        // by price with the rest, and the chat's live check marks them
        // "ناموجود" — Mahdi wants them visible, not hidden.
        p(115, "کرم پودر تیوپی دوسه اصل", 300000, "115.jpg", "base",
            setOf("daily", "party"),
            setOf("کرم پودر", "فوندیشن", "دوسه", "تیوپی"),
            inStock = false),
        p(47, "کرم پودر تیوپی مک MAC", 421000, "47.jpg", "base",
            setOf("daily", "party"),
            setOf("کرم پودر", "فوندیشن", "مک", "تیوپی"),
            inStock = false),
        p(102, "کرم پودر تیوپی گارنیش", 491000, "102.jpg", "base",
            setOf("daily", "party"),
            setOf("کرم پودر", "فوندیشن", "گارنیش", "تیوپی"),
            inStock = false),
        p(119, "کرم پودر گابرینی اصل", 586000, "119.jpg", "base",
            setOf("daily", "party"),
            setOf("کرم پودر", "فوندیشن", "گابرینی"),
            inStock = false),
        p(101, "کرم پودر شیشه ای گارنیش", 606000, "101.jpg", "base",
            setOf("daily", "party"),
            setOf("کرم پودر", "فوندیشن", "گارنیش", "شیشه ای"),
            inStock = false),
        p(45, "کرم پودر لورال اینفالیبل اصل", 853000, "45.jpg", "base",
            setOf("daily", "party"),
            setOf("کرم پودر", "فوندیشن", "لورال", "اینفالیبل"),
            inStock = false),
        p(140, "کرم پودر نارس مدل نچرال رادیانت اصل", 10267000, "140_2.jpg", "base",
            setOf("daily", "party"),
            setOf("کرم پودر", "فوندیشن", "نارس", "نچرال"),
            inStock = false),
        p(491, "کانسیلر مایع پرنسس لیدی", 535000, "491.webp", "base",
            setOf("daily", "party"),
            setOf("کانسیلر", "پوشاننده", "صورت")),
        p(497, "کانسیلر مایع رمانتیک", 285000, "497.webp", "base",
            setOf("daily", "natural"),
            setOf("کانسیلر", "پوشاننده", "صورت")),
        p(484, "ریمل آرسلار R01 (بلندکننده)", 650000, "484.webp", "eyes",
            setOf("party", "bold", "daily"),
            setOf("ریمل", "مژه", "بلند", "حجم", "آرسلار")),
        p(485, "ریمل آرسلار R02 (پرپشت‌کننده)", 650000, "485.webp", "eyes",
            setOf("party", "bold"),
            setOf("ریمل", "مژه", "حجم", "پرپشت", "آرسلار")),
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
            setOf("برس", "مو", "طرح دار"),
            related = setOf("شانه")),
        p(473, "تافت مو ویتروس اولترا استرانگ ۲۵۰ میل", 325000, "473.webp", "care",
            setOf("daily", "party"),
            setOf("تافت", "مو", "اسپری مو", "حالت دهنده")),
        p(481, "پد تخم مرغی", 95000, "481.webp", "base",
            setOf("daily", "natural"),
            setOf("پد", "اسفنج", "تخم مرغی", "بلندر"),
            related = setOf("کرم پودر")),
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
            setOf("براش", "تخت", "آرایش"),
            related = setOf("کرم پودر")),
        p(503, "شانه شپش", 15000, "503.webp", "care",
            setOf("daily"),
            setOf("شانه", "شپش")),
        p(411, "فر کننده مو فیلیپس 9 میلی‌متری سرامیکی", 5485000, "411.webp", "care",
            setOf("party", "bold"),
            setOf("فر کننده", "مو", "فیلیپس", "بابلیس", "سرامیکی"))
    )

    /** Full site catalog for the chat advisor: every live product on
     * aramis-beauty.ir (SiteProducts.kt, generated from a live scan
     * 2026-11-13), with the curated entries above winning on conflicts —
     * the curated ones carry hand-written keywords, tags and related
     * keywords. The advisor searches this whole list, so any word on the
     * site finds its products; the chat's live check corrects price and
     * stock at display time. The quiz filters this same list by id, and
     * its 17 ids all resolve to curated entries. */
    /** Curated entries win on conflicts; everything else comes from the
     * site list (bundled snapshot or the downloaded catalog.json). */
    fun merge(siteList: List<ShopProduct>): List<ShopProduct> {
        val byId = curatedProducts.associateBy { it.id }.toMutableMap()
        siteList.forEach { if (it.id !in byId) byId[it.id] = it }
        return byId.values.toList()
    }

    val products: List<ShopProduct> = merge(siteProducts)
}
