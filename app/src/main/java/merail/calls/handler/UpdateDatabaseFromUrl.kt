package merail.calls.handler

import android.content.Context
import androidx.compose.runtime.MutableState
import java.net.URL
import javax.net.ssl.HttpsURLConnection


class UpdateDatabaseFromUrl {
    companion object {
        private val logger = OperationLogger();
        private val databaseManager = DatabaseManager()
    }

    fun updateDatabaseFromUrl(context: Context, urlString: String, addedNumbersCount: MutableState<Int>?) {
        val url = URL(urlString)
        val uc: HttpsURLConnection = url.openConnection() as HttpsURLConnection
        logger.saveToLog(
            context,
            "Loading numbers from URL $urlString"
        );
        databaseManager.parseStream(uc.inputStream, context, addedNumbersCount);
    }
}
