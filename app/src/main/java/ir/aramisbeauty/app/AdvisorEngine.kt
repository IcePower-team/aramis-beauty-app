package ir.aramisbeauty.app

/**
 * Rule-based beauty advisor: matches the user's Persian message against
 * the shop catalog (keywords + skin/occasion/style tags) and recommends
 * up to 3 products. No network, no AI — everything runs on the phone.
 *
 * Matching is token-based (whole words), never substring-based: a naive
 * substring match would make "مو" (hair) hit inside "مهمونی" (party).
 * Normalization (آ→ا, ي→ی, ك→ک, ZWNJ→space, Persian digits→Latin)
 * is applied to both the input and the keywords before matching.
 */
object AdvisorEngine {

    data class Reply(
        val text: String,
        val products: List<Catalog.ShopProduct>,
        val openQuiz: Boolean = false
    )

    val defaultChips = listOf(
        "پوست چربه چی بزنم؟",
        "کرم پودر خوب چی داری؟",
        "برای مهمونی چی پیشنهاد می‌کنی؟",
        "ارزون‌ترین‌ها",
        "کوئیز زیبایی"
    )

    fun normalize(text: String): String {
        val digits = text.map { c ->
            when (c) {
                in '۰'..'۹' -> "0123456789"[c - '۰']
                in '٠'..'٩' -> "0123456789"[c - '٠']
                else -> c
            }
        }.joinToString("")
        return digits
            .replace('آ', 'ا')
            .replace('ي', 'ی')
            .replace('ك', 'ک')
            .replace("\u200C", " ")
            .replace("\u200D", "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun tokens(normalized: String): List<String> =
        normalized.split(" ").filter { it.isNotEmpty() }

    private fun hasWord(inputTokens: List<String>, word: String): Boolean =
        inputTokens.any { it == word || it.startsWith(word) }

    private fun hits(keywords: Set<String>, inputTokens: List<String>): Int {
        val set = inputTokens.toSet()
        // Keywords are normalized the same way as the input — otherwise
        // a keyword with آ (e.g. "آرسلار") would never match the
        // normalized input token ("ارسلار"). A matched multi-word
        // keyword scores by its word count, so the specific phrase
        // "روغن آرگان" outranks a lone "آرگان" or "روغن".
        return keywords.sumOf { kw ->
            val parts = normalize(kw).split(" ")
            if (parts.all { it in set }) parts.size else 0
        }
    }

    private fun primaryHits(product: Catalog.ShopProduct, inputTokens: List<String>): Int =
        hits(product.keywords, inputTokens)

    private fun relatedHits(product: Catalog.ShopProduct, inputTokens: List<String>): Int =
        hits(product.relatedKeywords, inputTokens)

    fun answer(rawInput: String): Reply {
        val n = normalize(rawInput)
        if (n.isBlank()) {
            return Reply("چیزی ننوشتی 😊 سؤالت رو بنویس — مثلاً «برای پوست چرب چی خوبه؟»", emptyList())
        }
        val t = tokens(n)

        // Greeting
        if (t.any { it in setOf("سلام", "درود", "هی", "صبح", "عصر", "شب") } &&
            t.size <= 3
        ) {
            return Reply(
                "سلام عزیزم! ✨ من مشاور زیبایی آرامیس‌م. بگو چی دنبالشی — " +
                    "مثلاً نوع پوستت، یه محصول خاص، یا موقعیتی که می‌خوای براش آماده شی.",
                emptyList()
            )
        }

        // Quiz handoff
        if ("کوئیز" in t || "کوییز" in t) {
            return Reply(
                "حتماً! کوئیز زیبایی ۴ تا سؤال می‌پرسه و ست مخصوص خودت رو می‌چینه ✨",
                emptyList(),
                openQuiz = true
            )
        }

        // Budget: "زیر ۳۰۰ هزار تومان"
        val under = Regex("""زیر\s*(\d+)\s*(هزار)?""").find(n)
        if (under != null) {
            val amount = under.groupValues[1].toIntOrNull() ?: 0
            val limit = if (under.groupValues[2].isNotEmpty()) amount * 1000 else amount
            if (limit > 0) {
                val picks = Catalog.products
                    .filter { it.priceToman <= limit }
                    .sortedWith(inStockFirstByPrice)
                    .take(20)
                if (picks.isNotEmpty()) {
                    return Reply(
                        "زیر ${formatPrice(limit)}، ${faNum(picks.size)} تا داریم، موجودها اول از ارزون به گرون 👇",
                        picks
                    )
                }
            }
        }

        // Cheapest / discount
        if (t.any { it in setOf("ارزون", "ارزان", "ارزونترین", "ارزانترین") }) {
            val picks = Catalog.products.sortedWith(inStockFirstByPrice).take(20)
            return Reply("ارزون‌ترین‌های فروشگاه — ${faNum(picks.size)} تا، از ارزون به گرون 👇", picks)
        }
        if ("تخفیف" in t) {
            val picks = listOfNotNull(
                Catalog.products.firstOrNull { it.id == 470 },
                Catalog.products.firstOrNull { it.id == 503 },
                Catalog.products.firstOrNull { it.id == 474 }
            )
            return Reply(
                "اسپری فیکس شیگلم الان ۲۱٪ تخفیف داره! 🎉 اینا هم ارزون‌ترین‌هستن 👇",
                picks
            )
        }

        // Bestsellers
        if (t.any { it in setOf("بهترین", "پرفروش", "محبوب", "پرفروشترین") }) {
            val picks = listOfNotNull(
                Catalog.products.firstOrNull { it.id == 484 },
                Catalog.products.firstOrNull { it.id == 485 },
                Catalog.products.firstOrNull { it.id == 494 }
            )
            return Reply("پرفروش‌ترین‌های فروشگاه 👇", picks)
        }

        // Tag weights from skin / occasion / style words
        val weights = mutableMapOf<String, Int>()
        fun add(tag: String, w: Int) { weights[tag] = (weights[tag] ?: 0) + w }
        val skin = when {
            hasWord(t, "چرب") -> { add("oily", 2); add("matte", 1); "چرب" }
            hasWord(t, "خشک") -> { add("dry", 2); add("glossy", 1); "خشک" }
            hasWord(t, "مختلط") -> { add("daily", 1); add("natural", 1); "مختلط" }
            hasWord(t, "حساس") -> { add("natural", 2); add("daily", 1); "حساس" }
            else -> null
        }
        val occasion = when {
            hasWord(t, "عروس") || hasWord(t, "مجلس") -> { add("party", 2); add("bold", 1); "مجلسی" }
            hasWord(t, "مهمونی") -> { add("party", 2); "مهمونی" }
            hasWord(t, "روزمره") -> { add("daily", 2); "روزمره" }
            else -> null
        }
        val style = when {
            hasWord(t, "جسورانه") || hasWord(t, "چشمگیر") -> { add("bold", 2); add("party", 1); "جسورانه" }
            hasWord(t, "طبیعی") || hasWord(t, "ملایم") -> { add("natural", 2); "طبیعی" }
            hasWord(t, "مات") -> { add("matte", 2); "مات" }
            hasWord(t, "براق") || hasWord(t, "شاین") -> { add("glossy", 2); "براق" }
            else -> null
        }

        // Product keyword matches: primary hits (what the product IS) beat
        // related hits (what it goes WITH — a sponge is not a foundation).
        // Ties keep catalog order (stable sort), so the house picks —
        // Dosseh, then the Arselar foundations — come first.
        data class Scored(
            val product: Catalog.ShopProduct,
            val primary: Int,
            val related: Int,
            val tagScore: Int
        )
        val scored = Catalog.products.map { p ->
            Scored(
                p,
                primaryHits(p, t),
                relatedHits(p, t),
                p.tags.sumOf { tag -> weights[tag] ?: 0 }
            )
        }
        // Relevance first, then stock: an exact match that is out of
        // stock still outranks a partial match that is in stock — but
        // within the same relevance, in-stock comes first (Mahdi's rule:
        // even a cheap out-of-stock item comes after every in-stock one
        // of the same relevance), cheapest first.
        val byRelevance = compareByDescending<Scored> { it.primary }
            .thenByDescending { it.product.inStock }
            .thenByDescending { it.tagScore }
            .thenBy { it.product.priceToman }

        val context = listOfNotNull(
            skin?.let { "پوست $it" },
            occasion,
            style
        ).joinToString("، ")
        fun foundText(picks: List<Catalog.ShopProduct>): String {
            val head = if (context.isNotEmpty()) "برای $context" else ""
            return if (picks.size > 3) {
                "${if (head.isNotEmpty()) "$head، " else ""}${faNum(picks.size)} تا پیدا کردم، موجودها اول از ارزون به گرون 👇"
            } else if (head.isNotEmpty()) {
                "$head، اینا بهت میاد 👇"
            } else {
                "این چندتا رو برات پیدا کردم 👇"
            }
        }

        // Up to 20 matches, cheapest first (after relevance): the chat
        // shows the first few and the rest by scrolling.
        val primaryPicks = scored.filter { it.primary > 0 }
            .sortedWith(byRelevance).take(20).map { it.product }
        if (primaryPicks.isNotEmpty()) {
            return Reply(foundText(primaryPicks), primaryPicks)
        }

        val relatedPicks = scored.filter { it.related > 0 }
            .sortedWith(
                compareByDescending<Scored> { it.related }
                    .thenByDescending { it.product.inStock }
                    .thenByDescending { it.tagScore }
                    .thenBy { it.product.priceToman }
            ).take(20).map { it.product }
        if (relatedPicks.isNotEmpty()) {
            return Reply(foundText(relatedPicks), relatedPicks)
        }

        // No product type mentioned — recommend a look from the tag weights
        if (weights.isNotEmpty()) {
            val look = pickLook(
                scored.map { it.product to (it.related + it.tagScore) }
                    .sortedByDescending { it.second }
            )
            if (look.isNotEmpty()) {
                return Reply("بر اساس چیزی که گفتی، این ست بهت میاد ✨", look)
            }
        }

        // Fallback
        return Reply(
            "دقیق متوجه نشدم 😅 ولی می‌تونی از اینا بپرسی — مثلاً «ریمل خوب چی داری؟» " +
                "یا «برای پوست خشک چی پیشنهاد می‌کنی؟» " +
                "اگه نمی‌دونی چی می‌خوای، کوئیز زیبایی رو بزن ✨",
            emptyList()
        )
    }

    private fun pickLook(
        scored: List<Pair<Catalog.ShopProduct, Int>>
    ): List<Catalog.ShopProduct> {
        val picked = mutableListOf<Catalog.ShopProduct>()
        val used = mutableSetOf<String>()
        for ((product, score) in scored) {
            if (score <= 0) break
            if (product.category !in used) {
                picked.add(product)
                used.add(product.category)
            }
            if (picked.size == 3) break
        }
        return picked
    }

    /** In-stock first, then cheapest — the standing list order. */
    private val inStockFirstByPrice =
        compareByDescending<Catalog.ShopProduct> { it.inStock }
            .thenBy { it.priceToman }

    private fun faNum(value: Int): String =
        value.toString().map { c ->
            if (c in '0'..'9') "۰۱۲۳۴۵۶۷۸۹"[c - '0'] else c
        }.joinToString("")

    private fun formatPrice(toman: Int): String {
        val grouped = String.format(java.util.Locale.US, "%,d", toman)
        val fa = grouped.map { c ->
            if (c in '0'..'9') "۰۱۲۳۴۵۶۷۸۹"[c - '0'] else c
        }.joinToString("")
        return "$fa تومان"
    }
}
