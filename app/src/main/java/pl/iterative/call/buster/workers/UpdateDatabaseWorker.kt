package pl.iterative.call.buster.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.iterative.call.buster.OperationLogger
import pl.iterative.call.buster.PreferenceHelper
import pl.iterative.call.buster.R
import pl.iterative.call.buster.UpdateDatabaseFromUrl

class UpdateDatabaseWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {
    companion object {
        private val logger = OperationLogger();
        private val preferenceHelper = PreferenceHelper();
        private val databaseUpdater = UpdateDatabaseFromUrl();
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        // Get file's URL from shared preferences
        val fileUrl = preferenceHelper.getPreference(
            applicationContext,
            applicationContext.getString(R.string.shared_preference_file_url)
        );

        logger.saveToLog(applicationContext, "Running automatic db update from $fileUrl")

        return@withContext databaseUpdater.updateDatabaseFromUrl(applicationContext, fileUrl!!, null);
    }
}
