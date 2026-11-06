package sa.factory.qalamcampussmarted.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import sa.factory.qalamcampussmarted.domain.usecase.*
import sa.factory.qalamcampussmarted.model.QalamCampusItem
import sa.factory.qalamcampussmarted.model.QalamCampusOrder
import sa.factory.qalamcampussmarted.model.QalamCampusTelemetry
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class QalamCampusUiState(
    val catalog: List<QalamCampusItem> = emptyList(),
    val filteredCatalog: List<QalamCampusItem> = emptyList(),
    val selectedCategory: String = "الكل",
    val cart: List<QalamCampusItem> = emptyList(),
    val telemetry: QalamCampusTelemetry? = null,
    val lastOrder: QalamCampusOrder? = null,
    val isCheckingOut: Boolean = false,
    val activeTab: String = "home"
)

class QalamCampusViewModel(
    private val getCatalogUseCase: GetCatalogUseCase,
    private val manageCartUseCase: ManageCartUseCase,
    private val processCheckoutUseCase: ProcessCheckoutUseCase,
    private val observeHardwareTelemetryUseCase: ObserveHardwareTelemetryUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(QalamCampusUiState())
    val uiState: StateFlow<QalamCampusUiState> = _uiState.asStateFlow()

    init {
        // Observe Catalog
        getCatalogUseCase().onEach { list ->
            _uiState.update { it.copy(catalog = list, filteredCatalog = list) }
        }.launchIn(viewModelScope)

        // Observe Cart
        manageCartUseCase.cart.onEach { cartItems ->
            _uiState.update { it.copy(cart = cartItems) }
        }.launchIn(viewModelScope)

        // Observe Telemetry
        observeHardwareTelemetryUseCase().onEach { telem ->
            _uiState.update { it.copy(telemetry = telem) }
        }.launchIn(viewModelScope)
    }

    fun selectCategory(category: String) {
        _uiState.update { state ->
            val filtered = if (category == "الكل") state.catalog else state.catalog.filter { it.category == category }
            state.copy(selectedCategory = category, filteredCatalog = filtered)
        }
    }

    fun switchTab(tab: String) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun addToCart(item: QalamCampusItem) = manageCartUseCase.add(item)
    fun removeFromCart(item: QalamCampusItem) = manageCartUseCase.remove(item)

    fun checkout() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingOut = true) }
            val order = processCheckoutUseCase()
            _uiState.update { it.copy(isCheckingOut = false, lastOrder = order, activeTab = "checkout") }
        }
    }
}
