package org.jellyfin.androidtv.ui.browsing

import org.jellyfin.apiclient.model.querying.ItemQuery
import org.jellyfin.apiclient.model.querying.NextUpQuery
import org.jellyfin.apiclient.model.querying.UpcomingEpisodesQuery
import org.jellyfin.apiclient.model.querying.SimilarItemsQuery
import org.jellyfin.apiclient.model.querying.LatestItemsQuery
import org.jellyfin.apiclient.model.querying.PersonsQuery
import org.jellyfin.apiclient.model.livetv.LiveTvChannelQuery
import org.jellyfin.apiclient.model.livetv.RecommendedProgramQuery
import org.jellyfin.apiclient.model.livetv.RecordingQuery
import org.jellyfin.apiclient.model.livetv.RecordingGroupQuery
import org.jellyfin.apiclient.model.livetv.SeriesTimerQuery
import org.jellyfin.apiclient.model.querying.ArtistsQuery
import org.jellyfin.apiclient.model.querying.SeasonQuery
import org.jellyfin.androidtv.constant.QueryType
import org.jellyfin.androidtv.constant.ChangeTriggerType
import org.jellyfin.androidtv.data.querying.ViewQuery

class BrowseRowDef {
    var headerText: String? = null
    var query: ItemQuery? = null
        private set
    var nextUpQuery: NextUpQuery? = null
        private set
    var upcomingQuery: UpcomingEpisodesQuery? = null
        private set
    var similarQuery: SimilarItemsQuery? = null
        private set
    var latestItemsQuery: LatestItemsQuery? = null
        private set
    var personsQuery: PersonsQuery? = null
        private set
    var tvChannelQuery: LiveTvChannelQuery? = null
        private set
    var programQuery: RecommendedProgramQuery? = null
        private set
    var recordingQuery: RecordingQuery? = null
        private set
    var recordingGroupQuery: RecordingGroupQuery? = null
        private set
    var seriesTimerQuery: SeriesTimerQuery? = null
        private set
    var artistsQuery: ArtistsQuery? = null
        private set
    var seasonQuery: SeasonQuery? = null
        private set
    var queryType: QueryType? = null
        private set
    var chunkSize = 0
        private set
    var isStaticHeight = false
        private set
    var preferParentThumb = false
        private set
    var changeTriggers: Array<ChangeTriggerType>? = emptyArray()
        private set

    @JvmOverloads
    constructor(header: String?, query: ItemQuery?, chunkSize: Int, preferParentThumb: Boolean = false, staticHeight: Boolean = false) {
        headerText = header
        this.query = query
        this.chunkSize = chunkSize
        this.preferParentThumb = preferParentThumb
        isStaticHeight = staticHeight
        queryType = QueryType.Items
    }

    constructor(header: String?, query: ItemQuery?, chunkSize: Int, changeTriggers: Array<ChangeTriggerType>?) : this(header, query, chunkSize, false, false, changeTriggers) {}

    @JvmOverloads
    constructor(header: String?, query: ItemQuery?, chunkSize: Int, preferParentThumb: Boolean, staticHeight: Boolean, changeTriggers: Array<ChangeTriggerType>?, queryType: QueryType? = QueryType.Items) {
        headerText = header
        this.query = query
        this.chunkSize = chunkSize
        this.queryType = queryType
        isStaticHeight = staticHeight
        this.preferParentThumb = preferParentThumb
        this.changeTriggers = changeTriggers
    }

    constructor(header: String?, query: ArtistsQuery?, chunkSize: Int, changeTriggers: Array<ChangeTriggerType>?) {
        headerText = header
        artistsQuery = query
        this.chunkSize = chunkSize
        queryType = QueryType.AlbumArtists
        this.changeTriggers = changeTriggers
    }

    constructor(header: String?, query: NextUpQuery?) {
        headerText = header
        nextUpQuery = query
        queryType = QueryType.NextUp
    }

    constructor(header: String?, query: SeriesTimerQuery?) {
        headerText = header
        seriesTimerQuery = query
        isStaticHeight = true
        queryType = QueryType.SeriesTimer
    }

    constructor(header: String?, query: NextUpQuery?, changeTriggers: Array<ChangeTriggerType>?) {
        headerText = header
        nextUpQuery = query
        queryType = QueryType.NextUp
        isStaticHeight = true
        this.changeTriggers = changeTriggers
    }

    constructor(header: String?, query: LatestItemsQuery?, changeTriggers: Array<ChangeTriggerType>?) {
        headerText = header
        latestItemsQuery = query
        queryType = QueryType.LatestItems
        isStaticHeight = true
        this.changeTriggers = changeTriggers
    }

    constructor(header: String?, query: SimilarItemsQuery?) {
        headerText = header
        similarQuery = query
        queryType = QueryType.SimilarSeries
    }

    constructor(header: String?, query: LiveTvChannelQuery?) {
        headerText = header
        tvChannelQuery = query
        queryType = QueryType.LiveTvChannel
    }

    constructor(header: String?, query: RecommendedProgramQuery?) {
        headerText = header
        programQuery = query
        queryType = QueryType.LiveTvProgram
        changeTriggers = arrayOf(ChangeTriggerType.GuideNeedsLoad)
    }

    @JvmOverloads
    constructor(header: String?, query: RecordingQuery?, chunkSize: Int = 0) {
        headerText = header
        recordingQuery = query
        this.chunkSize = chunkSize
        queryType = QueryType.LiveTvRecording
    }

    constructor(header: String?, query: RecordingGroupQuery?) {
        headerText = header
        recordingGroupQuery = query
        queryType = QueryType.LiveTvRecordingGroup
    }

    constructor(header: String?, query: PersonsQuery?, chunkSize: Int) {
        headerText = header
        personsQuery = query
        queryType = QueryType.Persons
        this.chunkSize = chunkSize
    }

    constructor(header: String?, query: SimilarItemsQuery?, type: QueryType?) {
        headerText = header
        similarQuery = query
        queryType = type
    }

    constructor(header: String?, query: SeasonQuery?) {
        headerText = header
        seasonQuery = query
        queryType = QueryType.Season
    }

    constructor(header: String?, query: UpcomingEpisodesQuery?) {
        headerText = header
        upcomingQuery = query
        queryType = QueryType.Upcoming
    }

    constructor(header: String?, query: ViewQuery?) {
        headerText = header
        isStaticHeight = true
        queryType = QueryType.Views
    }
}
