package de.mbur.eex_api.domain

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.ZonedDateTime

@JsonIgnoreProperties(ignoreUnknown = true)
data class Event(
		@JsonProperty("NUMCapacity")
		// @JsonFormat(shape = Shape.STRING, locale = "de_DE", pattern = "#.#")
		var capacity: String,
		@JsonProperty("CompanyID")
		var companyID: String,
		var count: Int,
		@JsonProperty("Country")
		var country: String,
		@JsonProperty("NUMEndDate")
		var endDate: ZonedDateTime,
		@JsonProperty("EventID")
		var eventID: String,
		var id: String?,
		@JsonProperty("ModificationTimeStamp")
		var modification: ZonedDateTime,
		@JsonProperty("PublicationTimeStamp")
		var publication: ZonedDateTime,
		@JsonProperty("NonavailabilityReason")
		var reason: String,
		@JsonProperty("Remarks")
		var remarks: String,
		@JsonProperty("NUMStartDate")
		var startDate: ZonedDateTime,
		@JsonProperty("Status")
		var status: String,
		@JsonProperty("Symbol")
		var symbol: String,
		@JsonProperty("TimeStamp")
		var timeStamp: ZonedDateTime,
		@JsonProperty("Type")
		var type: String,
		@JsonProperty("UnitID")
		var unitID: String,
) {
	companion object {
		val RXID = Regex("^0*+(.+?)#(.+?)_0*+(.+?)$")
	}
	fun calcID() {
		if (id == null) {
			val match = RXID.matchEntire(eventID)
			if (match?.groupValues?.size == 4) {
				id = match.groupValues[1]
				count = match.groupValues[3].toInt()
			}
		}
	}
}
