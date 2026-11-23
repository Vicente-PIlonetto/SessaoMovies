package dev.viko.sessao

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import dev.viko.sessao.data.sync.Notifier
import dev.viko.sessao.data.sync.WorkScheduler

class App : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        dev.viko.sessao.i18n.I18n.init(this)
        container = AppContainer(this)
        Notifier.createChannels(this)
        WorkScheduler.scheduleDaily(this)
    }

    /** O mesmo carregador (com cache em disco) para as telas e para a extração de cor. */
    override fun newImageLoader(context: PlatformContext): ImageLoader = container.images
}
