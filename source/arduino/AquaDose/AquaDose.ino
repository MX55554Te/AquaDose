/*
 * AquaDose
 *
 * This firmware implements a multiâ€‘channel dosing system for aquarium maintenance.
 * It supports up to PUMP_COUNT peristaltic pumps, configurable dosing
 * schedules, priming and calibration routines, and an onboard web
 * interface.  The interface is served over HTTP using the builtâ€‘in
 * WebServer library and styled using Bootstrap loaded from a CDN.  Users
 * can configure WiFi credentials, timezone settings, pump calibration,
 * dosing schedules and names via their browser.
 *
 * Features
 * --------
 *  - SoftAP setup mode: on first boot or after a WiFi reset the
 *    controller exposes an access point (AP) so the user can enter
 *    network credentials and select a timezone.  Credentials and
 *    configuration are stored in the nonâ€‘volatile NVS using the
 *    Preferences library.
 *  - Nonâ€‘blocking pump control: pumps run in the background without
 *    blocking the web server or scheduler.  A global run state is used
 *    to time pump activations and provide an emergency stop.
 *  - Calibration: users can prime a pump until fluid flows and then
 *    run a 60Â second calibration cycle.  After measuring the volume
 *    dispensed (in grams for water), they enter the value which is
 *    automatically converted to ml/sec and stored.
 *  - Dosing schedules: each pump supports a configurable array of
 *    schedule slots.  Each slot defines a time of day and a volume
 *    (ml) to dispense.  The controller ensures a schedule runs no
 *    more than once per day by tracking the last run date.
 *  - History log: recent pump activations are recorded with a
 *    timestamp, pump index, volume and reason.  The log is exposed via
 *    the web interface.
 *  - Timezone: the user can select from a list of common POSIX
 *    timezone strings.  The selected value is applied at runtime
 *    using tzset() and persisted in NVS.
 *  - Factory reset: a web route allows clearing saved WiFi credentials
 *    and timezone, forcing the unit back into setup mode.  Holding the
 *    BOOT pin (GPIO0) during startup for 5Â seconds also resets the
 *    configuration.
 *
 * Wiring
 * ------
 *  Connect each pump's negative lead to the drain of the MOSFET,
 *  connect the source to the common ground and the gate to the
 *  specified pumpPins[] element via a 100Â Î© resistor.  Always fit a
 *  flyback diode across the pump terminals (stripe on the diode to
 *  positive supply).
 *
 *  Ensure the ESP32's ground is tied to the power supply ground.  Use
 *  a 12Â V supply with adequate current rating and a buck converter to
 *  generate 5Â V for the ESP32.
 */

#include <WiFi.h>
#include <WebServer.h>
#include <Preferences.h>
#include <time.h>
#include <esp_task_wdt.h>
#include <esp_idf_version.h>
#include <freertos/FreeRTOS.h>
#include <stdarg.h>
#include <math.h>

// -----------------------------------------------------------------------------
// Configuration constants
// Adjust these values to match your hardware setup.
// -----------------------------------------------------------------------------

// Number of peristaltic pumps connected to the controller.  Up to 8
// pumps are supported but memory consumption increases with higher
// values.
#ifndef PUMP_COUNT
#define PUMP_COUNT 8
#endif

#if PUMP_COUNT < 1 || PUMP_COUNT > 8
#error "PUMP_COUNT must be between 1 and 8"
#endif

// Maximum number of dosing schedules per pump.  Each schedule can run
// once per day.  Increase this value to allow more daily dosings but
// note that web interface complexity increases accordingly.
#ifndef MAX_SCHEDULES
#define MAX_SCHEDULES 8
#endif

const float MIN_DOSE_ML = 0.1f;
const float MAX_DOSE_ML = 999.0f;

// Number of recent log entries kept in RAM for the web page.  These are
// intentionally not saved to NVS to avoid wearing flash memory.
#ifndef LOG_COUNT
#define LOG_COUNT 30
#endif

// GPIO pins assigned to pump MOSFET gates.  Change this array to
// reflect your wiring.  Avoid using pins required for boot mode
// selection (e.g. GPIO0, GPIO2, GPIO15) if possible.  Pins must be
// output capable.
const uint8_t PUMP_PINS[] = { 25, 26, 27, 14, 32, 33, 18, 19 };

static_assert(PUMP_COUNT <= (sizeof(PUMP_PINS) / sizeof(PUMP_PINS[0])),
              "PUMP_COUNT is greater than the number of configured PUMP_PINS");

// Status LED.  Many ESP32 dev boards use GPIO2 for the onboard LED.
// Set STATUS_LED_PIN to another GPIO if your board uses a different LED pin.
#ifndef STATUS_LED_PIN
#ifdef LED_BUILTIN
#define STATUS_LED_PIN LED_BUILTIN
#else
#define STATUS_LED_PIN 2
#endif
#endif
const bool STATUS_LED_ACTIVE_HIGH = true;
const unsigned long STATUS_LED_BLINK_MS = 500;

// SoftAP configuration when entering setup mode.  The SSID suffix is
// automatically appended with a unique number based on the chip ID.
const char *SOFT_AP_SSID_BASE = "AquaDose";
const char *SOFT_AP_PASSWORD = "12345678"; // must be at least 8 characters

// Name of the NVS namespace used for storing persistent settings.
const char *NVS_NAMESPACE = "dosing_cfg";

// Timeout in milliseconds for WiFi connection attempts before
// reverting to AP mode on failure.
const unsigned long WIFI_CONNECT_TIMEOUT_MS = 20000;

// Serial Monitor baud rate.
const unsigned long SERIAL_BAUD = 115200;

// NTP servers used after STA WiFi connects.  Time is required for
// schedules and readable log timestamps.
const char *NTP_SERVER_1 = "pool.ntp.org";
const char *NTP_SERVER_2 = "time.nist.gov";

// Timeout for priming a pump in seconds.  Priming stops
// automatically after this period to prevent accidental long run.
const int PRIME_TIMEOUT_SEC = 120;

// Check schedules several times per minute so a schedule edited close
// to its target minute is not missed.
const unsigned long SCHEDULE_CHECK_INTERVAL_MS = 5000;

// Watchdog timeout (in seconds).  We reset the watchdog regularly to
// avoid reboots when the web server blocks for too long.  Adjust
// according to your expected maximum blocking duration.
const int WATCHDOG_TIMEOUT_SEC = 10;

// -----------------------------------------------------------------------------
// Data structures
// -----------------------------------------------------------------------------

// A single schedule specifying a time and dose volume for a pump.  A
// schedule runs once per day at its configured time, provided the
// pump is enabled and the schedule is enabled.
struct ScheduleItem {
  bool enabled;
  int hour;
  int minute;
  float ml;
  int lastRunDay;
};

// Configuration for a single pump.
struct PumpConfig {
  bool enabled;        // whether scheduling is active for this pump
  char name[32];       // user defined pump name
  float mlPerSec;      // calibration value (ml per second)
  ScheduleItem schedules[MAX_SCHEDULES];
};

// Structure tracking the current running pump state.  Only one pump
// runs at a time to prevent overloading the power supply and to
// simplify hardware design.
struct RunState {
  bool running;        // true if a pump is currently running
  int pump;            // index of running pump (0â€“PUMP_COUNT-1) or -1
  unsigned long stopAt; // time to stop pump (millis).  0 means indefinite
  float ml;            // dose volume for log (or -1 for prime)
  char reason[16];     // reason for log entry (e.g. "schedule", "manual", "prime", "calib")
};

// Global variables

Preferences prefs;
WebServer server(80);

// Pump configuration array
PumpConfig pumps[PUMP_COUNT];

// Run state
RunState runState = {false, -1, 0, 0.0f, {0}};

// Timestamp for the start of an indefinite prime.  When a pump is
// primed indefinitely (stopAt == 0) we use this to enforce a safety
// timeout.  It is reset whenever a new prime starts or stops.
unsigned long primeStartTime = 0;

// Recent pump actions for the web page.  RAM only; cleared on reboot.
String ramLogs[LOG_COUNT];
int ramLogNext = 0;

// WiFi credentials and timezone (POSIX format)
String wifiSSID;
String wifiPass;
String tzString;
bool wifiConfigured = false;
bool wifiPendingValidation = false;
bool setupMode = false;

// -----------------------------------------------------------------------------
// Utility functions
// -----------------------------------------------------------------------------

void copyCString(char *dest, size_t destSize, const char *src) {
  if (destSize == 0) return;
  if (src == nullptr) src = "";
  strncpy(dest, src, destSize - 1);
  dest[destSize - 1] = '\0';
}

String jsonEscape(const String &input) {
  String out;
  out.reserve(input.length() + 8);
  for (size_t i = 0; i < input.length(); i++) {
    char c = input.charAt(i);
    uint8_t b = (uint8_t)c;
    switch (c) {
      case '\"': out += "\\\""; break;
      case '\\': out += "\\\\"; break;
      case '\b': out += "\\b"; break;
      case '\f': out += "\\f"; break;
      case '\n': out += "\\n"; break;
      case '\r': out += "\\r"; break;
      case '\t': out += "\\t"; break;
      default:
        if (b < 0x20) {
          char buf[7];
          snprintf(buf, sizeof(buf), "\\u%04x", b);
          out += buf;
        } else {
          out += c;
        }
        break;
    }
  }
  return out;
}

bool readPumpArg(int &pumpIndex) {
  if (!server.hasArg("p")) return false;
  String value = server.arg("p");
  if (value.length() == 0) return false;
  for (size_t i = 0; i < value.length(); i++) {
    if (!isDigit(value.charAt(i))) return false;
  }
  pumpIndex = value.toInt();
  return pumpIndex >= 0 && pumpIndex < PUMP_COUNT;
}

bool readIntArgStrict(const String &name, int &out) {
  if (!server.hasArg(name)) return false;
  String value = server.arg(name);
  value.trim();
  if (value.length() == 0) return false;
  int start = 0;
  if (value.charAt(0) == '-' || value.charAt(0) == '+') {
    if (value.length() == 1) return false;
    start = 1;
  }
  for (int i = start; i < value.length(); i++) {
    if (!isDigit(value.charAt(i))) return false;
  }
  out = value.toInt();
  return true;
}

bool readFloatArgStrict(const String &name, float &out) {
  if (!server.hasArg(name)) return false;
  String value = server.arg(name);
  value.trim();
  if (value.length() == 0) return false;
  int start = 0;
  bool sawDigit = false;
  bool sawDot = false;
  if (value.charAt(0) == '-' || value.charAt(0) == '+') {
    if (value.length() == 1) return false;
    start = 1;
  }
  for (int i = start; i < value.length(); i++) {
    char c = value.charAt(i);
    if (isDigit(c)) {
      sawDigit = true;
      continue;
    }
    if (c == '.' && !sawDot) {
      sawDot = true;
      continue;
    }
    return false;
  }
  if (!sawDigit) return false;
  out = value.toFloat();
  return true;
}

int clampInt(int value, int minValue, int maxValue) {
  if (value < minValue) return minValue;
  if (value > maxValue) return maxValue;
  return value;
}

void setStatusLed(bool on) {
  digitalWrite(STATUS_LED_PIN, (on == STATUS_LED_ACTIVE_HIGH) ? HIGH : LOW);
}

void appendRamLogLine(const char *line) {
  ramLogs[ramLogNext] = line;
  ramLogNext = (ramLogNext + 1) % LOG_COUNT;
}

