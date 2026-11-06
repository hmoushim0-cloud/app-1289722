package sa.factory.qalamcampussmarted.di

import sa.factory.qalamcampussmarted.data.repository.QalamCampusRepositoryImpl
import sa.factory.qalamcampussmarted.domain.repository.QalamCampusRepository
import sa.factory.qalamcampussmarted.domain.usecase.*
import sa.factory.qalamcampussmarted.hardware.HardwareSensorDriver
import sa.factory.qalamcampussmarted.ui.viewmodel.QalamCampusViewModel

class DiContainer {
    val sensorDriver = HardwareSensorDriver()
    val repository: QalamCampusRepository = QalamCampusRepositoryImpl(sensorDriver)

    val getCatalogUseCase = GetCatalogUseCase(repository)
    val manageCartUseCase = ManageCartUseCase(repository)
    val processCheckoutUseCase = ProcessCheckoutUseCase(repository)
    val observeHardwareTelemetryUseCase = ObserveHardwareTelemetryUseCase(repository)

    fun provideViewModel(): QalamCampusViewModel = QalamCampusViewModel(
        getCatalogUseCase,
        manageCartUseCase,
        processCheckoutUseCase,
        observeHardwareTelemetryUseCase
    )
}
