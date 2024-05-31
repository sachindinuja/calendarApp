package com.example.calendarxteib

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.CalendarView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.*
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var calendarView: CalendarView
    private lateinit var eventsRef: DatabaseReference
    private lateinit var alarmManager: AlarmManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize views
        calendarView = findViewById(R.id.calendarView)

        // Initialize Firebase database reference
        eventsRef = FirebaseDatabase.getInstance().getReference("events")

        // Initialize AlarmManager
        alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Set up event listener to mark flagged dates on the calendar
        eventsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                // Clear previous marks
                // Note: Use a custom calendar view to support marking dates
                for (eventSnapshot in dataSnapshot.children) {
                    val event = eventSnapshot.getValue(Event::class.java)
                    if (event != null) {
                        // Mark the event date on the calendar with a red dot or any other way supported by the custom calendar view
                    }
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Toast.makeText(this@MainActivity, "Failed to read events", Toast.LENGTH_SHORT).show()
            }
        })

        // Set click listener for marked dates
        calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val selectedDate = "$year-${month + 1}-$dayOfMonth"
            showEventDetails(selectedDate)
        }
    }

    private fun showEventDetails(date: String) {
        val eventsForDate = mutableListOf<Event>()

        // Fetch events from Firebase Database for the selected date
        eventsRef.orderByChild("date").equalTo(date).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                for (eventSnapshot in dataSnapshot.children) {
                    val event = eventSnapshot.getValue(Event::class.java)
                    event?.let {
                        eventsForDate.add(it)
                    }
                }
                if (eventsForDate.isNotEmpty()) {
                    // Display event details and show the notification dialog
                    val eventDetailsStringBuilder = StringBuilder()
                    for (event in eventsForDate) {
                        eventDetailsStringBuilder.append("Title: ${event.title}\n")
                        eventDetailsStringBuilder.append("Start Time: ${event.startTime}\n")
                        eventDetailsStringBuilder.append("End Time: ${event.endTime}\n")
                        eventDetailsStringBuilder.append("Note: ${event.note}\n\n")
                    }
                    val eventDetails = eventDetailsStringBuilder.toString()
                    Toast.makeText(this@MainActivity, eventDetails, Toast.LENGTH_LONG).show()

                    // Show the notification dialog
                    showNotificationDialog(eventsForDate[0])
                } else {
                    Toast.makeText(this@MainActivity, "No events found for this date", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Toast.makeText(this@MainActivity, "Failed to read events", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun showNotificationDialog(event: Event) {
        val options = arrayOf("15 minutes before", "1 hour before", "1 day before", "Custom")
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Set Notification Time")
            .setItems(options) { _, which ->
                val calendar = Calendar.getInstance()
                when (which) {
                    0 -> calendar.timeInMillis = event.startTime - 15 * 60 * 1000
                    1 -> calendar.timeInMillis = event.startTime - 60 * 60 * 1000
                    2 -> calendar.timeInMillis = event.startTime - 24 * 60 * 60 * 1000
                    3 -> showDateTimePicker(event.startTime)
                }
                if (which != 3) {
                    setNotification(calendar.timeInMillis, event.title)
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
        builder.create().show()
    }

    private fun showDateTimePicker(eventTime: Long) {
        val currentDate = Calendar.getInstance()
        DatePickerDialog(this, { _, year, month, dayOfMonth ->
            TimePickerDialog(this, { _, hourOfDay, minute ->
                val selectedDateTime = Calendar.getInstance()
                selectedDateTime.set(year, month, dayOfMonth, hourOfDay, minute)
                setNotification(selectedDateTime.timeInMillis, "Custom Event")
            }, currentDate.get(Calendar.HOUR_OF_DAY), currentDate.get(Calendar.MINUTE), true).show()
        }, currentDate.get(Calendar.YEAR), currentDate.get(Calendar.MONTH), currentDate.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun setNotification(notificationTime: Long, eventTitle: String) {
        val notificationIntent = Intent(this, NotificationReceiver::class.java).apply {
            putExtra("eventTitle", eventTitle)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, notificationTime, pendingIntent)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, notificationTime, pendingIntent)
        }
    }
}
