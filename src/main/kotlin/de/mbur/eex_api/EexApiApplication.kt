package de.mbur.eex_api

import de.mbur.eex_api.domain.Event
import de.mbur.eex_api.services.EEXAPIService
import de.mbur.eex_api.services.formatDateTime
import de.mbur.eex_api.services.formatToLocalTime
import mu.KotlinLogging
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Lazy
import org.springframework.web.client.RestTemplate
import java.io.Writer
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

private val log = KotlinLogging.logger {}

@SpringBootApplication
class EexApiApplication(@Lazy private val appConfig: AppConfig,
												@Lazy private val eexapiService: EEXAPIService) : CommandLineRunner {

	@Bean
	fun restTemplate(builder: RestTemplateBuilder): RestTemplate {
		return builder
				.rootUri(appConfig.baseUrl)
				.basicAuthentication(appConfig.username, appConfig.password)
				.setConnectTimeout(Duration.ofSeconds(10))
				.setReadTimeout(Duration.ofSeconds(60))
				.build()
	}

	override fun run(vararg args: String?) {
		try {
			val tsStart = System.currentTimeMillis()
			val statistics = ArrayList<String>()

			// PreussenElektra EON_EnergyDE
			val companyID = "EON_EnergyDE"
			val dtStart = ZonedDateTime.parse("2014-01-01T00:00:00Z")
			val dtEnd = ZonedDateTime.now(ZoneId.of("UTC"))
//			val dtEnd = ZonedDateTime.parse("2020-11-19T00:00:00Z")

			statistics.add("${formatDateTime(LocalDateTime.now(), "yyyy-MM-dd HH:mm:ss.SSS")}: " +
					"Start export of $companyID from ${formatDateTime(dtStart, "dd.MM.yyyy HH:mm:ss")} " +
					"to ${formatDateTime(dtEnd, "dd.MM.yyyy HH:mm:ss")}")

			val allEvents = ArrayList<Event>()
			var currentStart = dtStart
			var currentEnd = minOf(currentStart.plusYears(1), dtEnd)
			while (ChronoUnit.SECONDS.between(currentStart, dtEnd) > 1) {
				val tsWhileStart = System.currentTimeMillis()
				val events = eexapiService.getEvents(companyID, currentStart, currentEnd)
				allEvents.addAll(events)

				val ms = System.currentTimeMillis() - tsWhileStart
				statistics.add("${formatDateTime(LocalDateTime.now(), "yyyy-MM-dd HH:mm:ss.SSS")}: " +
						"Found ${events.size} from ${formatDateTime(currentStart, "dd.MM.yyyy HH:mm:ss")} " +
						"to ${formatDateTime(currentEnd, "dd.MM.yyyy HH:mm:ss")} in ${ms}ms")

				currentStart = currentEnd
				currentEnd = minOf(currentStart.plusYears(1), dtEnd)
			}

			statistics.add("${formatDateTime(LocalDateTime.now(), "yyyy-MM-dd HH:mm:ss.SSS")}: " +
					"Loading of ${allEvents.size} from ${formatDateTime(dtStart, "dd.MM.yyyy HH:mm:ss")} " +
					"to ${formatDateTime(dtEnd, "dd.MM.yyyy HH:mm:ss")} " +
					"took ${System.currentTimeMillis() - tsStart}ms")

			groupToMainEvents(allEvents, statistics)

			val now = LocalDateTime.now()

			val startTS = System.currentTimeMillis()
			val writer = prepareFile(appConfig.csvExportPath,
					formatDateTime(now, "yyyy-MM-dd_HH-mm-ss"), "export.csv")
			val headers = arrayOf("EventID", "Count", "UnitID", "Country", "CompanyID", "Timestamp",
					"Modification", "Publication", "Startdate", "Enddate", "Capacity", "Status")
			val csv = CSVPrinter(writer, CSVFormat.DEFAULT.withDelimiter(';').withHeader(*headers))
			try {
				log.debug { "Starting CSV Export of ${allEvents.size} events" }
				allEvents.forEach {
					csv.printRecord(
							it.id,
							it.count,
							it.unitID,
							it.country,
							it.companyID,
							formatToLocalTime(it.timeStamp, "dd.MM.yyyy HH:mm:ss"),
							formatToLocalTime(it.modification, "dd.MM.yyyy HH:mm:ss"),
							formatToLocalTime(it.publication, "dd.MM.yyyy HH:mm:ss"),
							formatToLocalTime(it.startDate, "dd.MM.yyyy HH:mm:ss"),
							formatToLocalTime(it.endDate, "dd.MM.yyyy HH:mm:ss"),
							it.capacity,
							it.status
					)
				}
				val ms = System.currentTimeMillis() - startTS
				log.debug { "Export took ${ms}ms" }
			} finally {
				csv.close(true)
				writer.close()
			}
			val statisticsWriter = prepareFile(appConfig.csvExportPath,
					formatDateTime(now, "yyyy-MM-dd_HH-mm-ss"), "statistics.log")
			statisticsWriter.use { sw ->
				writeStatistics(sw, statistics)
			}
		} catch (th: Throwable) {
			log.error(th) { "Something has gone terribly wrong" }
		}
	}
}

fun groupToMainEvents(allEvents: ArrayList<Event>, statistics: MutableList<String>) {
	val eventsByID: MutableMap<String, MutableList<Event>> = HashMap()
	allEvents.forEach { event ->
		event.calcID()
		val id = event.id!!
		if (!eventsByID.containsKey(id)) {
			eventsByID[id] = mutableListOf()
		}
		eventsByID[id]!!.add(event)
	}
	eventsByID.forEach { entry ->
		entry.value.sortByDescending { it.count }
		var lastCount = -1
		entry.value.forEach { event ->
			when {
				lastCount == -1 -> {
					lastCount = event.count
				}
				event.count == lastCount -> {
					log.warn { "The Event ${event.id} has more than one version ${event.count}" }
					statistics.add("${formatDateTime(LocalDateTime.now(), "yyyy-MM-dd HH:mm:ss.SSS")}: " +
							"The Event ${event.id} has more than one version ${event.count}")
				}
				event.count != lastCount - 1 -> {
					log.warn {
						"The Event ${event.id} has ${lastCount - event.count - 1} missing version(s) " +
								"between $lastCount and ${event.count}"
					}
					statistics.add("${formatDateTime(LocalDateTime.now(), "yyyy-MM-dd HH:mm:ss.SSS")}: " +
							"The Event ${event.id} has ${lastCount - event.count - 1} missing version(s) " +
							"between $lastCount and ${event.count}")
				}
			}
			lastCount = event.count
		}
	}
}

fun main(args: Array<String>) {
	runApplication<EexApiApplication>(*args)
}

fun prepareFile(path: String, subDir: String, fileName: String): Writer {
	val exportPath = Paths.get(path, subDir)
	Files.createDirectories(exportPath)
	val filePath = Paths.get(exportPath.toString(), fileName)
	return Files.newBufferedWriter(filePath, Charset.forName("UTF-8"))
}

fun writeStatistics(writer: Writer, statistics: List<String>) {
	statistics.forEach {
		writer.write("$it\n")
		writer.flush()
	}
}