void serialLogf(const char *format, ...) {
  char message[192];
  va_list args;
  va_start(args, format);
  vsnprintf(message, sizeof(message), format, args);
  va_end(args);

  Serial.print("[");
  struct tm t;
  if (getLocalTime(&t, 10)) {
    char timestamp[24];
    snprintf(timestamp, sizeof(timestamp), "%04d-%02d-%02d %02d:%02d:%02d",
             t.tm_year + 1900, t.tm_mon + 1, t.tm_mday,
             t.tm_hour, t.tm_min, t.tm_sec);
    Serial.print(timestamp);
  } else {
    Serial.print("ms=");
    Serial.print(millis());
  }
  Serial.print("] ");
  Serial.println(message);

  char line[240];
  struct tm logTime;
  if (getLocalTime(&logTime, 10)) {
    snprintf(line, sizeof(line), "%04d-%02d-%02d %02d:%02d:%02d | %s",
             logTime.tm_year + 1900, logTime.tm_mon + 1, logTime.tm_mday,
             logTime.tm_hour, logTime.tm_min, logTime.tm_sec, message);
  } else {
    snprintf(line, sizeof(line), "ms=%lu | %s", millis(), message);
  }
  appendRamLogLine(line);
}

/*
 * Add a recent log entry for the web page.  This is RAM only and is
 * intentionally not saved to NVS, because pump activity can create many
 * writes and NVS flash has limited write endurance.
 */
void addLog(int pumpIndex, float ml, const char *reason) {
  // Build timestamp string
  char timestamp[25];
  struct tm t;
  if (getLocalTime(&t)) {
    snprintf(timestamp, sizeof(timestamp), "%04d-%02d-%02d %02d:%02d",
             t.tm_year + 1900, t.tm_mon + 1, t.tm_mday, t.tm_hour, t.tm_min);
  } else {
    copyCString(timestamp, sizeof(timestamp), "no-time");
  }
  // Build log line
  char line[96];
  char mlStr[16];
  if (ml < 0) {
    // prime or indefinite operation, mark volume as "--"
    copyCString(mlStr, sizeof(mlStr), "--");
  } else {
    snprintf(mlStr, sizeof(mlStr), "%.2f", ml);
  }
  snprintf(line, sizeof(line), "%s | Pump %d | %s ml | %s", timestamp, pumpIndex + 1, mlStr, reason);
  appendRamLogLine(line);
}

/*
 * Load WiFi credentials and timezone from NVS.  If credentials are
 * missing, wifiConfigured remains false and the system enters setup
 * mode.  Timezone is set to a default (Asia/Jerusalem) if missing.
 */
void loadWifiAndTZ() {
  prefs.begin(NVS_NAMESPACE, true);
  wifiSSID = prefs.getString("wifi_ssid", "");
  wifiPass = prefs.getString("wifi_pass", "");
  tzString = prefs.getString("tz", "IST-2IDT,M3.4.4/26,M10.5.0/25");
  wifiPendingValidation = prefs.getBool("wifi_pending", false);
  prefs.end();
  wifiConfigured = (wifiSSID.length() > 0 && wifiPass.length() > 0);
  if (!wifiConfigured) {
    setupMode = true;
  }
  // Apply timezone
  setenv("TZ", tzString.c_str(), 1);
  tzset();
}

/*
 * Save WiFi credentials and timezone to NVS.  Called after user
 * submits credentials via the setup web page.  After saving, the
 * device will reboot to apply settings.
 */
void saveWifiAndTZ(const String &ssid, const String &pass, const String &tz) {
  prefs.begin(NVS_NAMESPACE, false);
  prefs.putString("wifi_ssid", ssid);
  prefs.putString("wifi_pass", pass);
  prefs.putString("tz", tz);
  prefs.putBool("wifi_pending", true);
  prefs.end();
}

void markWifiSettingsValidated() {
  prefs.begin(NVS_NAMESPACE, false);
  prefs.putBool("wifi_pending", false);
  prefs.end();
  wifiPendingValidation = false;
}

void discardPendingWifiSettings() {
  serialLogf("WiFi: first connection failed, clearing pending credentials from memory");
  prefs.begin(NVS_NAMESPACE, false);
  prefs.remove("wifi_ssid");
  prefs.remove("wifi_pass");
  prefs.remove("wifi_pending");
  prefs.end();
  wifiSSID = "";
  wifiPass = "";
  wifiConfigured = false;
  wifiPendingValidation = false;
  setupMode = true;
  WiFi.disconnect(true, true);
}

/*
 * Clear WiFi credentials and timezone from NVS.  This will cause
 * setup mode to be entered on next boot.
 */
void resetWifiSettings() {
  prefs.begin(NVS_NAMESPACE, false);
  prefs.remove("wifi_ssid");
  prefs.remove("wifi_pass");
  prefs.remove("wifi_pending");
  prefs.remove("tz");
  prefs.end();
  wifiSSID = "";
  wifiPass = "";
  wifiConfigured = false;
  wifiPendingValidation = false;
  setupMode = true;
}

/*
 * Clear every stored AquaDose setting from this firmware namespace.
 * This removes WiFi, timezone, pump names, calibration rates, schedules,
 * and schedule last-run markers.  Runtime RAM logs clear naturally on reboot.
 */
void factoryResetSettings() {
  serialLogf("Factory reset: clearing all saved AquaDose settings");
  stopAllPumps();
  prefs.begin(NVS_NAMESPACE, false);
  prefs.clear();
  prefs.end();
  wifiSSID = "";
  wifiPass = "";
  tzString = "IST-2IDT,M3.4.4/26,M10.5.0/25";
  wifiConfigured = false;
  wifiPendingValidation = false;
  setupMode = true;
  WiFi.disconnect(true, true);
}

/*
 * Load pump configuration from NVS into the pumps[] array.  If
 * configuration is missing, defaults are applied.  Each pump's
 * settings are stored under keys derived from the pump index.
 */
void loadPumpConfig() {
  prefs.begin(NVS_NAMESPACE, true);
  for (int p = 0; p < PUMP_COUNT; p++) {
    String base = "p" + String(p) + "_";
    pumps[p].enabled = prefs.getBool((base + "en").c_str(), false);
    pumps[p].mlPerSec = prefs.getFloat((base + "rate").c_str(), 0.0f);
    String defaultName = "Pump " + String(p + 1);
    String savedName = prefs.getString((base + "name").c_str(), defaultName);
    savedName.toCharArray(pumps[p].name, sizeof(pumps[p].name));
    for (int s = 0; s < MAX_SCHEDULES; s++) {
      String sb = base + "s" + String(s) + "_";
      pumps[p].schedules[s].enabled = prefs.getBool((sb + "en").c_str(), false);
      pumps[p].schedules[s].hour = prefs.getInt((sb + "h").c_str(), 12);
      pumps[p].schedules[s].minute = prefs.getInt((sb + "m").c_str(), 0);
      pumps[p].schedules[s].ml = prefs.getFloat((sb + "ml").c_str(), 1.0f);
      pumps[p].schedules[s].lastRunDay = prefs.getInt((sb + "last").c_str(), -1);
    }
  }
  prefs.end();
}

/*
 * Save configuration for a single pump to NVS.  Called after the
 * user updates pump settings via the web interface.
 */
void savePumpConfig(int p) {
  prefs.begin(NVS_NAMESPACE, false);
  String base = "p" + String(p) + "_";
  prefs.putBool((base + "en").c_str(), pumps[p].enabled);
  prefs.putFloat((base + "rate").c_str(), pumps[p].mlPerSec);
  prefs.putString((base + "name").c_str(), String(pumps[p].name));
  for (int s = 0; s < MAX_SCHEDULES; s++) {
    String sb = base + "s" + String(s) + "_";
    prefs.putBool((sb + "en").c_str(), pumps[p].schedules[s].enabled);
    prefs.putInt((sb + "h").c_str(), pumps[p].schedules[s].hour);
    prefs.putInt((sb + "m").c_str(), pumps[p].schedules[s].minute);
    prefs.putFloat((sb + "ml").c_str(), pumps[p].schedules[s].ml);
    prefs.putInt((sb + "last").c_str(), pumps[p].schedules[s].lastRunDay);
  }
  prefs.end();
}

/*
 * Stop any running pump immediately.  Called on emergency stop or
 * prime stop.  Also resets runState.
 */
void stopAllPumps() {
  bool wasRunning = runState.running;
  int stoppedPump = runState.pump;
  char stoppedReason[sizeof(runState.reason)];
  copyCString(stoppedReason, sizeof(stoppedReason), runState.reason);

  for (int i = 0; i < PUMP_COUNT; i++) {
    digitalWrite(PUMP_PINS[i], LOW);
  }
  runState.running = false;
  runState.pump = -1;
  runState.stopAt = 0;
  runState.ml = 0;
  runState.reason[0] = '\0';
  // Reset prime start time so that future primes start afresh
  primeStartTime = 0;

  if (wasRunning && stoppedPump >= 0) {
    serialLogf("STOP: pump %d (%s), reason=%s", stoppedPump + 1, pumps[stoppedPump].name, stoppedReason);
  } else {
    serialLogf("STOP: all pumps already idle");
  }
}

/*
 * Start a pump to dispense a specified volume (ml).  The pump will
 * run for a duration calculated from the calibration constant
 * (mlPerSec).  Returns true on success, false if a pump is already
 * running, the pump index is invalid or the mlPerSec calibration is
 * too low.  The reason string should be a short identifier used in
 * the log.
 */
bool startPumpMl(int pumpIndex, float ml, const char *reason) {
  if (pumpIndex < 0 || pumpIndex >= PUMP_COUNT) {
    serialLogf("DOSE rejected: invalid pump index %d", pumpIndex);
    return false;
  }
  if (runState.running) {
    serialLogf("DOSE rejected: pump %d is already running (%s)", runState.pump + 1, runState.reason);
    return false;
  }
  if (ml < MIN_DOSE_ML || ml > MAX_DOSE_ML) {
    serialLogf("DOSE rejected: pump %d invalid volume %.3f ml", pumpIndex + 1, ml);
    return false;
  }
  float rate = pumps[pumpIndex].mlPerSec;
  if (rate < 0.01f) {
    serialLogf("DOSE rejected: pump %d calibration too low (%.4f ml/sec)", pumpIndex + 1, rate);
    return false;
  }
  unsigned long durationMs = (unsigned long)((ml / rate) * 1000.0f);
  // Cap maximum run time to avoid runaway (2 minutes = 120000ms)
  if (durationMs > 120000UL) {
    serialLogf("DOSE rejected: pump %d duration too long (%lu ms)", pumpIndex + 1, durationMs);
    return false;
  }
  runState.running = true;
  runState.pump = pumpIndex;
  runState.stopAt = millis() + durationMs;
  runState.ml = ml;
  copyCString(runState.reason, sizeof(runState.reason), reason);
  // Start pump
  digitalWrite(PUMP_PINS[pumpIndex], HIGH);
  // Add log entry now (we know the intended dose)
  addLog(pumpIndex, ml, reason);
  serialLogf("DOSE start: pump %d (%s), %.2f ml, rate %.4f ml/sec, duration %.1f sec, reason=%s",
             pumpIndex + 1, pumps[pumpIndex].name, ml, rate, durationMs / 1000.0f, reason);
  return true;
}

/*
 * Start a pump for a specified number of seconds.  Used for
 * calibration runs and priming.  If seconds is zero or negative, the
 * pump runs indefinitely until stopped manually.  Returns false if
 * another pump is running or the index is invalid.
 */
