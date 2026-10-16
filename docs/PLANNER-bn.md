# Planner (alarm, timer, reminder, routines)

Settings > "Planner & Reminders" screen shows and edits everything. You can also use your voice.

## Voice examples
- Alarm: "Set an alarm for 6:30 AM", "Wake me up every weekday at 7"
- Timer / stopwatch: "Set a 10 minute timer", "Start the stopwatch", "Stop the stopwatch"
- Reminder: "Remind me in 20 minutes to call mom", "Remind me every day at 9 PM to take medicine"
- Place reminder: "Save this place as home", later "Remind me to buy milk when I reach home"
- Daily brief: "Turn on my morning brief at 7", "Read my brief now"
- Rules: "When I plug in the charger, tell me battery is charging", "At 11 PM set the phone to vibrate"
- Scheduled WhatsApp: "Send Rahul good night on WhatsApp at 11 PM" (phone must be unlocked)
- To-do: "Add buy milk to my to-do list", "What is on my list?", "Mark milk done"
- Birthdays: "Turn on birthday reminders", "Whose birthday is coming?"
- Health: "Remind me to drink water every 90 minutes", "Remind me to sleep at 10:30 PM"

## Files
All new code is in `app/src/main/java/com/jarvis/assistant/planner/`.
Hooks in old files (only added lines): JarvisApp.kt, GeminiLiveWebSocket.kt, JarvisConversationService.kt,
SettingsFragment.kt, fragment_settings.xml, AndroidManifest.xml.
Data is stored in its own SharedPreferences file ("jarvis_planner"), the app database is not touched.

## Phone settings that matter (Xiaomi/Redmi/Poco)
Battery: no restrictions, Autostart: on, Notifications: on, Location "all the time" only for place reminders.

# Phone unlock (Settings > Phone unlock)
- Saves your PIN encrypted with an Android Keystore key (never leaves the phone, never sent online).
- Optional secret phrase: Jarvis unlocks only after you say it (stored as a salted hash). 3 wrong phrases pause unlocking for 10 minutes.
- "Auto-unlock before phone tasks": if the phone is locked and a task needs the screen, Jarvis unlocks first.
- Voice: "Unlock my phone", "Lock the phone".
- Works only with a PIN lock and only if the lock screen keypad is visible to accessibility (some Xiaomi/Redmi/Poco phones hide it).
- Code: app/src/main/java/com/jarvis/assistant/unlock/

# Typing everywhere (keyboard fallback)
If an app does not expose its text box to accessibility (code editors, note apps, custom views), type_text now falls back to
copying the text and tapping it in Gboard's clipboard, the way a person would. Needs Gboard with Settings > Clipboard turned on and the keyboard open on the field.
It never types into the browser address bar or password fields. Code: app/src/main/java/com/jarvis/assistant/typing/KeyboardTyper.kt
