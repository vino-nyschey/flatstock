package app.flatstock

import android.app.Application
import app.flatstock.data.Repository
import app.flatstock.work.Notifications
import app.flatstock.work.ReminderWorker
import com.google.firebase.FirebaseApp

class FlatStockApp : Application() {
    /** Null when the build has no google-services.json; the app then shows how to set it up. */
    var repository: Repository? = null
        private set

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        if (FirebaseApp.getApps(this).isEmpty()) return
        repository = Repository()
        ReminderWorker.schedule(this)
    }
}
