/*
 * Copyright (c) 2018 stanwood Gmbh
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.stanwood.bitrise.ui.builds.vm

import android.arch.lifecycle.Lifecycle
import android.arch.lifecycle.LifecycleObserver
import android.arch.lifecycle.OnLifecycleEvent
import android.content.res.Resources
import android.databinding.ObservableArrayList
import android.databinding.ObservableBoolean
import io.stanwood.bitrise.data.model.App
import io.stanwood.bitrise.data.net.BitriseService
import io.stanwood.bitrise.navigation.SCREEN_ERROR
import io.stanwood.bitrise.navigation.SCREEN_NEW_BUILD
import kotlinx.coroutines.experimental.Deferred
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import org.joda.time.format.PeriodFormatter
import ru.terrakok.cicerone.Router
import timber.log.Timber


class BuildsViewModel(private val router: Router,
                      private val service: BitriseService,
                      private val token: String,
                      private val resources: Resources,
                      private val periodFormatter: PeriodFormatter,
                      private val app: App) : LifecycleObserver {

    val isLoading = ObservableBoolean(false)
    val items = ObservableArrayList<BuildItemViewModel>()
    val title: String
        get() = app.title

    private var deferred: Deferred<Any>? = null
    private var nextCursor: String? = null
    private val shouldLoadMoreItems: Boolean
        get() = !isLoading.get() && nextCursor != null

    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    fun onCreate() {
        onRefresh()
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    fun onResume() {
        onRefresh()
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun onDestroy() {
        deferred?.cancel()
    }

    fun onRefresh() {
        deferred?.cancel()
        items.clear()
        nextCursor = null
        loadMoreItems()
    }

    fun onEndOfListReached(itemCount: Int) {
        if(shouldLoadMoreItems) {
            loadMoreItems()
        }
    }

    fun onStartNewBuild() = router.navigateTo(SCREEN_NEW_BUILD)

    private fun loadMoreItems() {
        deferred = async(UI) {
            try {
                isLoading.set(true)
                fetchItems()
                    .forEach { viewModel ->
                        items.add(viewModel)
                    }
            } catch (exception: Exception) {
                Timber.e(exception)
                router.navigateTo(SCREEN_ERROR, exception.message)
            } finally {
                isLoading.set(false)
            }
        }
    }

    private suspend fun fetchItems() =
        service
            .getBuilds(token, app.slug, nextCursor)
            .await()
            .apply { nextCursor = paging.nextCursor }
            .data
            .map { build -> BuildItemViewModel(resources, periodFormatter, router, build) }
}