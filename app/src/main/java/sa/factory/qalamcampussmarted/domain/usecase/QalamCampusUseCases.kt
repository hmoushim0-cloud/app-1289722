package sa.factory.qalamcampussmarted.domain.usecase

import sa.factory.qalamcampussmarted.domain.repository.QalamCampusRepository
import sa.factory.qalamcampussmarted.model.QalamCampusItem
import sa.factory.qalamcampussmarted.model.QalamCampusOrder
import sa.factory.qalamcampussmarted.model.QalamCampusTelemetry
import kotlinx.coroutines.flow.Flow

class GetCatalogUseCase(private val repository: QalamCampusRepository) {
    operator fun invoke(): Flow<List<QalamCampusItem>> = repository.catalogFlow
}

class ManageCartUseCase(private val repository: QalamCampusRepository) {
    val cart = repository.activeCart
    fun add(item: QalamCampusItem) = repository.addToCart(item)
    fun remove(item: QalamCampusItem) = repository.removeFromCart(item)
    fun clear() = repository.clearCart()
}

class ProcessCheckoutUseCase(private val repository: QalamCampusRepository) {
    suspend operator fun invoke(paymentMethod: String = "Mada Contactless EMV"): QalamCampusOrder =
        repository.checkout(paymentMethod)
}

class ObserveHardwareTelemetryUseCase(private val repository: QalamCampusRepository) {
    operator fun invoke(): Flow<QalamCampusTelemetry> = repository.telemetryFlow
}
