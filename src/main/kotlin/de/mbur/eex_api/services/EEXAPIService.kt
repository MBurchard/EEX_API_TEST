package de.mbur.eex_api.services

import de.mbur.eex_api.APIResultList
import de.mbur.eex_api.AppConfig
import de.mbur.eex_api.domain.Event
import mu.KotlinLogging
import org.springframework.context.annotation.Lazy
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpMethod.GET
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpServerErrorException.ServiceUnavailable
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.Temporal

private val log = KotlinLogging.logger {}

@Service
class EEXAPIService(@Lazy private val appConfig: AppConfig,
										@Lazy private val restTemplate: RestTemplate) {

	fun getEvents(companyID: String, dtStart: Temporal, dtEnd: Temporal): Collection<Event> {
		try {
			val startTS = System.currentTimeMillis()
			val url = UriComponentsBuilder.fromPath(appConfig.eventPath)
					.queryParam("Symbol", "NonUsabilityGenerationPower")
					.queryParam("CompanyID", companyID) // PreussenElektra EON_EnergyDE
					.queryParam("Update_Start", formatDateTime(dtStart, "yyyy-MM-dd HH:mm"))
					.queryParam("Update_End", formatDateTime(dtEnd, "yyyy-MM-dd HH:mm"))
					.build().toUriString()
			log.debug { "URL: $url" }

			val response = retryRun({
				restTemplate.exchange(url, GET, null,
						object : ParameterizedTypeReference<APIResultList<Event>>() {})
			}, 5, 15)

			val events = response.body?.results?.get(0)?.result
			val ms = System.currentTimeMillis() - startTS
			log.debug {
				"Request for events for $companyID " +
						"from ${formatDateTime(dtStart, "dd.MM.yyyy HH:mm")} " +
						"to ${formatDateTime(dtEnd, "dd.MM.yyyy HH:mm")} took ${ms}ms"
			}
			log.debug { "Got ${events?.size} events" }
			if (events != null) {
				return events.toList()
			}
		} catch (th: Throwable) {
			log.error(th) { "get events failed" }
		}
		return emptyList()
	}

}

fun formatDateTime(dt: Temporal, pattern: String): String {
	return DateTimeFormatter.ofPattern(pattern).format(dt)
}

fun formatToLocalTime(dt: ZonedDateTime, pattern: String): String {
	return formatDateTime(dt.withZoneSameInstant(ZoneId.of("Europe/Berlin")), pattern)
}

fun <T> retryRun(action: () -> T, retryCount: Int, timout: Long): T {
	var count = 1
	do {
		try {
			return action()
		} catch (ex: ServiceUnavailable) {
			log.error { "Service is unavailable ${ex.message}" }
		}
		count++
		log.debug { "Retry $count of $retryCount in $timout seconds" }
		Thread.sleep(timout * 1000)
	} while (count <= retryCount)
	throw RuntimeException("The Action has not been completed normally")
}
