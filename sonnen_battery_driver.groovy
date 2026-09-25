metadata {
    definition(
        name: "Sonnen Battery (Optimized Polling)",
        namespace: "ke7lvb",
        author: "Ryan Lundell"
    ) {
        capability "PowerSource"
        capability "PowerMeter"
        capability "Battery"
        capability "Actuator"
        capability "Refresh"
        capability "EnergyMeter"

        attribute "energy", "number"
        attribute "BackupBuffer", "number"
        attribute "Consumption_W", "number"
        attribute "GridFeedIn_W", "number"
        attribute "OperatingMode", "number"
        attribute "Pac_total_W", "number"
        attribute "Production_W", "number"
        attribute "SystemStatus", "string"
        attribute "MinutesToCharge", "number"
        attribute "MinutesToDischarge", "number"
        attribute "FullChargeCapacity", "number"
        attribute "RemainingCapacity_Wh", "number"
        attribute "healthStatus", "enum", ["unknown", "offline", "online"]
        attribute "StateOfHealth", "number"
        attribute "CycleCount", "number"
        attribute "MaxCellTemperature", "number"
        attribute "MinCellTemperature", "number"


        attribute "flow_tile_large", "string"
        attribute "flow_tile_small", "string"

        command "setBackupBuffer", [[name: "Backup Buffer*", type: "NUMBER"]]
    }

    preferences {
        input name: "logEnable", type: "bool", title: "Enable logging", defaultValue: true
        input name: "battery_ip_address", type: "string", title: "Sonnen battery LAN IP", required: true

        input("refresh_interval", "enum", title: "Refresh Interval", required: true, defaultValue: "0", options: [
            0: "Do NOT update",
            10: "Every 10 seconds / 10 minutes / 4 hours",
            15: "Every 15 seconds / 15 minutes / 6 hours",
            20: "Every 20 seconds / 20 minutes / 8 hours",
            30: "Every 30 seconds / 30 minutes / 12 hours",
            1: "Every 60 seconds / 60 minutes / 24 hours"
        ])

        input("refresh_interval_unit", "enum", title: "Refresh Interval Unit", required: true, defaultValue: "1", options: [
            1: "Seconds",
            2: "Minutes",
            3: "Hours"
        ])

        input name: "flowTiles", type: "bool", title: "Display Flow Tiles", defaultValue: false
        input name: "enableChildDevices", type: "bool", title: "Enable Child Devices", defaultValue: false
        input name: "apiKey", type: "string", title: "API Key", required: true
    }
}

def installed() {
    if (logEnable) log.info "Driver installed"
    state.version = "1.6.0"
}

def updated() {
    if (logEnable) log.info "Settings updated"
    unschedule()

    if (settings.refresh_interval != "0") {
        scheduleRefreshJob()
    } else {
        if (logEnable) log.info "Polling disabled"
    }

    refreshDaily()

    if (!enableChildDevices) removeChildDevices()

    state.remove("USOC")
    state.remove("BatteryCharging")
    state.remove("BatteryDischarging")
    state.remove("FlowGridBattery")
    state.version = "1.6.0"
}

def scheduleRefreshJob() {
    def interval = settings.refresh_interval
    def unit = settings.refresh_interval_unit

    if (unit == "1") { // seconds
        if (interval == "1") {
            schedule("13 */1 * ? * * *", refresh)
        } else {
            schedule("3/${interval} * * ? * * *", refresh)
        }
    } else if (unit == "2") { // minutes
        if (interval == "1") {
            schedule("13 1 */1 ? * * *", refresh)
        } else {
            schedule("13 */${interval} * ? * * *", refresh)
        }
    } else { // hours
        if (interval == "1") {
            schedule("13 1 12 ? * * *", refresh)
        } else {
            schedule("13 1 */${interval} ? * * *", refresh)
        }
    }

    if (logEnable) log.info "Polling scheduled"
}

/* ---------------------------------------------------------
   MAIN REFRESH — /status ONLY (async, no API key needed)
--------------------------------------------------------- */
def refresh() {
    asynchttpGet("handleStatus", [
        uri: "http://${battery_ip_address}/api/v2/status",
        contentType: "application/json",
        timeout: 10
    ])

    // Capacity and battery health change slowly: refresh at most once a day
    def dayMs = 24 * 60 * 60 * 1000
    if (state.FullChargeCapacity == null || now() - (state.lastDailyCheck ?: 0) > dayMs) {
        refreshDaily()
    }
}

