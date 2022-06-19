package org.jellyfin.androidtv.ui.itemhandling

import android.content.Context
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.PresenterSelector
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.constant.ChangeTriggerType
import org.jellyfin.androidtv.constant.LiveTvOption
import org.jellyfin.androidtv.constant.QueryType
import org.jellyfin.androidtv.data.model.ChapterItemInfo
import org.jellyfin.androidtv.data.model.DataRefreshService
import org.jellyfin.androidtv.data.model.FilterOptions
import org.jellyfin.androidtv.data.querying.ContinueWatchingQuery
import org.jellyfin.androidtv.data.querying.SpecialsQuery
import org.jellyfin.androidtv.data.querying.StdItemQuery
import org.jellyfin.androidtv.data.querying.TrailersQuery
import org.jellyfin.androidtv.data.querying.ViewQuery
import org.jellyfin.androidtv.data.repository.UserViewsRepository
import org.jellyfin.androidtv.ui.GridButton
import org.jellyfin.androidtv.ui.GridFragment.SortOption
import org.jellyfin.androidtv.ui.browsing.EnhancedBrowseFragment
import org.jellyfin.androidtv.ui.livetv.TvManager
import org.jellyfin.androidtv.ui.presentation.PositionableListRowPresenter
import org.jellyfin.androidtv.ui.presentation.TextItemPresenter
import org.jellyfin.androidtv.util.Utils
import org.jellyfin.apiclient.interaction.ApiClient
import org.jellyfin.apiclient.interaction.EmptyResponse
import org.jellyfin.apiclient.interaction.Response
import org.jellyfin.apiclient.model.dto.BaseItemDto
import org.jellyfin.apiclient.model.dto.BaseItemPerson
import org.jellyfin.apiclient.model.dto.BaseItemType
import org.jellyfin.apiclient.model.dto.UserItemDataDto
import org.jellyfin.apiclient.model.livetv.LiveTvChannelQuery
import org.jellyfin.apiclient.model.livetv.RecommendedProgramQuery
import org.jellyfin.apiclient.model.livetv.RecordingGroupQuery
import org.jellyfin.apiclient.model.livetv.RecordingQuery
import org.jellyfin.apiclient.model.livetv.SeriesTimerQuery
import org.jellyfin.apiclient.model.querying.ArtistsQuery
import org.jellyfin.apiclient.model.querying.ItemQuery
import org.jellyfin.apiclient.model.querying.ItemSortBy
import org.jellyfin.apiclient.model.querying.ItemsResult
import org.jellyfin.apiclient.model.querying.LatestItemsQuery
import org.jellyfin.apiclient.model.querying.NextUpQuery
import org.jellyfin.apiclient.model.querying.PersonsQuery
import org.jellyfin.apiclient.model.querying.SeasonQuery
import org.jellyfin.apiclient.model.querying.SimilarItemsQuery
import org.jellyfin.apiclient.model.querying.UpcomingEpisodesQuery
import org.jellyfin.apiclient.model.results.ChannelInfoDtoResult
import org.jellyfin.apiclient.model.results.SeriesTimerInfoDtoResult
import org.jellyfin.apiclient.model.search.SearchHintResult
import org.jellyfin.apiclient.model.search.SearchQuery
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.ItemFields
import org.koin.java.KoinJavaComponent.get
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

class ItemRowAdapter : ArrayObjectAdapter {
    private var mQuery: ItemQuery? = null
    private var mNextUpQuery: NextUpQuery? = null
    private var mSeasonQuery: SeasonQuery? = null
    private var mUpcomingQuery: UpcomingEpisodesQuery? = null
    private var mSimilarQuery: SimilarItemsQuery? = null
    private var mPersonsQuery: PersonsQuery? = null
    private var mSearchQuery: SearchQuery? = null
    private var mSpecialsQuery: SpecialsQuery? = null
    private var mTrailersQuery: TrailersQuery? = null
    private var mTvChannelQuery: LiveTvChannelQuery? = null
    private var mTvProgramQuery: RecommendedProgramQuery? = null
    private var mTvRecordingQuery: RecordingQuery? = null
    private var mTvRecordingGroupQuery: RecordingGroupQuery? = null
    private var mArtistsQuery: ArtistsQuery? = null
	private var mLatestQuery: LatestItemsQuery? = null
	private var mContinueWatchingQuery: ContinueWatchingQuery? = null
    private var mSeriesTimerQuery: SeriesTimerQuery? = null
    var queryType: QueryType? = null
        private set
    var sortBy: String? = null
        private set
    private var mFilters: FilterOptions? = null
    private var mRetrieveFinishedListener: EmptyResponse? = null
    private var reRetrieveTriggers: Array<ChangeTriggerType>? = arrayOf()
    private var lastFullRetrieve: Calendar? = null
    private var mPersons: Array<BaseItemPerson>? = emptyArray()
    private var mChapters: List<ChapterItemInfo>? = null
    private var mItems: List<BaseItemDto>? = null
    var parent: ArrayObjectAdapter? = null
        private set
    private var mRow: ListRow? = null
    private var chunkSize = 0
    private var itemsLoaded = 0
    var totalItems = 0
    private var fullyLoaded = false
    private val currentlyRetrievingSemaphore = Any()
    private var isCurrentlyRetrieving = false
        get() {
            synchronized(currentlyRetrievingSemaphore) { return field }
        }
        private set(currentlyRetrieving) {
            synchronized(currentlyRetrievingSemaphore) { field = currentlyRetrieving }
        }
    var preferParentThumb = false
        private set
    var isStaticHeight = false
        private set
    private val apiClient = inject<ApiClient>(ApiClient::class.java)
	private val api by inject<org.jellyfin.sdk.api.client.ApiClient>(org.jellyfin.sdk.api.client.ApiClient::class.java)
    private val userViewsRepository = inject<UserViewsRepository>(UserViewsRepository::class.java)
    private var context: Context? = null
    fun setRow(row: ListRow?) {
        mRow = row
    }

    fun setReRetrieveTriggers(reRetrieveTriggers: Array<ChangeTriggerType>?) {
        this.reRetrieveTriggers = reRetrieveTriggers
    }

    constructor(context: Context?, query: ItemQuery?, chunkSize: Int, preferParentThumb: Boolean, presenter: Presenter?, parent: ArrayObjectAdapter?) : this(context, query, chunkSize, preferParentThumb, false, presenter, parent)

	@JvmOverloads
    constructor(context: Context?, query: ItemQuery?, chunkSize: Int, preferParentThumb: Boolean, staticHeight: Boolean, presenter: Presenter?, parent: ArrayObjectAdapter?, queryType: QueryType? = QueryType.Items) : super(presenter) {
        this.context = context
        this.parent = parent
        mQuery = query
        mQuery?.userId = get<UserRepository>(UserRepository::class.java).currentUser.value!!.id.toString()
        this.chunkSize = chunkSize
        this.preferParentThumb = preferParentThumb
        isStaticHeight = staticHeight
        if (chunkSize > 0) {
            mQuery!!.limit = chunkSize
        }
        this.queryType = queryType
    }

