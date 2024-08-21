package pl.iterative.call.buster

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(
    tableName = "blocked_numbers"
)
data class BlockedNumber(
    @PrimaryKey(autoGenerate = false) val phone: String,
    @ColumnInfo(name = "name") var name: String?
)

@Dao
interface BlockedNumberDao {
    @Query("SELECT * FROM blocked_numbers WHERE phone = (:number)")
    fun getByNumber(number: String): BlockedNumber?;

    @Query("SELECT COUNT(phone) from blocked_numbers")
    fun getSavedNumbersCount(): Int;

    @Insert
    fun insertAll(vararg numbers: BlockedNumber)

    @Insert
    fun insert(number: BlockedNumber)

    @Query("DELETE FROM blocked_numbers")
    fun nukeTable()
}