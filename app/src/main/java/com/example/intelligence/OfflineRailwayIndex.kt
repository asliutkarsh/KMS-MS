package com.example.intelligence

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class RailwayStation(
    val code: String,
    val name: String,
    val zone: String,
    val latitude: Double,
    val longitude: Double
)

data class OfflineTrainPosition(
    val nearestStation: RailwayStation,
    val distanceKm: Double,
    val nextNearestStation: RailwayStation,
    val distanceToNextKm: Double,
    val speedKmh: Float,
    val latitude: Double,
    val longitude: Double,
    val timestampMillis: Long
)

/**
 * Bundled 100% offline Indian Railways major junction index for GPS-only live train tracking
 * without requiring cellular data or internet connectivity.
 */
object OfflineRailwayIndex {

    val stations: List<RailwayStation> = listOf(
        RailwayStation("NDLS", "New Delhi", "NR", 28.6429, 77.2191),
        RailwayStation("NZM", "Hazrat Nizamuddin", "NR", 28.5880, 77.2530),
        RailwayStation("ANVT", "Anand Vihar Terminal", "NR", 28.6469, 77.3160),
        RailwayStation("CSMT", "Mumbai CSMT", "CR", 18.9398, 72.8355),
        RailwayStation("MMCT", "Mumbai Central", "WR", 18.9712, 72.8194),
        RailwayStation("LTT", "Lokmanya Tilak Terminus", "CR", 19.0695, 72.8919),
        RailwayStation("PUNE", "Pune Junction", "CR", 18.5289, 73.8744),
        RailwayStation("HWH", "Howrah Junction", "ER", 22.5839, 88.3426),
        RailwayStation("SDAH", "Sealdah", "ER", 22.5676, 88.3705),
        RailwayStation("MAS", "MGR Chennai Central", "SR", 13.0827, 80.2757),
        RailwayStation("MS", "Chennai Egmore", "SR", 13.0782, 80.2610),
        RailwayStation("SBC", "KSR Bengaluru", "SWR", 12.9781, 77.5697),
        RailwayStation("YPR", "Yesvantpur Junction", "SWR", 13.0238, 77.5504),
        RailwayStation("SC", "Secunderabad Junction", "SCR", 17.4344, 78.5013),
        RailwayStation("HYB", "Hyderabad Deccan", "SCR", 17.3924, 78.4673),
        RailwayStation("ADI", "Ahmedabad Junction", "WR", 23.0268, 72.6012),
        RailwayStation("BRC", "Vadodara Junction", "WR", 22.3107, 73.1810),
        RailwayStation("ST", "Surat", "WR", 21.2049, 72.8407),
        RailwayStation("JP", "Jaipur Junction", "NWR", 26.9196, 75.7878),
        RailwayStation("JU", "Jodhpur Junction", "NWR", 26.2838, 73.0227),
        RailwayStation("LKO", "Lucknow Charbagh", "NR", 26.8318, 80.9191),
        RailwayStation("CNB", "Kanpur Central", "NCR", 26.4539, 80.3512),
        RailwayStation("PRYJ", "Prayagraj Junction", "NCR", 25.4448, 81.8299),
        RailwayStation("BSB", "Varanasi Junction", "NR", 25.3268, 82.9866),
        RailwayStation("PNBE", "Patna Junction", "ECR", 25.6022, 85.1376),
        RailwayStation("DDU", "Pt. DD Upadhyaya Jn", "ECR", 25.2804, 83.1190),
        RailwayStation("AGC", "Agra Cantt", "NCR", 27.1578, 77.9897),
        RailwayStation("GWL", "Gwalior Junction", "NCR", 26.2156, 78.1828),
        RailwayStation("VGLJ", "Virangana Lakshmibai Jhansi", "NCR", 25.4416, 78.5567),
        RailwayStation("BPL", "Bhopal Junction", "WCR", 23.2678, 77.4134),
        RailwayStation("RKMP", "Rani Kamalapati", "WCR", 23.2206, 77.4395),
        RailwayStation("ET", "Itarsi Junction", "WCR", 22.6104, 77.7676),
        RailwayStation("NGP", "Nagpur Junction", "CR", 21.1522, 79.0889),
        RailwayStation("BZA", "Vijayawada Junction", "SCR", 16.5186, 80.6198),
        RailwayStation("VSKP", "Visakhapatnam", "ECoR", 17.7222, 83.2896),
        RailwayStation("BBS", "Bhubaneswar", "ECoR", 20.2665, 85.8435),
        RailwayStation("KGP", "Kharagpur Junction", "SER", 22.3396, 87.3250),
        RailwayStation("TATA", "Tatanagar Junction", "SER", 22.7689, 86.1976),
        RailwayStation("R", "Raipur Junction", "SECR", 21.2571, 81.6296),
        RailwayStation("BSP", "Bilaspur Junction", "SECR", 22.0622, 82.1695),
        RailwayStation("GHY", "Guwahati", "NFR", 26.1823, 91.7507),
        RailwayStation("NJP", "New Jalpaiguri", "NFR", 26.6852, 88.4426),
        RailwayStation("ASR", "Amritsar Junction", "NR", 31.6331, 74.8673),
        RailwayStation("CDG", "Chandigarh", "NR", 30.7016, 76.8213),
        RailwayStation("JAT", "Jammu Tawi", "NR", 32.7063, 74.8804),
        RailwayStation("KOTA", "Kota Junction", "WCR", 25.2238, 75.8819),
        RailwayStation("ERS", "Ernakulam Junction", "SR", 9.9696, 76.2908),
        RailwayStation("TVC", "Thiruvananthapuram Central", "SR", 8.4875, 76.9525),
        RailwayStation("CBE", "Coimbatore Junction", "SR", 10.9968, 76.9669),
        RailwayStation("MDU", "Madurai Junction", "SR", 9.9195, 78.1095)
    )