    constructor(context: Context?, query: ItemQuery?, chunkSize: Int, preferParentThumb: Boolean, staticHeight: Boolean, presenter: PresenterSelector?, parent: ArrayObjectAdapter?, queryType: QueryType?) : super(presenter) {
        this.context = context
        this.parent = parent
        mQuery = query
        mQuery!!.userId = get<UserRepository>(UserRepository::class.java).currentUser.value!!.id.toString()
        this.chunkSize = chunkSize
        this.preferParentThumb = preferParentThumb
        isStaticHeight = staticHeight
        if (chunkSize > 0) {
            mQuery!!.limit = chunkSize
        }
        this.queryType = queryType
    }

    constructor(context: Context?, query: ArtistsQuery?, chunkSize: Int, presenter: Presenter?, parent: ArrayObjectAdapter?) : super(presenter) {
        this.context = context
        this.parent = parent
        mArtistsQuery = query
        mArtistsQuery!!.userId = get<UserRepository>(UserRepository::class.java).currentUser.value!!.id.toString()
        isStaticHeight = true
        this.chunkSize = chunkSize
        if (chunkSize > 0) {
            mArtistsQuery!!.limit = chunkSize
        }
        queryType = QueryType.AlbumArtists
    }

    constructor(context: Context?, query: NextUpQuery?, preferParentThumb: Boolean, presenter: Presenter?, parent: ArrayObjectAdapter?) : super(presenter) {
        this.context = context
        this.parent = parent
        mNextUpQuery = query
        mNextUpQuery!!.userId = get<UserRepository>(UserRepository::class.java).currentUser.value!!.id.toString()
        queryType = QueryType.NextUp
        this.preferParentThumb = preferParentThumb
        isStaticHeight = true
    }

	constructor(context: Context?, query: ContinueWatchingQuery?, preferParentThumb: Boolean, presenter: Presenter?, parent: ArrayObjectAdapter?) : super(presenter) {
		this.context = context
		this.parent = parent
		mContinueWatchingQuery = query
		queryType = QueryType.ContinueWatching
		this.preferParentThumb = preferParentThumb
		isStaticHeight = true
	}

	constructor(context: Context?, query: SeriesTimerQuery?, presenter: Presenter?, parent: ArrayObjectAdapter?) : super(presenter) {
        this.context = context
        this.parent = parent
        mSeriesTimerQuery = query
        queryType = QueryType.SeriesTimer
    }

    constructor(context: Context?, query: LatestItemsQuery?, preferParentThumb: Boolean, presenter: Presenter?, parent: ArrayObjectAdapter?) : super(presenter) {
        this.context = context
        this.parent = parent
        mLatestQuery = query
        mLatestQuery!!.userId = get<UserRepository>(UserRepository::class.java).currentUser.value!!.id.toString()
        queryType = QueryType.LatestItems
        this.preferParentThumb = preferParentThumb
        isStaticHeight = true
    }

    constructor(context: Context?, people: Array<BaseItemPerson>?, presenter: Presenter?, parent: ArrayObjectAdapter?) : super(presenter) {
        this.context = context
        this.parent = parent
        mPersons = people
        isStaticHeight = true
        queryType = QueryType.StaticPeople
    }

    constructor(context: Context?, chapters: List<ChapterItemInfo>?, presenter: Presenter?, parent: ArrayObjectAdapter?) : super(presenter) {
        this.context = context
        this.parent = parent
        mChapters = chapters
        isStaticHeight = true
        queryType = QueryType.StaticChapters
    }

    constructor(context: Context?, items: List<BaseItemDto>?, presenter: Presenter?, parent: ArrayObjectAdapter?, queryType: QueryType?) : super(presenter) {
        this.context = context
        this.parent = parent
        mItems = items
        this.queryType = queryType
    }

    constructor(context: Context?, items: List<BaseItemDto>?, presenter: Presenter?, parent: ArrayObjectAdapter?, staticItems: Boolean) : super(presenter) { // last param is just for sig
        this.context = context
        this.parent = parent
        mItems = items
        queryType = QueryType.StaticItems
    }

    constructor(context: Context?, query: SpecialsQuery?, presenter: Presenter?, parent: ArrayObjectAdapter?) : super(presenter) {
        this.context = context
        this.parent = parent
        mSpecialsQuery = query
        queryType = QueryType.Specials
    }

    constructor(context: Context?, query: TrailersQuery?, presenter: Presenter?, parent: ArrayObjectAdapter?) : super(presenter) {
        this.context = context
        this.parent = parent
        mTrailersQuery = query
        queryType = QueryType.Trailers
    }

    constructor(context: Context?, query: LiveTvChannelQuery?, chunkSize: Int, presenter: Presenter?, parent: ArrayObjectAdapter?) : super(presenter) {
        this.context = context
        this.parent = parent
        mTvChannelQuery = query
        this.chunkSize = chunkSize
        if (chunkSize > 0) {
            mTvChannelQuery!!.limit = chunkSize
        }
        queryType = QueryType.LiveTvChannel
    }

    constructor(context: Context?, query: RecommendedProgramQuery?, presenter: Presenter?, parent: ArrayObjectAdapter?) : super(presenter) {
        this.context = context
        this.parent = parent
        mTvProgramQuery = query
        queryType = QueryType.LiveTvProgram
        isStaticHeight = true
    }

    constructor(context: Context?, query: RecordingQuery?, chunkSize: Int, presenter: Presenter?, parent: ArrayObjectAdapter?) : super(presenter) {
        this.context = context
        this.parent = parent
        mTvRecordingQuery = query
        this.chunkSize = chunkSize
        queryType = QueryType.LiveTvRecording
        isStaticHeight = true
    }

    constructor(context: Context?, query: RecordingGroupQuery?, presenter: Presenter?, parent: ArrayObjectAdapter?) : super(presenter) {
        this.context = context
        this.parent = parent
        mTvRecordingGroupQuery = query
        queryType = QueryType.LiveTvRecordingGroup
    }

    constructor(context: Context?, query: SimilarItemsQuery?, queryType: QueryType?, presenter: Presenter?, parent: ArrayObjectAdapter?) : super(presenter) {
        this.context = context
        this.parent = parent
        mSimilarQuery = query
        mSimilarQuery!!.userId = get<UserRepository>(UserRepository::class.java).currentUser.value!!.id.toString()
        this.queryType = queryType
    }

    constructor(context: Context?, query: UpcomingEpisodesQuery?, presenter: Presenter?, parent: ArrayObjectAdapter?) : super(presenter) {
        this.context = context
        this.parent = parent
        mUpcomingQuery = query
        mUpcomingQuery!!.userId = get<UserRepository>(UserRepository::class.java).currentUser.value!!.id.toString()
        queryType = QueryType.Upcoming
    }

    constructor(context: Context?, query: SeasonQuery?, presenter: Presenter?, parent: ArrayObjectAdapter?) : super(presenter) {
        this.context = context
        this.parent = parent
        mSeasonQuery = query
        mSeasonQuery!!.userId = get<UserRepository>(UserRepository::class.java).currentUser.value!!.id.toString()
        queryType = QueryType.Season
    }

