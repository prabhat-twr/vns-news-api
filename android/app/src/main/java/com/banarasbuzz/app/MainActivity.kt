package com.banarasbuzz.app

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Keep Rahu Kaal / Shubh Muhurat alarms armed (cheap; does nothing if alerts are off).
        PanchangAlerts.schedule(this)
        val start = intent?.getStringExtra(PanchangAlerts.EXTRA_ROUTE)
            ?.let { r -> Route.entries.firstOrNull { it.name == r } } ?: Route.HOME
        setContent {
            BanarasTheme { BuzzApp(startRoute = start) }
        }
    }
}

class FeedViewModel @JvmOverloads constructor(
    app: Application,
    autoLoad: Boolean = true,
) : AndroidViewModel(app) {
    var feed by mutableStateOf(Feed())
        internal set
    var loading by mutableStateOf(false)
        private set
    var loaded by mutableStateOf(false)
        internal set

    /** Full-year panchang, downloaded the first time the user looks beyond today. */
    var year by mutableStateOf<YearPanchang?>(null)
        internal set
    var yearLoading by mutableStateOf(false)
        private set
    var yearFailed by mutableStateOf(false)
        private set
    /** Why the year file is missing, when it is. */
    var yearProblem by mutableStateOf<Repository.YearProblem?>(null)
        private set

    /** Date shown on the Panchang screen; null means today. */
    var selectedDate by mutableStateOf<java.time.LocalDate?>(null)

    fun ensureYear(force: Boolean = false) {
        if (yearLoading || (year != null && !force)) return
        viewModelScope.launch {
            yearLoading = true
            yearFailed = false
            val (y, problem) = Repository.loadYear(getApplication<Application>())
            yearProblem = problem
            if (y != null) year = y else yearFailed = true
            yearLoading = false
        }
    }

    /** Panchang for a date: today's feed entry for today, else the year file. */
    fun panchangFor(date: java.time.LocalDate): Panchang? {
        val key = date.toString()
        if (date == todayIst()) {
            return feed.panchang?.takeIf { it.date == key } ?: year?.days?.get(key) ?: feed.nextDays[key] ?: feed.panchang
        }
        // The next 7 days come with the regular feed; further dates need the year file.
        return year?.days?.get(key) ?: feed.nextDays[key]
    }

    /** Vrat and tyohar: the whole year when loaded, else the next 45 days. */
    fun allObservances(): List<Observance> = year?.observances ?: feed.observances

    /** City picked in "My City Settings" — a cityId from news.json. */
    var city by mutableStateOf("varanasi")

    /** Bookmarked places and liked posts, kept while the app is open. */
    val saved = mutableStateMapOf<String, Boolean>()

    init {
        if (autoLoad) refresh()
    }

    private var loadedAtMillis = 0L
    private var loadedForDate: java.time.LocalDate? = null

    /** Reload when coming back to the app if the data is over 15 minutes old or the day has changed. */
    fun refreshIfStale() {
        val old = System.currentTimeMillis() - loadedAtMillis > 15 * 60_000
        if (loaded && (old || loadedForDate != todayIst())) refresh()
    }

    fun refresh() {
        if (loading) return
        viewModelScope.launch {
            loading = true
            loadedAtMillis = System.currentTimeMillis()
            loadedForDate = todayIst()
            feed = Repository.load(getApplication<Application>())
            loaded = true
            // New panchang files may have arrived: refresh the pinned notification and alarms.
            PanchangAlerts.schedule(getApplication<Application>())
            loading = false
        }
    }

    fun isSaved(id: String) = saved[id] == true
    fun toggleSaved(id: String) {
        saved[id] = !isSaved(id)
    }

    /** (cityId, cityName) pairs present in the news feed. */
    fun cities(): List<Pair<String, String>> = feed.news
        .filter { it.cityId.isNotBlank() }
        .map { it.cityId to it.cityName }
        .distinctBy { it.first }
}

enum class Route { HOME, NEWS, PANCHANG, EXPLORE, MORE, EVENTS, BUSINESS, COMMUNITY, YEAR }

