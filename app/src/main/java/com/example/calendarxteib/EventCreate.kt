import android.app.AlarmManager
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.calendarxteib.Event
import com.example.calendarxteib.NotificationReceiver
import com.example.calendarxteib.R
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.*

class EventCreate : AppCompatActivity() {
    private lateinit var titleEditText: EditText
    private lateinit var dateEditText: EditText
    private lateinit var startTimeEditText: EditText
    private lateinit var endTimeEditText: EditText
    private lateinit var noteEditText: EditText
    private lateinit var saveButton: Button
    private lateinit var eventsRef: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_event_create)

        // Initialize views
        titleEditText = findViewById(R.id.eventTitleEditText)
        dateEditText = findViewById(R.id.eventDateEditText)
        startTimeEditText = findViewById(R.id.startTimeEditText)
        endTimeEditText = findViewById(R.id.endTimeEditText)
        noteEditText = findViewById(R.id.noteEditText)
        saveButton = findViewById(R.id.saveButton)

        // Initialize Firebase database reference
        eventsRef = FirebaseDatabase.getInstance().getReference("events")

        // Set up date picker dialog
        dateEditText.setOnClickListener {
            showDatePickerDialog()
        }

        // Set up time picker dialog for start time
        startTimeEditText.setOnClickListener {
            showTimePickerDialog(startTimeEditText)
        }

        // Set up time picker dialog for end time
        endTimeEditText.setOnClickListener {
            showTimePickerDialog(endTimeEditText)
        }

        // Save event to Firebase database
        saveButton.setOnClickListener {
            saveEventToDatabase()
        }
    }

    private fun showDatePickerDialog() {
        val calendar = Calendar.getInstance()
        val datePickerDialog = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val selectedDate = "$year-${month + 1}-$dayOfMonth"
                dateEditText.setText(selectedDate)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.show()
    }

    private fun showTimePickerDialog(editText: EditText) {
        val calendar = Calendar.getInstance()
        val timePickerDialog = TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                val selectedTime = String.format("%02d:%02d", hourOfDay, minute)
                editText.setText(selectedTime)
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        )
        timePickerDialog.show()
    }

    private fun saveEventToDatabase() {
        val title = titleEditText.text.toString().trim()
        val date = dateEditText.text.toString().trim()
        val startTimeStr = startTimeEditText.text.toString().trim()
        val endTimeStr = endTimeEditText.text.toString().trim()
        val note = noteEditText.text.toString().trim()

        if (title.isNotEmpty() && date.isNotEmpty() && startTimeStr.isNotEmpty() && endTimeStr.isNotEmpty()) {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val startDate = dateFormat.parse("$date $startTimeStr")
            val endDate = dateFormat.parse("$date $endTimeStr")

            if (startDate != null && endDate != null) {
                val startTime = startDate.time
                val endTime = endDate.time

                val event = com.google.firebase.database.core.view.Event(
                    title,
                    date,
                    startTime,
                    endTime,
                    note
                )

                val eventId = eventsRef.push().key
                if (eventId != null) {
                    eventsRef.child(eventId).setValue(event).addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Toast.makeText(this, "Event created successfully", Toast.LENGTH_SHORT).show()
                            scheduleNotifications(event, startTime)
                            finish() // Close activity after saving event
                        } else {
                            Toast.makeText(this, "Failed to create event", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } else {
                Toast.makeText(this, "Invalid date or time format", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "All fields are required", Toast.LENGTH_SHORT).show()
        }
    }

    private fun scheduleNotifications(event: com.google.firebase.events.Event<String>, startTime: Long) {
        val notificationOptions = arrayOf("15 mins before", "1 hour before", "1 day before", "Custom time")
        val alertDialog = AlertDialog.Builder(this)
        alertDialog.setTitle("Set Notification Time")
        alertDialog.setItems(notificationOptions) { _, which ->
            when (which) {
                0 -> setNotification(event, startTime - 15 * 60 * 1000)
                1 -> setNotification(event, startTime - 60 * 60 * 1000)
                2 -> setNotification(event, startTime - 24 * 60 * 60 * 1000)
                3 -> showDateTimePicker(startTime)
            }
        }
        alertDialog.show()
    }



    private fun setNotification(event: Event, notificationTime: Long) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, NotificationReceiver::class.java).apply {
            putExtra("eventId", event.id)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setExact(AlarmManager.RTC_WAKEUP, notificationTime, pendingIntent)
        Toast.makeText(this, "Notification set for ${formatTime(notificationTime)}", Toast.LENGTH_SHORT).show()
    }

    private fun showDateTimePicker(defaultTime: Long) {
        val calendar = Calendar.getInstance().apply { timeInMillis = defaultTime }
        DatePickerDialog(this, { _, year, month, dayOfMonth ->
            calendar.apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, dayOfMonth)
            }
            TimePickerDialog(this, { _, hourOfDay, minute ->
                calendar.apply {
                    set(Calendar.HOUR_OF_DAY, hourOfDay)
                    set(Calendar.MINUTE, minute)
                }
                val customNotificationTime = calendar.timeInMillis
                // Handle custom notification logic here
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun formatTime(timeInMillis: Long): String {
        val calendar = Calendar.getInstance().apply { timeInMillis = timeInMillis }
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        return String.format("%02d:%02d", hour, minute)
    }
}