def handleStatus(resp, data) {
    if (resp.hasError()) {
        log.error "Error calling status: ${resp.getErrorMessage()}"
        markFailure()
        return
    }
    if (resp.status != 200) {
        log.error "Error calling status: HTTP ${resp.status}"
        markFailure()
        return
    }

    markOnline()
    def status = resp.json
    processStatus(status)

    if (flowTiles) updateTiles(status)
    if (enableChildDevices) updateChildDevices(status)
    if (state.FullChargeCapacity) estimateCharge(status)
}

/* ---------------------------------------------------------
   HEALTH — OFFLINE AFTER CONSECUTIVE FAILED POLLS
--------------------------------------------------------- */
def markOnline() {
    if (state.failCount) state.failCount = 0
    sendEvent(name: "healthStatus", value: "online")
}

def markFailure() {
    state.failCount = (state.failCount ?: 0) + 1
    if (state.failCount >= 3) sendEvent(name: "healthStatus", value: "offline")
}

/* ---------------------------------------------------------
   DAILY — CAPACITY (/latestdata) AND BATTERY HEALTH (/battery)
   (async, requires API key)
--------------------------------------------------------- */
def refreshDaily() {
    if (!battery_ip_address || !apiKey) return
    state.lastDailyCheck = now()

    asynchttpGet("handleLatest", [
        uri: "http://${battery_ip_address}/api/v2/latestdata",
        contentType: "application/json",
        headers: ['Auth-Token': apiKey],
        timeout: 10
    ])

    asynchttpGet("handleBattery", [
        uri: "http://${battery_ip_address}/api/v2/battery",
        contentType: "application/json",
        headers: ['Auth-Token': apiKey],
        timeout: 10
    ])
}

def handleLatest(resp, data) {
    if (resp.hasError()) {
        log.error "Error calling latestdata: ${resp.getErrorMessage()}"
        return
    }
    if (resp.status != 200) {
        log.error "Error calling latestdata: HTTP ${resp.status}"
        return
    }

    def latest = resp.json
    if (latest?.FullChargeCapacity != null) {
        state.FullChargeCapacity = latest.FullChargeCapacity
        sendEvent(name: "FullChargeCapacity", value: state.FullChargeCapacity, unit: "Wh")
        if (logEnable) log.info "FullChargeCapacity updated: ${state.FullChargeCapacity} Wh"
    }
}

def handleBattery(resp, data) {
    if (resp.hasError()) {
        log.error "Error calling battery: ${resp.getErrorMessage()}"
        return
    }
    if (resp.status != 200) {
        log.error "Error calling battery: HTTP ${resp.status}"
        return
    }

    def batt = resp.json
    if (logEnable) log.info "Battery response: ${batt}"
    if (!["stateofhealth", "cyclecount", "maximumcelltemperature", "minimumcelltemperature"].any { batt?.get(it) != null })
        log.warn "Battery endpoint returned none of the health fields"

    if (batt?.stateofhealth != null)
        sendEvent(name: "StateOfHealth", value: roundTo(batt.stateofhealth, 1), unit: "%")
    if (batt?.cyclecount != null)
        sendEvent(name: "CycleCount", value: Math.round(batt.cyclecount))
    if (batt?.maximumcelltemperature != null)
        sendTemperature("MaxCellTemperature", batt.maximumcelltemperature)
    if (batt?.minimumcelltemperature != null)
        sendTemperature("MinCellTemperature", batt.minimumcelltemperature)
}

// The API reports Celsius; convert to the hub's temperature scale
def sendTemperature(name, celsius) {
    def scale = location?.temperatureScale ?: "F"
    def value = (scale == "C") ? celsius : celsius * 9 / 5 + 32
    sendEvent(name: name, value: roundTo(value, 1), unit: "\u00B0${scale}")
}

/* ---------------------------------------------------------
   PROCESS STATUS
--------------------------------------------------------- */
def processStatus(data) {

    sendEvent(name: "Production_W", value: data.Production_W, unit: "W")
    sendEvent(name: "Consumption_W", value: data.Consumption_W, unit: "W")
    sendEvent(name: "GridFeedIn_W", value: data.GridFeedIn_W, unit: "W")
    sendEvent(name: "Pac_total_W", value: data.Pac_total_W, unit: "W")

    if (data.USOC != null)
        sendEvent(name: "battery", value: data.USOC, unit: "%")

    def power = (data.Production_W ?: 0) - (data.Consumption_W ?: 0)
    sendEvent(name: "power", value: power, unit: "W")
    sendEvent(name: "energy", value: (power / 1000), unit: "kW")

    sendEvent(name: "powerSource", value: (batteryFlow(data) > 0) ? "battery" : "mains")

    if (data.BackupBuffer != null)
        sendEvent(name: "BackupBuffer", value: data.BackupBuffer, unit: "%")

    if (data.OperatingMode != null)
        sendEvent(name: "OperatingMode", value: data.OperatingMode)

    if (data.SystemStatus != null)
        sendEvent(name: "SystemStatus", value: data.SystemStatus)
}

