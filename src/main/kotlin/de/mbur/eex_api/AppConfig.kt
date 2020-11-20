package de.mbur.eex_api

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(AppConfig.PREFIX)
data class AppConfig(
		var baseUrl: String = "",
		var eventPath: String = "",
		var password: String = "",
		var username: String = "",
		var csvExportPath: String = ""
) {
	companion object {
		const val PREFIX = "eex-api"
	}
}
