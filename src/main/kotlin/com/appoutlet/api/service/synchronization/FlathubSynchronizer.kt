package com.appoutlet.api.service.synchronization

import com.appoutlet.api.model.ApplicationPackageType
import com.appoutlet.api.model.ApplicationStore
import com.appoutlet.api.model.appoutlet.AppOutletApplication
import com.appoutlet.api.model.flathub.FlathubApplicationDetails
import com.appoutlet.api.model.flathub.FlathubCategory
import com.appoutlet.api.model.flathub.FlathubScreenshot
import com.appoutlet.api.repository.appoutlet.ApplicationRepository
import com.appoutlet.api.repository.flathub.FlathubRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono

@Service
class FlathubSynchronizer(
    private val flathubRepository: FlathubRepository,
    private val applicationRepository: ApplicationRepository,
    private val synchronizationProperties: SynchronizationProperties
) : Synchronizer {
	private val logger = LoggerFactory.getLogger(FlathubSynchronizer::class.java)

	init {
		if (!synchronizationProperties.flathub.enabled) {
			logger.warn("Synchronization disabled for Flathub")
		}
	}

	override fun synchronize(): Mono<Boolean> {
		return if (synchronizationProperties.flathub.enabled) {
			startSynchronization()
		} else {
			Mono.just(false)
		}
	}

	private fun startSynchronization(): Mono<Boolean> {
		return flathubRepository.getApps()
			.flatMap { flathubRepository.getApplicationDetails(it.flatpakAppId) }
			.map(this::convertFlathubApplicationToAppOutletApplication)
			.map { applicationRepository.save(it) }
			.buffer()
			.toMono()
			.map { true }
	}

	private fun convertFlathubApplicationToAppOutletApplication(
	    flathubApplication: FlathubApplicationDetails
	): AppOutletApplication {
		return AppOutletApplication(
			id = flathubApplication.flatpakAppId,
			name = flathubApplication.name,
			summary = flathubApplication.summary,
			description = flathubApplication.description,
			developer = flathubApplication.developerName,
			license = flathubApplication.projectLicense,
			homepage = flathubApplication.homepageUrl,
			bugtrackerUrl = flathubApplication.bugtrackerUrl,
			donationUrl = flathubApplication.donationUrl,
			icon = addFlathubContentManagerDomain(flathubApplication.iconDesktopUrl),
			downloadUrl = addFlathubContentManagerDomain(flathubApplication.downloadFlatpakRefUrl),
			version = flathubApplication.currentReleaseVersion,
			lastReleaseDate = flathubApplication.currentReleaseDate,
			creationDate = flathubApplication.inStoreSinceDate,
			tags = extractTags(flathubApplication.categories),
			screenshots = extractScreenshots(flathubApplication.screenshots),
			store = ApplicationStore.FLATHUB,
			packageType = ApplicationPackageType.FLATPAK
		)
	}

	private fun extractTags(categories: List<FlathubCategory>?): List<String> {
		if (categories == null) {
			return emptyList()
		}

		val result = mutableListOf<String>()

		categories.forEach { category ->
			result.add(category.name)
		}

		return result
	}

	private fun extractScreenshots(screenshots: List<FlathubScreenshot>?): List<String> {
		if (screenshots == null) {
			return emptyList()
		}

		val result = mutableListOf<String>()

		screenshots.forEach { screenshot ->
			screenshot.imgDesktopUrl?.let(result::add)
		}

		return result
	}

	private fun addFlathubContentManagerDomain(uri: String?): String? {
		return if (isFlatpakUriValid(uri)) {
			"$FLATHUB_CONTENT_MANAGER_DOMAIN$uri"
		} else {
			logger.warn("Invalid URI {}", uri)
			null
		}
	}

	private fun isFlatpakUriValid(uri: String?): Boolean {
		return (!uri.isNullOrBlank()) &&
			uri.startsWith("/")
	}

	companion object {
		private const val FLATHUB_CONTENT_MANAGER_DOMAIN = "https://dl.flathub.org"
	}
}
