package eu.kanade.tachiyomi.extension.en.novelfire

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class NovelFire : ParsedHttpSource() {
    private fun parseChapterDate(datetime: String?): Long {
        if (datetime.isNullOrBlank()) return 0L
        return try {
            val formatter = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
            formatter.timeZone = java.util.TimeZone.getTimeZone("UTC")
            formatter.parse(datetime)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
    override val name = "NovelFire"
    override val baseUrl = "https://novelfire.net"
    override val lang = "en"
    override val supportsLatest = true
    val isNovelSource: Boolean = true

    override fun popularMangaRequest(page: Int): Request =
        Request.Builder().url("$baseUrl/genre-all/sort-popular/status-all/all-novel?page=$page").headers(headers).build()

    override fun popularMangaSelector(): String = "ul.novel-list.col6 li.novel-item"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        val titleElement = element.selectFirst("h4.novel-title a") ?: element.selectFirst("a[title]")
        val coverElement = element.selectFirst("figure.novel-cover img")

        manga.title = titleElement?.attr("title") ?: "Unknown Title"
        manga.setUrlWithoutDomain(titleElement?.attr("href") ?: "")

        val coverUrl = coverElement?.attr("data-src") ?: coverElement?.attr("src") ?: ""
        manga.thumbnail_url = when {
            coverUrl.startsWith("http") -> coverUrl
            coverUrl.startsWith("//") -> "https:$coverUrl"
            coverUrl.isNotEmpty() -> baseUrl + coverUrl
            else -> ""
        }

        return manga
    }

    override fun popularMangaNextPageSelector(): String? = "a.page-link[rel=next], a[rel=next]"

    override fun latestUpdatesRequest(page: Int): Request =
        Request.Builder().url("$baseUrl/genre-all/sort-new/status-all/all-novel?page=$page").headers(headers).build()

    override fun latestUpdatesSelector(): String = "ul.novel-list.col6 li.novel-item"

    override fun latestUpdatesFromElement(element: Element): SManga {
        val manga = SManga.create()
        val titleElement = element.selectFirst("h4.novel-title a") ?: element.selectFirst("a[title]")
        val coverElement = element.selectFirst("figure.novel-cover img")

        manga.title = titleElement?.attr("title") ?: "Unknown Title"
        manga.setUrlWithoutDomain(titleElement?.attr("href") ?: "")

        val coverUrl = coverElement?.attr("data-src") ?: coverElement?.attr("src") ?: ""
        manga.thumbnail_url = when {
            coverUrl.startsWith("http") -> coverUrl
            coverUrl.startsWith("//") -> "https:$coverUrl"
            coverUrl.isNotEmpty() -> baseUrl + coverUrl
            else -> ""
        }

        return manga
    }
    override fun latestUpdatesNextPageSelector(): String? = "a.page-link[rel=next], a[rel=next]"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            return Request.Builder()
                .url("$baseUrl/ajax/searchLive?inputContent=${java.net.URLEncoder.encode(query, "UTF-8")}")
                .headers(headers)
                .build()
        }

        // Build advanced search URL with filters
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("search-adv")

            // Apply filter list (if empty, use default getFilterList to ensure defaults)
            val filterList = filters.ifEmpty { getFilterList() }
            filterList.filterIsInstance<UriFilter>().forEach { it.addToUri(this) }

            // Pagination
            addQueryParameter("page", page.toString())
        }.build()

        return Request.Builder().url(url).headers(headers).build()
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val bodyStr = response.body?.string().orEmpty()

        // If it's the live-search JSON payload
        if (bodyStr.trimStart().startsWith("{")) {
            val json = JSONObject(bodyStr)
            val html = json.optString("html", "")
            if (html.isEmpty()) return MangasPage(emptyList(), false)
            val document = Jsoup.parse(html, baseUrl)
            val mangas = document.select("ul.novel-list.horizontal.col2 li.novel-item").map { searchMangaFromElement(it) }
            return MangasPage(mangas, false)
        }

        // Otherwise it's the HTML from /search-adv
        val document = Jsoup.parse(bodyStr, baseUrl)
        val entries = document.select("ul.novel-list.col6 li.novel-item, ul.novel-list.horizontal.col2 li.novel-item").map { searchMangaFromElement(it) }
        val hasNext = document.selectFirst("a:contains(Next), a:contains(>>)") != null
        return MangasPage(entries, hasNext)
    }

    override fun searchMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        val link = element.selectFirst("a")
        val title = element.selectFirst("h4.novel-title.text1row")
        val cover = element.selectFirst("figure.novel-cover img")

        manga.title = title?.text()?.trim() ?: "Unknown Title"
        manga.setUrlWithoutDomain(link?.attr("href") ?: "")

        val coverUrl = cover?.attr("data-src")?.takeIf { it.isNotBlank() }
            ?: cover?.attr("src")?.takeIf { it.isNotBlank() }
            ?: ""
        manga.thumbnail_url = when {
            coverUrl.startsWith("http") -> coverUrl
            coverUrl.startsWith("//") -> "https:$coverUrl"
            coverUrl.isNotEmpty() -> baseUrl + coverUrl
            else -> ""
        }

        return manga
    }
    override fun searchMangaSelector(): String = "ul.novel-list.horizontal.col2 li.novel-item"

    override fun searchMangaNextPageSelector(): String? = null

    // =============================== Filters ===============================

    override fun getFilterList() = FilterList(
        OriginLanguageFilter(),
        GenreModeFilter(),
        GenreFilter(),
        ChaptersFilter(),
        RatingModeFilter(),
        RatingValueFilter(),
        StatusFilter(),
        SortFilter(defaultValue = "date"),
    )

    override fun mangaDetailsRequest(manga: SManga): Request {
        val novelPath = manga.url.removePrefix("/").removeSuffix("/")
        return Request.Builder().url("$baseUrl/$novelPath").headers(headers).build()
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()

        // Title - try multiple selectors
        manga.title = document.selectFirst("h1.novel-title, .novel-title h1, h1.entry-title")?.text()?.trim() ?: document.selectFirst("meta[property='og:title']")?.attr("content")?.trim()
            ?: document.title().removeSuffix(" - NovelFire")

        // Author - try multiple selectors
        manga.author = document.selectFirst(".author, .novel-author, .author-name")?.text()?.trim()
            ?: document.selectFirst("meta[property='og:novel:author']")?.attr("content")?.trim()
            ?: document.selectFirst("b:contains(Author), strong:contains(Author)")?.parent()?.ownText()?.trim()

        // Genres - from the provided HTML structure
        val genres = document.select(".categories ul li a")
            .map { it.text().trim() }
            .filter { it.isNotEmpty() }
        manga.genre = genres.joinToString(", ")

        // Status - using specific CSS classes
        val statusText = document.selectFirst(".header-stats .ongoing")?.text()?.lowercase()
            ?: document.selectFirst(".header-stats .completed")?.text()?.lowercase()
            ?: ""

        manga.status = when {
            statusText.contains("completed") -> SManga.COMPLETED
            statusText.contains("ongoing") -> SManga.ONGOING
            statusText.contains("dropped") -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }

        // Description - from the provided HTML structure
        manga.description = document.selectFirst(".summary .content, .summary .expand-wrapper")?.text()?.trim()
            ?: document.selectFirst(".novel-summary")?.text()?.trim()
            ?: document.selectFirst("meta[property='og:description']")?.attr("content")?.trim()
            ?: document.selectFirst("meta[name='description']")?.attr("content")?.trim()

        // Thumbnail - using .cover > img approach
        val coverElement = document.selectFirst(".cover > img")
        val coverUrl = coverElement?.attr("data-src") ?: coverElement?.attr("src")
        manga.thumbnail_url = when {
            coverUrl.isNullOrEmpty() -> document.selectFirst("meta[property='og:image']")?.attr("content")
            coverUrl.startsWith("http") -> coverUrl
            coverUrl.startsWith("//") -> "https:$coverUrl"
            coverUrl.startsWith("/") -> baseUrl + coverUrl
            else -> baseUrl + "/" + coverUrl
        }

        return manga
    }

    override fun chapterListRequest(manga: SManga): Request {
        val novelPath = manga.url.removePrefix("/").removeSuffix("/")
        return Request.Builder().url("$baseUrl/$novelPath/chapters").headers(headers).build()
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        val chapters = mutableListOf<SChapter>()

        // Parse chapters from first page
        chapters.addAll(parseChaptersFromPage(document))

        // Check if there are more pages by looking for pagination elements
        val hasMorePages = document.select(".pagination, .pager, .page-nav").isNotEmpty()

        if (hasMorePages) {
            val novelPath = response.request.url.toString()
                .substringAfter(baseUrl)
                .removeSuffix("/chapters")
                .removePrefix("/")

            var currentPage = 2
            var hasMore = true

            while (hasMore) {
                try {
                    val chapterUrl = "$baseUrl/$novelPath/chapters?page=$currentPage"
                    val pageResponse = client.newCall(Request.Builder().url(chapterUrl).headers(headers).build()).execute()
                    val pageDocument = pageResponse.asJsoup()
                    val pageChapters = parseChaptersFromPage(pageDocument)

                    if (pageChapters.isEmpty()) {
                        // No more chapters found, stop fetching
                        hasMore = false
                        break
                    }

                    chapters.addAll(pageChapters)
                    currentPage++
                    Thread.sleep(200)
                } catch (e: Exception) {
                    // Handle rate limiting - exponential backoff
                    Thread.sleep((2000 * (currentPage - 1)).toLong()) // 2s, 4s, 6s, etc.
                    // Try again, but limit retries
                    if (currentPage > 10) {
                        hasMore = false
                    }
                }
            }
        }

        return chapters.reversed() // Reverse to get Newest to Oldest Order
    }

    private fun parseChaptersFromPage(document: Document): List<SChapter> {
        return document.select(".chapter-list li a").map { element ->
            val chapter = SChapter.create()
            chapter.setUrlWithoutDomain(element.attr("href"))
            val timeElement = element.selectFirst("time.chapter-update")
            val datetime = timeElement?.attr("datetime")
            chapter.name = element.selectFirst("strong.chapter-title")?.text()?.trim() ?: element.text().trim()
            chapter.date_upload = parseChapterDate(datetime)
            chapter
        }
    }

    override fun chapterListSelector(): String = ".chapter-list li a"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(element.attr("href"))
        val timeElement = element.selectFirst("time.chapter-update")
        val datetime = timeElement?.attr("datetime")
        chapter.name = element.selectFirst("strong.chapter-title")?.text()?.trim() ?: element.text().trim()
        chapter.date_upload = parseChapterDate(datetime)
        return chapter
    }

    override fun pageListParse(document: Document): List<Page> {
        // Remove unwanted elements - ads, notifications, and elements with nf- prefix
        val contentElement = document.selectFirst("#content, .chapter-content, .novel-content")

        contentElement?.let { content ->
            // Remove unwanted elements
            content.select(".ad, .notification, div[id^='nf-'], div[class^='nf-'], script, style, .adsbygoogle").remove()

            // Clean up the content
            val cleanedContent = content.html().trim()

            // Return the cleaned content
            return listOf(Page(0, document.location(), cleanedContent))
        }

        // Fallback to body content if no specific content element found
        val fallbackContent = document.body()?.select(".content, .chapter-content, .novel-content")?.firstOrNull()?.html()?.trim()
            ?: document.body()?.text()?.trim()
            ?: "Content not found"

        return listOf(Page(0, document.location(), fallbackContent))
    }

    override fun imageUrlParse(document: Document): String = ""
}
