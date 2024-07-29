package merail.calls.handler

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import com.google.firebase.crashlytics.buildtools.reloc.org.apache.commons.io.IOUtils
import org.json.JSONArray
import org.json.JSONObject

@RequiresApi(Build.VERSION_CODES.N)
class IncomingCallService : CallScreeningService() {

    companion object {
        @SuppressLint("StaticFieldLeak")
        private val incomingCallAlert = IncomingCallAlert()

        private val countryCodesHelper = CountryCodes();
        private lateinit var numbersDb: JSONArray;
        private var loadedDbVersion = "";
        private val preferenceHelper = PreferenceHelper();
        private val logger = OperationLogger();
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        incomingCallAlert.closeWindow()
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onScreenCall(callDetails: Call.Details) {
        if (callDetails.callDirection == Call.Details.DIRECTION_INCOMING) {
            var phoneNumber = callDetails.handle.schemeSpecificPart
            logger.saveToLog(applicationContext, "Incoming call from " + phoneNumber);

            if (!phoneNumber.startsWith("+")) {
//                Add missing country prefix
                val countryCodeValue =
                    (this.getSystemService(TELEPHONY_SERVICE) as TelephonyManager).networkCountryIso
                val countryPrefix = "+" + countryCodesHelper.getCode(countryCodeValue.uppercase())
                phoneNumber = countryPrefix + phoneNumber
            }

            val savedDbVersion =
                preferenceHelper.getPreference(applicationContext, "saved_db_timestamp_pref");

            if (loadedDbVersion !== savedDbVersion) {
                // Let's load the DB first
                try {
                    applicationContext.openFileInput(getString(R.string.numbers_list_file))
                        .use { it ->
                            loadedDbVersion = savedDbVersion!!;
                            val myString: String = IOUtils.toString(it, "UTF-8");
                            val jsonObj = JSONObject(myString);
                            val numbersArray = jsonObj.getJSONArray("numbers")
                            numbersDb = numbersArray

                            handleIncomingCall(phoneNumber, callDetails);
                        }
                } catch (e: Exception) {
                    // Failed to load the DB (it doesn't exist?)
                    // Handle in default dialer
                    respondToCall(callDetails, CallResponse.Builder().build())
                }
            } else {
                handleIncomingCall(phoneNumber, callDetails);
            }
        }
    }

    private fun handleIncomingCall(phoneNumber: String, callDetails: Call.Details) {
        val perfStart = System.currentTimeMillis();

        val numberIsInTheDb = true

        if (numberIsInTheDb) {
            // Load users preferences
            val blockTheCall =
                preferenceHelper.getPreference(
                    applicationContext,
                    "call_blocking_block"
                )
                    .toBoolean()
            val silenceTheCall =
                preferenceHelper.getPreference(
                    applicationContext,
                    "call_blocking_silence"
                )
                    .toBoolean()
            val showInfoWindow =
                preferenceHelper.getPreference(
                    applicationContext,
                    "call_blocking_show_window"
                )
                    .toBoolean()

            if (showInfoWindow) {
                phoneNumber?.let {
                    var infoWindowText = "Warning: " + it;
                    if (true) {
                        infoWindowText += "(name)"
                    }
                    infoWindowText += " is in the blocked numbers database.";
                    incomingCallAlert.showWindow(this, infoWindowText)
                }
            }
            if (blockTheCall || silenceTheCall) {
                var logMessage = "Handling incoming call from " + phoneNumber + ":  ";
                if (blockTheCall) {
                    logMessage += "block "
                }
                if (silenceTheCall) {
                    logMessage += "silence"
                }
                logger.saveToLog(applicationContext, logMessage);
                val response = CallResponse.Builder()
                    // Sets whether the incoming call should be blocked.
                    .setDisallowCall(blockTheCall)
                    // Sets whether the incoming call should be rejected as if the user did so manually.
                    .setRejectCall(true)
                    // Sets whether ringing should be silenced for the incoming call.
                    .setSilenceCall(silenceTheCall)
                    // Sets whether the incoming call should not be displayed in the call log.
                    .setSkipCallLog(false)
                    // Sets whether a missed call notification should not be shown for the incoming call.
                    .setSkipNotification(false)
                    .build()

                respondToCall(callDetails, response)
            } else {
                //                        Handle in default dialer
                logger.saveToLog(applicationContext, "Handling incoming call from " + phoneNumber + " in the default dialer");
                respondToCall(callDetails, CallResponse.Builder().build())
            }
            System.out.println("Handled call from " + phoneNumber + " in " + (System.currentTimeMillis() - perfStart));
        } else {
            //                        Handle in default dialer
            logger.saveToLog(applicationContext, "Handling incoming call from " + phoneNumber + " in the default dialer");
            respondToCall(callDetails, CallResponse.Builder().build())
        }
    }
}