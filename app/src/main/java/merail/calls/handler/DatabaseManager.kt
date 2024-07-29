package merail.calls.handler

import android.R.attr.text
import android.R.id
import android.content.Context
import android.util.JsonReader
import android.util.JsonToken
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader


class DatabaseManager {
    companion object {
        private val logger = OperationLogger();
    }

    fun parseStream(inputStream: InputStream, context: Context) {
        // Parses a JSON stream, converts it to our structure and saves into a file
        // our structure looks as follows:
        // [ { "phone": "123", "name": "xyz" }, { "phone": "456", "name": "abc" }, ... ]

        BufferedReader(InputStreamReader(inputStream)).use { reader ->
            val numbersArray: MutableList<JSONObject> = ArrayList();
            System.out.println("numbersArray size " + numbersArray.size);

            val jsonReader = JsonReader(reader);
            jsonReader.beginArray();

            val perfStart = System.currentTimeMillis()

            while (jsonReader.hasNext()) {
                jsonReader.beginObject();

                val blockedEntity = JSONObject();
                var blockedEntityAgentName = "";
                var blockedEntityAgencyName = "";

                while (jsonReader.hasNext()) {
                    val key = jsonReader.nextName()
                    val nextString = jsonReader.nextString();
                    System.out.println("key" + key + "nextString" + nextString);
                    if (key === "phone") {
                        blockedEntity.put("phone", nextString);
                    } else if (key === "agent_name") {
                        blockedEntityAgentName = nextString
                    } else if (key === "agency_name") {
                        blockedEntityAgencyName = nextString
                    } else {
                        jsonReader.skipValue()
                    }
                }

                if (blockedEntityAgencyName.length > 0) {
                    blockedEntityAgentName += " (" + blockedEntityAgencyName + ")";
                }
                blockedEntity.put("name", blockedEntityAgentName);
                numbersArray.add(blockedEntity);
                if (numbersArray.size === 1) {
                    System.out.println(numbersArray);
                }

                jsonReader.endObject();
            }

            jsonReader.endArray();

            System.out.println("finished in " + (System.currentTimeMillis() - perfStart))

            System.out.println("numbersArray size " + numbersArray.size);


//                    var line: String? = reader.readLine()
//                    while (line != null) {
//                        stringBuilder.append(line)
//                        line = reader.readLine()
//                    }

            context.openFileOutput(context.getString(R.string.numbers_list_file), Context.MODE_PRIVATE).use {
//                it.write(stringBuilder.toString().toByteArray());
//                text =
//                    text + " finished in " + (System.currentTimeMillis() - performanceMarkStart) + "ms, got " + numbersArrayLen + " numbers"
//                System.out.println(" finished in " + (System.currentTimeMillis() - performanceMarkStart) + "ms, got " + numbersArrayLen + " numbers");
            }

//            logger.saveToLog(
//                context,
//                "Loaded " + numbersArrayLen + " numbers from " + uri
//            );
        }

    }
}
