package sa.factory.qalamcampussmarted.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface QalamCampusDao {
    @Query("SELECT * FROM smart_education_items_cache ORDER BY id ASC")
    fun observeAllItems(): Flow<List<QalamCampusEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<QalamCampusEntity>)

    @Query("DELETE FROM smart_education_items_cache")
    suspend fun clearAll()
}
