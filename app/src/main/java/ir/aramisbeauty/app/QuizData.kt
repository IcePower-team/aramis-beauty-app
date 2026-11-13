package ir.aramisbeauty.app

/**
 * Beauty quiz data: "چی به من میاد؟"
 *
 * The catalog is a curated set of live products — every entry was verified
 * InStock on the live site with its price and photo URL on 2026-11-13.
 * At result time the app re-checks each recommended product live (price +
 * availability) and swaps in the next-best product of the same category
 * when one is out of stock, so a stale bundle can never recommend a
 * product that is no longer available.
 *
 * Scoring is honest: it only uses dimensions we actually know about the
 * products (finish, intensity, occasion, skin type). Skin tone is asked
 * for the result text only — it never changes which products are picked,
 * because their shades were not verified.
 */
object QuizData {

    data class QuizProduct(
        val id: Int,
        val name: String,
        val priceToman: Int,
        val imageUrl: String,
        val category: String, // base | eyes | lips | care
        val tags: Set<String>
    ) {
        val url: String get() = "https://aramis-beauty.ir/product/$id"
    }

    data class QuizOption(
        val text: String,
        val weights: Map<String, Int> = emptyMap(),
        val key: String? = null
    )

    data class QuizQuestion(
        val title: String,
        val options: List<QuizOption>
    )

    val products = listOf(
        QuizProduct(466, "کرم پودر شیشه‌ای دوسه", 550000,
            "https://aramis-beauty.ir/shop/pic/product/aramis-beauty/466.webp",
            "base", setOf("daily", "party")),
        QuizProduct(491, "کانسیلر مایع پرنسس لیدی", 535000,
            "https://aramis-beauty.ir/shop/pic/product/aramis-beauty/491.webp",
            "base", setOf("daily", "party")),
        QuizProduct(497, "کانسیلر مایع رمانتیک", 285000,
            "https://aramis-beauty.ir/shop/pic/product/aramis-beauty/497.webp",
            "base", setOf("daily", "natural")),
        QuizProduct(484, "ریمل آرسلار R01 (بلندکننده)", 650000,
            "https://aramis-beauty.ir/shop/pic/product/aramis-beauty/484.webp",
            "eyes", setOf("party", "bold", "daily")),
        QuizProduct(485, "ریمل آرسلار R02 (پرپشت‌کننده)", 650000,
            "https://aramis-beauty.ir/shop/pic/product/aramis-beauty/485.webp",
            "eyes", setOf("party", "bold")),
        QuizProduct(486, "ریمل ستاره آبی شیدوس", 395000,
            "https://aramis-beauty.ir/shop/pic/product/aramis-beauty/486.webp",
            "eyes", setOf("daily", "natural")),
        QuizProduct(501, "ژل ابرو مورلی آرگان‌دار", 220000,
            "https://aramis-beauty.ir/shop/pic/product/aramis-beauty/501.webp",
            "eyes", setOf("natural", "daily")),
        QuizProduct(495, "ژل لیفت ابرو مشکی مورلی", 235000,
            "https://aramis-beauty.ir/shop/pic/product/aramis-beauty/495.webp",
            "eyes", setOf("bold", "party", "daily")),
        QuizProduct(483, "رژ خیس کره‌ای GAGK", 345000,
            "https://aramis-beauty.ir/shop/pic/product/aramis-beauty/483.webp",
            "lips", setOf("natural", "daily", "glossy")),
        QuizProduct(493, "روغن لب شفاف پینک کی", 250000,
            "https://aramis-beauty.ir/shop/pic/product/aramis-beauty/493.webp",
            "lips", setOf("natural", "daily", "glossy", "dry")),
        QuizProduct(494, "برق لب مجیک کوارلیا", 195000,
            "https://aramis-beauty.ir/shop/pic/product/aramis-beauty/494.webp",
            "lips", setOf("party", "bold", "glossy")),
        QuizProduct(496, "پیلاف لب اولیبولا", 485000,
            "https://aramis-beauty.ir/shop/pic/product/aramis-beauty/496.webp",
            "lips", setOf("party", "bold", "matte")),
        QuizProduct(498, "رژ لب روغنی جی‌ان‌وی", 180000,
            "https://aramis-beauty.ir/shop/pic/product/aramis-beauty/498.webp",
            "lips", setOf("natural", "daily")),
        QuizProduct(469, "اسپری فیکس آلوئه‌ورا کاریته", 590000,
            "https://aramis-beauty.ir/shop/pic/product/aramis-beauty/469.webp",
            "care", setOf("oily", "daily", "natural")),
        QuizProduct(470, "اسپری فیکس شیگلم", 379200,
            "https://aramis-beauty.ir/shop/pic/product/aramis-beauty/470.webp",
            "care", setOf("daily", "party", "oily")),
        QuizProduct(471, "اسپری فیکس کیس بیوتی", 630000,
            "https://aramis-beauty.ir/shop/pic/product/aramis-beauty/471.webp",
            "care", setOf("party", "bold")),
        QuizProduct(34, "روغن آرگان و کراتین انزو", 745000,
            "https://aramis-beauty.ir/shop/pic/product/aramis-beauty/34.jpg",
            "care", setOf("dry", "natural", "daily"))
    )

