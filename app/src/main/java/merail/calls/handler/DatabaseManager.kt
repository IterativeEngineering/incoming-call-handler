package merail.calls.handler

import android.content.Context
import android.util.JsonReader
import androidx.compose.runtime.MutableState
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader


class DatabaseManager {
    companion object {
        private val logger = OperationLogger();
        private val preferenceHelper = PreferenceHelper();
    }

    fun parseStream(inputStream: InputStream, context: Context, addedNumbersCount: MutableState<Int>?) {
        // Parses a JSON stream and saves into a file.
        // The structure remains the same, e.g.: [ { "phone": "123", "name": "xyz" }, { "phone": "456", "name": "abc" }, ... ]
        // but the name is joined for multiple numbers in case there are duplicates.
        BufferedReader(InputStreamReader(inputStream)).use { reader ->
            val numbersMap = HashMap<String, String>(150000);

            val jsonReader = JsonReader(reader);
            jsonReader.beginArray();

            try {
                while (jsonReader.hasNext()) {
                    jsonReader.beginObject();

                    var phone = "";
                    var name = "";

                    while (jsonReader.hasNext()) {
                        val key = jsonReader.nextName();
                        val nextString = jsonReader.nextString();

                        if (key == "phone") {
                            phone = nextString;
                        } else if (key == "name") {
                            name = nextString;
                        }
                    }
                    jsonReader.endObject();

                    val existingBlockedEntityName = numbersMap[phone]
                    if (existingBlockedEntityName !== null) {
                        numbersMap[phone] = "$existingBlockedEntityName | $name";
                    } else {
                        numbersMap[phone] = name;
                    }
                }

                jsonReader.endArray();

                // Insert numbers to DB for persistent storage
                val database = BlockedNumbersDatabase.getInstance(context);
                val dao = database.blockedNumberDao();

                // Remove previous numbers
                dao.nukeTable();

                val numbersList = numbersMap.map { it -> BlockedNumber(phone = it.key, name = it.value) }

                val typedArray = numbersList.toTypedArray()

                val perfStart = System.currentTimeMillis();

                dao.insertAll(*typedArray);

                addedNumbersCount?.value = dao.getSavedNumbersCount();

                preferenceHelper.setPreference(context, "db_update_timestamp", System.currentTimeMillis().toString());

                println("finished in " + (System.currentTimeMillis() - perfStart) + " " + dao.getSavedNumbersCount());
            } catch (exception: Exception) {
                // Exception may be thrown when JSON is invalid
            }
        }
    }
}