/* ---------------------------------------------------------
   TILES — USE STATUS DATA
--------------------------------------------------------- */
// Tiles use emoji and Unicode arrows: no external images, and short enough
// to stay well under the 1024-character dashboard attribute limit
def icon(ch, size)  { "<span style='font-size:${size}'>${ch}</span>" }
def arrow(ch, color, size) { "<span style='font-size:${size};color:${color}'>${ch}</span>" }

def tileIcons(size) {
    [sun: icon("\u2600\uFE0F", size), home: icon("\uD83C\uDFE0", size),
     batt: icon("\uD83D\uDD0B", size), grid: icon("\u26A1", size)]
}

def tileArrows(on, size) {
    def green = "#26e07f"
    def red = "#fa314a"
    [CP: on.CP ? arrow("\u2199", green, size) : "",   // sun -> home
     PB: on.PB ? arrow("\u2198", green, size) : "",   // sun -> battery
     PG: on.PG ? arrow("\u2193", green, size) : "",   // sun -> grid
     CB: on.CB ? arrow("\u2190", green, size) : "",   // battery -> home
     CG: on.CG ? arrow("\u2196", red, size) : "",     // grid -> home
     GB: on.GB ? arrow("\u2197", red, size) : ""]     // grid -> battery
}

def updateTiles(data) {
    def prod = data.Production_W ?: 0
    def cons = data.Consumption_W ?: 0
    def grid = data.GridFeedIn_W ?: 0
    def pac  = data.Pac_total_W ?: 0
    def bat  = batteryFlow(data)

    // Calculate flow booleans from power values
    def fCP = (prod > 0 && cons > 0)
    def fPB = (prod > cons && bat < 0)
    def fPG = (prod > 0 && grid > 0)
    def fCB = (bat > 0 && cons > 0)
    def fCG = (grid < 0 && cons > 0)
    def fGB = (bat < 0 && prod == 0 && grid < 0)

    def on = [CP: fCP, PB: fPB, PG: fPG, CB: fCB, CG: fCG, GB: fGB]

    def i = tileIcons("2em")
    def f = tileArrows(on, "2em")
    def large = "<table style='margin:auto;text-align:center'>"
    large += "<tr><td></td><td></td><td>${formatEnergy(prod)}</td><td></td><td></td></tr>"
    large += "<tr><td></td><td>${f.CP}</td><td>${i.sun}</td><td>${f.PB}</td><td></td></tr>"
    large += "<tr><td>${formatEnergy(cons)}</td><td>${i.home}</td><td>${f.PG}${f.CB}</td><td>${i.batt}</td><td>${formatEnergy(pac)}</td></tr>"
    large += "<tr><td></td><td>${f.CG}</td><td>${i.grid}</td><td>${f.GB}</td><td></td></tr>"
    large += "<tr><td></td><td></td><td>${formatEnergy(grid)}</td><td></td><td></td></tr>"
    large += "</table>"

    sendEvent(name: "flow_tile_large", value: large)

    i = tileIcons("1.3em")
    f = tileArrows(on, "1.3em")
    def small = "<table style='margin:auto;text-align:center'>"
    small += "<tr><td>${f.CP}</td><td>${i.sun}</td><td>${f.PB}</td></tr>"
    small += "<tr><td>${i.home}</td><td>${f.PG}${f.CB}</td><td>${i.batt}</td></tr>"
    small += "<tr><td>${f.CG}</td><td>${i.grid}</td><td>${f.GB}</td></tr>"
    small += "</table>"

    sendEvent(name: "flow_tile_small", value: small)
}


