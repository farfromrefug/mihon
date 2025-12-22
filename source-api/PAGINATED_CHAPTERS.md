# Paginated Chapter Lists

## Overview

Some manga sources have APIs that return chapters in pages rather than all at once. For example, a source might return 50 chapters per page, and you need to make multiple requests to get all chapters for a manga with 200+ chapters.

Koma now supports paginated chapter lists directly in `HttpSource`, making it easy to implement with minimal changes to your extension code.

## How to Implement

### 1. Enable pagination in your HttpSource

Override `supportsChapterListPagination()` to return `true`:

```kotlin
class MySource : HttpSource() {

    override val name = "My Source"
    override val baseUrl = "https://example.com"
    override val lang = "en"
    override val supportsLatest = true

    // Enable pagination support
    override fun supportsChapterListPagination() = true

    // Rest of your source implementation...
}
```

### 2. Update chapterListRequest to use the page parameter

The `page` parameter is now available (default is 1 for backward compatibility):

```kotlin
override fun chapterListRequest(manga: SManga, page: Int): Request {
    return GET("$baseUrl${manga.url}/chapters?page=$page", headers)
}
```

### 3. Update chapterListParse to set hasNextPage

Use the `hasNextPage` parameter to indicate if there are more pages:

```kotlin
override fun chapterListParse(response: Response, hasNextPage: MutableList<Boolean>?): List<SChapter> {
    val document = response.asJsoup()
    
    // Parse chapters from the response
    val chapters = document.select("div.chapter-item").map { element ->
        SChapter.create().apply {
            name = element.selectFirst("span.title")!!.text()
            url = element.selectFirst("a")!!.attr("href")
            date_upload = parseDate(element.selectFirst("span.date")!!.text())
        }
    }
    
    // Check if there's a "next page" button or link
    val hasNext = document.selectFirst("a.next-page") != null
    hasNextPage?.add(hasNext)
    
    return chapters
}
```

## Alternative: Determine hasNextPage

If the API doesn't have explicit pagination indicators, you can use other methods:

### Check for page size

If you know the page size, you can check if the number of chapters equals the page size:

```kotlin
override fun chapterListParse(response: Response, hasNextPage: MutableList<Boolean>?): List<SChapter> {
    val document = response.asJsoup()
    
    val chapters = document.select("div.chapter-item").map { ... }
    
    // If we got exactly PAGE_SIZE chapters, there might be more
    // If we got fewer, we've reached the end
    val hasNext = chapters.size == PAGE_SIZE
    hasNextPage?.add(hasNext)
    
    return chapters
}

companion object {
    private const val PAGE_SIZE = 50
}
```

### Parse from JSON response

```kotlin
override fun chapterListParse(response: Response, hasNextPage: MutableList<Boolean>?): List<SChapter> {
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
    
    // Get hasNextPage from API response
    val hasNext = jsonObject.optBoolean("hasNextPage", false)
    hasNextPage?.add(hasNext)
    
    return chapters
}
```

## Backward Compatibility

- If you don't override `supportsChapterListPagination()`, the source works exactly as before
- The `page` parameter in `chapterListRequest` has a default value of 1
- The `hasNextPage` parameter in `chapterListParse` is optional (nullable)
- Existing sources require zero changes to continue working

## Benefits

1. **Complete chapter lists**: Users can now access all chapters, not just the first page
2. **Infinite scrolling**: In the future, the UI will support loading chapters on-demand as users scroll
3. **Better performance**: Chapters are loaded progressively rather than all at once
4. **Memory efficient**: Only the currently visible chapters need to be in memory
5. **Minimal changes**: Just override 2-3 methods to enable pagination

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
- UI shows chapters progressively as they load