    constructor(context: Context?, query: PersonsQuery?, chunkSize: Int, presenter: Presenter?, parent: ArrayObjectAdapter?) : super(presenter) {
        this.context = context
        this.parent = parent
        this.chunkSize = chunkSize
        mPersonsQuery = query
        mPersonsQuery!!.userId = get<UserRepository>(UserRepository::class.java).currentUser.value!!.id.toString()
        if (chunkSize > 0) {
            mPersonsQuery!!.limit = chunkSize
        }
        queryType = QueryType.Persons
    }

    constructor(context: Context?, query: SearchQuery?, presenter: Presenter?, parent: ArrayObjectAdapter?) : super(presenter) {
        this.context = context
        this.parent = parent
        mSearchQuery = query
        mSearchQuery!!.userId = get<UserRepository>(UserRepository::class.java).currentUser.value!!.id.toString()
        mSearchQuery!!.limit = 50
        queryType = QueryType.Search
    }

    constructor(context: Context?, query: ViewQuery?, presenter: Presenter?, parent: ArrayObjectAdapter?) : super(presenter) {
        this.context = context
        this.parent = parent
        queryType = QueryType.Views
        isStaticHeight = true
    }

    fun setItemsLoaded(itemsLoaded: Int) {
        this.itemsLoaded = itemsLoaded
        fullyLoaded = chunkSize == 0 || itemsLoaded >= totalItems
    }

    fun getItemsLoaded(): Int {
        return itemsLoaded
    }

    fun setSortBy(option: SortOption) {
        if (option.value != sortBy) {
            sortBy = option.value
            when (queryType) {
                QueryType.AlbumArtists -> {
                    mArtistsQuery!!.sortBy = arrayOf(sortBy)
                    mArtistsQuery!!.sortOrder = option.order
                }
                else -> {
                    mQuery!!.sortBy = arrayOf(sortBy)
                    mQuery!!.sortOrder = option.order
                }
            }
            if (ItemSortBy.SortName != option.value) {
                startLetter = null
            }
        }
    }

    var filters: FilterOptions?
        get() = mFilters
        set(filters) {
            mFilters = filters
            when (queryType) {
                QueryType.AlbumArtists -> mArtistsQuery!!.filters = if (mFilters != null) mFilters!!.filters else null
                else -> mQuery!!.filters = if (mFilters != null) mFilters!!.filters else null
            }
        }

    fun setPosition(pos: Int) {
        val presenter = parent!!.getPresenter(this)
        if (presenter is PositionableListRowPresenter) {
            presenter.position = pos
        }
    }

    var startLetter: String?
        get() = if (mQuery != null) mQuery!!.nameStartsWithOrGreater else null
        set(value) {
            when (queryType) {
                QueryType.AlbumArtists -> if (value != null && value == "#") {
                    mArtistsQuery!!.nameStartsWithOrGreater = null
                } else {
                    mArtistsQuery!!.nameStartsWithOrGreater = value
                }
                else -> if (value != null && value == "#") {
                    mQuery!!.nameStartsWithOrGreater = null
                } else {
                    mQuery!!.nameStartsWithOrGreater = value
                }
            }
        }

    fun removeRow() {
        if (parent == null) {
            // just clear us
            clear()
            return
        }
        if (parent!!.size() == 1) {
            // we will be removing the last row - show something and prevent the framework from crashing
            // because there is nowhere for focus to land
            val emptyRow = ArrayObjectAdapter(TextItemPresenter())
            emptyRow.add(context!!.getString(R.string.lbl_no_items))
            parent!!.add(ListRow(HeaderItem(context!!.getString(R.string.lbl_empty)), emptyRow))
        }
        parent!!.remove(mRow)
    }

    fun loadMoreItemsIfNeeded(pos: Long) {
        if (fullyLoaded) {
            //context.getLogger().Debug("Row is fully loaded");
            return
        }
        if (isCurrentlyRetrieving) {
            Timber.d("Not loading more because currently retrieving")
            return
        }
        if (pos >= itemsLoaded - 20) {
            Timber.d("Loading more items starting at %d", itemsLoaded)
            retrieveNext()
        }
    }

    private fun retrieveNext() {
        if (fullyLoaded || isCurrentlyRetrieving) {
            return
        }
        when (queryType) {
            QueryType.Persons -> {
                if (mPersonsQuery == null) {
                    return
                }
                notifyRetrieveStarted()

                //set the query to go get the next chunk
                mPersonsQuery!!.startIndex = itemsLoaded
                retrieve(mPersonsQuery)
            }
            QueryType.LiveTvChannel -> {
                if (mTvChannelQuery == null) {
                    return
                }
                notifyRetrieveStarted()

                //set the query to go get the next chunk
                mTvChannelQuery!!.startIndex = itemsLoaded
                retrieve(mTvChannelQuery)
            }
            QueryType.AlbumArtists -> {
                if (mArtistsQuery == null) {
                    return
                }
                notifyRetrieveStarted()

                //set the query to go get the next chunk
                mArtistsQuery!!.startIndex = itemsLoaded
                retrieve(mArtistsQuery)
            }
            else -> {
                if (mQuery == null) {
                    return
                }
                notifyRetrieveStarted()

                //set the query to go get the next chunk
                mQuery!!.startIndex = itemsLoaded
                retrieve(mQuery)
            }
        }
    }

    fun ReRetrieveIfNeeded(): Boolean {
        if (reRetrieveTriggers == null) {
            return false
        }
        var retrieve = false
        val dataRefreshService = get<DataRefreshService>(DataRefreshService::class.java)
        for (trigger in reRetrieveTriggers!!) {
            when (trigger) {
                ChangeTriggerType.LibraryUpdated -> retrieve = retrieve or (lastFullRetrieve!!.timeInMillis < dataRefreshService.lastLibraryChange)
                ChangeTriggerType.MoviePlayback -> retrieve = retrieve or (lastFullRetrieve!!.timeInMillis < dataRefreshService.lastMoviePlayback)
                ChangeTriggerType.TvPlayback -> retrieve = retrieve or (lastFullRetrieve!!.timeInMillis < dataRefreshService.lastTvPlayback)
                ChangeTriggerType.MusicPlayback -> retrieve = retrieve or (lastFullRetrieve!!.timeInMillis < dataRefreshService.lastMusicPlayback)
                ChangeTriggerType.FavoriteUpdate -> retrieve = retrieve or (lastFullRetrieve!!.timeInMillis < dataRefreshService.lastFavoriteUpdate)
                ChangeTriggerType.VideoQueueChange -> retrieve = retrieve or (lastFullRetrieve!!.timeInMillis < dataRefreshService.lastVideoQueueChange)
                ChangeTriggerType.GuideNeedsLoad -> {
                    val start: Calendar = GregorianCalendar(TimeZone.getTimeZone("Z"))
                    start[Calendar.MINUTE] = if (start[Calendar.MINUTE] >= 30) 30 else 0
                    start[Calendar.SECOND] = 0
                    retrieve = retrieve or TvManager.programsNeedLoad(start)
                }
                ChangeTriggerType.Always -> retrieve = true
            }
        }
        if (retrieve) {
            Timber.i("Re-retrieving row of type %s", queryType.toString())
            Retrieve()
        }
        return retrieve
    }