bool startPumpSeconds(int pumpIndex, int seconds, const char *reason) {
  if (pumpIndex < 0 || pumpIndex >= PUMP_COUNT) {
    serialLogf("RUN rejected: invalid pump index %d", pumpIndex);
    return false;
  }
  if (runState.running) {
    serialLogf("RUN rejected: pump %d is already running (%s)", runState.pump + 1, runState.reason);
    return false;
  }
  runState.running = true;
  runState.pump = pumpIndex;
  if (seconds > 0) {
    runState.stopAt = millis() + (unsigned long)seconds * 1000UL;
    // Not a prime, reset primeStartTime
    primeStartTime = 0;
  } else {
    runState.stopAt = 0; // indefinite run
    // Record start time for safety timeout
    primeStartTime = millis();
  }
  runState.ml = -1.0f; // unknown volume
  copyCString(runState.reason, sizeof(runState.reason), reason);
  digitalWrite(PUMP_PINS[pumpIndex], HIGH);
  addLog(pumpIndex, -1.0f, reason);
  if (seconds > 0) {
    serialLogf("RUN start: pump %d (%s), %d sec, reason=%s", pumpIndex + 1, pumps[pumpIndex].name, seconds, reason);
  } else {
    serialLogf("RUN start: pump %d (%s), until stopped, timeout %d sec, reason=%s",
               pumpIndex + 1, pumps[pumpIndex].name, PRIME_TIMEOUT_SEC, reason);
  }
  return true;
}

/*
 * Background pump runner.  Called regularly in loop() to stop a pump
 * when its run duration has elapsed.  If runState.stopAt is zero
 * (indefinite prime), the pump will not autoâ€‘stop.
 */
void updatePumpRunner() {
  if (!runState.running) return;
  if (runState.stopAt == 0) {
    // indefinite prime; check for timeout to avoid runaway
    if (primeStartTime > 0 && ((millis() - primeStartTime) / 1000UL >= PRIME_TIMEOUT_SEC)) {
      serialLogf("PRIME timeout: pump %d stopped after %d sec", runState.pump + 1, PRIME_TIMEOUT_SEC);
      stopAllPumps();
      primeStartTime = 0;
    }
    return;
  }
  if ((long)(millis() - runState.stopAt) >= 0) {
    // Time elapsed â€“ stop pump
    int stoppedPump = runState.pump;
    float stoppedMl = runState.ml;
    char stoppedReason[sizeof(runState.reason)];
    copyCString(stoppedReason, sizeof(stoppedReason), runState.reason);
    digitalWrite(PUMP_PINS[runState.pump], LOW);
    runState.running = false;
    runState.pump = -1;
    runState.stopAt = 0;
    runState.ml = 0;
    runState.reason[0] = '\0';
    if (stoppedMl >= 0.0f) {
      serialLogf("DOSE complete: pump %d (%s), %.2f ml, reason=%s",
                 stoppedPump + 1, pumps[stoppedPump].name, stoppedMl, stoppedReason);
    } else {
      serialLogf("RUN complete: pump %d (%s), reason=%s",
                 stoppedPump + 1, pumps[stoppedPump].name, stoppedReason);
    }
  }
}

/*
 * Check all schedules for each pump.  If the current time matches a
 * schedule and that schedule hasn't run yet today, start the pump
 * with the configured dose.  This function is called once per
 * few seconds from loop().
 */
void checkSchedules() {
  // Do not schedule runs if a pump is already running
  if (runState.running) return;
  static int lastBlockedDay = -1;
  static int lastBlockedPump = -1;
  static int lastBlockedSchedule = -1;
  static int lastBlockedMinuteOfDay = -1;
  struct tm t;
  if (!getLocalTime(&t)) {
    static bool loggedNoTime = false;
    if (!loggedNoTime) {
      serialLogf("SCHEDULE skipped: time is not available yet");
      loggedNoTime = true;
    }
    return;
  }
  int currentDay = t.tm_yday;
  int currentHour = t.tm_hour;
  int currentMinute = t.tm_min;
  int currentMinuteOfDay = currentHour * 60 + currentMinute;
  for (int p = 0; p < PUMP_COUNT; p++) {
    if (!pumps[p].enabled) {
      for (int s = 0; s < MAX_SCHEDULES; s++) {
        ScheduleItem &sc = pumps[p].schedules[s];
        if (!sc.enabled) continue;
        if (currentHour == sc.hour && currentMinute == sc.minute && sc.lastRunDay != currentDay) {
          if (lastBlockedDay != currentDay || lastBlockedPump != p || lastBlockedSchedule != s || lastBlockedMinuteOfDay != currentMinuteOfDay) {
            serialLogf("SCHEDULE blocked: pump %d (%s), slot %d is due at %02d:%02d, but scheduled dosing is not allowed for this pump",
                       p + 1, pumps[p].name, s + 1, sc.hour, sc.minute);
            lastBlockedDay = currentDay;
            lastBlockedPump = p;
            lastBlockedSchedule = s;
            lastBlockedMinuteOfDay = currentMinuteOfDay;
          }
          return;
        }
      }
      continue;
    }
    for (int s = 0; s < MAX_SCHEDULES; s++) {
      ScheduleItem &sc = pumps[p].schedules[s];
      if (!sc.enabled) continue;
      if (currentHour == sc.hour && currentMinute == sc.minute && sc.lastRunDay != currentDay) {
        serialLogf("SCHEDULE match: pump %d (%s), slot %d, %02d:%02d, %.2f ml",
                   p + 1, pumps[p].name, s + 1, sc.hour, sc.minute, sc.ml);
        if (startPumpMl(p, sc.ml, "schedule")) {
          sc.lastRunDay = currentDay;
          savePumpConfig(p);
          return; // run only one schedule per minute
        }
      }
    }
  }
}

// -----------------------------------------------------------------------------
// Web page rendering helpers
// These functions assemble HTML for the setup and main pages.  Strings
// are created using raw string literals (R"EOF(...)EOF") for clarity.
// -----------------------------------------------------------------------------

/*
 * Return HTML for the WiFi setup page.  This page is served when
 * setupMode is true.  Users must enter SSID, password, and select
 * timezone.  The form submits to /wifiSave via GET parameters.  A
 * selection of common timezones is provided; the selected value is
 * persisted to NVS.  If you need more timezones, extend the list.
 */
String htmlSetupPage() {
  // Options list for timezone selection.  Each option uses the POSIX
  // string as the value and a human readable label.  At least eight
  // timezones are provided; modify or extend as needed.
  String tzOptions = "";
  struct {
    const char *label;
    const char *posix;
  } tzList[] = {
    {"Asia/Jerusalem", "IST-2IDT,M3.4.4/26,M10.5.0/25"},
    {"UTC", "UTC0"},
    {"Europe/London", "GMT0BST,M3.5.0/1,M10.5.0/2"},
    {"Europe/Berlin", "CET-1CEST,M3.5.0,M10.5.0/3"},
    {"America/New York", "EST+5EDT,M3.2.0/2,M11.1.0/2"},
    {"America/Los Angeles", "PST+8PDT,M3.2.0/2,M11.1.0/2"},
    {"Asia/Tokyo", "JST-9"},
    {"Australia/Sydney", "AEST-10AEDT,M10.1.0,M4.1.0/3"}
  };
  const size_t tzCount = sizeof(tzList) / sizeof(tzList[0]);
  for (size_t i = 0; i < tzCount; i++) {
    tzOptions += "<option value=\"" + String(tzList[i].posix) + "\"";
    if (tzString == tzList[i].posix) tzOptions += " selected";
    tzOptions += ">" + String(tzList[i].label) + "</option>";
  }
  String html = R"HTML(
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>AquaDose Setup</title>
  <link rel="icon" type="image/svg+xml" href="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 64 64'%3E%3Crect width='64' height='64' rx='14' fill='%230d6efd'/%3E%3Cpath d='M32 9C25 19 17 28 17 39a15 15 0 0 0 30 0C47 28 39 19 32 9z' fill='white'/%3E%3Cpath d='M32 18C26 27 22 32 22 39a10 10 0 0 0 20 0C42 32 38 27 32 18z' fill='%230d6efd'/%3E%3Cpath d='M26 40c2 5 9 7 14 2' fill='none' stroke='white' stroke-width='4' stroke-linecap='round'/%3E%3C/svg%3E">
  <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css" rel="stylesheet">
  <style>
    body { background:#f5f5f5; }
    .card { border-radius:12px; box-shadow:0 2px 10px rgba(0,0,0,0.1); }
  </style>
</head>
<body>
  <div class="container py-5">
    <div class="card p-4">
      <h2 class="mb-3">AquaDose Setup</h2>
      <p>Enter your WiFi network credentials and choose your timezone.</p>
      <form action="/wifiSave" method="GET">
        <div class="mb-3">
          <label for="ssid" class="form-label">WiFi SSID</label>
          <input type="text" id="ssid" name="ssid" class="form-control" required>
        </div>
        <div class="mb-3">
          <label for="pass" class="form-label">WiFi Password</label>
          <input type="password" id="pass" name="pass" class="form-control" required>
        </div>
        <div class="mb-3">
          <label for="tz" class="form-label">Timezone</label>
          <select id="tz" name="tz" class="form-select">
)HTML";
  html += tzOptions;
  html += R"HTML(
          </select>
        </div>
        <button type="submit" class="btn btn-primary">Save and Reboot</button>
      </form>
      <hr>
      <p class="text-muted">The device will reboot after saving.  If it cannot
        connect to your network it will return to setup mode.  The access point
        uses password 12345678.</p>
    </div>
  </div>
</body>
</html>
)HTML";
  return html;
}

/*
 * Return HTML for the main management page.  This page is served when
 * wifiConfigured is true.  It displays pump status, allows editing
 * pump names, schedules and calibration, and exposes controls for
 * manual dosing, priming, calibration, timezone change and WiFi reset.
 */
