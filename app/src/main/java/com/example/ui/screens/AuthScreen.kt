package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FolderCopy
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.R
import com.example.auth.GoogleAuthHelper
import com.example.data.local.SyncPrefsDataStore
import com.example.data.remote.GitHubUpdateService
import com.example.domain.model.GitHubReleaseInfo
import com.example.util.SystemIntents
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    auth: FirebaseAuth,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember(context) { SyncPrefsDataStore(context.applicationContext) }
    val gitHubUpdateService = remember { GitHubUpdateService() }

    val savedUpdateLink by prefs.githubRepoFlow.collectAsStateWithLifecycle(initialValue = "")
    var updateLinkInput by remember(savedUpdateLink) { mutableStateOf(savedUpdateLink) }
    var isUpdatePanelExpanded by remember { mutableStateOf(false) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var releaseInfo by remember { mutableStateOf<GitHubReleaseInfo?>(null) }
    var updateStatusMessage by remember { mutableStateOf<String?>(null) }
    var updateIsError by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        GoogleAuthHelper.attemptAutoSignIn(context, auth)
        isLoading = false
    }

    LaunchedEffect(savedUpdateLink) {
        val trimmed = savedUpdateLink.trim()
        if (trimmed.isNotBlank() && !isDirectUpdateUrl(trimmed)) {
            gitHubUpdateService.checkLatestRelease(trimmed)
                .onSuccess { info ->
                    releaseInfo = info
                }
        }
    }

    Scaffold { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Image(
                            painter = painterResource(id = R.drawable.img_hero_network_1794840678255),
                            contentDescription = stringResource(id = R.string.app_name),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                        )
                    }
                }

                Text(
                    text = stringResource(id = R.string.sign_in_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )

                Text(
                    text = stringResource(id = R.string.sign_in_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    FeatureRow(Icons.Rounded.Bolt, "أداء فائق لـ 20,000+ رقم بدون إثقال سجل الهاتف أو واتساب")
                    FeatureRow(Icons.Rounded.FolderCopy, "فئات متداخلة (كاتيجوري جوه كاتيجوري) وبحث فوري")
                    FeatureRow(Icons.Rounded.Place, "عرض المواقع على الخريطة ومعرفة الأرقام القريبة منك")
                    FeatureRow(Icons.Rounded.Payments, "روابط الدفع السريعة (InstaPay, Orange Cash, Axis Pay)")
                }

                if (errorMessage != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = errorMessage.orEmpty(),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = {
                        errorMessage = null
                        isLoading = true
                        scope.launch {
                            GoogleAuthHelper.signInWithGoogleButton(context, auth)
                                .onFailure { err ->
                                    errorMessage = err.localizedMessage ?: "تعذر تسجيل الدخول"
                                }
                            isLoading = false
                        }
                    },
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("google_sign_in_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.AccountCircle,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(modifier = Modifier.size(10.dp))
                        Text(
                            text = stringResource(id = R.string.sign_in_google),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }

                AuthUpdateCard(
                    savedUpdateLink = savedUpdateLink,
                    updateLinkInput = updateLinkInput,
                    onUpdateLinkInputChange = { updateLinkInput = it },
                    isExpanded = isUpdatePanelExpanded,
                    onToggleExpanded = { isUpdatePanelExpanded = !isUpdatePanelExpanded },
                    isChecking = isCheckingUpdate,
                    releaseInfo = releaseInfo,
                    statusMessage = updateStatusMessage,
                    isError = updateIsError,
                    onCheckOrOpen = { rawLink ->
                        val trimmed = rawLink.trim()
                        if (trimmed.isBlank()) {
                            updateIsError = true
                            updateStatusMessage = "أدخل رابط التحديث أو اسم مستودع GitHub أولاً"
                            return@AuthUpdateCard
                        }
                        scope.launch {
                            updateStatusMessage = null
                            updateIsError = false
                            prefs.setGithubRepoSlug(trimmed)
                            if (isDirectUpdateUrl(trimmed)) {
                                releaseInfo = null
                                updateStatusMessage = "تم حفظ الرابط وجاري فتحه..."
                                SystemIntents.openWebsiteOrPayment(context, trimmed)
                            } else {
                                isCheckingUpdate = true
                                gitHubUpdateService.checkLatestRelease(trimmed)
                                    .onSuccess { info ->
                                        releaseInfo = info
                                        updateIsError = false
                                        updateStatusMessage = if (info.isUpdateAvailable) {
                                            "يتوفر إصدار أحدث (${info.tagName}) جاهز للتحميل"
                                        } else {
                                            "التطبيق متصل بـ ${info.tagName} — يمكنك التحميل مباشرة بالأسفل"
                                        }
                                    }
                                    .onFailure { err ->
                                        releaseInfo = null
                                        updateIsError = true
                                        updateStatusMessage = err.localizedMessage ?: "تعذر فحص التحديثات، تأكد من الرابط والإنترنت"
                                    }
                                isCheckingUpdate = false
                            }
                        }
                    },
                    onOpenUrl = { url ->
                        SystemIntents.openWebsiteOrPayment(context, url)
                    },
                )
            }
        }
    }
}

