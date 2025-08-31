package eu.kanade.tachiyomi.extension.en.novelfire

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

// each filter adds itself to the URL
interface UriFilter {
    fun addToUri(builder: HttpUrl.Builder)
}

open class UriPartFilter(
    name: String,
    private val param: String,
    private val vals: Array<Pair<String, String>>,
    defaultValue: String? = null,
) : Filter.Select<String>(
    name,
    vals.map { it.first }.toTypedArray(),
    vals.indexOfFirst { it.second == defaultValue }.takeIf { it != -1 } ?: 0,
),
    UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        builder.addQueryParameter(param, vals[state].second)
    }
}

open class UriMultiSelectOption(name: String, val value: String) : Filter.CheckBox(name)

open class UriMultiSelectFilter(
    name: String,
    private val param: String,
    private val vals: Array<Pair<String, String>>,
) : Filter.Group<UriMultiSelectOption>(name, vals.map { UriMultiSelectOption(it.first, it.second) }), UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        val checked = state.filter { it.state }
        checked.forEach { builder.addQueryParameter(param, it.value) }
    }
}

// =============== Filters for NovelFire advanced search ===============

// Genres multi-select
class GenreFilter : UriMultiSelectFilter(
    "Genres",
    "categories[]",
    arrayOf(
        // Known IDs
        "Action" to "3",
        "Adult" to "28",
        "Adventure" to "4",
        "Anime" to "46",
        "Arts" to "47",
        "Comedy" to "5",
        "Drama" to "24",
        "Ecchi" to "26",
        "Eastern" to "44",
        "Fan-Fiction" to "48",
        "Fantasy" to "6",
        "Game" to "19",
        "Gender Bender" to "25",
        "Historical" to "12",
        "Horror" to "37",
        "Isekai" to "49",
        "Josei" to "2",
        "Lgbt+" to "45",
        "Magic" to "50",
        "Magical realism" to "51",
        "Manhua" to "52",
        "Martial Arts" to "15",
        "Mature" to "8",
        "Mecha" to "34",
        "Military" to "53",
        "Modern life" to "54",
        "Movies" to "55",
        "Mystery" to "16",
        "Other" to "64",
        "Psychological" to "9",
        "Realistic fiction" to "56",
        "Reincarnation" to "43",
        "Romance" to "1",
        "School Life" to "21",
        "Sci-fi" to "20",
        "Seinen" to "10",
        "Shoujo" to "38",
        "Shoujo Ai" to "57",
        "Shounen" to "17",
        "Shounen Ai" to "39",
        "Slice of Life" to "13",
        "Smut" to "29",
        "Sports" to "42",
        "Supernatural" to "18",
        "System" to "58",
        "Tragedy" to "32",
        "Urban" to "63",
        "Urban Life" to "59",
        "Video Games" to "60",
        "War" to "61",
        "Wuxia" to "31",
        "Xianxia" to "23",
        "Xuanhuan" to "22",
        "Yaoi" to "14",
        "Yuri" to "62",
    ),
)

class GenreModeFilter : UriPartFilter(
    "Genres Mode",
    "ctgcon",
    arrayOf(
        "and" to "and",
        "or" to "or",
        "exclude" to "exclude",
    ),
    defaultValue = "and",
)

// Origin / Raw Language
class OriginLanguageFilter : UriMultiSelectFilter(
    "Origin / Raw Language",
    "country_id[]",
    arrayOf(
        "Chinese Novel" to "1",
        "Korean Novel" to "2",
        "Japanese Novel" to "3",
        "English Novel" to "4",
    ),
)

// Translation Status
class StatusFilter : UriPartFilter(
    "Translation Status",
    "status",
    arrayOf(
        "All" to "-1",
        "Ongoing" to "0",
        "Completed" to "1",
    ),
    defaultValue = "-1",
)

// Chapters range
class ChaptersFilter : UriPartFilter(
    "Chapters",
    "totalchapter",
    arrayOf(
        "All" to "0",
        "<50" to "1,49",
        "50-100" to "50,100",
        "100-200" to "100,200",
        "200-500" to "200,500",
        "500-1000" to "500,1000",
        ">1000" to "1001,1000000",
    ),
    defaultValue = "0",
)

// Rating comparator (min/max) and value (0..5)
class RatingModeFilter : UriPartFilter(
    "Rating Comparator",
    "ratcon",
    arrayOf(
        "Minimum" to "min",
        "Maximum" to "max",
    ),
    defaultValue = "min",
)

class RatingValueFilter : Filter.Text("Rating Value (Max 5)"), UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        val v = state.trim()
        if (v.isEmpty()) return
        val num = v.toIntOrNull()
            ?: throw IllegalArgumentException("Rating must be an integer between 0 and 5")
        if (num !in 0..5) throw IllegalArgumentException("Rating must be an integer between 0 and 5")
        builder.addQueryParameter("rating", num.toString())
    }
}

// Sort
class SortFilter(defaultValue: String? = null) : UriPartFilter(
    "Sort Results By",
    "sort",
    arrayOf(
        "Last Updated (Newest)" to "date",
        "Rank (Top)" to "rank-top",
        "Review Count (Most)" to "review",
        "Rating Score (Top)" to "rating-score-top",
        "Bookmark Count (Top)" to "bookmark",
        "Title A>Z" to "abc",
        "Title Z>A" to "cba",
    ),
    defaultValue,
)