    fun Retrieve() {
        notifyRetrieveStarted()
        lastFullRetrieve = Calendar.getInstance()
        itemsLoaded = 0
        when (queryType) {
            QueryType.Items -> retrieve(mQuery)
            QueryType.NextUp -> retrieve(mNextUpQuery)
            QueryType.LatestItems -> retrieve(mLatestQuery)
            QueryType.Upcoming -> retrieve(mUpcomingQuery)
            QueryType.Season -> retrieve(mSeasonQuery)
            QueryType.Views -> retrieveViews()
            QueryType.SimilarSeries -> retrieveSimilarSeries(mSimilarQuery)
            QueryType.SimilarMovies -> retrieveSimilarMovies(mSimilarQuery)
            QueryType.Persons -> retrieve(mPersonsQuery)
            QueryType.LiveTvChannel -> retrieve(mTvChannelQuery)
            QueryType.LiveTvProgram -> retrieve(mTvProgramQuery)
            QueryType.LiveTvRecording -> retrieve(mTvRecordingQuery)
            QueryType.LiveTvRecordingGroup -> retrieve(mTvRecordingGroupQuery)
            QueryType.StaticPeople -> loadPeople()
            QueryType.StaticChapters -> loadChapters()
            QueryType.StaticItems -> loadStaticItems()
            QueryType.StaticAudioQueueItems -> loadStaticAudioItems()
            QueryType.Specials -> retrieve(mSpecialsQuery)
            QueryType.Trailers -> retrieve(mTrailersQuery)
            QueryType.Search -> retrieve(mSearchQuery)
            QueryType.AlbumArtists -> retrieve(mArtistsQuery)
            QueryType.AudioPlaylists -> retrieveAudioPlaylists(mQuery)
            QueryType.Premieres -> retrievePremieres(mQuery)
            QueryType.SeriesTimer -> retrieve(mSeriesTimerQuery)
			QueryType.ContinueWatching -> retrieveContinue(mContinueWatchingQuery)
			else -> {
				Timber.i(message = "Query Type Not Recognized falling back to generic Items Query")
				retrieve(mQuery)
			}
		}
    }

    private fun loadPeople() {
        if (mPersons != null) {
            for (person in mPersons!!) {
                add(BaseRowItem(person))
            }
        } else {
            removeRow()
        }
        notifyRetrieveFinished()
    }

    private fun loadChapters() {
        if (mChapters != null) {
            for (chapter in mChapters!!) {
                add(BaseRowItem(chapter))
            }
        } else {
            removeRow()
        }
        notifyRetrieveFinished()
    }

    private fun loadStaticItems() {
        if (mItems != null) {
            for (item in mItems!!) {
                add(BaseRowItem(item))
            }
            itemsLoaded = mItems!!.size
        } else {
            removeRow()
        }
        notifyRetrieveFinished()
    }

    private fun loadStaticAudioItems() {
        if (mItems != null) {
            var i = 0
            for (item in mItems!!) {
                add(AudioQueueItem(i++, item))
            }
            itemsLoaded = i
        } else {
            removeRow()
        }
        notifyRetrieveFinished()
    }

    private fun retrieveViews() {
        val adapter = this
        val (_, _, _, id) = get<UserRepository>(UserRepository::class.java).currentUser.value!!
        apiClient.value.GetUserViews(id.toString(), object : Response<ItemsResult>() {
            override fun onResponse(response: ItemsResult) {
                if (response.totalRecordCount > 0) {
                    var i = 0
                    val prevItems = if (adapter.size() > 0) adapter.size() else 0
                    for (item in response.items) {
                        //re-map the display prefs id to our actual id
                        item.displayPreferencesId = item.id
                        if (userViewsRepository.value.isSupported(item.collectionType)) {
                            adapter.add(BaseRowItem(i++, item, preferParentThumb, isStaticHeight))
                        }
                    }
                    totalItems = response.totalRecordCount
                    setItemsLoaded(itemsLoaded + i)
                    if (prevItems > 0) {
                        // remove previous items as we re-retrieved
                        // this is done this way instead of clearing the adapter to avoid bugs in the framework elements
                        removeItems(0, prevItems)
                    }
                } else {
                    // no results - don't show us
                    removeRow()
                }
                notifyRetrieveFinished()
            }

            override fun onError(exception: Exception) {
                Timber.e(exception, "Error retrieving items")
                removeRow()
                notifyRetrieveFinished(exception)
            }
        })
    }

    private fun retrieve(query: SearchQuery?) {
        val adapter = this
        apiClient.value.GetSearchHintsAsync(query, object : Response<SearchHintResult>() {
            override fun onResponse(response: SearchHintResult) {
                if (response.searchHints != null && response.searchHints.isNotEmpty()) {
                    var i = 0
                    if (adapter.size() > 0) {
                        adapter.clear()
                    }
                    for (item in response.searchHints) {
                        if (userViewsRepository.value.isSupported(item.type)) {
                            i++
                            adapter.add(BaseRowItem(item))
                        }
                    }
                    totalItems = response.totalRecordCount
                    setItemsLoaded(itemsLoaded + i)
                }
                notifyRetrieveFinished()
            }

            override fun onError(exception: Exception) {
                Timber.e(exception, "Error retrieving search results")
                notifyRetrieveFinished(exception)
            }
        })
    }

    fun addToParentIfResultsReceived() {
        if (itemsLoaded > 0 && parent != null) {
            parent!!.add(mRow)
        }
    }

    private fun retrieve(query: ItemQuery?) {
        apiClient.value.GetItemsAsync(query, object : Response<ItemsResult>() {
            override fun onResponse(response: ItemsResult) {
                if (response.items != null && response.items.isNotEmpty()) {
                    totalItems = if (query!!.enableTotalRecordCount) response.totalRecordCount else response.items.size
                    var i = getItemsLoaded()
                    val prevItems = if (i == 0 && size() > 0) size() else 0
                    for (item in response.items) {
                        add(BaseRowItem(i++, item, preferParentThumb, isStaticHeight))
                    }
                    setItemsLoaded(i)
                    if (i == 0) {
                        removeRow()
                    } else if (prevItems > 0) {
                        // remove previous items as we re-retrieved
                        // this is done this way instead of clearing the adapter to avoid bugs in the framework elements
                        removeItems(0, prevItems)
                    }
                } else {
                    // no results - don't show us
                    if (getItemsLoaded() == 0) {
                        removeRow()
                    }
                }
                notifyRetrieveFinished()
            }

            override fun onError(exception: Exception) {
                Timber.e(exception, "Error retrieving items")
                removeRow()
                notifyRetrieveFinished(exception)
            }
        })
    }

