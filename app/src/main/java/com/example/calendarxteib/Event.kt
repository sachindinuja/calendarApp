//package com.example.calendarxteib
//
//data class Event(
//    val title: String = "",
//    val date: String = "",
//    val startTime: String = "",
//    val endTime: String = "",
//    val note: String = ""
//) {
//    val dateMillis: Any
//        get() {
//            TODO()
//        }
//}


package com.example.calendarxteib

data class Event(
    val id: String,
    val title: String,
    val date: String,
    val startTime: Long,
    val endTime: String,
    val note: String
)



