package ir.aramisbeauty.app

/**
 * Beauty quiz data: "چی به من میاد؟"
 *
 * The product pool is the curated quiz subset of [Catalog] (17 products,
 * all verified InStock on the live site). At result time the app
 * re-checks each recommended product live (price + availability) and
 * swaps in the next-best product of the same category when one is out
 * of stock, so a stale bundle can never recommend a product that is
 * no longer available.
 *
 * Scoring is honest: it only uses dimensions we actually know about the
 * products (finish, intensity, occasion, skin type). Skin tone is asked
 * for the result text only — it never changes which products are picked,
 * because their shades were not verified.
 */
object QuizData {

    /** The 17 quiz products (subset of the shared catalog). */
    private val quizIds = setOf(
        466, 491, 497, 484, 485, 486, 501, 495,
        483, 493, 494, 496, 498, 469, 470, 471, 34
    )

    val products: List<Catalog.ShopProduct> =
        Catalog.products.filter { it.id in quizIds }

    data class QuizOption(
        val text: String,
        val weights: Map<String, Int> = emptyMap(),
        val key: String? = null
    )

    data class QuizQuestion(
        val title: String,
        val options: List<QuizOption>
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
    fun score(weights: Map<String, Int>): List<Pair<Catalog.ShopProduct, Int>> {
        return products.map { p ->
            p to p.tags.sumOf { tag -> weights[tag] ?: 0 }
        }.sortedByDescending { it.second }
    }

    /** The result look: top 3 products with distinct categories. */
    fun pickLook(scored: List<Pair<Catalog.ShopProduct, Int>>): List<Catalog.ShopProduct> {
        val picked = mutableListOf<Catalog.ShopProduct>()
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