    private fun retrieveAudioPlaylists(query: ItemQuery?) {
        //Add specialized playlists first
        clear()
        add(GridButton(EnhancedBrowseFragment.FAVSONGS, context!!.getString(R.string.lbl_favorites), R.drawable.favorites))
        itemsLoaded = 1
        retrieve(query)
    }

    private fun retrieve(query: ArtistsQuery?) {
        apiClient.value.GetAlbumArtistsAsync(query, object : Response<ItemsResult>() {
            override fun onResponse(response: ItemsResult) {
                if (response.items != null && response.items.isNotEmpty()) {
                    totalItems = response.totalRecordCount
                    var i = getItemsLoaded()
                    val prevItems = if (i == 0 && size() > 0) size() else 0
                    for (item in response.items) {
                        add(BaseRowItem(i++, item, preferParentThumb, isStaticHeight))
                    }
                    setItemsLoaded(i)
                    if (i == 0) {
                        removeRow()
                    } else if (prevItems > 0) {
                        // remove previous items as we re-retrieved
                        // this is done this way instead of clearing the adapter to avoid bugs in the framework elements
                        removeItems(0, prevItems)
                    }
                } else {
                    // no results - don't show us
                    totalItems = 0
                    removeRow()
                }
                notifyRetrieveFinished()
            }
        })
    }

    private fun retrieve(query: LatestItemsQuery?) {
        apiClient.value.GetLatestItems(query, object : Response<Array<BaseItemDto?>?>() {
            override fun onResponse(response: Array<BaseItemDto?>?) {
                if (response != null && response.isNotEmpty()) {
                    totalItems = response.size
                    var i = getItemsLoaded()
                    val prevItems = if (i == 0 && size() > 0) size() else 0
                    for (item in response) {
                        add(BaseRowItem(i++, item, preferParentThumb, isStaticHeight))
                    }
                    setItemsLoaded(i)
                    if (i == 0) {
                        removeRow()
                    } else if (prevItems > 0) {
                        // remove previous items as we re-retrieved
                        // this is done this way instead of clearing the adapter to avoid bugs in the framework elements
                        removeItems(0, prevItems)
                    }
                } else {
                    // no results - don't show us
                    totalItems = 0
                    removeRow()
                }
                notifyRetrieveFinished()
            }
        })
    }

    private fun retrieveContinue(query: ContinueWatchingQuery?) {
		ProcessLifecycleOwner.get().lifecycleScope.launch {
			val response = api.itemsApi.getResumeItems(
					fields = listOf(ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
							ItemFields.OVERVIEW,
							ItemFields.ITEM_COUNTS,
							ItemFields.DISPLAY_PREFERENCES_ID,
							ItemFields.CHILD_COUNT),
					imageTypeLimit = 1,
					limit = 50,
					mediaTypes = query?.mediaTypes,
					excludeActiveSessions = true,
			).content.items.orEmpty()
			if (response.isNotEmpty()) {
				totalItems = response.size
				var i = getItemsLoaded()
				val prevItems = if (i == 0 && size() > 0) size() else 0
				for (item in response) {
					val userData = UserItemDataDto()
					userData.playedPercentage = item.userData?.playedPercentage
					userData.playbackPositionTicks = item.userData?.playbackPositionTicks ?: 0
					userData.playCount = item.userData?.playCount ?: 0
					userData.isFavorite = item.userData?.isFavorite ?: false
					userData.played = item.userData?.played ?: false
					userData.key = item.userData?.key

					val result = BaseItemDto()
					result.baseItemType = valueOf<BaseItemType>(item.type.toString(), BaseItemType.Movie)

					result.name = item.name
					result.serverId = item.serverId
					result.id = item.id.toString()
					result.container = item.container
					result.hasSubtitles = item.hasSubtitles
					result.communityRating = item.communityRating
					result.userData = userData
					result.originalTitle = item.originalTitle
					result.sourceType = item.sourceType
					result.playlistItemId = item.playlistItemId
					result.airsBeforeSeasonNumber = item.airsBeforeSeasonNumber
					result.airsAfterSeasonNumber = item.airsAfterSeasonNumber
					result.airsBeforeEpisodeNumber = item.airsBeforeEpisodeNumber
					result.canDelete = item.canDelete
					result.canDownload = item.canDownload
					result.preferredMetadataLanguage = item.preferredMetadataLanguage
					result.preferredMetadataCountryCode = item.preferredMetadataCountryCode
					result.sortName = item.sortName
					result.forcedSortName = item.forcedSortName
					result.criticRating = item.criticRating
					result.path = item.path
					result.officialRating = item.officialRating
					result.customRating = item.customRating
					result.channelName = item.channelName
					result.overview = item.overview
					result.shortOverview = item.overview
					result.cumulativeRunTimeTicks = item.cumulativeRunTimeTicks
					result.runTimeTicks = item.runTimeTicks
					result.aspectRatio = item.aspectRatio
					result.productionYear = item.productionYear
					result.isPlaceHolder = item.isPlaceHolder
					result.number = item.number
					result.channelNumber = item.channelNumber
					result.indexNumber = item.indexNumber
					result.indexNumberEnd = item.indexNumberEnd
					result.parentIndexNumber = item.parentIndexNumber
					result.isFolder = item.isFolder
					result.localTrailerCount = item.localTrailerCount
					result.recursiveItemCount = item.recursiveItemCount
					result.childCount = item.childCount
					result.seriesName = item.seriesName
					result.specialFeatureCount = item.specialFeatureCount
					result.displayPreferencesId = item.displayPreferencesId
					result.status = item.status
					result.airTime = item.airTime
					result.primaryImageAspectRatio = item.primaryImageAspectRatio
					result.album = item.album
					result.collectionType = item.collectionType
					result.displayOrder = item.displayOrder
					result.albumPrimaryImageTag = item.albumPrimaryImageTag
					result.seriesPrimaryImageTag = item.seriesPrimaryImageTag
					result.albumArtist = item.albumArtist
					result.seasonName = item.seasonName
					result.partCount = item.partCount
					result.mediaSourceCount = item.mediaSourceCount
					result.parentLogoImageTag = item.parentLogoImageTag
					result.parentArtImageTag = item.parentArtImageTag
					result.seriesThumbImageTag = item.seriesThumbImageTag
					result.seriesStudio = item.seriesStudio
					result.parentThumbImageTag = item.parentThumbImageTag
					result.parentPrimaryImageItemId = item.parentPrimaryImageItemId
					result.parentPrimaryImageTag = item.parentPrimaryImageTag
					result.mediaType = item.mediaType
					result.trailerCount = item.trailerCount
					result.movieCount = item.movieCount
					result.seriesCount = item.seriesCount
					result.programCount = item.programCount
					result.episodeCount = item.episodeCount
					result.songCount = item.songCount
					result.albumCount = item.albumCount
					result.artistCount = item.artistCount
					result.musicVideoCount = item.musicVideoCount
					result.lockData = item.lockData
					result.width = item.width
					result.height = item.height
					result.cameraMake = item.cameraMake
					result.cameraModel = item.cameraModel
					result.software = item.software
					result.exposureTime = item.exposureTime
					result.focalLength = item.focalLength
					result.aperture = item.aperture
					result.shutterSpeed = item.shutterSpeed
					result.latitude = item.latitude
					result.longitude = item.longitude
					result.altitude = item.altitude
					result.isoSpeedRating = item.isoSpeedRating
					result.seriesTimerId = item.seriesTimerId
					result.programId = item.programId
					result.channelPrimaryImageTag = item.channelPrimaryImageTag
					result.completionPercentage = item.completionPercentage
					result.isRepeat = item.isRepeat
					result.episodeTitle = item.episodeTitle
					result.isMovie = item.isMovie
					result.isSports = item.isSports
					result.isSeries = item.isSeries
					result.isLive = item.isLive
					result.isNews = item.isNews
					result.isKids = item.isKids
					result.isPremiere = item.isPremiere
					result.timerId = item.timerId

					add(BaseRowItem(i++, result, preferParentThumb, isStaticHeight))
				}
				setItemsLoaded(i)
				if (i == 0) {
					removeRow()
				} else if (prevItems > 0) {
					// remove previous items as we re-retrieved
					// this is done this way instead of clearing the adapter to avoid bugs in the framework elements
					removeItems(0, prevItems)
				}
			} else {
				// no results - don't show us
				totalItems = 0
				removeRow()
			}
			notifyRetrieveFinished()
		}
    }

