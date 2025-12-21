# Paginated Chapter Lists

## Overview

Some manga sources have APIs that return chapters in pages rather than all at once. For example, a source might return 50 chapters per page, and you need to make multiple requests to get all chapters for a manga with 200+ chapters.

Previously, Koma would only fetch the first page of chapters, leaving many chapters inaccessible. Now, you can implement the `PaginatedChapterListSource` interface to properly support paginated chapter lists.

## How to Implement

### 1. Implement the PaginatedChapterListSource interface

```kotlin
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.PaginatedChapterListSource
import eu.kanade.tachiyomi.source.model.ChaptersPage
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Request
import okhttp3.Response

class MySource : HttpSource(), PaginatedChapterListSource {

    override val name = "My Source"
    override val baseUrl = "https://example.com"
    override val lang = "en"
    override val supportsLatest = true

    // Implement the paginated chapter list method
    override suspend fun getChapterList(manga: SManga, page: Int): ChaptersPage {
        val response = client.newCall(chapterListRequest(manga, page)).await()
        return chapterListParse(response, page)
    }

    // Create the request for a specific page
    private fun chapterListRequest(manga: SManga, page: Int): Request {
        return GET("$baseUrl${manga.url}/chapters?page=$page", headers)
    }

    // Parse the response and extract chapters
    private fun chapterListParse(response: Response, page: Int): ChaptersPage {
        val json = response.body.string()
        val jsonObject = JSONObject(json)
        
        // Parse chapters from the response
        val chaptersArray = jsonObject.getJSONArray("chapters")
        val chapters = (0 until chaptersArray.length()).map { i ->
            val chapterJson = chaptersArray.getJSONObject(i)
            SChapter.create().apply {
                name = chapterJson.getString("title")
                url = chapterJson.getString("url")
                date_upload = chapterJson.getLong("publishedAt") * 1000
                chapter_number = chapterJson.getDouble("number").toFloat()
            }
        }
        
        // Determine if there are more pages
        val hasNextPage = jsonObject.optBoolean("hasNextPage", false)
        
        return ChaptersPage(chapters, hasNextPage)
    }

    // Other required HttpSource methods...
    // ...
}
```

### 2. Alternative: Check for next page indicator

If the API doesn't explicitly tell you if there's a next page, you can check for pagination elements:

```kotlin
private fun chapterListParse(response: Response): ChaptersPage {
    val document = response.asJsoup()
    
    val chapters = document.select("div.chapter-item").map { element ->
        SChapter.create().apply {
            name = element.selectFirst("span.title")!!.text()
            url = element.selectFirst("a")!!.attr("href")
            date_upload = parseDate(element.selectFirst("span.date")!!.text())
        }
    }
    
    // Check if there's a "next page" button or link
    val hasNextPage = document.selectFirst("a.next-page") != null
    
    return ChaptersPage(chapters, hasNextPage)
}
```

### 3. Alternative: Use page size to determine if there are more pages

If you know the page size, you can check if the number of chapters equals the page size:

```kotlin
private fun chapterListParse(response: Response): ChaptersPage {
    val document = response.asJsoup()
    
    val chapters = document.select("div.chapter-item").map { ... }
    
    // If we got exactly PAGE_SIZE chapters, there might be more
    // If we got fewer, we've reached the end
    val hasNextPage = chapters.size == PAGE_SIZE
    
    return ChaptersPage(chapters, hasNextPage)
}

companion object {
    private const val PAGE_SIZE = 50
}
```

## Benefits

1. **Complete chapter lists**: Users can now access all chapters, not just the first page
2. **Better performance**: Chapters are loaded on-demand rather than all at once
3. **Memory efficient**: Only the currently visible chapters need to be in memory
4. **Improved UX**: Users see chapters immediately and can start reading while more chapters load in the background

## Backward Compatibility

- If a source doesn't implement `PaginatedChapterListSource`, it continues to work as before using the standard `getChapterList(manga: SManga)` method
- The default implementation of `getChapterList(manga: SManga)` in `PaginatedChapterListSource` automatically loads all pages, so existing code that expects a complete list continues to work

## Example: Real-world scenario

A manga has 300 chapters, and the API returns 50 chapters per page:

**Before** (without pagination):
- First call: Returns chapters 1-50
- User only sees chapters 1-50 and can't access chapters 51-300

**After** (with pagination):
- First call: Returns chapters 1-50 with `hasNextPage = true`
- Second call: Returns chapters 51-100 with `hasNextPage = true`
- Third call: Returns chapters 101-150 with `hasNextPage = true`
- ... continues until all 300 chapters are loaded
- User can access all 300 chapters