    private val stationCodeSet = stations.map { it.code }.toSet()

    fun detectStationCodeInText(text: String): String? {
        val tokens = Regex("\\b[A-Z]{2,5}\\b").findAll(text).map { it.value }.toList()
        return tokens.firstOrNull { it in stationCodeSet }
    }

    fun resolveOfflinePosition(
        latitude: Double,
        longitude: Double,
        speedMetersPerSecond: Float,
        timestampMillis: Long = System.currentTimeMillis()
    ): OfflineTrainPosition {
        val ranked = stations
            .map { station -> station to haversineKm(latitude, longitude, station.latitude, station.longitude) }
            .sortedBy { it.second }

        val first = ranked[0]
        val second = ranked[1]

        return OfflineTrainPosition(
            nearestStation = first.first,
            distanceKm = first.second,
            nextNearestStation = second.first,
            distanceToNextKm = second.second,
            speedKmh = (speedMetersPerSecond * 3.6f).coerceAtLeast(0f),
            latitude = latitude,
            longitude = longitude,
            timestampMillis = timestampMillis
        )
    }

    fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadiusKm = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadiusKm * c
    }

    fun formatShareText(trainLabel: String, pnr: String?, position: OfflineTrainPosition): String {
        val distStr = String.format("%.1f", position.distanceKm)
        val speedStr = String.format("%.0f", position.speedKmh)
        return buildString {
            append("Live Train Update (Offline GPS via KMS-MS)\n")
            append("Train: $trainLabel\n")
            if (!pnr.isNullOrBlank()) append("PNR: $pnr\n")
            append("Nearest Station: ${position.nearestStation.name} (${position.nearestStation.code}) — $distStr km away\n")
            append("Next Major Hub: ${position.nextNearestStation.name} (${position.nextNearestStation.code})\n")
            append("Estimated Speed: $speedStr km/h\n")
            append("GPS: ${String.format("%.4f", position.latitude)}, ${String.format("%.4f", position.longitude)}")
        }
    }
}