    private fun retrievePremieres(query: ItemQuery?) {
        val adapter = this
        //First we need current Next Up to filter our list with
        val nextUp = NextUpQuery()
        nextUp.userId = query!!.userId
        nextUp.parentId = query.parentId
        nextUp.limit = 50
        apiClient.value.GetNextUpEpisodesAsync(nextUp, object : Response<ItemsResult>() {
            override fun onResponse(nextUpResponse: ItemsResult) {
                apiClient.value.GetItemsAsync(query, object : Response<ItemsResult>() {
                    override fun onResponse(response: ItemsResult) {
                        if (adapter.size() > 0) {
                            adapter.clear()
                        }
                        if (response.items != null && response.items.isNotEmpty()) {
                            var i = 0
                            val compare = Calendar.getInstance()
                            compare.add(Calendar.MONTH, -2)
                            val nextUpItems = nextUpResponse.items
                            for (item in response.items) {
                                if (item.indexNumber != null && item.indexNumber == 1 && (item.dateCreated == null || item.dateCreated.after(compare.time))
                                        && (item.userData == null || item.userData.likes == null || item.userData.likes)) {
                                    // new unwatched episode 1 not disliked - check to be sure prev episode not already in next up
                                    var nextUpItem: BaseItemDto? = null
                                    for (n in nextUpItems.indices) {
                                        if (nextUpItems[n].seriesId == item.seriesId) {
                                            nextUpItem = nextUpItems[n]
                                            break
                                        }
                                    }
                                    if (nextUpItem == null || nextUpItem.id == item.id) {
                                        //Now - let's be sure there isn't already a premiere for this series
                                        var existing: BaseRowItem? = null
                                        var existingPos = -1
                                        for (n in 0 until adapter.size()) {
                                            if ((adapter[n] as BaseRowItem).baseItem.seriesId == item.seriesId) {
                                                existing = adapter[n] as BaseRowItem
                                                existingPos = n
                                                break
                                            }
                                        }
                                        if (existing == null) {
                                            Timber.d("Adding new episode 1 to premieres %s", item.seriesName)
                                            adapter.add(BaseRowItem(i++, item, preferParentThumb, true))
                                        } else if (existing.baseItem.parentIndexNumber > item.parentIndexNumber) {
                                            //Replace the newer item with the earlier season
                                            Timber.d("Replacing newer episode 1 with an older season for %s", item.seriesName)
                                            adapter.replace(existingPos, BaseRowItem(i++, item, preferParentThumb, false))
                                        } // otherwise, just ignore this newer season premiere since we have the older one already
                                    } else {
                                        Timber.i("Didn't add %s to premieres because different episode is in next up.", item.seriesName)
                                    }
                                }
                            }
                            setItemsLoaded(itemsLoaded + i)
                        }
                        if (adapter.size() == 0) {
                            removeRow()
                        }
                        notifyRetrieveFinished()
                    }
                })
            }
        })
    }

    private fun retrieve(query: NextUpQuery?) {
        val adapter = this
        apiClient.value.GetNextUpEpisodesAsync(query, object : Response<ItemsResult>() {
            override fun onResponse(response: ItemsResult) {
                if (response.items != null && response.items.isNotEmpty()) {
                    if (adapter.size() > 0) {
                        adapter.clear()
                    }
                    var i = 0
                    for (item in response.items) {
                        adapter.add(BaseRowItem(i++, item, preferParentThumb, isStaticHeight))
                    }
                    totalItems = response.totalRecordCount
                    setItemsLoaded(itemsLoaded + i)
                    if (i == 0) {
                        removeRow()
                        notifyRetrieveFinished()
                    } else {
                        //If this was for a single series, get the rest of the episodes in the season
                        if (query!!.seriesId != null) {
                            val first = if (adapter.size() == 1) (adapter[0] as BaseRowItem).baseItem else null
                            if (first != null && first.indexNumber != null && first.seasonId != null) {
                                val rest = StdItemQuery()
                                rest.userId = query.userId
                                rest.parentId = first.seasonId
                                rest.startIndex = first.indexNumber
                                apiClient.value.GetItemsAsync(rest, object : Response<ItemsResult>() {
                                    override fun onResponse(innerResponse: ItemsResult) {
                                        if (response.items != null) {
                                            var n = response.items.size
                                            for (item in innerResponse.items) {
                                                adapter.add(BaseRowItem(n++, item, preferParentThumb, false))
                                            }
                                            totalItems += innerResponse.totalRecordCount
                                            setItemsLoaded(itemsLoaded + n)
                                        }
                                        notifyRetrieveFinished()
                                    }

                                    override fun onError(exception: Exception) {
                                        Timber.e(exception, "Unable to retrieve subsequent episodes in next up")
                                        notifyRetrieveFinished()
                                    }
                                })
                            }
                        }
                    }
                } else {
                    // no results - don't show us
                    removeRow()
                    notifyRetrieveFinished()
                }
            }

            override fun onError(exception: Exception) {
                Timber.e(exception, "Error retrieving next up items")
                removeRow()
                notifyRetrieveFinished(exception)
            }
        })
    }