String htmlMainPage() {
  String html = R"HTML(
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>AquaDose Control</title>
  <link rel="icon" type="image/svg+xml" href="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 64 64'%3E%3Crect width='64' height='64' rx='14' fill='%230d6efd'/%3E%3Cpath d='M32 9C25 19 17 28 17 39a15 15 0 0 0 30 0C47 28 39 19 32 9z' fill='white'/%3E%3Cpath d='M32 18C26 27 22 32 22 39a10 10 0 0 0 20 0C42 32 38 27 32 18z' fill='%230d6efd'/%3E%3Cpath d='M26 40c2 5 9 7 14 2' fill='none' stroke='white' stroke-width='4' stroke-linecap='round'/%3E%3C/svg%3E">
  <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css" rel="stylesheet">
  <link href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.3/font/bootstrap-icons.min.css" rel="stylesheet">
  <style>
    :root {
      --surface: #ffffff;
      --surface-soft: #f4f7fb;
      --surface-blue: #edf6ff;
      --ink: #172033;
      --muted: #65738a;
      --line: #dce4ef;
      --brand: #0d6efd;
      --success-soft: #e9f8ef;
      --warning-soft: #fff7df;
    }
    body { background:var(--surface-soft); color:var(--ink); overflow-x:hidden; }
    .app-shell { max-width:1440px; }
    .topbar { background:linear-gradient(135deg, #0b2a4a, #0d6efd); color:#fff; border-radius:0 0 18px 18px; box-shadow:0 12px 30px rgba(13,110,253,0.22); }
    .brand-mark { width:42px; height:42px; border-radius:12px; display:inline-flex; align-items:center; justify-content:center; background:rgba(255,255,255,0.16); border:1px solid rgba(255,255,255,0.22); }
    .brand-copy, .help-copy { min-width:0; }
    .brand-copy h1, .brand-copy div, .help-copy h2 { overflow-wrap:break-word; }
    .toolbar-card, .panel, .pump-card { background:var(--surface); border:1px solid var(--line); border-radius:12px; box-shadow:0 10px 24px rgba(23,32,51,0.06); }
    .stat-card { background:var(--surface); border:1px solid var(--line); border-radius:12px; padding:16px; min-height:112px; }
    .stat-label { color:var(--muted); font-size:0.86rem; font-weight:600; text-transform:uppercase; letter-spacing:.04em; }
    .stat-value { font-size:1.28rem; font-weight:700; margin-top:4px; }
    .pump-card { padding:20px; margin-bottom:18px; }
    .pump-header { display:flex; align-items:flex-start; justify-content:space-between; gap:12px; border-bottom:1px solid var(--line); padding-bottom:14px; margin-bottom:16px; }
    .pump-title { display:flex; align-items:center; gap:10px; margin:0; }
    .pump-icon { width:36px; height:36px; border-radius:10px; display:inline-flex; align-items:center; justify-content:center; background:var(--surface-blue); color:var(--brand); }
    .log-entry { background:#111827; border-radius:8px; padding:6px 10px; margin-bottom:5px; font-family:monospace; }
    .schedule-row { background:#f8fafc; border:1px solid #e5edf6; border-radius:10px; padding:12px; margin-bottom:10px; }
    .schedule-row.on { background:#eef7ff; border-color:#b9dcff; }
    .schedule-row.off .schedule-fields { display:none; }
    .schedule-summary { display:flex; align-items:center; justify-content:space-between; gap:12px; flex-wrap:wrap; }
    .schedule-off-note { color:var(--muted); font-size:0.92rem; }
    .schedule-details { background:#f8fafc; border:1px solid var(--line); border-radius:12px; padding:14px; margin-top:16px; }
    .schedule-details > summary { cursor:pointer; font-size:1.05rem; font-weight:700; list-style:none; display:flex; align-items:center; justify-content:space-between; gap:12px; }
    .schedule-details > summary::-webkit-details-marker { display:none; }
    .schedule-details > summary::after { content:"\F282"; font-family:"bootstrap-icons"; color:var(--brand); transition:transform .18s ease; }
    .schedule-details[open] > summary::after { transform:rotate(180deg); }
    .help-panel { background:#f1f8ff; border:1px solid #cfe7ff; border-radius:12px; padding:16px; margin-bottom:16px; }
    .help-panel ol, .help-panel ul { margin-bottom:0; padding-left:22px; }
    .calibration-guide { background:#f8fbff; border:1px solid #d7e7fb; border-radius:12px; padding:14px; margin:16px 0; }
    .calibration-guide-title { display:flex; align-items:center; gap:8px; font-weight:700; margin-bottom:10px; }
    .calibration-steps { display:grid; grid-template-columns:repeat(2, minmax(0, 1fr)); gap:10px; margin:0; }
    .calibration-step { display:flex; align-items:flex-start; gap:10px; background:#fff; border:1px solid #e0e9f5; border-radius:10px; padding:10px 12px; min-height:68px; }
    .step-number { flex:0 0 28px; width:28px; height:28px; border-radius:50%; display:inline-flex; align-items:center; justify-content:center; background:var(--brand); color:#fff; font-weight:700; font-size:0.9rem; }
    .step-text { line-height:1.35; }
    .step-text b { display:block; margin-bottom:2px; }
    .hint { color:var(--muted); font-size:0.92rem; margin-top:4px; }
    .mini-help { color:var(--muted); font-size:0.9rem; }
    .question { display:inline-flex; align-items:center; justify-content:center; width:22px; height:22px; border-radius:50%; background:#e7f1ff; color:#0b5ed7; font-size:14px; font-weight:700; margin-left:6px; cursor:help; }
    .section-title { display:flex; align-items:center; gap:8px; }
    .form-label { margin-bottom:4px; font-weight:600; }
    .status-line { font-size:1.08rem; }
    .status-idle { color:#157347; }
    .status-active { color:#9a6700; }
    .info-pill { display:inline-flex; align-items:center; gap:6px; background:#f8fbff; border:1px solid var(--line); border-radius:999px; padding:5px 10px; color:#24344d; font-size:0.9rem; margin:4px 8px 8px 0; }
    .icon-title { display:inline-flex; align-items:center; gap:10px; }
    .action-row .btn { min-height:42px; }
    .bi { line-height:1; }
    .debug-log-window { width:min(560px, calc(100vw - 24px)); margin-top:82px; margin-right:12px; z-index:1080; }
    .debug-log-card { background:#111827; border:1px solid #475569; box-shadow:0 12px 34px rgba(0,0,0,0.45); }
    .debug-log-text { width:100%; height:300px; resize:vertical; background:#05070a; color:#d1fae5; border:1px solid #334155; border-radius:8px; padding:10px; font-family:Consolas, monospace; font-size:0.88rem; }
    .about-copy p { margin-bottom:0.85rem; }
    .about-copy p:last-child { margin-bottom:0; }
    .about-copy .tagline { font-weight:700; color:var(--brand); }
    .about-link { display:inline-flex; align-items:center; gap:6px; font-weight:600; }
    @media (max-width: 768px) {
      .topbar { border-radius:0 0 14px 14px; }
      .pump-header { flex-direction:column; }
      .stat-card { min-height:auto; }
      .brand-copy h1 { font-size:1.5rem; }
      .help-panel .d-flex { align-items:flex-start; }
      .calibration-steps { grid-template-columns:1fr; }
      .calibration-step { min-height:0; }
      .action-row .btn { width:100%; }
    }
    @media (max-width: 576px) {
      .topbar .container > .d-flex { display:block !important; }
      .topbar .container > .d-flex > .d-flex:first-child { align-items:flex-start !important; margin-bottom:14px; }
      .topbar .container > .d-flex > .d-flex:last-child { display:block !important; }
      .topbar .btn { width:100%; margin-bottom:8px; }
      .brand-copy div { max-width:280px; }
      .help-panel > .d-flex { display:block !important; }
      .help-panel .bi.fs-3 { margin-bottom:8px; display:inline-block; }
      .help-copy { max-width:310px; }
      .help-copy h2 { font-size:1.15rem; line-height:1.35; }
      .help-copy h2 .question { display:none; }
      .help-copy ol { padding-left:18px; }
      .help-copy li { margin-bottom:4px; overflow-wrap:anywhere; }
    }
  </style>
</head>
<body>
  <header class="topbar mb-4">
    <div class="container app-shell py-3">
      <div class="d-flex justify-content-between align-items-center gap-3 flex-wrap">
        <div class="d-flex align-items-center gap-3">
          <div class="brand-mark"><i class="bi bi-droplet-half fs-3"></i></div>
          <div class="brand-copy">
            <h1 class="h3 mb-1">AquaDose</h1>
            <div class="opacity-75">Aquarium dosing controller for calibrated, daily dosing.</div>
          </div>
        </div>
        <div class="d-flex gap-2 flex-wrap justify-content-end">
          <button class="btn btn-outline-light" type="button" data-bs-toggle="modal" data-bs-target="#aboutModal" title="Show product and creator information."><i class="bi bi-info-circle"></i> About</button>
          <button class="btn btn-light" type="button" data-bs-toggle="collapse" data-bs-target="#debugLogPanel" aria-expanded="false" aria-controls="debugLogPanel" title="Show or hide a floating RAM-only debug log window."><i class="bi bi-terminal"></i> Show logs</button>
          <button class="btn btn-danger" onclick="stopAll()" title="Immediately turns every pump output off. Use this if a pump is running when it should not."><i class="bi bi-stop-circle"></i> Emergency Stop</button>
        </div>
      </div>
    </div>
  </header>
  <main class="container app-shell pb-4">
    <div class="collapse position-fixed top-0 end-0 debug-log-window" id="debugLogPanel">
      <div class="card debug-log-card text-light">
        <div class="card-header d-flex justify-content-between align-items-center">
          <span><i class="bi bi-terminal"></i> Debug logs</span>
          <button class="btn btn-sm btn-outline-light" type="button" data-bs-toggle="collapse" data-bs-target="#debugLogPanel" aria-label="Close logs"><i class="bi bi-x-lg"></i></button>
        </div>
        <div class="card-body">
          <textarea id="floatingLogContainer" class="debug-log-text" readonly>No logs yet.</textarea>
          <div class="mini-help mt-2">RAM only. Clears on reboot. Serial Monitor at 115200 baud shows the live stream.</div>
        </div>
      </div>
    </div>
    <section class="row g-3 mb-3">
      <div class="col-lg-5">
        <div id="statusMsg" class="stat-card">Loading status...</div>
      </div>
      <div class="col-sm-6 col-lg-3">
        <div class="stat-card">
          <div class="stat-label">Controller Time</div>
          <div class="stat-value" id="timeCard">--:--:--</div>
          <div class="mini-help">Schedules use this clock.</div>
        </div>
      </div>
      <div class="col-sm-6 col-lg-2">
        <div class="stat-card">
          <div class="stat-label">Pumps</div>
          <div class="stat-value" id="pumpCountCard">--</div>
          <div class="mini-help">Configured outputs</div>
        </div>
      </div>
      <div class="col-lg-2">
        <div class="stat-card">
          <div class="stat-label">Schedules</div>
          <div class="stat-value" id="scheduleCountCard">--</div>
          <div class="mini-help">Enabled rows</div>
        </div>
      </div>
    </section>
    <div class="help-panel">
      <div class="d-flex align-items-start gap-3">
        <i class="bi bi-shield-check fs-3 text-primary"></i>
        <div class="help-copy">
          <h2 class="h5 mb-2">Start with water before dosing additives <span class="question" title="A pump must be calibrated so the controller knows how long to run it for each milliliter.">?</span></h2>
          <ol>
            <li>Prime the tube until liquid reaches the end of the dosing line.</li>
            <li>Run calibration into a cup for 60 seconds.</li>
            <li>Weigh the water in grams. For water, grams are almost the same as milliliters.</li>
            <li>Save that number with Save Calibration.</li>
            <li>Test a small manual dose before enabling schedules.</li>
          </ol>
        </div>
      </div>
    </div>
    <div id="pumpContainer"></div>
    <div class="panel p-3 mb-3">
      <h2 class="h5 section-title mb-2"><i class="bi bi-clock text-primary"></i> Timezone <span class="question" title="Schedules use this timezone. If the timezone is wrong, doses can happen at the wrong hour.">?</span></h2>
      <p class="mb-2">Current timezone: <span class="badge text-bg-light border" id="tzCurrent"></span></p>
      <div class="mb-3">
        <label for="tzSelect" class="form-label">Change timezone:</label>
        <select id="tzSelect" class="form-select">
          <!-- Options will be inserted via JavaScript -->
        </select>
        <div class="hint">Change this if scheduled doses happen at the wrong local time.</div>
      </div>
      <button class="btn btn-primary" onclick="saveTZ()" title="Stores the selected timezone in the ESP32 memory."><i class="bi bi-save"></i> Save Timezone</button>
    </div>
    <div class="panel p-3 mb-3">
      <h2 class="h5 section-title mb-2"><i class="bi bi-wifi text-primary"></i> WiFi Setup <span class="question" title="Use this only when you want the controller to forget the current WiFi and return to setup access point mode. Pump calibration and schedules are kept.">?</span></h2>
      <p class="mini-help">This clears WiFi only. Pump names, calibration values, and schedules stay saved.</p>
      <button class="btn btn-warning" onclick="resetWifi()" title="Forgets the WiFi network and reboots into AquaDose setup mode."><i class="bi bi-router"></i> Reset WiFi / Reenter Setup</button>
    </div>
    <div class="panel p-3 mb-3 border-danger">
      <h2 class="h5 section-title mb-2 text-danger"><i class="bi bi-exclamation-octagon"></i> Factory Reset <span class="question" title="This clears all AquaDose saved memory: WiFi, timezone, pump names, calibration rates, schedules, and schedule history.">?</span></h2>
      <p class="mini-help">This returns the controller to default setup mode and clears all saved pump settings. Use only when you want to start from zero.</p>
      <button class="btn btn-outline-danger" onclick="factoryReset()" title="Deletes all saved settings from ESP32 NVS and reboots into setup mode."><i class="bi bi-trash3"></i> Restore Factory Defaults</button>
    </div>
  </main>
  <div class="modal fade" id="aboutModal" tabindex="-1" aria-labelledby="aboutModalLabel" aria-hidden="true">
    <div class="modal-dialog modal-lg modal-dialog-centered modal-dialog-scrollable">
      <div class="modal-content">
        <div class="modal-header">
          <h2 class="modal-title h5" id="aboutModalLabel"><i class="bi bi-droplet-half text-primary"></i> About AquaDose</h2>
          <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close about"></button>
        </div>
        <div class="modal-body about-copy">
          <p>Hi, I’m Or.</p>
          <p>I come from a background in automation, software development, and hardware. I’m also a planted aquarium hobbyist.</p>
          <p>AquaDose started as a personal project after I realized how expensive dependable dosing systems can be. That cost can become a real barrier for people who want to enter the hobby or scale their aquariums properly.</p>
          <p>Instead of compromising, I decided to build a dosing system that is practical, accessible, and easy to understand.</p>
          <p>AquaDose is an open source, affordable, and modular dosing system that anyone can build, modify, and improve.</p>
          <p>This project is built by a hobbyist, for hobbyists.</p>
          <p class="tagline">Geek to geek.</p>
          <p><a class="about-link" href="https://www.linkedin.com/in/or-araha-05035aa5/" target="_blank" rel="noopener noreferrer"><i class="bi bi-linkedin"></i> Or Araha on LinkedIn</a></p>
        </div>
        <div class="modal-footer">
          <button type="button" class="btn btn-primary" data-bs-dismiss="modal">Close</button>
        </div>
      </div>
    </div>
  </div>
  <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js"></script>
  <script>
    const escapeHtml = (value) => {
      return String(value).replace(/[&<>"']/g, ch => ({
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#39;'
      }[ch]));
    };
    const schedulePanelOpen = {};
    const pumpDrafts = {};

    // Fetch current status from /status and render page
    window.loadStatus = async () => {
      const res = await fetch('/status');
      if (!res.ok) {
        document.getElementById('statusMsg').textContent = 'Failed to load status';
        return;
      }
      const data = await res.json();
      try {
      const runningPump = data.pumps && data.runningPump >= 0 ? data.pumps[data.runningPump] : null;
      const controllerTime = data.timeValid ? `Controller time: ${escapeHtml(data.currentTime)}` : 'Controller time is not synced yet - schedules will not run';
      const activeScheduleRows = data.pumps.reduce((total, pump) => total + pump.schedules.filter(s => s.enabled).length, 0);
      document.getElementById('statusMsg').innerHTML = data.running ?
        `<div class="stat-label">Pump Status</div><div class="status-line status-active mt-1"><i class="bi bi-activity"></i> <b>Active:</b> Pump ${data.runningPump + 1}${runningPump ? ' - ' + escapeHtml(runningPump.name) : ''}</div><div class="mini-help mt-1">Reason: ${escapeHtml(data.runningReason)}. ${controllerTime}</div>` :
        `<div class="stat-label">Pump Status</div><div class="status-line status-idle mt-1"><i class="bi bi-check-circle"></i> <b>All pumps idle.</b></div><div class="mini-help mt-1">${controllerTime}</div>`;
      document.getElementById('timeCard').textContent = data.timeValid ? data.currentTime : 'Not synced';
      document.getElementById('pumpCountCard').textContent = data.pumps.length;
      document.getElementById('scheduleCountCard').textContent = activeScheduleRows;
      // Render timezone
      document.getElementById('tzCurrent').textContent = data.tz;
      renderTZOptions(data.tz);
      // Render pumps
      const pc = document.getElementById('pumpContainer');
      pc.innerHTML = '';
      data.pumps.forEach(sourcePump => {
        const draft = pumpDrafts[sourcePump.id];
        const p = draft ? {
          ...sourcePump,
          enabled: draft.enabled,
          name: draft.name,
          mlPerSec: Number(draft.rate),
          schedules: sourcePump.schedules.map((schedule, idx) => draft.schedules[idx] ? {
            ...schedule,
            enabled: draft.schedules[idx].enabled,
            hour: draft.schedules[idx].hour,
            minute: draft.schedules[idx].minute,
            ml: draft.schedules[idx].ml
          } : schedule)
        } : sourcePump;
        const pumpRate = Number(p.mlPerSec);
        const rateIsConfigured = pumpRate >= 0.01;
        const rateValue = rateIsConfigured ? pumpRate.toFixed(4) : '';
        const ratePlaceholder = 'Not calibrated yet - use Save Calibration with grams of water from 60s test';
        const enabledScheduleCount = p.schedules.filter(s => s.enabled).length;
        const scheduleWarnings = [
          enabledScheduleCount > 0 && !p.enabled ? '<div class="alert alert-warning py-2"><i class="bi bi-exclamation-triangle"></i> Schedule rows are ON, but this pump is blocked because "Allow scheduled dosing for this pump" is OFF.</div>' : '',
          enabledScheduleCount > 0 && !rateIsConfigured ? '<div class="alert alert-warning py-2"><i class="bi bi-exclamation-triangle"></i> Schedule rows are ON, but this pump is not calibrated. It will not dose until calibration is saved.</div>' : ''
        ].join('');
        let schedulesHtml = '';
        p.schedules.forEach((s, idx) => {
          const scheduleSummary = s.enabled ? `${String(s.hour).padStart(2, '0')}:${String(s.minute).padStart(2, '0')} - ${s.ml} ml` : 'Off';
          schedulesHtml += `
            <div class="schedule-row ${s.enabled ? 'on' : 'off'}" id="p${p.id}s${idx}row">
              <div class="schedule-summary">
                <div class="form-check form-switch mb-0">
                <input class="form-check-input" type="checkbox" id="p${p.id}s${idx}en" ${s.enabled ? 'checked' : ''} onchange="toggleScheduleFields(${p.id}, ${idx})">
                <label class="form-check-label" title="Turn this single daily dose time on or off.">Schedule ${idx+1}</label>
                </div>
                <span class="schedule-off-note" id="p${p.id}s${idx}summary">${scheduleSummary}</span>
              </div>
              <div class="row g-2 schedule-fields mt-2">
                <div class="col-4">
                  <label class="form-label">Hour <span class="question" title="Use 24-hour time. Example: 18 means 6 PM.">?</span></label>
                  <input type="number" min="0" max="23" class="form-control" id="p${p.id}s${idx}h" value="${s.hour}" title="Hour of the day, from 0 to 23." oninput="toggleScheduleFields(${p.id}, ${idx})">
                </div>
                <div class="col-4">
                  <label class="form-label">Minute <span class="question" title="Minute of the hour. Example: 30 means half past the hour.">?</span></label>
                  <input type="number" min="0" max="59" class="form-control" id="p${p.id}s${idx}m" value="${s.minute}" title="Minute, from 0 to 59." oninput="toggleScheduleFields(${p.id}, ${idx})">
                </div>
                <div class="col-4">
                  <label class="form-label">ml <span class="question" title="How many milliliters this pump should dose at this time. Start small until you trust the setup.">?</span></label>
                  <input type="number" min="0.1" max="999" step="0.1" class="form-control" id="p${p.id}s${idx}ml" value="${s.ml}" title="Dose amount in milliliters, from 0.1 to 999." oninput="toggleScheduleFields(${p.id}, ${idx})">
                </div>
              </div>
            </div>
          `;
        });
        pc.innerHTML += `
          <div class="pump-card">
            <div class="pump-header">
              <div>
                <h2 class="h4 pump-title"><span class="pump-icon"><i class="bi bi-cpu"></i></span>${escapeHtml(p.name)} <span class="question" title="One dosing head. Give it a name like Alkalinity, Calcium, Magnesium, or Trace.">?</span></h2>
                <div class="mini-help mt-2">Prime the line, calibrate the pump, test manually, then enable schedules.</div>
              </div>
              <div class="text-lg-end">
                <span class="badge rounded-pill text-bg-${p.enabled ? 'success' : 'secondary'}">${p.enabled ? 'Scheduled dosing allowed' : 'Schedules blocked'}</span>
                <div class="mini-help mt-2">${rateIsConfigured ? pumpRate.toFixed(4) + ' ml/sec' : 'Not calibrated'}</div>
              </div>
            </div>
            <div class="mb-2">
              <span class="info-pill" title="This is the ESP32 pin connected to this pump driver's MOSFET gate."><i class="bi bi-lightning-charge text-warning"></i> Output: GPIO${p.pin}</span>
              <span class="info-pill" title="Pump numbering in this page starts at 1, but the firmware stores it internally from 0."><i class="bi bi-hash text-primary"></i> Pump ${p.id + 1}</span>
              <span class="info-pill" title="Enabled schedule rows for this pump."><i class="bi bi-calendar-check text-success"></i> ${enabledScheduleCount} schedules on</span>
            </div>
            <div class="form-check form-switch mb-3">
              <input class="form-check-input" type="checkbox" id="p${p.id}en" ${p.enabled ? 'checked' : ''} title="Allows this pump to run from its daily schedule rows. It does not start the pump immediately." onchange="rememberPumpDraft(${p.id})">
              <label class="form-check-label">Allow scheduled dosing for this pump <span class="question" title="This does not turn the pump on now. It only allows enabled schedule rows below to run at their saved time. Manual Dose, Prime, and Calibration still work when this is off.">?</span></label>
            </div>
            <div class="row g-3">
              <div class="col-lg-6">
                <label class="form-label">Pump name <span class="question" title="A friendly name for what this pump doses.">?</span></label>
                <input type="text" class="form-control" id="p${p.id}name" value="${escapeHtml(p.name)}" title="Example: Alkalinity, Calcium, Magnesium, Food, Trace." oninput="rememberPumpDraft(${p.id})">
              </div>
              <div class="col-lg-6">
                <label class="form-label">Pump speed after calibration <span class="question" title="This is calculated automatically from calibration. You normally do not type grams here. Run Calibrate 60s, collect water, then enter the collected grams in Save Calibration.">?</span></label>
                <input type="number" step="0.001" min="0" class="form-control" id="p${p.id}rate" value="${rateValue}" placeholder="${ratePlaceholder}" title="Calculated unit: ml/sec. Leave blank until you run calibration, or enter a known ml/sec value only if you already measured it." oninput="rememberPumpDraft(${p.id})">
                <div class="hint">${rateIsConfigured ? 'Configured flow rate. Unit is milliliters per second.' : 'Not calibrated yet. Use water: run Calibrate 60s, weigh the collected water in grams, then click Save Calibration.'}</div>
              </div>
            </div>
            <div class="calibration-guide">
              <div class="calibration-guide-title"><i class="bi bi-clipboard-check text-primary"></i> Calibration steps for this pump</div>
              <div class="calibration-steps">
                <div class="calibration-step">
                  <span class="step-number">1</span>
                  <span class="step-text"><b>Fill the tube</b>Click Start Prime until liquid reaches the end of the dosing line.</span>
                </div>
                <div class="calibration-step">
                  <span class="step-number">2</span>
                  <span class="step-text"><b>Stop priming</b>Click Stop Prime when the tube is full and no air remains.</span>
                </div>
                <div class="calibration-step">
                  <span class="step-number">3</span>
                  <span class="step-text"><b>Run the test</b>Put the outlet in a measuring cup and click Calibrate 60s.</span>
                </div>
                <div class="calibration-step">
                  <span class="step-number">4</span>
                  <span class="step-text"><b>Save the result</b>Weigh the collected water in grams, then click Save Calibration.</span>
                </div>
              </div>
            </div>
            <div class="d-flex flex-wrap gap-2 mb-3 action-row">
              <button class="btn btn-success" onclick="savePump(${p.id})" title="Saves this pump name, calibration rate, and schedule settings."><i class="bi bi-save"></i> Save Pump Settings</button>
              <button class="btn btn-primary" onclick="primeStart(${p.id})" title="Runs this pump until you press Stop Prime, or until the safety timeout. Use this to fill empty tubing."><i class="bi bi-play-fill"></i> Start Prime</button>
              <button class="btn btn-outline-primary" onclick="primeStop()" title="Stops priming or any currently running pump."><i class="bi bi-stop-fill"></i> Stop Prime</button>
              <button class="btn btn-warning" onclick="calRun(${p.id})" title="Runs the pump for exactly 60 seconds so you can measure how much water came out."><i class="bi bi-speedometer2"></i> Calibrate 60s</button>
              <button class="btn btn-outline-secondary" onclick="calSave(${p.id})" title="Enter the grams of water collected during the 60 second calibration run."><i class="bi bi-calculator"></i> Save Calibration</button>
              <button class="btn btn-info" onclick="manual(${p.id})" title="Runs a one-time dose now. Good for testing before enabling schedules."><i class="bi bi-droplet"></i> Manual Dose Now</button>
            </div>
            <details id="p${p.id}schedulePanel" class="schedule-details" ${schedulePanelOpen[p.id] !== undefined ? (schedulePanelOpen[p.id] ? 'open' : '') : (enabledScheduleCount > 0 ? 'open' : '')} ontoggle="rememberSchedulePanel(${p.id})">
              <summary><i class="bi bi-calendar-week"></i> Daily schedules (${enabledScheduleCount} on)</summary>
              <div class="hint mb-2">Open this only when you want automatic dosing. A schedule row only shows its time and ml fields after you turn that row on.</div>
              <div class="hint mb-2">Important: click Save Pump Settings after changing schedules. The pump also must be calibrated before scheduled dosing can run.</div>
              ${scheduleWarnings}
              ${schedulesHtml}
            </details>
          </div>
        `;
      });
      // Render RAM-only debug logs
      const lc = document.getElementById('floatingLogContainer');
      if (lc) {
        lc.value = data.logs.length ? data.logs.slice().reverse().join('\n') : 'No logs yet.';
      }
      } catch (err) {
        document.getElementById('statusMsg').innerHTML = `<b>Page render error:</b> ${escapeHtml(err.message)}`;
      }
    };
    // Render timezone options from the same list used on the server
    const renderTZOptions = (current) => {
      const tzList = [
        {label:'Asia/Jerusalem', posix:'IST-2IDT,M3.4.4/26,M10.5.0/25'},
        {label:'UTC', posix:'UTC0'},
        {label:'Europe/London', posix:'GMT0BST,M3.5.0/1,M10.5.0/2'},
        {label:'Europe/Berlin', posix:'CET-1CEST,M3.5.0,M10.5.0/3'},
        {label:'America/New York', posix:'EST+5EDT,M3.2.0/2,M11.1.0/2'},
        {label:'America/Los Angeles', posix:'PST+8PDT,M3.2.0/2,M11.1.0/2'},
        {label:'Asia/Tokyo', posix:'JST-9'},
        {label:'Australia/Sydney', posix:'AEST-10AEDT,M10.1.0,M4.1.0/3'}
      ];
      const sel = document.getElementById('tzSelect');
      sel.innerHTML = '';
      tzList.forEach(item => {
        const opt = document.createElement('option');
        opt.value = item.posix;
        opt.textContent = item.label;
        if (item.posix === current) opt.selected = true;
        sel.appendChild(opt);
      });
    };
    window.toggleScheduleFields = (pumpId, scheduleId) => {
      const enabled = document.getElementById(`p${pumpId}s${scheduleId}en`).checked;
      const row = document.getElementById(`p${pumpId}s${scheduleId}row`);
      const summary = document.getElementById(`p${pumpId}s${scheduleId}summary`);
      rememberPumpDraft(pumpId);
      row.classList.toggle('off', !enabled);
      row.classList.toggle('on', enabled);
      if (enabled) {
        const hour = document.getElementById(`p${pumpId}s${scheduleId}h`).value.padStart(2, '0');
        const minute = document.getElementById(`p${pumpId}s${scheduleId}m`).value.padStart(2, '0');
        const ml = document.getElementById(`p${pumpId}s${scheduleId}ml`).value;
        summary.textContent = `${hour}:${minute} - ${ml} ml`;
      } else {
        summary.textContent = 'Off';
      }
    };
    window.rememberSchedulePanel = (pumpId) => {
      const panel = document.getElementById(`p${pumpId}schedulePanel`);
      if (panel) schedulePanelOpen[pumpId] = panel.open;
    };
    window.rememberPumpDraft = (pumpId) => {
      const enabledEl = document.getElementById(`p${pumpId}en`);
      const nameEl = document.getElementById(`p${pumpId}name`);
      const rateEl = document.getElementById(`p${pumpId}rate`);
      if (!enabledEl || !nameEl || !rateEl) return;

      const schedules = [];
      for (let s = 0;; s++) {
        const enEl = document.getElementById(`p${pumpId}s${s}en`);
        if (!enEl) break;
        schedules.push({
          enabled: enEl.checked,
          hour: document.getElementById(`p${pumpId}s${s}h`).value,
          minute: document.getElementById(`p${pumpId}s${s}m`).value,
          ml: document.getElementById(`p${pumpId}s${s}ml`).value
        });
      }

      pumpDrafts[pumpId] = {
        enabled: enabledEl.checked,
        name: nameEl.value,
        rate: rateEl.value,
        schedules
      };
    };
    const requestAction = async (url, successMessage = '') => {
      const res = await fetch(url);
      const text = await res.text();
      if (!res.ok) {
        alert(text || `Request failed (${res.status})`);
        await loadStatus();
        return false;
      }
      if (successMessage) alert(successMessage);
      return true;
    };
    // Save timezone selection
    window.saveTZ = async () => {
      const tz = document.getElementById('tzSelect').value;
      if (!await requestAction(`/saveTZ?tz=${encodeURIComponent(tz)}`, 'Timezone saved.  Will apply on next reload.')) return;
      loadStatus();
    };
    // Save pump configuration
    window.savePump = async (id) => {
      rememberPumpDraft(id);
      const qs = new URLSearchParams();
      qs.append('p', id);
      qs.append('name', document.getElementById(`p${id}name`).value);
      qs.append('enabled', document.getElementById(`p${id}en`).checked ? '1' : '0');
      qs.append('rate', document.getElementById(`p${id}rate`).value);
      // Determine number of schedules by probing for input elements.  We
      // loop until an element with the expected id is not found.  This
      // avoids relying on a compileâ€‘time macro in JavaScript.
      for (let s = 0;; s++) {
        const enEl = document.getElementById(`p${id}s${s}en`);
        if (!enEl) break;
        qs.append(`s${s}en`, enEl.checked ? '1' : '0');
        qs.append(`s${s}h`, document.getElementById(`p${id}s${s}h`).value);
        qs.append(`s${s}m`, document.getElementById(`p${id}s${s}m`).value);
        qs.append(`s${s}ml`, document.getElementById(`p${id}s${s}ml`).value);
      }
      if (!await requestAction('/savePump?' + qs.toString(), 'Pump settings saved.')) return;
      delete pumpDrafts[id];
      await loadStatus();
    };
    // Start indefinite prime on selected pump
    window.primeStart = async (id) => {
      if (!await requestAction(`/primeStart?p=${id}`)) return;
      await loadStatus();
    };
    // Stop prime
    window.primeStop = async () => {
      if (!await requestAction('/primeStop')) return;
      await loadStatus();
    };
    // Start calibration run (60 seconds)
    window.calRun = async (id) => {
      if (!confirm('Run pump for 60 seconds for calibration? Make sure a measuring vessel is ready.')) return;
      if (!await requestAction(`/calRun?p=${id}`)) return;
      alert('Calibration run started.  After 60 seconds, measure the water in grams and click Save Calibration.');
      await loadStatus();
    };
    // Save calibration result
    window.calSave = async (id) => {
      const grams = prompt('Enter measured water weight in grams:', '60');
      if (!grams) return;
      if (!await requestAction(`/calSave?p=${id}&grams=${encodeURIComponent(grams)}`, 'Calibration saved.')) return;
      delete pumpDrafts[id];
      await loadStatus();
    };
    // Start a manual dose
    window.manual = async (id) => {
      const ml = prompt('Enter dose volume in ml (0.1 to 999):', '5');
      if (!ml) return;
      if (!await requestAction(`/manual?p=${id}&ml=${encodeURIComponent(ml)}`)) return;
      await loadStatus();
    };
    // Emergency stop
    window.stopAll = async () => {
      if (!await requestAction('/stop')) return;
      await loadStatus();
    };
    // Reset WiFi
    window.resetWifi = async () => {
      if (!confirm('Reset WiFi settings and reboot into setup mode?')) return;
      await fetch('/reset');
      alert('Device will reboot into setup mode.');
    };
    // Factory reset all saved settings
    window.factoryReset = async () => {
      const warning = 'Factory reset will delete ALL saved AquaDose settings: WiFi, timezone, pump names, calibration rates, schedules, and schedule history. The controller will reboot into setup mode. Continue?';
      if (!confirm(warning)) return;
      if (!confirm('Last confirmation: this cannot be undone from the web page. Restore factory defaults now?')) return;
      await fetch('/factoryReset');
      alert('Factory reset started. The controller will reboot into setup mode.');
    };
    // Periodically refresh status
    setInterval(loadStatus, 10000);
    // Initial load
    loadStatus();
  </script>
</body>
</html>
)HTML";
  return html;
}

// -----------------------------------------------------------------------------
// HTTP request handlers
// -----------------------------------------------------------------------------

/*
 * Handler for root URL.  Determines whether to serve the setup page
 * (when not configured or in setup mode) or the main management page
 * (when connected to WiFi).  This keeps the logic in one place.
 */
void handleRoot() {
  if (setupMode) {
    server.send(200, "text/html", htmlSetupPage());
  } else {
    server.send(200, "text/html", htmlMainPage());
  }
}

/*
 * Handler for saving WiFi credentials and timezone.  Expects
 * parameters "ssid", "pass", and "tz" via GET.  Saves values to
 * NVS and triggers a reboot.  Only used in setup mode.
 */
void handleWifiSave() {
  String ssid = server.arg("ssid");
  String pass = server.arg("pass");
  String tz = server.arg("tz");
  if (ssid.length() == 0 || pass.length() == 0) {
    serialLogf("WEB /wifiSave rejected: missing SSID or password");
    server.send(400, "text/plain", "Missing SSID or password");
    return;
  }
  if (tz.length() == 0) {
    tz = "UTC0";
  }
  saveWifiAndTZ(ssid, pass, tz);
  serialLogf("WEB /wifiSave: saved pending SSID '%s', timezone '%s', rebooting", ssid.c_str(), tz.c_str());
  server.send(200, "text/plain", "Credentials saved. Rebooting...");
  delay(1000);
  ESP.restart();
}

/*
 * Handler for resetting WiFi and timezone settings.  Clears
 * credentials and restarts the controller.  Accessible only from
 * management page.
 */
void handleReset() {
  serialLogf("WEB /reset: clearing WiFi settings and rebooting");
  resetWifiSettings();
  server.send(200, "text/plain", "WiFi reset. Rebooting into setup mode...");
  delay(1000);
  ESP.restart();
}

/*
 * Handler for factory reset.  Clears the full AquaDose NVS namespace:
 * WiFi, timezone, pump names, calibration values, schedules, and history.
 */
void handleFactoryReset() {
  serialLogf("WEB /factoryReset: clearing all saved settings and rebooting");
  factoryResetSettings();
  server.send(200, "text/plain", "Factory reset complete. Rebooting into setup mode...");
  delay(1000);
  ESP.restart();
}

/*
 * Handler for /status endpoint.  Returns JSON describing the
 * controller state: whether a pump is running, which pump, the
 * running reason, timezone, pump configs and recent logs.  This is
 * consumed by the JavaScript in the web page.
 */
void handleStatus() {
  String json = "{";
  json += "\"running\":" + String(runState.running ? "true" : "false") + ",";
  json += "\"runningPump\":" + String(runState.pump) + ",";
  json += "\"runningReason\":\"" + jsonEscape(String(runState.reason)) + "\",";
  json += "\"tz\":\"" + jsonEscape(tzString) + "\",";
  struct tm statusTime;
  if (getLocalTime(&statusTime, 10)) {
    char currentTime[20];
    snprintf(currentTime, sizeof(currentTime), "%02d:%02d:%02d", statusTime.tm_hour, statusTime.tm_min, statusTime.tm_sec);
    json += "\"timeValid\":true,";
    json += "\"currentTime\":\"" + String(currentTime) + "\",";
  } else {
    json += "\"timeValid\":false,";
    json += "\"currentTime\":\"not synced\",";
  }
  // Pumps array
  json += "\"pumps\":[";
  for (int p = 0; p < PUMP_COUNT; p++) {
    if (p > 0) json += ",";
    json += "{";
    json += "\"id\":" + String(p) + ",";
    json += "\"pin\":" + String(PUMP_PINS[p]) + ",";
    json += "\"name\":\"" + jsonEscape(String(pumps[p].name)) + "\",";
    json += "\"enabled\":" + String(pumps[p].enabled ? "true" : "false") + ",";
    json += "\"mlPerSec\":" + String(pumps[p].mlPerSec, 4) + ",";
    json += "\"schedules\":[";
    for (int s = 0; s < MAX_SCHEDULES; s++) {
      if (s > 0) json += ",";
      json += "{";
      json += "\"enabled\":" + String(pumps[p].schedules[s].enabled ? "true" : "false") + ",";
      json += "\"hour\":" + String(pumps[p].schedules[s].hour) + ",";
      json += "\"minute\":" + String(pumps[p].schedules[s].minute) + ",";
      json += "\"ml\":" + String(pumps[p].schedules[s].ml, 2);
      json += "}";
    }
    json += "]";
    json += "}";
  }
  json += "],";
  // Logs array
  json += "\"logs\":[";
  bool first = true;
  for (int i = 0; i < LOG_COUNT; i++) {
    int slot = (ramLogNext + i) % LOG_COUNT;
    String line = ramLogs[slot];
    if (line.length() == 0) continue;
    if (!first) json += ",";
    first = false;
    json += "\"" + jsonEscape(line) + "\"";
  }
  json += "]";
  json += "}";
  server.send(200, "application/json", json);
}

/*
 * Handler for saving pump configuration.  Expects parameters:
 *  p = pump index
 *  name = pump name
 *  enabled = "1" if schedules enabled
 *  rate = calibration ml/sec
 *  sXen, sXh, sXm, sXml for each schedule index X
 */
void handleSavePump() {
  int p = -1;
  if (!readPumpArg(p)) {
    serialLogf("WEB /savePump rejected: invalid pump index");
    server.send(400, "text/plain", "Invalid pump index");
    return;
  }
  String name = server.arg("name");
  name.trim();
  if (name.length() == 0) {
    name = "Pump " + String(p + 1);
  }
  float rate = server.arg("rate").toFloat();
  if (rate < 0.0f || rate > 100.0f) {
    serialLogf("WEB /savePump rejected: pump %d invalid rate %.4f", p + 1, rate);
    server.send(400, "text/plain", "Invalid calibration rate");
    return;
  }
  pumps[p].enabled = (server.arg("enabled") == "1");
  pumps[p].mlPerSec = rate;
  name.toCharArray(pumps[p].name, sizeof(pumps[p].name));
  for (int s = 0; s < MAX_SCHEDULES; s++) {
    String prefix = "s" + String(s);
    bool oldEnabled = pumps[p].schedules[s].enabled;
    int oldHour = pumps[p].schedules[s].hour;
    int oldMinute = pumps[p].schedules[s].minute;
    float oldMl = pumps[p].schedules[s].ml;
    bool newEnabled = (server.arg(prefix + "en") == "1");
    int rawHour = server.arg(prefix + "h").toInt();
    int rawMinute = server.arg(prefix + "m").toInt();
    float scheduleMl = server.arg(prefix + "ml").toFloat();
    if (newEnabled) {
      if (!readIntArgStrict(prefix + "h", rawHour) || rawHour < 0 || rawHour > 23) {
        serialLogf("WEB /savePump rejected: pump %d slot %d invalid hour", p + 1, s + 1);
        server.send(400, "text/plain", "Invalid schedule hour");
        return;
      }
      if (!readIntArgStrict(prefix + "m", rawMinute) || rawMinute < 0 || rawMinute > 59) {
        serialLogf("WEB /savePump rejected: pump %d slot %d invalid minute", p + 1, s + 1);
        server.send(400, "text/plain", "Invalid schedule minute");
        return;
      }
      if (!readFloatArgStrict(prefix + "ml", scheduleMl) || scheduleMl < MIN_DOSE_ML || scheduleMl > MAX_DOSE_ML) {
        serialLogf("WEB /savePump rejected: pump %d slot %d invalid volume", p + 1, s + 1);
        server.send(400, "text/plain", "Invalid schedule volume");
        return;
      }
    }
    int newHour = clampInt(rawHour, 0, 23);
    int newMinute = clampInt(rawMinute, 0, 59);
    float newMl = (scheduleMl >= MIN_DOSE_ML && scheduleMl <= MAX_DOSE_ML) ? scheduleMl : 1.0f;
    pumps[p].schedules[s].enabled = newEnabled;
    pumps[p].schedules[s].hour = newHour;
    pumps[p].schedules[s].minute = newMinute;
    pumps[p].schedules[s].ml = newMl;
    if (oldEnabled != newEnabled || oldHour != newHour || oldMinute != newMinute || fabs(oldMl - newMl) > 0.001f) {
      pumps[p].schedules[s].lastRunDay = -1;
    }
  }
  savePumpConfig(p);
  serialLogf("WEB /savePump: pump %d (%s), enabled=%s, rate=%.4f ml/sec",
             p + 1, pumps[p].name, pumps[p].enabled ? "yes" : "no", pumps[p].mlPerSec);
  server.send(200, "text/plain", "Saved pump configuration");
}

/*
 * Handler for manual dosing.  Expects parameters p (pump index) and ml
 * (milliliters).  Starts pump for calculated duration.
 */
void handleManual() {
  int p = -1;
  if (!readPumpArg(p)) {
    serialLogf("WEB /manual rejected: invalid pump index");
    server.send(400, "text/plain", "Invalid pump index");
    return;
  }
  float ml = server.arg("ml").toFloat();
  if (ml < MIN_DOSE_ML || ml > MAX_DOSE_ML) {
    serialLogf("WEB /manual rejected: pump %d invalid volume %.3f ml", p + 1, ml);
    server.send(400, "text/plain", "Invalid dose volume");
    return;
  }
  if (runState.running) {
    server.send(409, "text/plain", "Another pump is already running");
    return;
  }
  float rate = pumps[p].mlPerSec;
  if (rate < 0.01f) {
    serialLogf("WEB /manual rejected: pump %d is not calibrated", p + 1);
    server.send(409, "text/plain", "Pump is not calibrated. Run Calibrate 60s and Save Calibration first.");
    return;
  }
  unsigned long durationMs = (unsigned long)((ml / rate) * 1000.0f);
  if (durationMs > 120000UL) {
    serialLogf("WEB /manual rejected: pump %d dose %.2f ml would run too long (%lu ms)", p + 1, ml, durationMs);
    server.send(409, "text/plain", "Dose would run longer than the 120 second safety limit. Reduce ml or recalibrate the pump.");
    return;
  }
  if (!startPumpMl(p, ml, "manual")) {
    server.send(409, "text/plain", "Could not start manual dose. Check logs for details.");
  } else {
    server.send(200, "text/plain", "Manual dosing started");
  }
}

/*
 * Handler for starting indefinite priming.  Expects parameter p (pump
 * index).  Starts pump indefinitely until /primeStop is called or
 * timeout occurs.
 */
void handlePrimeStart() {
  int p = -1;
  if (!readPumpArg(p)) {
    serialLogf("WEB /primeStart rejected: invalid pump index");
    server.send(400, "text/plain", "Invalid pump index");
    return;
  }
  if (runState.running) {
    server.send(409, "text/plain", "Another pump is already running");
    return;
  }
  if (!startPumpSeconds(p, 0, "prime")) {
    server.send(409, "text/plain", "Could not start prime. Check logs for details.");
  } else {
    server.send(200, "text/plain", "Prime started");
  }
}

/*
 * Handler for stopping prime or any running pump.
 */
void handlePrimeStop() {
  serialLogf("WEB /primeStop requested");
  stopAllPumps();
  server.send(200, "text/plain", "Prime stopped");
}

/*
 * Handler for starting a 60 second calibration run.  Expects p
 * parameter.  After run completes, the user must measure the volume
 * dispensed and call /calSave.
 */
void handleCalRun() {
  int p = -1;
  if (!readPumpArg(p)) {
    serialLogf("WEB /calRun rejected: invalid pump index");
    server.send(400, "text/plain", "Invalid pump index");
    return;
  }
  if (runState.running) {
    server.send(409, "text/plain", "Another pump is already running");
    return;
  }
  if (!startPumpSeconds(p, 60, "calib")) {
    server.send(409, "text/plain", "Could not start calibration. Check logs for details.");
  } else {
    server.send(200, "text/plain", "Calibration run started");
  }
}

/*
 * Handler for saving calibration.  Expects p (pump index) and grams
 * (measured weight of water).  Computes ml/sec as grams/60 and
 * updates the pump.  Note: calibration assumes water with density
 * approx. 1Â g/ml.  For other liquids adjust accordingly.
 */
void handleCalSave() {
  int p = -1;
  if (!readPumpArg(p)) {
    serialLogf("WEB /calSave rejected: invalid pump index");
    server.send(400, "text/plain", "Invalid pump index");
    return;
  }
  float grams = server.arg("grams").toFloat();
  if (grams <= 0.0f || grams > 6000.0f) {
    serialLogf("WEB /calSave rejected: pump %d invalid grams %.3f", p + 1, grams);
    server.send(400, "text/plain", "Invalid calibration data");
    return;
  }
  pumps[p].mlPerSec = grams / 60.0f;
  savePumpConfig(p);
  serialLogf("WEB /calSave: pump %d (%s), %.2f g over 60 sec -> %.4f ml/sec",
             p + 1, pumps[p].name, grams, pumps[p].mlPerSec);
  server.send(200, "text/plain", "Calibration saved");
}

/*
 * Handler for saving timezone from the main page.  Expects parameter
 * tz (POSIX string).  Updates tzString, saves to NVS and applies via
 * tzset().  Does not reboot automatically.
 */
void handleSaveTZ() {
  String tz = server.arg("tz");
  if (tz.length() == 0) {
    serialLogf("WEB /saveTZ rejected: missing timezone");
    server.send(400, "text/plain", "Missing timezone");
    return;
  }
  prefs.begin(NVS_NAMESPACE, false);
  prefs.putString("tz", tz);
  prefs.end();
  tzString = tz;
  setenv("TZ", tzString.c_str(), 1);
  tzset();
  serialLogf("WEB /saveTZ: timezone set to '%s'", tzString.c_str());
  server.send(200, "text/plain", "Timezone saved");
}

/*
 * Handler for emergency stop.  Stops all pumps immediately.
 */
void handleStop() {
  serialLogf("WEB /stop requested");
  stopAllPumps();
  server.send(200, "text/plain", "Stopped");
}

// -----------------------------------------------------------------------------
// WiFi management
// -----------------------------------------------------------------------------

/*
 * Attempt to connect to the saved WiFi network.  Returns true if
 * connection succeeds within the timeout, false otherwise.  While
 * connecting, the watchdog timer is periodically fed to prevent
 * resets.
 */
bool connectToWiFi() {
  if (!wifiConfigured) return false;
  serialLogf("WiFi: connecting to SSID '%s'", wifiSSID.c_str());
  WiFi.mode(WIFI_STA);
  WiFi.begin(wifiSSID.c_str(), wifiPass.c_str());
  unsigned long start = millis();
  while (WiFi.status() != WL_CONNECTED && (millis() - start) < WIFI_CONNECT_TIMEOUT_MS) {
    delay(100);
    // Feed watchdog to avoid resets
    esp_task_wdt_reset();
  }
  bool connected = (WiFi.status() == WL_CONNECTED);
  if (connected) {
    serialLogf("WiFi: connected, IP=%s, RSSI=%d dBm", WiFi.localIP().toString().c_str(), WiFi.RSSI());
    if (wifiPendingValidation) {
      serialLogf("WiFi: new credentials validated and will be kept");
      markWifiSettingsValidated();
    }
  } else {
    serialLogf("WiFi: connection failed after %lu ms, starting setup AP", WIFI_CONNECT_TIMEOUT_MS);
    if (wifiPendingValidation) {
      discardPendingWifiSettings();
    }
  }
  return connected;
}

void syncTimeFromNTP() {
  if (WiFi.status() != WL_CONNECTED) {
    serialLogf("Time: NTP sync skipped, WiFi is not connected");
    return;
  }
  serialLogf("Time: syncing via NTP, timezone=%s", tzString.c_str());
  configTzTime(tzString.c_str(), NTP_SERVER_1, NTP_SERVER_2);

  struct tm t;
  unsigned long start = millis();
  while (!getLocalTime(&t, 250) && (millis() - start) < 5000UL) {
    esp_task_wdt_reset();
    delay(50);
  }
  if (getLocalTime(&t, 10)) {
    serialLogf("Time: NTP sync OK");
  } else {
    serialLogf("Time: NTP sync failed; schedules will wait for valid time");
  }
}

void updateStatusLed() {
  static unsigned long lastBlinkMs = 0;
  static bool blinkOn = false;
  static int lastNetworkOk = -1;

  bool networkOk = (!setupMode && WiFi.status() == WL_CONNECTED);
  if (lastNetworkOk != (networkOk ? 1 : 0)) {
    serialLogf("LED: network status is %s, LED %s",
               networkOk ? "OK" : "NOT READY",
               networkOk ? "solid ON" : "blinking");
    lastNetworkOk = networkOk ? 1 : 0;
  }

  if (networkOk) {
    blinkOn = true;
    setStatusLed(true);
    return;
  }

  unsigned long now = millis();
  if (now - lastBlinkMs >= STATUS_LED_BLINK_MS) {
    lastBlinkMs = now;
    blinkOn = !blinkOn;
    setStatusLed(blinkOn);
  }
}

/*
 * Start SoftAP for setup mode.  Generates SSID using base and
 * chipId.  This should be called when wifiConfigured is false or
 * connection fails.
 */
void startSoftAP() {
  uint32_t chipId = ESP.getEfuseMac() & 0xFFFFFF;
  String apSSID = String(SOFT_AP_SSID_BASE) + "-" + String(chipId, HEX);
  WiFi.mode(WIFI_AP);
  WiFi.softAP(apSSID.c_str(), SOFT_AP_PASSWORD);
  serialLogf("WiFi: setup AP started, SSID=%s, IP=%s", apSSID.c_str(), WiFi.softAPIP().toString().c_str());
}

/*
 * Check if the BOOT button (GPIO0) is held low at startup to
 * trigger factory reset.  Press and hold during boot for at least
 * 5Â seconds to clear settings.  This allows recovery if WiFi
 * credentials are incorrect or the router is replaced.
 */
void checkBootReset() {
  pinMode(0, INPUT_PULLUP);
  unsigned long pressed = 0;
  // Wait 5 seconds at startup for user to hold the button
  unsigned long start = millis();
  while (millis() - start < 5000) {
    if (digitalRead(0) == LOW) {
      if (pressed == 0) pressed = millis();
      if (millis() - pressed >= 4000) {
        // Held for 4 seconds â€“ reset settings
        factoryResetSettings();
        // Provide small delay and restart
        delay(200);
        ESP.restart();
        return;
      }
    } else {
      pressed = 0;
    }
    delay(10);
  }
}

/*
 * Initialize the ESP32 task watchdog.
 *
 * Arduino-ESP32 3.x is based on ESP-IDF 5.x, where esp_task_wdt_init()
 * no longer accepts (timeoutSeconds, triggerPanic). It now requires an
 * esp_task_wdt_config_t pointer. The compatibility wrapper below keeps
 * the sketch buildable on both Arduino-ESP32 2.x and 3.x cores.
 */
void setupWatchdog() {
#if ESP_IDF_VERSION_MAJOR >= 5
  esp_task_wdt_config_t wdtConfig = {};
  wdtConfig.timeout_ms = (uint32_t)WATCHDOG_TIMEOUT_SEC * 1000UL;
  wdtConfig.idle_core_mask = (1 << portNUM_PROCESSORS) - 1;
  wdtConfig.trigger_panic = true;

  esp_err_t err = esp_task_wdt_init(&wdtConfig);
  if (err == ESP_ERR_INVALID_STATE) {
    // The Arduino core may initialize TWDT before setup(); update it instead.
    err = esp_task_wdt_reconfigure(&wdtConfig);
  }

  if (err != ESP_OK && err != ESP_ERR_INVALID_STATE) {
    Serial.printf("Watchdog init failed: %d\n", (int)err);
    return;
  }
#else
  esp_err_t err = esp_task_wdt_init(WATCHDOG_TIMEOUT_SEC, true);
  if (err != ESP_OK && err != ESP_ERR_INVALID_STATE) {
    Serial.printf("Watchdog init failed: %d\n", (int)err);
    return;
  }
#endif

  err = esp_task_wdt_add(NULL);  // Watch the Arduino loop task.
  if (err != ESP_OK && err != ESP_ERR_INVALID_STATE) {
    Serial.printf("Watchdog task add failed: %d\n", (int)err);
  }
}

// -----------------------------------------------------------------------------
// Setup and main loop
// -----------------------------------------------------------------------------

void setup() {
  Serial.begin(SERIAL_BAUD);
  delay(200);
  serialLogf("Boot: AquaDose starting on ESP32");
  serialLogf("Boot: pump count=%d, serial baud=%lu", PUMP_COUNT, SERIAL_BAUD);
  pinMode(STATUS_LED_PIN, OUTPUT);
  setStatusLed(false);
  serialLogf("GPIO: status LED assigned to GPIO%d", STATUS_LED_PIN);
  // Initialize pump GPIOs
  for (int i = 0; i < PUMP_COUNT; i++) {
    pinMode(PUMP_PINS[i], OUTPUT);
    digitalWrite(PUMP_PINS[i], LOW);
    serialLogf("GPIO: pump %d assigned to GPIO%d", i + 1, PUMP_PINS[i]);
  }
  // Enable watchdog timer
  setupWatchdog();
  // Check for factory reset via BOOT pin
  checkBootReset();
  // Load settings
  loadWifiAndTZ();
  loadPumpConfig();
  serialLogf("Config: timezone=%s, WiFi configured=%s, pending validation=%s",
             tzString.c_str(), wifiConfigured ? "yes" : "no", wifiPendingValidation ? "yes" : "no");
  for (int i = 0; i < PUMP_COUNT; i++) {
    serialLogf("Config: pump %d name='%s', enabled=%s, rate=%.4f ml/sec",
               i + 1, pumps[i].name, pumps[i].enabled ? "yes" : "no", pumps[i].mlPerSec);
  }
  // Attempt WiFi connection if configured
  if (!setupMode) {
    bool connected = connectToWiFi();
    if (!connected) {
      setupMode = true;
    }
  }
  // Start appropriate network mode
  if (setupMode) {
    startSoftAP();
    Serial.println("Entered setup mode (SoftAP)");
    Serial.print("Connect to SSID: ");
    Serial.println(WiFi.softAPSSID());
    Serial.print("Access point IP: ");
    Serial.println(WiFi.softAPIP());
  } else {
    Serial.print("Connected to WiFi network ");
    Serial.println(WiFi.SSID());
    Serial.print("IP address: ");
    Serial.println(WiFi.localIP());
    syncTimeFromNTP();
  }
  // Configure HTTP handlers
  server.on("/", handleRoot);
  server.on("/wifiSave", handleWifiSave);
  server.on("/reset", handleReset);
  server.on("/factoryReset", handleFactoryReset);
  server.on("/status", handleStatus);
  server.on("/savePump", handleSavePump);
  server.on("/manual", handleManual);
  server.on("/primeStart", handlePrimeStart);
  server.on("/primeStop", handlePrimeStop);
  server.on("/calRun", handleCalRun);
  server.on("/calSave", handleCalSave);
  server.on("/saveTZ", handleSaveTZ);
  server.on("/stop", handleStop);
  server.begin();
  Serial.println("HTTP server started");
}

// Variables for schedule and watchdog timing
static unsigned long lastScheduleCheck = 0;
static unsigned long lastWatchdogFeed = 0;

void loop() {
  // Handle web requests
  server.handleClient();
  // Update pump runner
  updatePumpRunner();
  updateStatusLed();
  // Check schedules every minute (60000ms) only in nonâ€‘setup mode
  if (!setupMode && (millis() - lastScheduleCheck >= SCHEDULE_CHECK_INTERVAL_MS)) {
    lastScheduleCheck = millis();
    checkSchedules();
  }
  // Feed watchdog periodically (each second)
  if (millis() - lastWatchdogFeed >= 1000UL) {
    lastWatchdogFeed = millis();
    esp_task_wdt_reset();
  }
}
