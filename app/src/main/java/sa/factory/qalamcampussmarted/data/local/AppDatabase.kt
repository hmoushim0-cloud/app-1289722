package sa.factory.qalamcampussmarted.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [QalamCampusEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun smart_educationDao(): QalamCampusDao
}