    private fun retrieve(query: LiveTvChannelQuery?) {
        val adapter = this
        apiClient.value.GetLiveTvChannelsAsync(query, object : Response<ChannelInfoDtoResult>() {
            override fun onResponse(response: ChannelInfoDtoResult) {
                if (response.items != null && response.items.isNotEmpty()) {
                    var i = itemsLoaded
                    if (i == 0 && adapter.size() > 0) {
                        adapter.clear()
                    }
                    for (item in response.items) {
                        adapter.add(BaseRowItem(i, item))
                        i++
                    }
                    totalItems = response.totalRecordCount
                    setItemsLoaded(i)
                    if (i == 0) {
                        removeRow()
                    }
                } else {
                    // no results - don't show us
                    removeRow()
                }
                notifyRetrieveFinished()
            }

            override fun onError(exception: Exception) {
                Timber.e(exception, "Error retrieving live tv channels")
                removeRow()
                notifyRetrieveFinished(exception)
            }
        })
    }

    private fun retrieve(query: RecommendedProgramQuery?) {
        val adapter = this
        apiClient.value.GetRecommendedLiveTvProgramsAsync(query, object : Response<ItemsResult>() {
            override fun onResponse(response: ItemsResult) {
                TvManager.updateProgramsNeedsLoadTime()
                if (response.items != null && response.items.isNotEmpty()) {
                    var i = 0
                    val prevItems = if (adapter.size() > 0) adapter.size() else 0
                    for (item in response.items) {
                        adapter.add(BaseRowItem(item, isStaticHeight))
                        i++
                    }
                    totalItems = response.totalRecordCount
                    setItemsLoaded(i)
                    if (i == 0) {
                        removeRow()
                    } else if (prevItems > 0) {
                        // remove previous items as we re-retrieved
                        // this is done this way instead of clearing the adapter to avoid bugs in the framework elements
                        removeItems(0, prevItems)
                    }
                } else {
                    // no results - don't show us
                    removeRow()
                }
                notifyRetrieveFinished()
            }

            override fun onError(exception: Exception) {
                Timber.e(exception, "Error retrieving live tv programs")
                removeRow()
                notifyRetrieveFinished(exception)
            }
        })
    }

    private fun retrieve(query: RecordingGroupQuery?) {
        val adapter = this
        apiClient.value.GetLiveTvRecordingGroupsAsync(query, object : Response<ItemsResult>() {
            override fun onResponse(response: ItemsResult) {
                if (response.items != null && response.items.isNotEmpty()) {
                    var i = 0
                    val prevItems = if (adapter.size() > 0) adapter.size() else 0
                    for (item in response.items) {
                        item.baseItemType = BaseItemType.RecordingGroup // the API does not fill this in
                        item.isFolder = true // nor this
                        adapter.add(BaseRowItem(item))
                        i++
                    }
                    totalItems = response.totalRecordCount
                    setItemsLoaded(itemsLoaded + i)
                    if (i == 0) {
                        removeRow()
                    } else if (prevItems > 0) {
                        // remove previous items as we re-retrieved
                        // this is done this way instead of clearing the adapter to avoid bugs in the framework elements
                        removeItems(0, prevItems)
                    }
                } else {
                    // no results - don't show us
                    removeRow()
                }
                notifyRetrieveFinished()
            }

            override fun onError(exception: Exception) {
                Timber.e(exception, "Error retrieving live tv recording groups")
                removeRow()
                notifyRetrieveFinished(exception)
            }
        })
    }

    private fun retrieve(query: SeriesTimerQuery?) {
        val adapter = this
        apiClient.value.GetLiveTvSeriesTimersAsync(query, object : Response<SeriesTimerInfoDtoResult>() {
            override fun onResponse(response: SeriesTimerInfoDtoResult) {
                if (response.items != null && response.items.isNotEmpty()) {
                    var i = 0
                    val prevItems = if (adapter.size() > 0) adapter.size() else 0
                    for (item in response.items) {
                        adapter.add(BaseRowItem(item))
                        i++
                    }
                    totalItems = response.totalRecordCount
                    setItemsLoaded(itemsLoaded + i)
                    if (i == 0) {
                        removeRow()
                    } else if (prevItems > 0) {
                        // remove previous items as we re-retrieved
                        // this is done this way instead of clearing the adapter to avoid bugs in the framework elements
                        removeItems(0, prevItems)
                    }
                } else {
                    // no results - don't show us
                    removeRow()
                }
                notifyRetrieveFinished()
            }

            override fun onError(exception: Exception) {
                Timber.e(exception, "Error retrieving live tv series timers")
                removeRow()
                notifyRetrieveFinished(exception)
            }
        })
    }

    private fun retrieve(query: RecordingQuery?) {
        val adapter = this
        apiClient.value.GetLiveTvRecordingsAsync(query, object : Response<ItemsResult>() {
            override fun onResponse(response: ItemsResult) {
                if (response.items != null && response.items.isNotEmpty()) {
                    var i = 0
                    val prevItems = if (adapter.size() > 0) adapter.size() else 0
                    if (adapter.chunkSize == 0) {
                        // and recordings as first item if showing all
                        adapter.add(BaseRowItem(GridButton(LiveTvOption.LIVE_TV_RECORDINGS_OPTION_ID, context!!.getString(R.string.lbl_recorded_tv), R.drawable.tile_port_record)))
                        i++
                        if (Utils.canManageRecordings(get<UserRepository>(UserRepository::class.java).currentUser.value)) {
                            // and schedule
                            adapter.add(BaseRowItem(GridButton(LiveTvOption.LIVE_TV_SCHEDULE_OPTION_ID, context!!.getString(R.string.lbl_schedule), R.drawable.tile_port_time)))
                            i++
                            // and series
                            adapter.add(BaseRowItem(GridButton(LiveTvOption.LIVE_TV_SERIES_OPTION_ID, context!!.getString(R.string.lbl_series), R.drawable.tile_port_series_timer)))
                            i++
                        }
                    }
                    for (item in response.items) {
                        adapter.add(BaseRowItem(item, isStaticHeight))
                        i++
                    }
                    totalItems = response.totalRecordCount
                    setItemsLoaded(itemsLoaded + i)
                    if (i == 0) {
                        removeRow()
                    } else if (prevItems > 0) {
                        // remove previous items as we re-retrieved
                        // this is done this way instead of clearing the adapter to avoid bugs in the framework elements
                        removeItems(0, prevItems)
                    }
                } else {
                    // no results - don't show us
                    removeRow()
                }
                notifyRetrieveFinished()
            }

            override fun onError(exception: Exception) {
                Timber.e(exception, "Error retrieving live tv recordings")
                removeRow()
                notifyRetrieveFinished(exception)
            }
        })
    }

    private fun retrieve(query: SpecialsQuery?) {
        val adapter = this
        apiClient.value.GetSpecialFeaturesAsync(get<UserRepository>(UserRepository::class.java).currentUser.value!!.id.toString(), query!!.itemId, object : Response<Array<BaseItemDto?>>() {
            override fun onResponse(response: Array<BaseItemDto?>) {
                if (response.isNotEmpty()) {
                    var i = 0
                    if (adapter.size() > 0) {
                        adapter.clear()
                    }
                    for (item in response) {
                        adapter.add(BaseRowItem(i++, item, preferParentThumb, false))
                    }
                    totalItems = response.size
                    setItemsLoaded(itemsLoaded + i)
                    if (i == 0) {
                        removeRow()
                    }
                } else {
                    // no results - don't show us
                    removeRow()
                }
                notifyRetrieveFinished()
            }

            override fun onError(exception: Exception) {
                Timber.e(exception, "Error retrieving special features")
                removeRow()
                notifyRetrieveFinished(exception)
            }
        })
    }

