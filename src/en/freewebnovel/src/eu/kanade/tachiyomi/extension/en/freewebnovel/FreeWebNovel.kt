package eu.kanade.tachiyomi.extension.en.freewebnovel

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class FreeWebNovel : ParsedHttpSource() {
    override val name = "FreeWebNovel"
    override val baseUrl = "https://freewebnovel.com"
    override val lang = "en"
    override val supportsLatest = true
    val isNovelSource: Boolean = true

    override fun popularMangaRequest(page: Int): Request =
        Request.Builder()
            .url(if (page == 1) "$baseUrl/sort/most-popular" else "$baseUrl/sort/most-popular/$page")
            .headers(headers)
            .build()
    override fun popularMangaSelector(): String = ".ul-list1 .li"
    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        val a = element.selectFirst(".txt h3.tit a")
        manga.setUrlWithoutDomain(a?.attr("href") ?: "")
        manga.title = a?.text() ?: ""
        manga.thumbnail_url = element.selectFirst(".pic img")?.absUrl("src")
        return manga
    }
    override fun popularMangaNextPageSelector(): String? = "a:contains(>>)"

    override fun latestUpdatesRequest(page: Int): Request =
        Request.Builder()
            .url(if (page == 1) "$baseUrl/sort/latest-novel" else "$baseUrl/sort/latest-novel/$page")
            .headers(headers)
            .build()
    override fun latestUpdatesSelector(): String = popularMangaSelector()
    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)
    override fun latestUpdatesNextPageSelector(): String? = "a:contains(>>)"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val formBody = FormBody.Builder()
            .add("searchkey", query)
            .build()
        return Request.Builder()
            .url("$baseUrl/search?page=$page")
            .headers(headers)
            .post(formBody)
            .build()
    }
    override fun searchMangaSelector(): String = popularMangaSelector()
    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)
    override fun searchMangaNextPageSelector(): String? = null

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        val ogTitle = document.selectFirst("meta[property=og:title]")?.attr("content")
        val ogAuthor = document.selectFirst("meta[property=og:novel:author]")?.attr("content")
        val ogGenre = document.selectFirst("meta[property=og:novel:genre]")?.attr("content")
        val ogStatus = document.selectFirst("meta[property=og:novel:status]")?.attr("content")
        val ogDesc = document.selectFirst("meta[property=og:description]")?.attr("content")
        val ogImage = document.selectFirst("meta[property=og:image]")?.attr("content")

        val descElement = document.selectFirst(".txt .inner")
        val fullDesc = descElement?.select("p")?.joinToString("\n") { it.text() }?.takeIf { it.isNotBlank() } ?: ogDesc

        manga.title = ogTitle ?: document.title()
        manga.author = ogAuthor
        manga.genre = ogGenre
        manga.status = when (ogStatus?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        manga.description = fullDesc
        manga.thumbnail_url = ogImage
        return manga
    }

    override fun chapterListSelector(): String = "ul.ul-list5#idData li a"
    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(element.attr("href"))
        chapter.name = element.text()
        return chapter
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        // Reverse order so the newest chapter appears first
        val body = response.body?.string().orEmpty()
        val doc = Jsoup.parse(body)
        return doc.select(chapterListSelector())
            .map { chapterFromElement(it) }
            .reversed()
    }

    override fun pageListParse(document: Document): List<Page> {
        val article = document.selectFirst("div.txt #article")
        val content = article?.html() ?: ""
        return listOf(Page(0, document.location(), content))
    }
    override fun imageUrlParse(document: Document): String = ""
}
