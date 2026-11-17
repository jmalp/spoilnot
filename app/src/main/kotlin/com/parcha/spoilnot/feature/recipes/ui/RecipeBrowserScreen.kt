package com.parcha.spoilnot.feature.recipes.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.designsystem.component.PillField
import com.parcha.spoilnot.core.designsystem.component.RoundIconButton
import com.parcha.spoilnot.core.designsystem.component.SpoilNotSnackbarHost
import com.parcha.spoilnot.core.designsystem.component.TonalButton
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotCorners
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotSurfaces
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotText
import com.parcha.spoilnot.core.ui.MessageEffect
import com.parcha.spoilnot.feature.recipes.domain.BrowserScript
import com.parcha.spoilnot.feature.recipes.domain.ExtractionMethod
import com.parcha.spoilnot.feature.recipes.domain.RecipeContent
import com.parcha.spoilnot.feature.recipes.domain.SourceUrl

/** How long after a page finishes loading to check it again, for pages that add their recipe with script. */
private const val SETTLE_DELAY_MS = 1_500L

@Composable
fun RecipeBrowserScreen(
    url: String?,
    fallback: Boolean,
    prefill: RecipeContent,
    onBack: () -> Unit,
    onOpenRecipe: (String) -> Unit,
    onCreateManually: (RecipeContent) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecipeBrowserViewModel = hiltViewModel<RecipeBrowserViewModel, RecipeBrowserViewModel.Factory>(
        key = "browser_${url}_$fallback",
        creationCallback = { it.create(url, fallback, prefill) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val saveState by viewModel.saveState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var address by rememberSaveable { mutableStateOf(state.url.orEmpty()) }
    var progress by remember { mutableIntStateOf(100) }
    var canGoBack by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    // The recipe saved from the current page, so the bar can offer Open.
    var savedId by remember(state.found) { mutableStateOf<String?>(null) }
    val focus = LocalFocusManager.current
    val addressFocus = remember { FocusRequester() }

    MessageEffect(viewModel.messages, snackbar) {}
    SaveStateEffect(saveState, onSaved = { savedId = it }, onOpenExisting = onOpenRecipe, viewModel = viewModel)
    BackHandler(enabled = canGoBack) { webView?.goBack() }
    LaunchedEffect(Unit) { if (state.url == null) addressFocus.requestFocus() }

    val load = { input: String ->
        SourceUrl.fromAddressBar(input)?.let { target ->
            focus.clearFocus()
            address = target
            webView?.loadUrl(SourceUrl.preferHttps(target))
        }
    }

    Scaffold(
        modifier = modifier.testTag("screen_recipe_browser"),
        containerColor = SpoilNotSurfaces.current.page,
        snackbarHost = { SpoilNotSnackbarHost(snackbar) },
        topBar = {
            // Close, the page's round address field and the page's Back (DESIGN 19.7, 19.10), on the page's color.
            Column(Modifier.background(SpoilNotSurfaces.current.page).statusBarsPadding()) {
                Row(
                    Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RoundIconButton(Icons.Rounded.Close, stringResource(R.string.recipe_close_browser), onBack)
                    PillField(
                        value = address,
                        onValueChange = { address = it },
                        placeholder = stringResource(R.string.recipe_browser_address),
                        leadingIcon = Icons.Rounded.Search,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { load(address) }),
                        modifier = Modifier.weight(1f).focusRequester(addressFocus).testTag("browser_address"),
                    )
                    if (canGoBack) {
                        RoundIconButton(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            stringResource(R.string.back),
                            { webView?.goBack() },
                        )
                    }
                }
                if (progress < 100) {
                    LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        bottomBar = {
            Column {
                AnimatedVisibility(
                    visible = state.found != null,
                    enter = slideInVertically { it },
                    exit = slideOutVertically { it },
                ) {
                    state.found?.let { found ->
                        FoundBar(
                            title = found.content.title,
                            heuristic = found.method == ExtractionMethod.Heuristic,
                            saving = saveState == SavingRecipeViewModel.SaveState.Saving,
                            savedId = savedId,
                            onSave = viewModel::saveFound,
                            onOpen = onOpenRecipe,
                        )
                    }
                }
                state.notFound?.let { prefillFromPage ->
                    NotFoundBar(onCreateManually = { onCreateManually(prefillFromPage) })
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            AndroidView(
                factory = { context ->
                    RecipeWebView(context).also { view ->
                        view.listener = object : RecipeWebView.Listener {
                            override fun onPageStarted(url: String) {
                                address = url
                                viewModel.onNavigate(url)
                            }

                            override fun onPageResult(result: String?, final: Boolean) = viewModel.onPage(result, final)

                            override fun onHistory(canGoBackNow: Boolean, url: String) {
                                canGoBack = canGoBackNow
                                address = url
                            }

                            override fun onProgress(percent: Int) {
                                progress = percent
                            }
                        }
                        webView = view
                        state.url?.let { view.loadUrl(SourceUrl.preferHttps(it)) }
                    }
                },
                onRelease = { view ->
                    view.release()
                    if (webView === view) webView = null
                },
                modifier = Modifier.fillMaxSize().testTag("browser_web_view"),
            )
            if (state.url == null) StartHint()
        }
    }
}

@Composable
private fun StartHint() {
    Surface(Modifier.fillMaxSize(), color = SpoilNotSurfaces.current.page) {
        Column(
            Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Icon(Icons.Rounded.Language, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                stringResource(R.string.recipe_browser_hint),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * "Recipe found: Title. Save" (DESIGN 5.5), then Open once saved: a bar in the highlight card's colors with rounded top
 * corners (DESIGN 19.8, 19.11).
 */
@Composable
internal fun FoundBar(
    title: String,
    heuristic: Boolean,
    saving: Boolean,
    savedId: String?,
    onSave: () -> Unit,
    onOpen: (String) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = BarShape,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 16.dp)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
                .testTag("recipe_found_bar"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(if (savedId != null) R.string.recipe_saved else R.string.recipe_found),
                    style = SpoilNotText.RowSupporting,
                )
                Text(title, style = SpoilNotText.RowTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (heuristic && savedId == null) {
                    Text(stringResource(R.string.recipe_check_this_short), style = MaterialTheme.typography.bodySmall)
                }
            }
            when {
                savedId != null -> TonalButton(
                    text = stringResource(R.string.action_open),
                    onClick = { onOpen(savedId) },
                    modifier = Modifier.testTag("found_open"),
                )

                saving -> CircularProgressIndicator(Modifier.padding(8.dp))

                else -> Button(
                    onClick = onSave,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("found_save"),
                ) { Text(stringResource(R.string.action_save), style = SpoilNotText.ButtonMedium) }
            }
        }
    }
}

/** "Couldn't find a recipe here" with Create manually, on the bar color with rounded top corners. */
@Composable
internal fun NotFoundBar(onCreateManually: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = BarShape) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 16.dp)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
                .testTag("recipe_not_found_bar"),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.recipe_not_found), style = MaterialTheme.typography.bodyLarge)
            TonalButton(
                text = stringResource(R.string.recipe_add_manual),
                onClick = onCreateManually,
                modifier = Modifier.align(Alignment.End).testTag("not_found_create"),
            )
        }
    }
}

/** The bars under the page: rounded on top, as a sheet is. */
private val BarShape = RoundedCornerShape(topStart = SpoilNotCorners.Highlight, topEnd = SpoilNotCorners.Highlight)

/**
 * The locked-down web view (DESIGN 11.6): http and https only, no file or content access, and no JavaScript
 * interface; the page is read with evaluateJavascript.
 */
@SuppressLint("SetJavaScriptEnabled", "ViewConstructor")
class RecipeWebView(context: android.content.Context) : WebView(context) {
    interface Listener {
        fun onPageStarted(url: String)

        fun onPageResult(result: String?, final: Boolean)

        fun onHistory(canGoBackNow: Boolean, url: String)

        fun onProgress(percent: Int)
    }

    var listener: Listener? = null
    private var released = false
    private val settle = Runnable { check(final = true) }

    init {
        settings.apply {
            // Recipe sites need script to render; nothing on the page can call into the app.
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            setGeolocationEnabled(false)
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            safeBrowsingEnabled = true
        }
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val target = request.url
                return when (target.scheme?.lowercase()) {
                    "https" -> false

                    // Cleartext is blocked app-wide, so try the same page over https.
                    "http" -> {
                        view.loadUrl(SourceUrl.preferHttps(target.toString()))
                        true
                    }

                    // intent:, file:, content:, javascript:, mailto: and every other scheme.
                    else -> true
                }
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                removeCallbacks(settle)
                listener?.onPageStarted(url)
            }

            override fun onPageFinished(view: WebView, url: String) {
                check(final = false)
                removeCallbacks(settle)
                postDelayed(settle, SETTLE_DELAY_MS)
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                listener?.onHistory(view.canGoBack(), url)
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                listener?.onProgress(newProgress)
            }
        }
    }

    private fun check(final: Boolean) {
        if (released) return
        evaluateJavascript(BrowserScript.SOURCE) { result -> if (!released) listener?.onPageResult(result, final) }
    }

    fun release() {
        released = true
        listener = null
        removeCallbacks(settle)
        stopLoading()
        destroy()
    }
}