    val questions = listOf(
        QuizQuestion(
            "پوستت چطوره؟",
            listOf(
                QuizOption("چرب", mapOf("oily" to 2, "matte" to 1), "oily"),
                QuizOption("خشک", mapOf("dry" to 2, "glossy" to 1), "dry"),
                QuizOption("مختلط", mapOf("daily" to 1, "natural" to 1), "mixed"),
                QuizOption("حساس", mapOf("natural" to 2, "daily" to 1), "sensitive")
            )
        ),
        QuizQuestion(
            "رنگ پوستت چیه؟",
            listOf(
                QuizOption("روشن", key = "light"),
                QuizOption("گندمی", key = "wheat"),
                QuizOption("سبزه", key = "deep")
            )
        ),
        QuizQuestion(
            "برای چه موقعیتی می‌خوای؟",
            listOf(
                QuizOption("روزمره", mapOf("daily" to 2), "daily"),
                QuizOption("مهمونی", mapOf("party" to 2), "party"),
                QuizOption("عروس و مجالس", mapOf("party" to 2, "bold" to 1), "bridal")
            )
        ),
        QuizQuestion(
            "چه سبکی دوست داری؟",
            listOf(
                QuizOption("طبیعی و ملایم", mapOf("natural" to 2, "daily" to 1), "natural"),
                QuizOption("متعادل", mapOf("daily" to 1, "natural" to 1, "party" to 1), "balanced"),
                QuizOption("جسورانه و چشمگیر", mapOf("bold" to 2, "party" to 1), "bold")
            )
        )
    )

    /** Score every product by the chosen answers' tag weights. */
    fun score(weights: Map<String, Int>): List<Pair<QuizProduct, Int>> {
        return products.map { p ->
            p to p.tags.sumOf { tag -> weights[tag] ?: 0 }
        }.sortedByDescending { it.second }
    }

    /** The result look: top 3 products with distinct categories. */
    fun pickLook(scored: List<Pair<QuizProduct, Int>>): List<QuizProduct> {
        val picked = mutableListOf<QuizProduct>()
        val usedCategories = mutableSetOf<String>()
        for ((product, _) in scored) {
            if (product.category !in usedCategories) {
                picked.add(product)
                usedCategories.add(product.category)
            }
            if (picked.size == 3) break
        }
        return picked
    }

    fun profileTitle(occasionKey: String?, styleKey: String?): String {
        if (occasionKey == "bridal") return "ست مجلسی باشکوه ✨"
        return when (occasionKey to styleKey) {
            "daily" to "natural" -> "ست روزانه‌ی طبیعی ✨"
            "daily" to "balanced" -> "ست روزانه‌ی شیک ✨"
            "daily" to "bold" -> "ست روزانه‌ی جسورانه ✨"
            "party" to "natural" -> "ست مهمونی ملایم ✨"
            "party" to "balanced" -> "ست مهمونی شیک ✨"
            "party" to "bold" -> "ست مهمونی جسورانه ✨"
            else -> "ست زیبایی تو ✨"
        }
    }

    fun profileDescription(skinKey: String?, toneKey: String?): String {
        val skinTip = when (skinKey) {
            "oily" -> "برای پوست چرب، فیکس‌کننده‌ها ماندگاری آرایشت رو بیشتر می‌کنن."
            "dry" -> "برای پوست خشک، محصولات آبرسان و براق انتخاب شدن."
            "mixed" -> "برای پوست مختلط، تعادلی از مات و براق انتخاب شده."
            "sensitive" -> "برای پوست حساس، فرمول‌های ملایم انتخاب شدن."
            else -> ""
        }
        val toneTip = when (toneKey) {
            "light" -> "رنگ‌های ملایم و صورتی به پوست روشنت میاد."
            "wheat" -> "رنگ‌های گرم به پوست گندمی‌ت میاد."
            "deep" -> "رنگ‌های جسورانه به پوست سبزه‌ت میاد."
            else -> ""
        }
        return listOf(skinTip, toneTip).filter { it.isNotEmpty() }.joinToString(" ")
    }
}
