package pl.iterative.call.buster

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The Room database for this app
 */
@Database(entities = [BlockedNumber::class], version = 1, exportSchema = false)
abstract class BlockedNumbersDatabase : RoomDatabase() {
    abstract fun blockedNumberDao(): BlockedNumberDao

    companion object {

        // For Singleton instantiation
        @Volatile private var instance: BlockedNumbersDatabase? = null

        fun getInstance(context: Context): BlockedNumbersDatabase {
            return instance ?: synchronized(this) {
                instance ?: buildDatabase(context).also { instance = it }
            }
        }

        // Create and pre-populate the database. See this article for more details:
        // https://medium.com/google-developers/7-pro-tips-for-room-fbadea4bfbd1#4785
        private fun buildDatabase(context: Context): BlockedNumbersDatabase {
            return Room.databaseBuilder(context, BlockedNumbersDatabase::class.java, context.getString(R.string.room_db_name))
                .allowMainThreadQueries()
                .build()
        }
    }
}