@Composable
private fun AuthUpdateCard(
    savedUpdateLink: String,
    updateLinkInput: String,
    onUpdateLinkInputChange: (String) -> Unit,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    isChecking: Boolean,
    releaseInfo: GitHubReleaseInfo?,
    statusMessage: String?,
    isError: Boolean,
    onCheckOrOpen: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val hasNewerRelease = releaseInfo?.isUpdateAvailable == true
    val savedTrimmed = savedUpdateLink.trim()
    val hasDirectSavedUrl = savedTrimmed.isNotBlank() && isDirectUpdateUrl(savedTrimmed)

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (hasNewerRelease) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("auth_update_card"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onToggleExpanded)
                    .minimumInteractiveComponentSize()
                    .testTag("auth_update_toggle_btn"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.SystemUpdate,
                    contentDescription = stringResource(id = R.string.auth_update_title),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(id = R.string.auth_update_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (hasNewerRelease) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            },
                        ) {
                            Text(
                                text = if (hasNewerRelease) {
                                    stringResource(id = R.string.auth_update_available_badge)
                                } else {
                                    "v${BuildConfig.VERSION_NAME}"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (hasNewerRelease) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            )
                        }
                    }
                    Text(
                        text = when {
                            releaseInfo != null -> "${releaseInfo.releaseName} (${releaseInfo.tagName})"
                            savedTrimmed.isNotBlank() -> savedTrimmed
                            else -> "اضغط لإدخال رابط التحديث أو مستودع GitHub"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (isExpanded) "إغلاق قسم التحديث" else "فتح قسم التحديث",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (releaseInfo != null) {
                FilledTonalButton(
                    onClick = { onOpenUrl(releaseInfo.apkDownloadUrl ?: releaseInfo.htmlUrl) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("auth_download_update_btn"),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Download,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${stringResource(id = R.string.auth_update_download_apk)} • ${releaseInfo.tagName}",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            } else if (hasDirectSavedUrl && !isExpanded) {
                FilledTonalButton(
                    onClick = { onOpenUrl(savedTrimmed) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("auth_open_direct_link_btn"),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(id = R.string.auth_update_open_link),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
                    expandVertically(spring(stiffness = Spring.StiffnessMediumLow)),
                exit = fadeOut(spring(stiffness = Spring.StiffnessMediumLow)) +
                    shrinkVertically(spring(stiffness = Spring.StiffnessMediumLow)),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedTextField(
                        value = updateLinkInput,
                        onValueChange = onUpdateLinkInputChange,
                        label = { Text(stringResource(id = R.string.auth_update_link_label)) },
                        placeholder = { Text(stringResource(id = R.string.auth_update_link_placeholder)) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_update_link_input"),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { onCheckOrOpen(updateLinkInput) },
                            enabled = !isChecking,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("auth_check_update_btn"),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            if (isChecking) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Text(
                                    text = if (isDirectUpdateUrl(updateLinkInput)) {
                                        stringResource(id = R.string.auth_update_open_link)
                                    } else {
                                        stringResource(id = R.string.auth_update_check_action)
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        if (releaseInfo != null) {
                            OutlinedButton(
                                onClick = { onOpenUrl(releaseInfo.htmlUrl) },
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("auth_open_releases_btn"),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                    contentDescription = stringResource(id = R.string.auth_update_open_releases),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }

                    if (!statusMessage.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isError) {
                                MaterialTheme.colorScheme.errorContainer
                            } else {
                                MaterialTheme.colorScheme.secondaryContainer
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = statusMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isError) {
                                    MaterialTheme.colorScheme.onErrorContainer
                                } else {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                },
                                modifier = Modifier.padding(10.dp),
                            )
                        }
                    }

                    if (releaseInfo != null && releaseInfo.body.isNotBlank()) {
                        Text(
                            text = releaseInfo.body.take(220),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun isDirectUpdateUrl(input: String): Boolean {
    val trimmed = input.trim()
    if (trimmed.isBlank()) return false
    val lower = trimmed.lowercase()
    if (lower.endsWith(".apk")) return true
    if (lower.startsWith("http://") || lower.startsWith("https://")) {
        val afterHost = lower
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
        if (!afterHost.startsWith("github.com/")) {
            return true
        }
        val pathParts = afterHost
            .removePrefix("github.com/")
            .removeSuffix(".git")
            .trim('/')
            .split("/")
            .filter { it.isNotBlank() }
        return pathParts.size > 2
    }
    return false
}

@Composable
private fun FeatureRow(icon: ImageVector, text: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