/* ---------------------------------------------------------
   CHILD DEVICES — USE STATUS DATA
--------------------------------------------------------- */
def updateChildDevices(data) {
    def prod = data.Production_W ?: 0
    def cons = data.Consumption_W ?: 0
    def grid = data.GridFeedIn_W ?: 0
    def pac  = batteryFlow(data)

    child("Sonnen Total Production").parse([[name: "energy", value: prod / 1000, unit: "kW"]])
    child("Sonnen Total Consumption").parse([[name: "energy", value: cons / 1000, unit: "kW"]])
    child("Sonnen Energy to Grid").parse([[name: "energy", value: [grid / 1000, 0].max(), unit: "kW"]])
    child("Sonnen Energy from Grid").parse([[name: "energy", value: [-grid / 1000, 0].max(), unit: "kW"]])
    child("Sonnen Energy from Battery").parse([[name: "energy", value: [pac / 1000, 0].max(), unit: "kW"]])
    child("Sonnen Energy to Battery").parse([[name: "energy", value: [-pac / 1000, 0].max(), unit: "kW"]])
}

def child(name) {
    def d = getChildDevice(name)
    if (!d) d = addChildDevice("hubitat", "Generic Component Energy Meter", name, [name: name, isComponent: false])
    return d
}

def removeChildDevices() {
    getChildDevices().each { d ->
        try {
            deleteChildDevice(d.deviceNetworkId)
            if (logEnable) log.info "Removed child device ${d.displayName}"
        } catch (e) {
            log.warn "Could not remove child device ${d.displayName}: ${e.message}"
        }
    }
}

// Called by the Generic Component driver when Refresh is pressed on a child
def componentRefresh(cd) {
    refresh()
}

/* ---------------------------------------------------------
   CHARGE ESTIMATION
--------------------------------------------------------- */
def estimateCharge(data) {
    def cap = state.FullChargeCapacity ?: 0        // Wh
    def usoc = data.USOC ?: 0                      // %
    def pac = batteryFlow(data)                    // W (+ discharge, - charge)

    // Wh currently stored
    def remainingWh = Math.round(cap * (usoc / 100))
    sendEvent(name: "RemainingCapacity_Wh", value: remainingWh, unit: "Wh")


    // Wh needed to reach 100%
    def neededWh = Math.round(cap - remainingWh)

    // Time to full (minutes)
    def tCharge = 0
    if (pac < 0) {
        def chargePower = Math.abs(pac)   // convert negative to positive
        tCharge = Math.round((neededWh / chargePower) * 60)
    }

    // Time to empty (minutes)
    def tDischarge = 0
    if (pac > 0) {
        def dischargePower = pac
        tDischarge = Math.round((remainingWh / dischargePower) * 60)
    }

    sendEvent(name: "MinutesToCharge", value: tCharge, unit: "min")
    sendEvent(name: "MinutesToDischarge", value: tDischarge, unit: "min")
}


/* ---------------------------------------------------------
   BACKUP BUFFER COMMAND
--------------------------------------------------------- */
def setBackupBuffer(buffer) {
    // Valid values: 0 (disabled) or a whole number from 5 to 100
    def value = null
    try {
        def bd = new BigDecimal(buffer.toString())
        if (bd.stripTrailingZeros().scale() <= 0) value = bd.intValue()
    } catch (e) { }

    if (value == null || !(value == 0 || (value >= 5 && value <= 100))) {
        log.error "Invalid Backup Buffer ${buffer}: use 0 (disabled) or a whole number from 5 to 100"
        return
    }

    asynchttpPut("handleBackupBuffer", [
        uri: "http://${battery_ip_address}/api/v2/site/configurations",
        requestContentType: "application/json",
        contentType: "application/json",
        headers: ['Auth-Token': apiKey],
        body: [EM_USOC: value.toString()],
        timeout: 10
    ], [value: value])
}

def handleBackupBuffer(resp, data) {
    if (resp.hasError()) {
        log.error "BackupBuffer error: ${resp.getErrorMessage()}"
        return
    }
    if (resp.status != 200) {
        log.error "BackupBuffer error: HTTP ${resp.status}"
        return
    }

    log.info "BackupBuffer set to ${data.value}%"
    refresh()
}

/* ---------------------------------------------------------
   HELPERS
--------------------------------------------------------- */
// Battery power with standby draw filtered out (about -25 W when idle):
// anything within 50 W of zero counts as not charging or discharging
def batteryFlow(data) {
    def pac = data.Pac_total_W ?: 0
    return (pac.abs() < 50) ? 0 : pac
}

def roundTo(n, places) {
    new BigDecimal(n.toString()).setScale(places, BigDecimal.ROUND_HALF_UP)
}

def formatEnergy(e) {
    if (e == null) return "0 W"
    if (e.abs() < 1000) return "${e} W"
    if (e.abs() < 1_000_000) return "${(e/1000).toDouble().round(2)} kW"
    return "${(e/1_000_000).toDouble().round(2)} MW"
}
