package pl.iterative.call.buster

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.work.ListenableWorker
import java.net.URL
import javax.net.ssl.HttpsURLConnection


class UpdateDatabaseFromUrl {
    companion object {
        private val logger = OperationLogger();
        private val databaseManager = DatabaseManager()
    }

    fun updateDatabaseFromUrl(
        context: Context,
        urlString: String,
        addedNumbersCount: MutableState<Int>?,
        lastUpdateDateFormatted: MutableState<String>? = null
    ): ListenableWorker.Result {
        val url = URL(urlString)
        val uc: HttpsURLConnection = url.openConnection() as HttpsURLConnection
        logger.saveToLog(
            context,
            "Loading numbers from URL $urlString"
        );
        return databaseManager.parseStream(uc.inputStream, context, addedNumbersCount, lastUpdateDateFormatted);
    }
}
