package de.mbur.eex_api

data class APIResultList<T>(
		val results: Array<APIResult<T>>
)
