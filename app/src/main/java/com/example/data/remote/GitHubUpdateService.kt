package com.example.data.remote

import com.example.BuildConfig
import com.example.domain.model.GitHubReleaseInfo
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path

@JsonClass(generateAdapter = true)
data class GitHubAssetDto(
    @param:Json(name = "name") val name: String = "",
    @param:Json(name = "browser_download_url") val browserDownloadUrl: String = "",
)

@JsonClass(generateAdapter = true)
data class GitHubReleaseDto(
    @param:Json(name = "tag_name") val tagName: String = "",
    @param:Json(name = "name") val name: String? = null,
    @param:Json(name = "body") val body: String? = null,
    @param:Json(name = "html_url") val htmlUrl: String = "",
    @param:Json(name = "published_at") val publishedAt: String? = null,
    @param:Json(name = "assets") val assets: List<GitHubAssetDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class GitHubRepoDto(
    @param:Json(name = "full_name") val fullName: String = "",
    @param:Json(name = "default_branch") val defaultBranch: String = "main",
    @param:Json(name = "html_url") val htmlUrl: String = "",
    @param:Json(name = "pushed_at") val pushedAt: String? = null,
)

@JsonClass(generateAdapter = true)
data class GitHubTagDto(
    @param:Json(name = "name") val name: String = "",
)

interface GitHubApi {
    @Headers("Accept: application/vnd.github+json", "User-Agent: RabtaContacts-Android")
    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun getLatestRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
    ): GitHubReleaseDto

    @Headers("Accept: application/vnd.github+json", "User-Agent: RabtaContacts-Android")
    @GET("repos/{owner}/{repo}")
    suspend fun getRepoInfo(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
    ): GitHubRepoDto

    @Headers("Accept: application/vnd.github+json", "User-Agent: RabtaContacts-Android")
    @GET("repos/{owner}/{repo}/tags")
    suspend fun getTags(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
    ): List<GitHubTagDto>
}

class GitHubUpdateService {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val client = OkHttpClient.Builder().build()

    private val api: GitHubApi = Retrofit.Builder()
        .baseUrl("https://api.github.com/")
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(GitHubApi::class.java)

    suspend fun checkLatestRelease(repoSlug: String): Result<GitHubReleaseInfo> = runCatching {
        val cleaned = repoSlug
            .trim()
            .removePrefix("https://github.com/")
            .removePrefix("http://github.com/")
            .removePrefix("github.com/")
            .removeSuffix(".git")
            .trim('/')
        val parts = cleaned.split("/").filter { it.isNotBlank() }
        require(parts.size >= 2) { "اكتب اسم المستودع بصيغة owner/repo" }
        val owner = parts[0]
        val repo = parts[1]

        try {
            val dto = api.getLatestRelease(owner, repo)
            val apkAsset = dto.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
            val cleanRemoteTag = dto.tagName.trim().removePrefix("v").removePrefix("V")
            val currentVersion = BuildConfig.VERSION_NAME.trim().removePrefix("v").removePrefix("V")
            val isNewer = isRemoteVersionNewer(cleanRemoteTag, currentVersion)
            GitHubReleaseInfo(
                tagName = dto.tagName,
                releaseName = dto.name?.ifBlank { dto.tagName } ?: dto.tagName,
                body = dto.body.orEmpty(),
                htmlUrl = dto.htmlUrl,
                apkDownloadUrl = apkAsset?.browserDownloadUrl
                    ?: "https://github.com/$owner/$repo/raw/main/RabtaContacts.apk",
                publishedAt = dto.publishedAt.orEmpty().take(10),
                isUpdateAvailable = isNewer,
            )
        } catch (httpErr: HttpException) {
            if (httpErr.code() == 404) {
                // Repository may exist without formal Releases yet -> check repo + tags + raw RabtaContacts.apk
                val repoInfo = api.getRepoInfo(owner, repo)
                val latestTag = runCatching { api.getTags(owner, repo).firstOrNull()?.name }.getOrNull()
                val rawApkUrl = "https://github.com/$owner/$repo/raw/${repoInfo.defaultBranch}/RabtaContacts.apk"
                val tagLabel = latestTag ?: repoInfo.defaultBranch
                val isNewer = latestTag != null && isRemoteVersionNewer(
                    latestTag.removePrefix("v").removePrefix("V"),
                    BuildConfig.VERSION_NAME.removePrefix("v").removePrefix("V"),
                )
                GitHubReleaseInfo(
                    tagName = tagLabel,
                    releaseName = "${repoInfo.fullName} (متصل بنجاح)",
                    body = "المستودع متصل ويعمل. لم يتم إنشاء Release رسمي بعد — يمكنك تحميل أحدث RabtaContacts.apk مباشرة من فرع ${repoInfo.defaultBranch}.",
                    htmlUrl = "${repoInfo.htmlUrl}/releases",
                    apkDownloadUrl = rawApkUrl,
                    publishedAt = repoInfo.pushedAt.orEmpty().take(10),
                    isUpdateAvailable = isNewer,
                )
            } else {
                throw IllegalStateException("خطأ GitHub (${httpErr.code()}): تأكد من أن المستودع عام (Public) واسمه صحيح")
            }
        }
    }

    private fun isRemoteVersionNewer(remote: String, local: String): Boolean {
        val rParts = remote.split(".").map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
        val lParts = local.split(".").map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
        val max = maxOf(rParts.size, lParts.size)
        for (i in 0 until max) {
            val r = rParts.getOrElse(i) { 0 }
            val l = lParts.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }
}
