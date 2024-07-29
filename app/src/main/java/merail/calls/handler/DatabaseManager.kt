package merail.calls.handler

import android.content.Context
import android.util.JsonReader
import androidx.compose.runtime.MutableState
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.HashMap


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

            context.openFileOutput(
                context.getString(R.string.numbers_list_file),
                Context.MODE_PRIVATE
            ).use {
                it.write(numbersMap.toString().toByteArray());

                preferenceHelper.setPreference(
                    context,
                    "saved_db_timestamp_pref",
                    System.currentTimeMillis().toString()
                );

                val numbersMapSize = numbersMap.size
                addedNumbersCount?.value = numbersMapSize

                logger.saveToLog(
                    context,
                    "Loaded $numbersMapSize numbers"
                );
            }
        }

    }
}