    private fun retrieve(query: TrailersQuery?) {
        val adapter = this
        apiClient.value.GetLocalTrailersAsync(get<UserRepository>(UserRepository::class.java).currentUser.value!!.id.toString(), query!!.itemId, object : Response<Array<BaseItemDto>>() {
            override fun onResponse(response: Array<BaseItemDto>) {
                if (response.isNotEmpty()) {
                    var i = 0
                    if (adapter.size() > 0) {
                        adapter.clear()
                    }
                    for (item in response) {
                        item.name = context!!.getString(R.string.lbl_trailer) + (i + 1)
                        adapter.add(BaseRowItem(i++, item, preferParentThumb, false, BaseRowItem.SelectAction.Play))
                    }
                    totalItems = response.size
                    setItemsLoaded(itemsLoaded + i)
                    if (i == 0) {
                        removeRow()
                    }
                } else {
                    // no results - don't show us
                    removeRow()
                }
                notifyRetrieveFinished()
            }

            override fun onError(exception: Exception) {
                Timber.e(exception, "Error retrieving special features")
                removeRow()
                notifyRetrieveFinished(exception)
            }
        })
    }

    private fun retrieveSimilarSeries(query: SimilarItemsQuery?) {
        val adapter = this
        apiClient.value.GetSimilarItems(query, object : Response<ItemsResult>() {
            override fun onResponse(response: ItemsResult) {
                if (response.items != null && response.items.isNotEmpty()) {
                    var i = 0
                    if (adapter.size() > 0) {
                        adapter.clear()
                    }
                    for (item in response.items) {
                        adapter.add(BaseRowItem(i++, item))
                    }
                    totalItems = response.totalRecordCount
                    setItemsLoaded(itemsLoaded + i)
                    if (i == 0) {
                        removeRow()
                    }
                } else {
                    // no results - don't show us
                    removeRow()
                }
                notifyRetrieveFinished()
            }

            override fun onError(exception: Exception) {
                Timber.e(exception, "Error retrieving similar series items")
                removeRow()
                notifyRetrieveFinished(exception)
            }
        })
    }

    private fun retrieveSimilarMovies(query: SimilarItemsQuery?) {
        val adapter = this
        apiClient.value.GetSimilarItems(query, object : Response<ItemsResult>() {
            override fun onResponse(response: ItemsResult) {
                if (response.items != null && response.items.isNotEmpty()) {
                    var i = 0
                    if (adapter.size() > 0) {
                        adapter.clear()
                    }
                    for (item in response.items) {
                        adapter.add(BaseRowItem(i++, item))
                    }
                    totalItems = response.totalRecordCount
                    setItemsLoaded(itemsLoaded + i)
                    if (i == 0) {
                        removeRow()
                    }
                } else {
                    // no results - don't show us
                    removeRow()
                }
                notifyRetrieveFinished()
            }

            override fun onError(exception: Exception) {
                Timber.e(exception, "Error retrieving similar series items")
                removeRow()
                notifyRetrieveFinished(exception)
            }
        })
    }

    private fun retrieve(query: UpcomingEpisodesQuery?) {
        val adapter = this
        apiClient.value.GetUpcomingEpisodesAsync(query, object : Response<ItemsResult>() {
            override fun onResponse(response: ItemsResult) {
                if (response.items != null && response.items.isNotEmpty()) {
                    var i = 0
                    if (adapter.size() > 0) {
                        adapter.clear()
                    }
                    for (item in response.items) {
                        if (query!!.parentId == null || item.seriesId == null || item.seriesId == query.parentId) {
                            adapter.add(BaseRowItem(i++, item))
                        }
                    }
                    totalItems = response.totalRecordCount
                    setItemsLoaded(itemsLoaded + i)
                    if (i == 0) {
                        removeRow()
                    }
                } else {
                    // no results - don't show us
                    removeRow()
                }
                notifyRetrieveFinished()
            }

            override fun onError(exception: Exception) {
                Timber.e(exception, "Error retrieving upcoming items")
                removeRow()
                notifyRetrieveFinished(exception)
            }
        })
    }

    private fun retrieve(query: PersonsQuery?) {
        val adapter = this
        apiClient.value.GetPeopleAsync(query, object : Response<ItemsResult>() {
            override fun onResponse(response: ItemsResult) {
                if (response.items != null && response.items.isNotEmpty()) {
                    var i = itemsLoaded
                    if (i == 0 && adapter.size() > 0) {
                        adapter.clear()
                    }
                    for (item in response.items) {
                        adapter.add(BaseRowItem(i++, item))
                    }
                    totalItems = response.totalRecordCount
                    setItemsLoaded(i)
                    if (i == 0) {
                        removeRow()
                    }
                } else {
                    // no results - don't show us
                    removeRow()
                }
                notifyRetrieveFinished()
            }

            override fun onError(exception: Exception) {
                Timber.e(exception, "Error retrieving people")
                removeRow()
                notifyRetrieveFinished(exception)
            }
        })
    }

    private fun retrieve(query: SeasonQuery?) {
        val adapter = this
        apiClient.value.GetSeasonsAsync(query, object : Response<ItemsResult>() {
            override fun onResponse(response: ItemsResult) {
                if (response.items != null && response.items.isNotEmpty()) {
                    var i = 0
                    val prevItems = if (adapter.size() > 0) adapter.size() else 0
                    for (item in response.items) {
                        adapter.add(BaseRowItem(i++, item))
                    }
                    totalItems = response.totalRecordCount
                    setItemsLoaded(itemsLoaded + i)
                    if (prevItems > 0) {
                        // remove previous items as we re-retrieved
                        // this is done this way instead of clearing the adapter to avoid bugs in the framework elements
                        removeItems(0, prevItems)
                    }
                } else {
                    // no results - don't show us
                    removeRow()
                }
                notifyRetrieveFinished()
            }

            override fun onError(exception: Exception) {
                Timber.e(exception, "Error retrieving season items")
                notifyRetrieveFinished(exception)
            }
        })
    }

    private fun notifyRetrieveFinished(exception: Exception? = null) {
        isCurrentlyRetrieving = false
        if (mRetrieveFinishedListener != null) {
            if (exception == null) mRetrieveFinishedListener!!.onResponse() else mRetrieveFinishedListener!!.onError(exception)
        }
    }

    fun setRetrieveFinishedListener(response: EmptyResponse?) {
        mRetrieveFinishedListener = response
    }

    private fun notifyRetrieveStarted() {
        isCurrentlyRetrieving = true
    }

	private inline fun <reified T : Enum<T>> valueOf(type: String, default: T): T {
		return try {
			java.lang.Enum.valueOf(T::class.java, type)
		} catch (e: Exception) {
			default
		}
	}
}