private data class Tab(val route: Route, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Route.HOME, "Home", Icons.Filled.Home),
    Tab(Route.NEWS, "News", Icons.AutoMirrored.Filled.List),
    Tab(Route.PANCHANG, "Panchang", Icons.Filled.DateRange),
    Tab(Route.EXPLORE, "Explore", Icons.Filled.Search),
    Tab(Route.MORE, "More", Icons.Filled.Menu),
)
private val tabRoutes = tabs.map { it.route }.toSet()

/** Which bottom tab lights up for a route. */
private fun Route.tab(): Route = when (this) {
    Route.EVENTS, Route.BUSINESS, Route.COMMUNITY -> Route.EXPLORE
    Route.YEAR -> Route.PANCHANG
    else -> this
}

/** What screens can ask the app shell to do. */
class AppActions(
    val go: (Route) -> Unit,
    val back: () -> Unit,
    val open: (String) -> Unit,
    val say: (String) -> Unit,
)

private val stackSaver = listSaver<SnapshotStateList<Route>, String>(
    save = { list -> list.map { it.name } },
    restore = { names -> mutableStateListOf(*names.map { Route.valueOf(it) }.toTypedArray()) },
)

@Composable
fun BuzzApp(vm: FeedViewModel = viewModel(), startRoute: Route = Route.HOME) {
    val context = LocalContext.current
    val stack = rememberSaveable(saver = stackSaver) { mutableStateListOf(startRoute) }
    val current = stack.last()

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // Web page being read in the in-app browser (null = none). Survives rotation.
    var webUrl by rememberSaveable { mutableStateOf<String?>(null) }

    val actions = remember {
        AppActions(
            go = { route ->
                if (route in tabRoutes) {
                    stack.clear()
                    stack.add(route)
                } else if (stack.last() != route) {
                    stack.add(route)
                }
            },
            back = {
                if (stack.size > 1) stack.removeAt(stack.lastIndex)
                else { stack.clear(); stack.add(Route.HOME) }
            },
            // Web links open in the in-app browser so readers stay in the app;
            // maps, the Play Store and non-web links go to their own apps.
            open = { url -> if (opensOutsideApp(url)) openExternally(context, url) else webUrl = url },
            say = { msg ->
                scope.launch {
                    snackbar.currentSnackbarData?.dismiss()
                    snackbar.showSnackbar(msg)
                }
            },
        )
    }

    BackHandler(enabled = current != Route.HOME) { actions.back() }

    // Coming back to the app (e.g. the next morning): reload if the data is stale.
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    androidx.compose.runtime.DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) vm.refreshIfStale()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = BB.Cream,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
                    tabs.forEach { t ->
                        val selected = current.tab() == t.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = { actions.go(t.route) },
                            icon = { Icon(t.icon, contentDescription = t.label) },
                            label = {
                                Text(
                                    t.label,
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = BB.Saffron,
                                selectedTextColor = BB.Saffron,
                                indicatorColor = BB.Tile,
                                unselectedIconColor = BB.Muted,
                                unselectedTextColor = BB.Muted,
                            ),
                        )
                    }
                }
            },
        ) { padding ->
            Box(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .background(BB.Cream)
            ) {
                if (!vm.loaded && vm.loading) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center), color = BB.Saffron)
                } else {
                    if (!Features.isLive(current)) {
                        ComingSoonScreen(current, actions)
                    } else when (current) {
                        Route.HOME -> HomeScreen(vm, actions)
                        Route.NEWS -> NewsScreen(vm, actions)
                        Route.PANCHANG -> PanchangScreen(vm, actions)
                        Route.EXPLORE -> ExploreScreen(vm, actions)
                        Route.MORE -> MoreScreen(vm, actions)
                        Route.EVENTS -> EventsScreen(vm, actions)
                        Route.BUSINESS -> BusinessScreen(vm, actions)
                        Route.COMMUNITY -> CommunityScreen(vm, actions)
                        Route.YEAR -> YearScreen(vm, actions)
                    }
                }
            }
        }

        // In-app browser on top of everything (tab bar hidden while reading).
        webUrl?.let { url ->
            Box(
                Modifier
                    .fillMaxSize()
                    .background(BB.Cream)
                    .systemBarsPadding()
            ) {
                WebScreen(url = url, onClose = { webUrl = null })
            }
        }
    }
}
