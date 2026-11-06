package sa.factory.qalamcampussmarted.data.remote

import sa.factory.qalamcampussmarted.model.QalamCampusItem
import sa.factory.qalamcampussmarted.model.QalamCampusOrder

interface QalamCampusApiService {
    suspend fun fetchCatalog(): List<QalamCampusItem>
    suspend fun submitOrder(order: QalamCampusOrder): Boolean
}
