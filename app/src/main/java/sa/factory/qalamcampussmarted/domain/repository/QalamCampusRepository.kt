package sa.factory.qalamcampussmarted.domain.repository

import sa.factory.qalamcampussmarted.model.QalamCampusItem
import sa.factory.qalamcampussmarted.model.QalamCampusOrder
import sa.factory.qalamcampussmarted.model.QalamCampusTelemetry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface QalamCampusRepository {
    val catalogFlow: Flow<List<QalamCampusItem>>
    val activeCart: StateFlow<List<QalamCampusItem>>
    val telemetryFlow: Flow<QalamCampusTelemetry>
    
    fun addToCart(item: QalamCampusItem)
    fun removeFromCart(item: QalamCampusItem)
    fun clearCart()
    suspend fun checkout(paymentMethod: String): QalamCampusOrder
    suspend fun refreshCatalog()
}
