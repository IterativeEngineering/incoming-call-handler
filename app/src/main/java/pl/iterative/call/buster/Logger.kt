package pl.iterative.call.buster

import android.content.Context
import java.util.Calendar

class OperationLogger {
    companion object {
        private const val logFileName = "operations_log"
    }

    fun getLog(context: Context): String {
        var log = "";

//        try {
//            context.openFileInput(logFileName).use {
//                log = IOUtils.toString(it, "UTF-8");
//            }
//        } catch (e: Exception) {}

        return log;
    }

    fun saveToLog(context: Context, content: String) {
        // context.openFileOutput(logFileName, Context.MODE_APPEND).use {
        //     it.write(('[' + Calendar.getInstance().time.toString() + "] " + content + "\n").toByteArray());
        // }
    }

    fun clearLog(context: Context) {
        // context.openFileOutput(logFileName, Context.MODE_PRIVATE).use {
        //     it.write(("").toByteArray());
        // }
    }
}
